package com.terp.erp.meeting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Scans com.terp.erp so the shared beans of erp-common (PermissionChecker, exception handler, trace filter) load.
@SpringBootApplication(scanBasePackages = "com.terp.erp")
@ConfigurationPropertiesScan
public class MeetingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeetingApplication.class, args);
    }
}
