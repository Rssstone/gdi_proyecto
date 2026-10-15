package com.gdi.citas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CitasApplication {
    public static void main(String[] args) {
        SpringApplication.run(CitasApplication.class, args);
    }
}
