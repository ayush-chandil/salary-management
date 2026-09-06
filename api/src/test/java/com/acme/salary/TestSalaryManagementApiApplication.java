package com.acme.salary;

import org.springframework.boot.SpringApplication;

public class TestSalaryManagementApiApplication {

  public static void main(String[] args) {
    SpringApplication.from(SalaryManagementApiApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
