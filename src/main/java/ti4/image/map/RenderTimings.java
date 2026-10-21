package ti4.image.map;

import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.time.StopWatch;
import ti4.game.Game;
import ti4.helpers.DateTimeHelper;
import ti4.message.MessageHelper;
import ti4.settings.GlobalSettings;

final class RenderTimings {

    private static final RenderTimings DISABLED = new RenderTimings(false);

    private final boolean enabled;
    private final StopWatch total;
    private StopWatch draw;
    private StopWatch tiles;
    private StopWatch graphics;
    private StopWatch discord;
    private StopWatch website;

    private RenderTimings(boolean enabled) {
        this.enabled = enabled;
        this.total = enabled ? StopWatch.createStarted() : null;
    }

    static RenderTimings start() {
        boolean debug = GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, Boolean.FALSE);
        return debug ? new RenderTimings(true) : DISABLED;
    }

    static RenderTimings disabled() {
        return DISABLED;
    }

    boolean isEnabled() {
        return enabled;
    }

    void startDraw() {
        if (enabled) draw = StopWatch.createStarted();
    }

    void stopDraw() {
        if (enabled) draw.stop();
    }

    void startTiles() {
        if (enabled) tiles = StopWatch.createStarted();
    }

    void stopTiles() {
        if (enabled) tiles.stop();
    }

    void startGraphics() {
        if (enabled) graphics = StopWatch.createStarted();
    }

    void stopGraphics() {
        if (enabled) graphics.stop();
    }

    void startDiscord() {
        if (enabled) discord = StopWatch.createStarted();
    }

    void stopDiscord() {
        if (enabled) discord.stop();
    }

    void startWebsite() {
        if (enabled) website = StopWatch.createStarted();
    }

    void stopWebsite() {
        if (enabled) website.stop();
    }

    void log(Game game, @Nullable GenericInteractionCreateEvent event) {
        if (!enabled) return;
        total.stop();

        StringBuilder sb = new StringBuilder();

        String totalTimeStr = DateTimeHelper.getTimeRepresentationNanoSeconds(total.getNanoTime());
        String totalLine = String.format("%-34s%s", "Total time (" + game.getName() + "):", totalTimeStr);
        sb.append(totalLine);

        sb.append(line("  Draw time:", 36, draw, total));
        sb.append(line("    Tile time (of Draw Time):", 38, tiles, draw));
        sb.append(line("    Graphics time (of Draw Time):", 38, graphics, draw));
        sb.append(line("  Discord time:", 36, discord, total));
        sb.append(line("  Website time:", 36, website, total));
        sb.append('\n');

        String message = "```\nDEBUG - GenerateMap Timing:\n" + sb + "\n```";
        MessageHelper.sendMessageToEventServerBotLogChannel(event, message);
    }

    private static String line(String name, int padRight, StopWatch subStopWatch, StopWatch totalStopWatch) {
        if (subStopWatch == null || totalStopWatch == null) {
            return "";
        }
        long subTime = subStopWatch.getNanoTime();
        long totalTime = totalStopWatch.getNanoTime();
        double percentage = ((double) subTime / totalTime) * 100.0;
        String timeStr = DateTimeHelper.getTimeRepresentationNanoSeconds(subTime);
        return String.format("\n%-" + padRight + "s%s (%2.2f%%)", name, timeStr, percentage);
    }
}
