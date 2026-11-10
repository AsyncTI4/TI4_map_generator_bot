package ti4;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import ti4.cron.CronManager;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.ButtonProcessor;
import ti4.discord.interactions.listeners.ModalListener;
import ti4.discord.interactions.selections.SelectionMenuProcessor;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.migration.DataMigrationManager;
import ti4.logging.BotLogger;
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
    private static final Duration WARMUP_DURATION = Duration.ofMinutes(2);

    private static volatile boolean shuttingDown;

    static void main(String[] args) {
        StartupTimeline startup = new StartupTimeline();
        startup.mark("STARTING THE JVM");
        GlobalSettings.loadSettings();
        BotLogger.info("\n# __BOT IS STARTING UP__");

        ConfigurableApplicationContext applicationContext = SpringApplication.run(AsyncTI4DiscordBot.class, args);
        applicationContext.getBean(JdaLifecycleService.class);
        ActiveLeaseService activeLeaseService = applicationContext.getBean(ActiveLeaseService.class);
        GameManager.preloadManagedGames();
        startup.mark("STARTING SPRING");

        String[] resolvedArgs = resolveSourceArgs(args);
        JdaService.startJdaAndRegisterListeners(resolvedArgs);
        startup.mark("BUILDING JDA");
        boolean jdaReady = JdaService.awaitJdaReady();
        startup.mark("CONNECTING TO DISCORD");
        if (!jdaReady || !JdaService.initializeGuilds(resolvedArgs)) {
            applicationContext.close();
            throw new IllegalStateException("Failed to initialize JDA and guilds");
        }
        startup.mark("SETTING UP SERVERS");
        GameManager.buildPreloadedManagedGames();
        JdaService.loadStaticDataAndResources();
        startup.mark("LOADING DATA");
        ButtonProcessor.checkButtonHandlersSetup();
        SelectionMenuProcessor.checkSelectionMenuHandlersSetup();
        ModalListener.checkModalHandlersSetup();
        startup.mark("WARMING INTERACTION HANDLERS");
        activeLeaseService.beginLeaseParticipation(AsyncTI4DiscordBot::runLeaseOwnedStartupWork);
        JdaService.registerAndStartCronJobs();
        startup.mark("JOINING THE LEASE AND REGISTERING CRONS");
        JdaService.markProcessReady();
        BotLogger.info(startup.summary(JdaService.describeServers()));
    }

    private static void runLeaseOwnedStartupWork() {
        CronManager.runLeaseStartJobs();
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

    public static void markShuttingDown() {
        shuttingDown = true;
    }

    public static boolean isUnstable() {
        return shuttingDown || isWithinStartupDuration();
    }

    private static boolean isWithinStartupDuration() {
        return System.currentTimeMillis() - START_TIME_MILLISECONDS <= WARMUP_DURATION.toMillis();
    }

    private static final class StartupTimeline {

        private final long startedAtMillis =
                ManagementFactory.getRuntimeMXBean().getStartTime();
        private final List<String> stages = new ArrayList<>();
        private long lastMarkMillis = startedAtMillis;

        private void mark(String stage) {
            long now = System.currentTimeMillis();
            stages.add(String.format("%,d MS %s", now - lastMarkMillis, stage));
            lastMarkMillis = now;
        }

        private String summary(String servers) {
            return String.format(
                    "STARTED UP ON %s IN %,d MS AFTER THE JVM STARTED: %s",
                    servers, lastMarkMillis - startedAtMillis, String.join(", ", stages));
        }
    }
}
