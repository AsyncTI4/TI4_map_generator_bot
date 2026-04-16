package ti4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import ti4.logging.BotLogger;
import ti4.logging.RollbarManager;
import ti4.settings.GlobalSettings;

@EnableScheduling
@SpringBootApplication
public class AsyncTI4DiscordBot {

    public static final long START_TIME_MILLISECONDS = System.currentTimeMillis();

    public static void main(String[] args) {
        GlobalSettings.loadSettings();
        RollbarManager.init();
        BotLogger.info("\n# __BOT IS STARTING UP__");

        SpringApplication.run(AsyncTI4DiscordBot.class, args);
    }
}
