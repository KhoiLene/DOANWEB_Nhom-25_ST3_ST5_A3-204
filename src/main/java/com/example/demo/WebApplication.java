package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.shopchain")
@EnableScheduling
@EntityScan(basePackages = "com.shopchain.entity")
@EnableJpaRepositories(basePackages = "com.shopchain.repository")
public class WebApplication {

	static {
		// PostgreSQL recognizes Asia/Ho_Chi_Minh, unlike the legacy Asia/Saigon ID.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
	}

	public static void main(String[] args) {
		LocalDockerServices.startIfNeeded();
		SpringApplication.run(WebApplication.class, args);
	}

}
