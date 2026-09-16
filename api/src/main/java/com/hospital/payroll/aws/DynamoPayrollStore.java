package com.hospital.payroll.aws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.payroll.model.Advance;
import com.hospital.payroll.model.AttendanceBatch;
import com.hospital.payroll.model.AttendancePerson;
import com.hospital.payroll.model.Employee;
import com.hospital.payroll.model.NameMapping;
import com.hospital.payroll.model.Payslip;
import com.hospital.payroll.model.SavedDayAdjustment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DynamoPayrollStore {

    @Inject DynamoDbClient dynamoDb;
    @Inject S3Client s3;
    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "payroll.aws.employees-table")
    String employeesTable;
    @ConfigProperty(name = "payroll.aws.batches-table")
    String batchesTable;
    @ConfigProperty(name = "payroll.aws.payslips-table")
    String payslipsTable;
    @ConfigProperty(name = "payroll.aws.adjustments-table")
    String adjustmentsTable;
    @ConfigProperty(name = "payroll.aws.mappings-table")
    String mappingsTable;
    @ConfigProperty(name = "payroll.aws.advances-table")
    String advancesTable;
    @ConfigProperty(name = "payroll.aws.data-bucket")
    String dataBucket;

    public List<Employee> listActiveEmployees() {
        return scanJson(employeesTable, Employee.class).stream()
                .filter(Employee::isActive)
                .sorted(Comparator.comparing(e -> e.getName() == null ? "" : e.getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<Employee> findEmployee(String id) {
        return getJson(employeesTable, Map.of("id", s(id)), Employee.class);
    }

    public Optional<Employee> findEmployeeByCode(String code) {
        return listActiveEmployees().stream()
                .filter(e -> code != null && code.equals(e.getAttendanceCode()))
                .findFirst();
    }

    public Employee saveEmployee(Employee employee) {
        if (employee.getId() == null || employee.getId().isBlank()) {
            employee.setId(UUID.randomUUID().toString());
        }
        putJson(employeesTable, Map.of(
                "id", s(employee.getId()),
                "attendanceCode", s(nullToEmpty(employee.getAttendanceCode())),
                "active", AttributeValue.fromBool(employee.isActive()),
                "overtimeEligible", AttributeValue.fromBool(employee.getOvertimeEligible())
        ), employee);
        return employee;
    }

    public List<NameMapping> listMappings() {
        return scanJson(mappingsTable, NameMapping.class);
    }

    public Optional<NameMapping> findMappingByNormalized(String normalized) {
        return getJson(mappingsTable, Map.of("sourceNameNormalized", s(normalized)), NameMapping.class);
    }

    public Optional<NameMapping> findMappingByCode(String code) {
        return listMappings().stream().filter(m -> code != null && code.equals(m.getSourceCode())).findFirst();
    }

    public void saveMapping(NameMapping mapping) {
        if (mapping.getId() == null || mapping.getId().isBlank()) {
            mapping.setId(UUID.randomUUID().toString());
        }
        putJson(mappingsTable, Map.of(
                "sourceNameNormalized", s(mapping.getSourceNameNormalized()),
                "sourceCode", s(nullToEmpty(mapping.getSourceCode()))
        ), mapping);
    }

    public AttendanceBatch saveBatch(AttendanceBatch batch) {
        if (batch.getId() == null || batch.getId().isBlank()) {
            batch.setId(UUID.randomUUID().toString());
        }
        String key = "batches/" + batch.getId() + ".json";
        try {
            byte[] body = mapper.writeValueAsBytes(batch.getPeople() == null ? List.of() : batch.getPeople());
            s3.putObject(PutObjectRequest.builder()
                    .bucket(dataBucket)
                    .key(key)
                    .contentType("application/json")
                    .build(), RequestBody.fromBytes(body));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store attendance days", ex);
        }
        batch.setPeopleS3Key(key);
        List<AttendancePerson> people = batch.getPeople();
        putJson(batchesTable, Map.of(
                "id", s(batch.getId()),
                "uploadedAt", s(batch.getUploadedAt() == null ? "" : batch.getUploadedAt().toString())
        ), copyMeta(batch));
        batch.setPeople(people);
        return batch;
    }

    public Optional<AttendanceBatch> findBatch(String id) {
        return getJson(batchesTable, Map.of("id", s(id)), AttendanceBatch.class).map(this::loadPeople);
    }

    public List<AttendanceBatch> listBatches() {
        return scanJson(batchesTable, AttendanceBatch.class).stream()
                .sorted(Comparator.comparing(AttendanceBatch::getUploadedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private AttendanceBatch loadPeople(AttendanceBatch batch) {
        if (batch.getPeopleS3Key() == null || batch.getPeopleS3Key().isBlank()) {
            return batch;
        }
        try {
            byte[] json = s3.getObject(GetObjectRequest.builder()
                    .bucket(dataBucket)
                    .key(batch.getPeopleS3Key())
                    .build(), ResponseTransformer.toBytes()).asByteArray();
            batch.setPeople(mapper.readValue(json, new TypeReference<List<AttendancePerson>>() {}));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load attendance days", ex);
        }
        return batch;
    }

    public Optional<Payslip> findPayslip(String id) {
        return getJson(payslipsTable, Map.of("id", s(id)), Payslip.class);
    }

    public List<Payslip> listPayslips(String month) {
        var response = dynamoDb.query(QueryRequest.builder()
                .tableName(payslipsTable)
                .indexName("MonthIndex")
                .keyConditionExpression("#m = :m")
                .expressionAttributeNames(Map.of("#m", "month"))
                .expressionAttributeValues(Map.of(":m", s(month)))
                .build());
        List<Payslip> slips = new ArrayList<>();
        for (var item : response.items()) {
            fromItem(item, Payslip.class).ifPresent(slips::add);
        }
        slips.sort(Comparator.comparing(p -> p.getEmployeeName() == null ? "" : p.getEmployeeName()));
        return slips;
    }

    public void replacePayslips(String month, List<Payslip> slips) {
        for (Payslip existing : listPayslips(month)) {
            dynamoDb.deleteItem(DeleteItemRequest.builder()
                    .tableName(payslipsTable)
                    .key(Map.of("id", s(existing.getId())))
                    .build());
        }
        for (Payslip slip : slips) {
            savePayslip(slip);
        }
    }

    public Payslip savePayslip(Payslip slip) {
        if (slip.getId() == null || slip.getId().isBlank()) {
            slip.setId(UUID.randomUUID().toString());
        }
        putJson(payslipsTable, Map.of(
                "id", s(slip.getId()),
                "month", s(slip.getMonth())
        ), slip);
        return slip;
    }

    public Optional<SavedDayAdjustment> findAdjustment(String employeeId, LocalDate date) {
        return getJson(adjustmentsTable, Map.of(
                "employeeId", s(employeeId),
                "date", s(date.toString())
        ), SavedDayAdjustment.class);
    }

    public List<SavedDayAdjustment> listAdjustments(String employeeId) {
        var response = dynamoDb.query(QueryRequest.builder()
                .tableName(adjustmentsTable)
                .keyConditionExpression("employeeId = :e")
                .expressionAttributeValues(Map.of(":e", s(employeeId)))
                .build());
        List<SavedDayAdjustment> items = new ArrayList<>();
        for (var item : response.items()) {
            fromItem(item, SavedDayAdjustment.class).ifPresent(items::add);
        }
        return items;
    }

    public void saveAdjustment(SavedDayAdjustment saved) {
        putJson(adjustmentsTable, Map.of(
                "employeeId", s(saved.getEmployeeId()),
                "date", s(saved.getDate().toString())
        ), saved);
    }

    public List<Advance> listAdvances() {
        return scanJson(advancesTable, Advance.class).stream()
                .sorted(Comparator
                        .comparing((Advance a) -> a.getMonth() == null ? "" : a.getMonth())
                        .reversed()
                        .thenComparing(a -> a.getGivenOn() == null ? LocalDate.MIN : a.getGivenOn(), Comparator.reverseOrder()))
                .toList();
    }

    public List<Advance> listAdvancesByMonth(String month) {
        var response = dynamoDb.query(QueryRequest.builder()
                .tableName(advancesTable)
                .indexName("MonthIndex")
                .keyConditionExpression("#m = :m")
                .expressionAttributeNames(Map.of("#m", "month"))
                .expressionAttributeValues(Map.of(":m", s(month)))
                .build());
        List<Advance> items = new ArrayList<>();
        for (var item : response.items()) {
            fromItem(item, Advance.class).ifPresent(items::add);
        }
        items.sort(Comparator.comparing((Advance a) -> a.getEmployeeName() == null ? "" : a.getEmployeeName(),
                String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    public List<Advance> listAdvancesByEmployeeAndMonth(String employeeId, String month) {
        return listAdvancesByMonth(month).stream()
                .filter(a -> employeeId != null && employeeId.equals(a.getEmployeeId()))
                .toList();
    }

    public Optional<Advance> findAdvance(String id) {
        return getJson(advancesTable, Map.of("id", s(id)), Advance.class);
    }

    public Advance saveAdvance(Advance advance) {
        if (advance.getId() == null || advance.getId().isBlank()) {
            advance.setId(UUID.randomUUID().toString());
        }
        putJson(advancesTable, Map.of(
                "id", s(advance.getId()),
                "month", s(advance.getMonth()),
                "employeeId", s(nullToEmpty(advance.getEmployeeId()))
        ), advance);
        return advance;
    }

    public void deleteAdvance(String id) {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(advancesTable)
                .key(Map.of("id", s(id)))
                .build());
    }

    public Map<String, Long> deleteDemoData() {
        Map<String, Long> deleted = new HashMap<>();
        deleted.put("attendance_batches", deleteAll(batchesTable, "id"));
        deleted.put("payslips", deleteAll(payslipsTable, "id"));
        deleted.put("name_mappings", deleteAll(mappingsTable, "sourceNameNormalized"));
        long adj = 0;
        for (var item : scanRaw(adjustmentsTable)) {
            dynamoDb.deleteItem(DeleteItemRequest.builder()
                    .tableName(adjustmentsTable)
                    .key(Map.of("employeeId", item.get("employeeId"), "date", item.get("date")))
                    .build());
            adj++;
        }
        deleted.put("day_adjustments", adj);
        return deleted;
    }

    private AttendanceBatch copyMeta(AttendanceBatch batch) {
        AttendanceBatch meta = new AttendanceBatch();
        meta.setId(batch.getId());
        meta.setOriginalFilename(batch.getOriginalFilename());
        meta.setPeriodStart(batch.getPeriodStart());
        meta.setPeriodEnd(batch.getPeriodEnd());
        meta.setUploadedAt(batch.getUploadedAt());
        meta.setTotalRows(batch.getTotalRows());
        meta.setMappedPeople(batch.getMappedPeople());
        meta.setUnmappedPeople(batch.getUnmappedPeople());
        meta.setPeopleS3Key(batch.getPeopleS3Key());
        return meta;
    }

    private long deleteAll(String table, String pk) {
        long count = 0;
        for (var item : scanRaw(table)) {
            dynamoDb.deleteItem(DeleteItemRequest.builder()
                    .tableName(table)
                    .key(Map.of(pk, item.get(pk)))
                    .build());
            count++;
        }
        return count;
    }

    private List<Map<String, AttributeValue>> scanRaw(String table) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            var response = dynamoDb.scan(ScanRequest.builder().tableName(table).exclusiveStartKey(start).build());
            items.addAll(response.items());
            start = response.lastEvaluatedKey();
            if (start != null && start.isEmpty()) start = null;
        } while (start != null);
        return items;
    }

    private <T> List<T> scanJson(String table, Class<T> type) {
        List<T> items = new ArrayList<>();
        for (var item : scanRaw(table)) {
            fromItem(item, type).ifPresent(items::add);
        }
        return items;
    }

    private <T> Optional<T> getJson(String table, Map<String, AttributeValue> key, Class<T> type) {
        var response = dynamoDb.getItem(GetItemRequest.builder().tableName(table).key(key).build());
        if (!response.hasItem() || response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return fromItem(response.item(), type);
    }

    private void putJson(String table, Map<String, AttributeValue> keys, Object value) {
        try {
            Map<String, AttributeValue> item = new HashMap<>(keys);
            item.put("json", s(mapper.writeValueAsString(value)));
            dynamoDb.putItem(PutItemRequest.builder().tableName(table).item(item).build());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not write " + table, ex);
        }
    }

    private <T> Optional<T> fromItem(Map<String, AttributeValue> item, Class<T> type) {
        AttributeValue json = item.get("json");
        if (json == null || json.s() == null) {
            return Optional.empty();
        }
        try {
            T value = mapper.readValue(json.s(), type);
            if (value instanceof Employee employee) {
                AttributeValue overtime = item.get("overtimeEligible");
                if (overtime != null && overtime.bool() != null && overtime.bool()) {
                    employee.setOvertimeEligible(true);
                }
            }
            return Optional.of(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read " + type.getSimpleName(), ex);
        }
    }

    private static AttributeValue s(String value) {
        return AttributeValue.fromS(value == null ? "" : value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
