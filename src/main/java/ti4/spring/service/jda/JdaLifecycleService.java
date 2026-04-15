package ti4.spring.service.jda;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;

@Service
@RequiredArgsConstructor
class JdaLifecycleService {

  private final ApplicationArguments applicationArguments;

  @PostConstruct
  private void init() {
    JdaService.initialize(applicationArguments.getSourceArgs());
  }

  @PreDestroy
  private void shutdown() {
    JdaService.shutdown();
  }
}
