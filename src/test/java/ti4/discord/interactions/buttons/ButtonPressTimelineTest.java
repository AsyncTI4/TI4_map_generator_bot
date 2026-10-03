package ti4.discord.interactions.buttons;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ButtonPressTimelineTest {

    @Test
    void stagesMeasureTheGapSinceThePreviousMark() {
        ButtonPressTimeline timeline = ButtonPressTimeline.received(1000, 1080);
        timeline.markStarted(1085);
        timeline.markCompleted(ButtonPressStage.CONTEXT, 1105);
        timeline.markCompleted(ButtonPressStage.LOG, 1106);
        timeline.markCompleted(ButtonPressStage.RESOLVE, 1140);
        timeline.markCompleted(ButtonPressStage.SAVE, 1160);
        timeline.markFinished(1161);

        assertThat(timeline.getStageMillis())
                .containsEntry(ButtonPressStage.GATEWAY, 80L)
                .containsEntry(ButtonPressStage.HANDOFF, 5L)
                .containsEntry(ButtonPressStage.CONTEXT, 20L)
                .containsEntry(ButtonPressStage.LOG, 1L)
                .containsEntry(ButtonPressStage.RESOLVE, 34L)
                .containsEntry(ButtonPressStage.SAVE, 20L)
                .doesNotContainKeys(ButtonPressStage.REPLAY_SNAPSHOT, ButtonPressStage.REPLAY_SETTLE);
        assertThat(timeline.getPreprocessingMillis()).isEqualTo(85);
        assertThat(timeline.getProcessingMillis()).isEqualTo(76);
        assertThat(timeline.getResponseMillis()).isEqualTo(161);
    }

    @Test
    void combatReplayWorkIsTimedSeparatelyFromTheHandler() {
        // Combat replay runs once before resolving (snapshot) and once after saving (settle);
        // neither should be charged to the resolve or save stages around it.
        ButtonPressTimeline timeline = ButtonPressTimeline.received(0, 0);
        timeline.markStarted(0);
        timeline.markCompleted(ButtonPressStage.REPLAY_SNAPSHOT, 7);
        timeline.markCompleted(ButtonPressStage.RESOLVE, 20);
        timeline.markCompleted(ButtonPressStage.SAVE, 30);
        timeline.markCompleted(ButtonPressStage.REPLAY_SETTLE, 33);

        assertThat(timeline.getStageMillis())
                .containsEntry(ButtonPressStage.REPLAY_SNAPSHOT, 7L)
                .containsEntry(ButtonPressStage.RESOLVE, 13L)
                .containsEntry(ButtonPressStage.SAVE, 10L)
                .containsEntry(ButtonPressStage.REPLAY_SETTLE, 3L);
    }

    @Test
    void processingIncludesTimeAfterTheLastMarkWhenAStageThrows() {
        ButtonPressTimeline timeline = ButtonPressTimeline.received(0, 10);
        timeline.markStarted(10);
        timeline.markCompleted(ButtonPressStage.CONTEXT, 20);
        timeline.markFinished(500);

        assertThat(timeline.getStageMillis()).doesNotContainKey(ButtonPressStage.RESOLVE);
        assertThat(timeline.getProcessingMillis()).isEqualTo(490);
        assertThat(timeline.getHandlerId()).isEmpty();
    }

    @Test
    void markResolvedTimesTheResolveStageAndRemembersTheHandler() {
        ButtonPressTimeline timeline = ButtonPressTimeline.received(0, 0);
        timeline.markStarted(0);
        timeline.markCompleted(ButtonPressStage.LOG, 3);
        timeline.markResolved("reacted_", 45);

        assertThat(timeline.getStageMillis()).containsEntry(ButtonPressStage.RESOLVE, 42L);
        assertThat(timeline.getHandlerId()).contains("reacted_");
    }

    @Test
    void hostClockBehindDiscordDoesNotProduceNegativeTimes() {
        ButtonPressTimeline timeline = ButtonPressTimeline.received(1000, 950);
        timeline.markStarted(960);
        timeline.markFinished(990);

        assertThat(timeline.getStageMillis()).containsEntry(ButtonPressStage.GATEWAY, 0L);
        assertThat(timeline.getPreprocessingMillis()).isZero();
        assertThat(timeline.getResponseMillis()).isZero();
    }
}
