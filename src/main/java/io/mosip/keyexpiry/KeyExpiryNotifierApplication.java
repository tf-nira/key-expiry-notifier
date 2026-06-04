package io.mosip.keyexpiry;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@ComponentScan(basePackages = {"io.mosip.*", "${mosip.auth.adapter.impl.basepackage}"})

@SpringBootApplication
@EnableScheduling
public class KeyExpiryNotifierApplication {
	public static void main(String args[]) {
		new SpringApplicationBuilder(KeyExpiryNotifierApplication.class)
		.web(WebApplicationType.NONE)
		.run(args);
	}
	
	 @Bean
	    CommandLineRunner test() {
	        return args -> {
	            System.out.println("Spring Boot started successfully!");
	        };
	    }
}
