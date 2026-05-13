package com.monitor.call;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CallApplication {

	public static void main(String[] args) {
		SpringApplication.run(CallApplication.class, args);
	}

}
