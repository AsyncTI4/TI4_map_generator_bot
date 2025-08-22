package ti4.spring.configuration;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*

can you explain @/src/main/java/ti4/spring/configuration/MetricsConfiguration.java?
It doesn't seem to be wired correctly. We can't find "MeterRegistry". Maybe we should
declare a concrete type instead? It works locally, but not in docker.

> Root cause
> Your code in MetricsConfiguration.java is correct. The failure to inject MeterRegistry
> is almost certainly due to packaging: your build uses maven-jar-plugin + maven-assembly-plugin
> to create a fat JAR. These tools do not merge Spring Boot’s auto-configuration resource
> files (META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
> by default. As a result, Spring Boot’s Micrometer auto-configuration classes are not discovered
> at runtime, and no MeterRegistry bean is created, causing the UnsatisfiedDependencyException.
>
> Why this happens
>
> Spring Boot 3 uses resource-based autoconfiguration discovery via AutoConfiguration.imports.
> When you build an uber JAR with the assembly plugin, duplicate resource files are typically
> overwritten rather than merged. That drops many auto-config entries (including Micrometer’s),
> so @EnableAutoConfiguration does not import them.
>
> Locally in IDE (classpath run), it would work because individual JARs contribute their own
> resource entries. The error surfaces in the packaged/assembled JAR.
>
> Preferred fix (use Spring Boot packaging)
>
> Replace assembly-based fat jar with Spring Boot’s packaging. This correctly merges metadata
> and produces an executable boot-jar.
> In pom.xml, add the Spring Boot repackage plugin and remove the assembly fat-jar execution
> for runtime distribution.
> Keep your existing plain jar if you need it, but deploy/run the boot-jar.
>
> Example plugin block (replace the assembly usage for runtime with this):
>
> - Add spring-boot-maven-plugin:
>     - groupId: org.springframework.boot
>     - artifactId: spring-boot-maven-plugin
>     - execution: repackage
>
>     Alternative fix (if you must keep an uber JAR)
>
> Switch to maven-shade-plugin and add resource transformers that merge Boot 3’s metadata:
> Merge META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
> Merge META-INF/spring/org.springframework.boot.actuate.autoconfigure/… if present
> For any libs still using Spring Boot 2-style, also merge META-INF/spring.factories This
> ensures all auto-configurations remain discoverable in the shaded jar.
>
> --------------------------------------
>
> Interim workaround (not recommended long-term)
>
> Manually provide a registry bean so injection succeeds, e.g., define a PrometheusMeterRegistry
> bean in a configuration class and keep the parameter type as MeterRegistry. This unblocks
> startup but bypasses Boot’s CompositeMeterRegistry and can lead to double registration once
> packaging is fixed. Use only while migrating packaging.

Temporary manual Prometheus MeterRegistry wiring.
Provides Prometheus-backed MeterRegistry if Boot autoconfiguration doesn't produce one
(e.g., when packaging drops auto-config metadata).
Uses @ConditionalOnMissingBean so it will not conflict once proper Boot autoconfiguration
is restored; then Boot will supply its managed (Composite) registry. */
@Configuration
public class ManualPrometheusRegistryConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public PrometheusConfig prometheusConfig() {
        return PrometheusConfig.DEFAULT;
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry(PrometheusConfig config, Clock clock) {
        // Use the default Prometheus CollectorRegistry to ensure Actuator (if present) sees the same registry.
        return new PrometheusMeterRegistry(config);
    }
}
