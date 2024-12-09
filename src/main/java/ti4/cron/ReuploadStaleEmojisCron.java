package ti4.cron;

import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.message.BotLogger;
import ti4.service.emoji.ApplicationEmojiService;

@UtilityClass
public class ReuploadStaleEmojisCron {

    public static void register() {
        CronManager.scheduleOnce(ReuploadStaleEmojisCron.class, ReuploadStaleEmojisCron::reuploadEmojisAndDeleteHanging, 30, TimeUnit.SECONDS);
    }

    private static void reuploadEmojisAndDeleteHanging() {
        try {
            ApplicationEmojiService.reuploadStaleEmojis();
            ApplicationEmojiService.deleteHangingEmojis();
        } catch (Exception e) {
            BotLogger.log("**ReuploadStaleEmojisCron failed.**", e);
        }
        BotLogger.log("Ran ReuploadStaleEmojisCron.");
    }
}
