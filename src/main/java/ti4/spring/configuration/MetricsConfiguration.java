package ti4.spring.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * MetricsConfiguration: wires the Spring Boot MeterRegistry into SREStats so custom Micrometer counters
 * are registered on the application's Prometheus-enabled registry and appear at /actuator/prometheus.
 *
 * What this file does:
 * - Provides a Spring @Configuration that runs at startup and initializes SREStats with the managed MeterRegistry.
 *
 * How it does it:
 * - Declares a CommandLineRunner bean that receives the auto-configured MeterRegistry and calls SREStats.init(...).
 * - This ensures SREStats does NOT fall back to SimpleMeterRegistry (which is not exported) and instead uses the
 *   same registry Spring Actuator Prometheus endpoint exports.
 */
@Configuration
public class MetricsConfiguration {

    @Bean
    CommandLineRunner initSREStats(MeterRegistry meterRegistry) {
        return args -> {
            // Initialize the static metrics utility with the Actuator-managed registry.
            ti4.service.statistics.SREStats.init(meterRegistry);
        };
    }
}
