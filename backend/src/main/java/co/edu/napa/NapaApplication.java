package co.edu.napa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NapaApplication {
    public static void main(String[] args) {
        SpringApplication.run(NapaApplication.class, args);
    }
}
