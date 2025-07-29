package ti4.cron;

import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.message.BotLogger;
import ti4.service.emoji.ApplicationEmojiService;

@UtilityClass
public class ReuploadStaleEmojisCron {

    public static void register() {
        CronManager.scheduleOnce(ReuploadStaleEmojisCron.class, ReuploadStaleEmojisCron::reuploadEmojisAndDeleteHanging, 2, TimeUnit.MINUTES);
    }

    private static void reuploadEmojisAndDeleteHanging() {
        BotLogger.info("Running ReuploadStaleEmojisCron.");
        try {
            ApplicationEmojiService.reuploadStaleEmojis();
            ApplicationEmojiService.deleteHangingEmojis();
            ApplicationEmojiService.reportMissingEnums();
        } catch (Exception e) {
            BotLogger.error("**ReuploadStaleEmojisCron failed.**", e);
        }
        BotLogger.info("Finished ReuploadStaleEmojisCron.");
    }
}
