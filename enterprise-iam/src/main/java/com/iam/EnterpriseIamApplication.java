package com.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

import com.iam.config.AppProperties;

@SpringBootApplication
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties(AppProperties.class)
public class EnterpriseIamApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseIamApplication.class, args);
    }
}
