package ti4.spring.service.jda;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;

@Service
public class JdaLifecycleService {

    @PreDestroy
    private void shutdown() {
        JdaService.shutdown();
    }
}
