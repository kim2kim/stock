package com.fun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.fun.controller"})
public class Application {
    public static void main(String[] args) throws Exception {
    	ApplicationContext ctx = SpringApplication.run(Application.class, args);   	
    }
}
