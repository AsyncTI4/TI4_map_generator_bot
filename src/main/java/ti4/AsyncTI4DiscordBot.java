package ti4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import ti4.message.logging.BotLogger;
import ti4.settings.GlobalSettings;
import ti4.settings.GlobalSettings.ImplementedSettings;

@EnableScheduling
@SpringBootApplication
public class AsyncTI4DiscordBot {

    public static final long START_TIME_MILLISECONDS = System.currentTimeMillis();

    public static void main(String[] args) {
        GlobalSettings.loadSettings();
        GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, false);
        BotLogger.info("\n# __BOT IS STARTING UP__");

        SpringApplication.run(AsyncTI4DiscordBot.class, args);
    }
}
