package com.hospital.payroll.model;


public class NameMapping {

    private String id;

    private String sourceNameNormalized;

    private String sourceName;
    private String sourceCode;
    private String employeeId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceNameNormalized() {
        return sourceNameNormalized;
    }

    public void setSourceNameNormalized(String sourceNameNormalized) {
        this.sourceNameNormalized = sourceNameNormalized;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }
}
