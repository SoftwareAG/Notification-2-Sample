package com.c8y.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;

@ComponentScan(basePackages = { "com.c8y.notification" })
@MicroserviceApplication
public class App {
	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}
}