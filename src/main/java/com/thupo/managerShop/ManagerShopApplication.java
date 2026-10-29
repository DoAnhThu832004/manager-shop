package com.thupo.managerShop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ManagerShopApplication {

	public static void main(String[] args) {
		SpringApplication.run(ManagerShopApplication.class, args);
	}

}
