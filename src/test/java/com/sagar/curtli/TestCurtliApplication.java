package com.sagar.curtli;

import org.springframework.boot.SpringApplication;

public class TestCurtliApplication {

	public static void main(String[] args) {
		SpringApplication.from(CurtliApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
