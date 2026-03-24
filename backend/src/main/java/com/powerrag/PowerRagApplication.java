package com.powerrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PowerRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerRagApplication.class, args);
    }
}
