package com.bettercloud.pf.rl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
    public Faker faker() {
	    return new Faker();
    }

	@Bean
	public RouterFunction<ServerResponse> route(RateLimitedHandler handler) {
		return RouterFunctions.route(RequestPredicates.method(HttpMethod.GET), handler::rateLimitHandler);
	}
}
