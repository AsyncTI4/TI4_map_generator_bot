package ti4.spring.service.jda;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;

@Service
@DependsOn("activeLeaseService")
public class JdaLifecycleService {

    @PreDestroy
    private static void shutdown() {
        JdaService.shutdown();
    }
}
