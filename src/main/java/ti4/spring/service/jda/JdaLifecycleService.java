package ti4.spring.service.jda;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;

@Service
@RequiredArgsConstructor
public class JdaLifecycleService {

    private final ApplicationArguments applicationArguments;

    @PreDestroy
    private void shutdown() {
        JdaService.shutdown();
    }

    public String[] resolveSourceArgs() {
        String[] sourceArgs = applicationArguments.getSourceArgs();
        if (sourceArgs.length >= 3) {
            return sourceArgs;
        }

        // Compatibility bridge: the legacy startup path passes Discord config as positional args,
        // while the Compose/docker-rollout deployment path provides the same values via env vars.
        String botToken = System.getenv("DISCORD_BOT_TOKEN");
        String botUserId = System.getenv("DISCORD_BOT_USERID");
        String guildIdList = System.getenv("GUILDID_LIST");
        if (isBlank(botToken) || isBlank(botUserId) || isBlank(guildIdList)) {
            return sourceArgs;
        }

        List<String> resolvedArgs = new ArrayList<>();
        resolvedArgs.add(botToken);
        resolvedArgs.add(botUserId);
        resolvedArgs.addAll(Arrays.asList(guildIdList.trim().split("\\s+")));
        return resolvedArgs.toArray(String[]::new);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
