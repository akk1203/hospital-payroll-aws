package com.hospital.payroll.model;


import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AttendanceBatch {

    private String id;
    private String originalFilename;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Instant uploadedAt = Instant.now();
    private int totalRows;
    private int mappedPeople;
    private int unmappedPeople;
    private String peopleS3Key;
    private List<AttendancePerson> people = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getMappedPeople() {
        return mappedPeople;
    }

    public void setMappedPeople(int mappedPeople) {
        this.mappedPeople = mappedPeople;
    }

    public int getUnmappedPeople() {
        return unmappedPeople;
    }

    public void setUnmappedPeople(int unmappedPeople) {
        this.unmappedPeople = unmappedPeople;
    }

    public String getPeopleS3Key() {
        return peopleS3Key;
    }

    public void setPeopleS3Key(String peopleS3Key) {
        this.peopleS3Key = peopleS3Key;
    }

    public List<AttendancePerson> getPeople() {
        return people;
    }

    public void setPeople(List<AttendancePerson> people) {
        this.people = people;
    }
}
