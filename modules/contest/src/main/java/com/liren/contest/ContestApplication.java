package com.liren.contest;

import com.liren.api.problem.api.ProblemInterface;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(clients = {ProblemInterface.class})
@SpringBootApplication
public class ContestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContestApplication.class, args);
    }
}