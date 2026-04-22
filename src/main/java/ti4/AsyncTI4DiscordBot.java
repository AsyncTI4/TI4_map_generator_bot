package ti4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import ti4.discord.JdaService;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.migration.DataMigrationManager;
import ti4.logging.BotLogger;
import ti4.logging.RollbarManager;
import ti4.settings.GlobalSettings;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.service.jda.JdaLifecycleService;

@EnableScheduling
@SpringBootApplication
public class AsyncTI4DiscordBot {

    public static final long START_TIME_MILLISECONDS = System.currentTimeMillis();

    public static void main(String[] args) {
        GlobalSettings.loadSettings();
        RollbarManager.init();
        BotLogger.info("\n# __BOT IS STARTING UP__");

        ConfigurableApplicationContext applicationContext = SpringApplication.run(AsyncTI4DiscordBot.class, args);
        applicationContext.getBean(JdaLifecycleService.class);
        ActiveLeaseService activeLeaseService = applicationContext.getBean(ActiveLeaseService.class);

        String[] resolvedArgs = resolveSourceArgs(args);
        JdaService.startJdaAndRegisterListeners(resolvedArgs);
        if (!JdaService.waitForJdaReadyAndInitializeGuilds(resolvedArgs)) {
            applicationContext.close();
            throw new IllegalStateException("Failed to initialize JDA and guilds");
        }
        JdaService.loadStaticDataAndResources();
        JdaService.indexGameNames();
        activeLeaseService.beginLeaseParticipation(AsyncTI4DiscordBot::runLeaseOwnedStartupWork);
        JdaService.registerAndStartCronJobs();
        JdaService.markProcessReady();
    }

    static void runLeaseOwnedStartupWork() {
        if (DataMigrationManager.runMigrations()) {
            BotLogger.info("FINISHED RUNNING MIGRATIONS");
        }

        if (GameManager.startManagedGamesWarmupIfNeeded()) {
            BotLogger.info("STARTED BACKGROUND MANAGED GAME WARMUP");
        } else {
            BotLogger.info("DEFERRED BACKGROUND MANAGED GAME WARMUP UNTIL THIS PROCESS BECOMES ACTIVE");
        }
    }

    private static String[] resolveSourceArgs(String[] sourceArgs) {
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
