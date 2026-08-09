package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.GameStats.OverruleTargetMigration.OverruleEntry;

class GameStatsTest {

    @Test
    void shouldCancelTheNewestUncanceledCopy() {
        GameStats stats = new GameStats();
        stats.recordAcPlay("Flank Speed", null);
        stats.recordAcPlay("Flank Speed", null);

        assertThat(stats.markLatestPlayCanceled("Flank Speed")).isTrue();
        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::isCanceled)
                .containsExactly(false, true);

        // A second cancel falls back to the earlier copy, and then there is nothing left to cancel.
        assertThat(stats.markLatestPlayCanceled("Flank Speed")).isTrue();
        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::isCanceled)
                .containsExactly(true, true);
        assertThat(stats.markLatestPlayCanceled("Flank Speed")).isFalse();
        assertThat(stats.markLatestPlayCanceled("Parley")).isFalse();
    }

    @Test
    void shouldLeaveALaterCopyPlayableAfterAnEarlierOneIsCanceled() {
        GameStats stats = new GameStats();
        stats.recordAcPlay("Flank Speed", null);
        stats.markLatestPlayCanceled("Flank Speed");
        stats.recordAcPlay("Flank Speed", null);

        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::isCanceled)
                .containsExactly(true, false);
        assertThat(stats.getTotalPlays("Flank Speed")).isEqualTo(2);
    }

    @Test
    void shouldMigrateSabotageTargetOntoTheCanceledPlay() {
        GameStats stats = new GameStats();
        stats.recordAcPlay("Flank Speed", null);
        stats.getActionCardPlays().add(legacySabotage("Flank Speed"));

        GameStats.OverruleTargetMigration migration = stats.migrateTargetsToCanceledFlags();

        assertThat(migration.changed()).isTrue();
        assertThat(stats.getActionCardPlays()).hasSize(2);
        assertThat(stats.getActionCardPlays().getFirst().isCanceled()).isTrue();
        ActionCardPlay converted = stats.getActionCardPlays().get(1);
        assertThat(converted.getActionCard()).isEqualTo(GameStats.SABOTAGE);
        assertThat(converted.getTarget()).isNull();
        assertThat(converted.getPlayerId()).isEqualTo("canceler");
        assertThat(converted.isCanceled()).isFalse();
    }

    @Test
    void shouldHarvestOverrulePlaysWithPlayerIdAndPlaceholderUnmatchedCancels() {
        GameStats stats = new GameStats();
        stats.getActionCardPlays().add(legacySabotage(GameStats.OVERRULE));
        stats.getActionCardPlays().add(legacyOverrule("Warfare"));
        stats.getActionCardPlays().add(legacyOverrule("Politics"));

        GameStats.OverruleTargetMigration migration = stats.migrateTargetsToCanceledFlags();

        assertThat(migration.overrulePlays())
                .containsExactly(new OverruleEntry("winnu", "Warfare"), new OverruleEntry("winnu", "Politics"));
        assertThat(stats.getActionCardPlays()).hasSize(4);
        ActionCardPlay placeholder = stats.getActionCardPlays().getFirst();
        assertThat(placeholder.getActionCard()).isEqualTo(GameStats.OVERRULE);
        assertThat(placeholder.getPlayerId()).isNull();
        assertThat(placeholder.isCanceled()).isTrue();
        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::getTarget)
                .containsOnlyNulls();
    }

    @Test
    void shouldBeIdempotentOnASecondMigrationRun() {
        GameStats stats = new GameStats();
        stats.recordAcPlay("Flank Speed", null);
        stats.getActionCardPlays().add(legacySabotage("Flank Speed"));
        stats.migrateTargetsToCanceledFlags();

        GameStats.OverruleTargetMigration secondRun = stats.migrateTargetsToCanceledFlags();

        assertThat(secondRun.changed()).isFalse();
        assertThat(secondRun.overrulePlays()).isEmpty();
        assertThat(stats.getActionCardPlays()).hasSize(2);
    }

    private static ActionCardPlay legacySabotage(String target) {
        return legacyPlay(GameStats.SABOTAGE, "canceler", target);
    }

    private static ActionCardPlay legacyOverrule(String target) {
        return legacyPlay(GameStats.OVERRULE, "winnu", target);
    }

    private static ActionCardPlay legacyPlay(String actionCard, String playerId, String target) {
        var play = new ActionCardPlay(actionCard, playerId);
        play.setTarget(target);
        return play;
    }
}
