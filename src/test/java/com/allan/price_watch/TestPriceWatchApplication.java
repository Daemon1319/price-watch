package com.allan.price_watch;

import org.springframework.boot.SpringApplication;

public class TestPriceWatchApplication {

	public static void main(String[] args) {
		SpringApplication.from(PriceWatchApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
