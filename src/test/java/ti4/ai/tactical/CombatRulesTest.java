package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Combat roll buttons are unowned: whoever presses one rolls for themselves. The AI may only roll when its own
// round count says it is its turn to roll, and it rolls before assigning hits so its dice reflect the start of
// the round.
class CombatRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile tile = test.place("26", position);
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        test.aiIsActive("action");
    }

    @Test
    void rollsWhenTheOpponentHasAlreadyRolledThisRound() {
        tracker("sol", 1);
        AiPrompt buttons = prompt("buttons", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_space");

        assertThat(pressedId(CombatRules.next(test.context(buttons)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void neverRollsTwiceInARound() {
        tracker("nekro", 1);
        AiPrompt buttons = prompt("buttons", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_space");

        assertThat(CombatRules.next(test.context(buttons))).isEmpty();
    }

    // At the start of a round it gives the human a moment to play action cards before rolling.
    @Test
    void waitsBrieflyWhenBothSidesAreLevel() {
        AiPrompt fresh = prompt("fresh", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_space");
        assertThat(CombatRules.next(test.context(fresh)).orElseThrow()).isInstanceOf(AiDecision.Wait.class);

        AiPrompt older = prompt(
                "older",
                PromptSource.COMBAT_THREAD,
                NOW - CombatRules.ROLL_GRACE_MILLIS,
                "combatRoll_" + position + "_space");
        assertThat(pressedId(CombatRules.next(test.context(older)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void rollsBeforeAssigningHitsWhenBothAreOnOneMessage() {
        tracker("sol", 1);
        AiPrompt hits = prompt(
                "hits",
                PromptSource.COMBAT_THREAD,
                NOW,
                "FFCC_nekro_autoAssignSpaceHits_" + position + "_1",
                "combatRoll_" + position + "_space");
        assertThat(pressedId(CombatRules.next(test.context(hits)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");

        tracker("nekro", 1);
        assertThat(pressedId(CombatRules.next(test.context(hits)).orElseThrow()))
                .isEqualTo("FFCC_nekro_autoAssignSpaceHits_" + position + "_1");
    }

    // Ground combat posts one roll button per planet in the system; the one the seat fights on may not be first.
    @Test
    void findsItsPlanetsRollAmongSeveralGroundCombatRolls() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 2);
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
        test.game.setStoredValue("combatRoundTrackersol" + position + "lodor", "1");
        AiPrompt buttons = prompt(
                "buttons",
                PromptSource.COMBAT_THREAD,
                NOW,
                "getDamageButtons_" + position + "_groundcombat",
                "combatRoll_" + position + "_otherplanet",
                "combatRoll_" + position + "_lodor");

        assertThat(pressedId(CombatRules.next(test.context(buttons)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_lodor");
    }

    // The opponent's own hit assignment is theirs to press.
    @Test
    void neverAssignsTheOpponentsHits() {
        tracker("nekro", 1);
        AiPrompt theirs =
                prompt("theirs", PromptSource.COMBAT_THREAD, NOW, "FFCC_sol_autoAssignSpaceHits_" + position + "_1");

        assertThat(CombatRules.next(test.context(theirs))).isEmpty();
    }

    // On its own tactical action its PDS on Lodor covers the system, so it fires at the defending cruiser before the
    // space combat.
    @Test
    void firesItsOwnSpaceCannonWhenAttacking() {
        Tile tile = test.game.getTileByPosition(position);
        test.nekro.addPlanet("lodor");
        test.units(tile, "lodor", test.nekro, UnitType.Pds, 1);
        test.game.setActiveSystem(position);
        AiPrompt cannon =
                prompt("cannon", PromptSource.PUBLIC, NOW, "combatRoll_" + position + "_space_spacecannonoffence");

        assertThat(pressedId(CombatRules.next(test.context(cannon)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space_spacecannonoffence");
    }

    @Test
    void firesAntiFighterBarrageOnceWithDestroyersAgainstFighters() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(tile, "space", test.sol, UnitType.Fighter, 2);
        AiPrompt afb = prompt("afb", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_space_afb");

        assertThat(pressedId(CombatRules.next(test.context(afb)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space_afb");
        assertThat(CombatRules.next(test.context(afb)).filter(AiDecision.Press.class::isInstance))
                .isEmpty();
    }

    // Rolling while the opponent still assigns last round's hits would start the next round under them.
    @Test
    void waitsWhileTheOpponentIsAssigningHits() {
        AiPrompt roll = prompt(
                "roll",
                PromptSource.COMBAT_THREAD,
                NOW - CombatRules.ROLL_GRACE_MILLIS,
                "combatRoll_" + position + "_space");
        AiPrompt theirHits = prompt(
                "theirs",
                PromptSource.COMBAT_THREAD,
                NOW - CombatRules.ROLL_GRACE_MILLIS,
                "FFCC_sol_autoAssignSpaceHits_" + position + "_1");

        assertThat(CombatRules.next(test.contextAt(NOW + 3_600_000L, roll, theirHits)))
                .isEmpty();
        // Without the pending assignment the grace period is long over, so it would roll.
        assertThat(pressedId(
                        CombatRules.next(test.contextAt(NOW + 3_600_000L, roll)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    // Between two AI seats the active player rolls first after a short pause, which leaves room for anti-fighter
    // barrage; the defender then rolls at once because it is a round behind.
    @Test
    void rollsQuicklyAgainstAnotherAiSeat() {
        test = AiTestGame.withSolAi();
        test.nekroHome();
        Tile tile = test.place("26", position);
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        test.aiIsActive("action");
        AiPrompt fresh = prompt("fresh", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_space");

        assertThat(CombatRules.next(test.context(fresh)).orElseThrow()).isInstanceOf(AiDecision.Wait.class);
        assertThat(CombatRules.next(test.contextFor(test.sol, Set.of(), NOW, fresh)))
                .isEmpty();

        long afterPause = NOW + CombatRules.AI_ROLL_GRACE_MILLIS;
        assertThat(pressedId(CombatRules.next(test.contextAt(afterPause, fresh)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
        test.game.setStoredValue("combatRoundTrackernekro" + position + "space", "1");
        assertThat(pressedId(CombatRules.next(test.contextFor(test.sol, Set.of(), NOW, fresh))
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    // Level trackers mean both sides have rolled this round: the seat assigns the hits it took before it may roll
    // the next round, even against another AI that does not need a pause.
    @Test
    void assignsItsOwnHitsBeforeRollingTheNextRound() {
        test = AiTestGame.withSolAi();
        test.nekroHome();
        Tile tile = test.place("26", position);
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 2);
        test.aiIsActive("action");
        tracker("nekro", 1);
        tracker("sol", 1);
        AiPrompt hits = prompt(
                "hits",
                PromptSource.COMBAT_THREAD,
                NOW - CombatRules.ROLL_GRACE_MILLIS,
                "FFCC_nekro_autoAssignSpaceHits_" + position + "_1",
                "combatRoll_" + position + "_space");

        assertThat(pressedId(CombatRules.next(test.context(hits)).orElseThrow()))
                .isEqualTo("FFCC_nekro_autoAssignSpaceHits_" + position + "_1");
    }

    // The hit assignment and the next roll can arrive as separate messages; the roll still comes first.
    @Test
    void rollsFromAnotherMessageBeforeAssigningHits() {
        tracker("sol", 1);
        AiPrompt roll = prompt("roll", PromptSource.COMBAT_THREAD, NOW - 500, "combatRoll_" + position + "_space");
        AiPrompt hits =
                prompt("hits", PromptSource.COMBAT_THREAD, NOW, "FFCC_nekro_autoAssignSpaceHits_" + position + "_1");

        assertThat(pressedId(CombatRules.next(test.context(roll, hits)).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");

        tracker("nekro", 1);
        assertThat(pressedId(CombatRules.next(test.context(roll, hits)).orElseThrow()))
                .isEqualTo("FFCC_nekro_autoAssignSpaceHits_" + position + "_1");
    }

    private void tracker(String faction, int rounds) {
        test.game.setStoredValue("combatRoundTracker" + faction + position + "space", String.valueOf(rounds));
    }
}
