package com.e24online.mdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the 24online MDM service.
 * This application uses Spring Data JDBC for database access.
 */
@SpringBootApplication
public class OnlineMdmApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineMdmApplication.class, args);
    }
}

