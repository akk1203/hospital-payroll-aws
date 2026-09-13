package com.hospital.payroll.service;

import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.Employee;
import com.hospital.payroll.model.NameMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EmployeeMatcher {

    @Inject
    DynamoPayrollStore store;

    public MatchResult match(String sourceCode, String sourceName) {
        List<Employee> employees = store.listActiveEmployees();
        List<NameMapping> mappings = store.listMappings();
        if (sourceCode != null && !sourceCode.isBlank()) {
            Optional<Employee> byCode = employees.stream()
                    .filter(e -> sourceCode.trim().equals(e.getAttendanceCode()))
                    .findFirst();
            if (byCode.isPresent()) {
                return MatchResult.matched(byCode.get(), "attendance code " + sourceCode);
            }
            Optional<NameMapping> mappedCode = mappings.stream()
                    .filter(m -> sourceCode.trim().equals(m.getSourceCode()))
                    .findFirst();
            if (mappedCode.isPresent()) {
                return employees.stream()
                        .filter(e -> e.getId().equals(mappedCode.get().getEmployeeId()))
                        .findFirst()
                        .map(emp -> MatchResult.matched(emp, "saved code mapping"))
                        .orElse(MatchResult.unmatched());
            }
        }

        String normalized = NameNormalizer.normalize(sourceName);
        if (!normalized.isEmpty()) {
            Optional<NameMapping> saved = mappings.stream()
                    .filter(m -> normalized.equals(m.getSourceNameNormalized()))
                    .findFirst();
            if (saved.isPresent()) {
                return employees.stream()
                        .filter(e -> e.getId().equals(saved.get().getEmployeeId()))
                        .findFirst()
                        .map(emp -> MatchResult.matched(emp, "saved name mapping"))
                        .orElse(MatchResult.unmatched());
            }
            Employee best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Employee employee : employees) {
                String empNorm = NameNormalizer.normalize(employee.getName());
                if (empNorm.equals(normalized)) {
                    return MatchResult.matched(employee, "exact name");
                }
                int distance = levenshtein(empNorm, normalized);
                int threshold = Math.max(1, Math.min(empNorm.length(), normalized.length()) / 5);
                if (distance <= threshold && distance < bestDistance) {
                    bestDistance = distance;
                    best = employee;
                }
            }
            if (best != null) {
                return MatchResult.matched(best, "similar name");
            }
        }
        return MatchResult.unmatched();
    }

    public void saveMapping(String sourceCode, String sourceName, String employeeId) {
        String normalized = NameNormalizer.normalize(sourceName);
        NameMapping mapping = store.findMappingByNormalized(normalized).orElseGet(NameMapping::new);
        mapping.setSourceName(sourceName);
        mapping.setSourceNameNormalized(normalized);
        mapping.setSourceCode(sourceCode);
        mapping.setEmployeeId(employeeId);
        store.saveMapping(mapping);
    }

    static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    public record MatchResult(Employee employee, String reason) {
        static MatchResult unmatched() {
            return new MatchResult(null, null);
        }

        static MatchResult matched(Employee employee, String reason) {
            return new MatchResult(employee, reason);
        }

        public boolean isMatched() {
            return employee != null;
        }
    }
}
