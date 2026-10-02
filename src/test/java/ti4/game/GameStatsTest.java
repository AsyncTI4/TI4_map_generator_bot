package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.GameStats.ActionCardPlay;

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
    void shouldHarvestOverruleChoicesAndPlaceholderUnmatchedCancels() {
        GameStats stats = new GameStats();
        stats.getActionCardPlays().add(legacySabotage(GameStats.OVERRULE));
        stats.getActionCardPlays().add(legacyOverrule("Warfare"));

        GameStats.OverruleTargetMigration migration = stats.migrateTargetsToCanceledFlags();

        assertThat(migration.strategyCardChoices()).containsExactlyEntriesOf(Map.of("Warfare", 1));
        assertThat(stats.getActionCardPlays()).hasSize(3);
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
        assertThat(secondRun.strategyCardChoices()).isEmpty();
        assertThat(stats.getActionCardPlays()).hasSize(2);
    }

    @Test
    void shouldRemoveACancelThatNeverHappenedAlongWithTheSabotageThatRecordedIt() {
        // pbd24955c: the Blitz player pressed the Sabotage button on their own already-canceled
        // Blitz while holding no Sabotage, which recorded a cancel out of thin air.
        GameStats stats = new GameStats();
        stats.setActionCardPlays(new ArrayList<>(List.of(
                play("Blitz", "winnu", true),
                play(GameStats.SABOTAGE, "l1z1x", false),
                play("Blitz", null, true),
                play(GameStats.SABOTAGE, "winnu", false))));

        assertThat(stats.removeFabricatedCancels()).isEqualTo(2);
        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::getActionCard, ActionCardPlay::getPlayerId)
                .containsExactly(tuple("Blitz", "winnu"), tuple(GameStats.SABOTAGE, "l1z1x"));
        // The real Blitz keeps its cancel - that Sabotage genuinely happened.
        assertThat(stats.getActionCardPlays().getFirst().isCanceled()).isTrue();
    }

    @Test
    void shouldKeepAPlaceholderStandingInForAPlayThatWasNeverRecorded() {
        // Legacy code recorded Overrule only once its strategy card was chosen, so an Overrule
        // canceled before that has no play of its own - the placeholder is its only trace.
        GameStats stats = new GameStats();
        stats.setActionCardPlays(new ArrayList<>(List.of(
                play("Sanction", "naalu", false),
                play(GameStats.OVERRULE, null, true),
                play(GameStats.SABOTAGE, "sol", false))));

        assertThat(stats.removeFabricatedCancels()).isZero();
        assertThat(stats.getActionCardPlays()).hasSize(3);
    }

    @Test
    void shouldKeepASecondOverrulePlaceholderRatherThanGuessItWasAStrayPress() {
        // The deck's single Overrule is reshuffled and replayed often enough that two placeholders
        // can both be real - each a play sabotaged before its strategy card was chosen. Nothing
        // here tells that apart from a stray press, so neither is touched.
        GameStats stats = new GameStats();
        stats.setActionCardPlays(new ArrayList<>(List.of(
                play(GameStats.OVERRULE, null, true),
                play(GameStats.SABOTAGE, "sol", false),
                play("Flank Speed", "naalu", false),
                play(GameStats.OVERRULE, null, true),
                play(GameStats.SABOTAGE, "l1z1x", false))));

        assertThat(stats.removeFabricatedCancels()).isZero();
        assertThat(stats.getActionCardPlays()).hasSize(5);
    }

    @Test
    void shouldRemoveEveryFabricatedCancelWhenACardWasHitMoreThanTwice() {
        // tourney7p33 played one Repeal Law and recorded three cancels of it.
        GameStats stats = new GameStats();
        stats.setActionCardPlays(new ArrayList<>(List.of(
                play("Repeal Law", "naaz", true),
                play(GameStats.SABOTAGE, "sol", false),
                play("Repeal Law", null, true),
                play(GameStats.SABOTAGE, "naaz", false),
                play("Repeal Law", null, true),
                play(GameStats.SABOTAGE, "winnu", false))));

        assertThat(stats.removeFabricatedCancels()).isEqualTo(4);
        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::getActionCard)
                .containsExactly("Repeal Law", GameStats.SABOTAGE);
    }

    @Test
    void shouldRemoveAPlaceholderForACardThatWasNeverPlayed() {
        // pbd24643: the play was undone, but its Sabotage button stayed live in the channel, so a
        // later press recorded a cancel of a card that was no longer in play. Only Overrule gets
        // the benefit of the doubt here - every other card's play would have been recorded.
        GameStats stats = new GameStats();
        stats.setActionCardPlays(new ArrayList<>(List.of(
                play("Skilled Retreat", "mentak", true),
                play(GameStats.SABOTAGE, "nekro", false),
                play("Insider Information", null, true),
                play(GameStats.SABOTAGE, "hacan", false))));

        assertThat(stats.removeFabricatedCancels()).isEqualTo(2);
        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::getActionCard, ActionCardPlay::getPlayerId)
                .containsExactly(tuple("Skilled Retreat", "mentak"), tuple(GameStats.SABOTAGE, "nekro"));
    }

    @Test
    void shouldLeaveACancelAloneWhileACopyOfThatCardIsStillStanding() {
        // A live copy is one this cancel could legitimately belong to, so it is not ours to judge.
        GameStats stats = new GameStats();
        stats.setActionCardPlays(new ArrayList<>(List.of(
                play("Flank Speed", "sol", false),
                play("Flank Speed", null, true),
                play(GameStats.SABOTAGE, "naalu", false))));

        assertThat(stats.removeFabricatedCancels()).isZero();
    }

    @Test
    void shouldChangeNothingOnASecondRun() {
        GameStats stats = new GameStats();
        stats.setActionCardPlays(new ArrayList<>(List.of(
                play("Blitz", "winnu", true),
                play(GameStats.SABOTAGE, "l1z1x", false),
                play("Blitz", null, true),
                play(GameStats.SABOTAGE, "winnu", false))));

        assertThat(stats.removeFabricatedCancels()).isEqualTo(2);
        assertThat(stats.removeFabricatedCancels()).isZero();
        assertThat(stats.getActionCardPlays()).hasSize(2);
    }

    private static ActionCardPlay play(String actionCard, String playerId, boolean canceled) {
        ActionCardPlay play = new ActionCardPlay(actionCard, playerId);
        play.setCanceled(canceled);
        return play;
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
