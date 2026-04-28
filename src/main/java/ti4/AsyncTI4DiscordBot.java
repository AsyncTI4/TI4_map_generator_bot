package ti4;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.ButtonProcessor;
import ti4.discord.interactions.listeners.ModalListener;
import ti4.discord.interactions.selections.SelectionMenuProcessor;
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
    public static final String INSTANCE_ID = UUID.randomUUID().toString();
    public static final String SHORT_INSTANCE_ID = INSTANCE_ID.substring(0, 8);
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

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
        BotLogger.info("WARMING INTERACTION HANDLERS");
        ButtonProcessor.checkButtonHandlersSetup();
        SelectionMenuProcessor.checkSelectionMenuHandlersSetup();
        ModalListener.checkModalHandlersSetup();
        BotLogger.info("FINISHED WARMING INTERACTION HANDLERS");
        activeLeaseService.beginLeaseParticipation(AsyncTI4DiscordBot::runLeaseOwnedStartupWork);
        JdaService.registerAndStartCronJobs();
        JdaService.markProcessReady();
    }

    private static void runLeaseOwnedStartupWork() {
        BotLogger.info("STARTED BACKGROUND MANAGED GAME WARMUP");
        GameManager.warmup();
        DataMigrationManager.runMigrations();
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

        var resolvedArgs = new ArrayList<String>();
        resolvedArgs.add(botToken);
        resolvedArgs.add(botUserId);
        resolvedArgs.addAll(Arrays.asList(WHITESPACE_PATTERN.split(guildIdList.trim())));
        return resolvedArgs.toArray(String[]::new);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean durationHasPassedSinceStartup(Duration duration) {
        return System.currentTimeMillis() - START_TIME_MILLISECONDS <= duration.toMillis();
    }
}
