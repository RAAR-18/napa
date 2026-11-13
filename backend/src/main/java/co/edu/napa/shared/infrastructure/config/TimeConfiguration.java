package co.edu.napa.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfiguration {
    @Bean
    Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
