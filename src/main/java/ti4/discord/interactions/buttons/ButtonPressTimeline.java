package ti4.discord.interactions.buttons;

import java.util.EnumMap;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.DateTimeHelper;

class ButtonPressTimeline {

    private final long discordCreatedAtMillis;
    private final Map<ButtonPressStage, Long> stageMillis = new EnumMap<>(ButtonPressStage.class);
    private long startedAtMillis;
    private long lastMarkMillis;
    private long finishedAtMillis;

    private ButtonPressTimeline(long discordCreatedAtMillis, long receivedAtMillis) {
        this.discordCreatedAtMillis = discordCreatedAtMillis;
        stageMillis.put(ButtonPressStage.GATEWAY, Math.max(0, receivedAtMillis - discordCreatedAtMillis));
        lastMarkMillis = receivedAtMillis;
    }

    static ButtonPressTimeline received(ButtonInteractionEvent event) {
        return received(DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction()), now());
    }

    static ButtonPressTimeline received(long discordCreatedAtMillis, long receivedAtMillis) {
        return new ButtonPressTimeline(discordCreatedAtMillis, receivedAtMillis);
    }

    void markStarted() {
        markStarted(now());
    }

    void markStarted(long startedAtMillis) {
        this.startedAtMillis = startedAtMillis;
        markCompleted(ButtonPressStage.HANDOFF, startedAtMillis);
    }

    void markCompleted(ButtonPressStage stage) {
        markCompleted(stage, now());
    }

    void markCompleted(ButtonPressStage stage, long completedAtMillis) {
        stageMillis.merge(stage, Math.max(0, completedAtMillis - lastMarkMillis), Long::sum);
        lastMarkMillis = completedAtMillis;
    }

    void markFinished() {
        markFinished(now());
    }

    void markFinished(long finishedAtMillis) {
        this.finishedAtMillis = finishedAtMillis;
    }

    Map<ButtonPressStage, Long> getStageMillis() {
        return Map.copyOf(stageMillis);
    }

    long getDiscordCreatedAtMillis() {
        return discordCreatedAtMillis;
    }

    long getPreprocessingMillis() {
        return Math.max(0, startedAtMillis - discordCreatedAtMillis);
    }

    long getProcessingMillis() {
        return Math.max(0, finishedAtMillis - startedAtMillis);
    }

    long getResponseMillis() {
        return Math.max(0, finishedAtMillis - discordCreatedAtMillis);
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
