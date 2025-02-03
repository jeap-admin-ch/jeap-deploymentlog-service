package ch.admin.bit.jeap.deploymentlog.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class DeploymentLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeploymentLogApplication.class, args);
    }
}
