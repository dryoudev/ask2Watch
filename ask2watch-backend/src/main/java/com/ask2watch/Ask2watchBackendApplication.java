package com.ask2watch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
public class Ask2watchBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(Ask2watchBackendApplication.class, args);
	}

}
