package ti4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AsyncTI4DiscordBot {

    public static final long START_TIME_MILLISECONDS = System.currentTimeMillis();

    public static void main(String[] args) {
        SpringApplication.run(AsyncTI4DiscordBot.class, args);
    }
}
