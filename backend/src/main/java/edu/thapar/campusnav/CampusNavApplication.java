package edu.thapar.campusnav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CampusNav — Smart Campus Navigation & Route Optimization System
 * for Thapar Institute of Engineering and Technology, Patiala.
 *
 * Start with:  mvn spring-boot:run   (from the backend folder)
 * Then open:   http://localhost:8088/app
 */
@SpringBootApplication
public class CampusNavApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusNavApplication.class, args);
    }
}
