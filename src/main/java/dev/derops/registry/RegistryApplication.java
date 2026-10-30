package dev.derops.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs embedded for development ({@code mvn spring-boot:run}) and as a WAR on an
 * external Apache Tomcat 10.1 in production.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RegistryApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(RegistryApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(RegistryApplication.class);
    }
}
