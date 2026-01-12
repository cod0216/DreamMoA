package com.garret.dreammoa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
@ComponentScan(basePackages = {"com.garret.dreammoa", "com.example.openaitest.config"})
@SpringBootApplication
@EnableScheduling
public class DreamMoaApplication {

	public static void main(String[] args) {
		SpringApplication.run(DreamMoaApplication.class, args);
//		String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
	}
}
