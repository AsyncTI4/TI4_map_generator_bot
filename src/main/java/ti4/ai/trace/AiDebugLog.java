package ti4.ai.trace;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.utility.DiscordThreadUtility;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ThreadGetter;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class AiDebugLog {

    public enum Mode {
        OFF,
        LOG,
        PUBLIC;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Mode of(String id) {
            for (Mode mode : values()) {
                if (mode.id().equals(id)) return mode;
            }
            return OFF;
        }
    }

    private static final String MODE_KEY = "aiDebugMode";
    private static final String THREAD_NAME_SUFFIX = "-ai-debug";
    private static final int MAX_LINE = 1900;
    private static final Pattern MENTION = Pattern.compile("<@[!&]?\\d+>");

    public static Mode mode(Game game) {
        return Mode.of(game.getStoredValue(MODE_KEY));
    }

    public static boolean isOn(Game game) {
        return mode(game) != Mode.OFF;
    }

    public static void setMode(Game game, Mode mode) {
        if (mode == Mode.OFF) {
            game.removeStoredValue(MODE_KEY);
        } else {
            game.setStoredValue(MODE_KEY, mode.id());
        }
    }

    public static void log(Game game, Player seat, String text) {
        Mode mode = mode(game);
        if (mode == Mode.OFF) return;
        String line = withoutPings(StringUtils.abbreviate(
                seat.getUserName() + " (round " + game.getRound() + ", " + game.getPhaseOfGame() + "): " + text,
                MAX_LINE));
        BotLogger.info("AI debug " + game.getName() + " " + line);
        if (mode == Mode.PUBLIC) withThread(game, thread -> MessageHelper.sendMessageToChannel(thread, line));
    }

    public static void withThread(Game game, Consumer<ThreadChannel> action) {
        TextChannel channel = game.getMainGameChannel();
        if (channel == null) return;
        String name = threadName(game);
        channel.getThreadChannels().stream()
                .filter(thread -> thread.getName().equals(name))
                .findFirst()
                .ifPresentOrElse(action, () -> ThreadGetter.getThreadInChannel(channel, name, action));
    }

    static String withoutPings(String text) {
        return MENTION.matcher(text)
                .replaceAll("@someone")
                .replace("@everyone", "everyone")
                .replace("@here", "here");
    }

    private static String threadName(Game game) {
        return DiscordThreadUtility.fitThreadName(game.getName() + THREAD_NAME_SUFFIX);
    }
}
