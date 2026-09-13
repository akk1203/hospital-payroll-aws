package com.hospital.payroll.service;

import com.hospital.payroll.aws.DynamoPayrollStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@ApplicationScoped
public class DemoResetService {

    @Inject
    DynamoPayrollStore store;

    @ConfigProperty(name = "payroll.demo.reset-enabled", defaultValue = "false")
    boolean resetEnabled;

    @ConfigProperty(name = "payroll.environment", defaultValue = "dev")
    String environment;

    public boolean isResetEnabled() {
        return resetEnabled && !"prod".equalsIgnoreCase(environment);
    }

    public Map<String, Object> status() {
        return Map.of(
                "resetEnabled", isResetEnabled(),
                "environment", environment
        );
    }

    public Map<String, Object> reset() {
        if (!isResetEnabled()) {
            throw new DemoResetNotAllowedException("Demo data reset is only available in non-production");
        }
        return Map.of(
                "message", "Demo data cleared. Employees were kept.",
                "deleted", store.deleteDemoData()
        );
    }
}
