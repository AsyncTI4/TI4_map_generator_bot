package ti4.ai.runtime;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("jdaLifecycleService")
class AiRuntimeLifecycle {

    @PostConstruct
    void start() {
        AiRuntime.start();
    }

    @PreDestroy
    void stop() {
        AiRuntime.shutdown();
    }
}
