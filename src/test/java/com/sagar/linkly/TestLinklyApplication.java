package com.sagar.linkly;

import org.springframework.boot.SpringApplication;

public class TestLinklyApplication {

	public static void main(String[] args) {
		SpringApplication.from(LinklyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
