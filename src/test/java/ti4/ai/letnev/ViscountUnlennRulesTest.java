package ti4.ai.letnev;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Viscount Unlenn gives the ship with the best chance to hit an extra die for a round and readies every status phase,
// so the seat uses it in its own space combats unless the fight is already decided, and never for anybody else's.
class ViscountUnlennRulesTest extends BaseTi4Test {

    private static final String SPACE = "space";

    private AiTestGame test;
    private Player letnev;
    private Tile tile;
    private String position;
    private CombatMoment moment;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        letnev = test.addSeat(AiTestGame.SECOND_AI_ID, "letnev", "green");
        letnev.addLeader("letnevagent");
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        test.isActive(letnev, "action");
        test.game.setActiveSystem(position);
        moment = new CombatMoment(position, SPACE, false, 0);
    }

    private void fleets(UnitType own, int ownCount, UnitType enemy, int enemyCount) {
        test.units(tile, SPACE, letnev, own, ownCount);
        test.units(tile, SPACE, test.sol, enemy, enemyCount);
    }

    private AiPrompt combatStart() {
        return prompt(
                "start",
                PromptSource.COMBAT_THREAD,
                NOW,
                "FFCC_letnev_getAgentSelection_letnevagent",
                "combatRoll_" + position + "_space");
    }

    private AiPrompt targets() {
        return prompt(
                "targets",
                PromptSource.COMBAT_THREAD,
                NOW,
                "exhaustAgent_letnevagent_sol",
                "exhaustAgent_letnevagent_letnev");
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(letnev, Set.of(), NOW, prompts);
    }

    private String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    @Test
    void givesAnExtraDieToItsOwnShipWhenTheFightIsClose() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        AiPrompt start = combatStart();

        assertThat(pressed(ViscountUnlennRules.atRoundStart(context(start), moment)))
                .isEqualTo("FFCC_letnev_getAgentSelection_letnevagent");
        assertThat(pressed(ViscountUnlennRules.atRoundStart(context(targets()), moment)))
                .isEqualTo("exhaustAgent_letnevagent_letnev");
    }

    // The seat must not roll while the agent is half-way through its buttons, or the die would be wasted.
    @Test
    void waitsForTheTargetButtonsBeforeLettingTheRoundGo() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        AiTurnContext context = context(combatStart());
        ViscountUnlennRules.atRoundStart(context, moment);

        assertThat(ViscountUnlennRules.atRoundStart(context, moment)).containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void givesUpWaitingForTheTargetButtonsAfterAWhile() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        ViscountUnlennRules.atRoundStart(context(combatStart()), moment);

        AiTurnContext later = test.contextFor(letnev, Set.of(), NOW + 120_000L);

        assertThat(ViscountUnlennRules.atRoundStart(later, moment)).isEmpty();
    }

    // The agent is never saved: it readies every status phase, so every space combat gets the die, even a won or a lost
    // one.
    @Test
    void usesTheAgentOnAFightItHasAlreadyWon() {
        fleets(UnitType.Dreadnought, 6, UnitType.Fighter, 1);

        assertThat(pressed(ViscountUnlennRules.atRoundStart(context(combatStart()), moment)))
                .isEqualTo("FFCC_letnev_getAgentSelection_letnevagent");
    }

    @Test
    void usesTheAgentOnAHopelessFight() {
        fleets(UnitType.Fighter, 1, UnitType.Dreadnought, 6);

        assertThat(pressed(ViscountUnlennRules.atRoundStart(context(combatStart()), moment)))
                .isEqualTo("FFCC_letnev_getAgentSelection_letnevagent");
    }

    @Test
    void usesTheFreeAgentEvenInASkirmishBecauseItReadiesEveryRound() {
        fleets(UnitType.Destroyer, 1, UnitType.Destroyer, 1);

        assertThat(pressed(ViscountUnlennRules.atRoundStart(context(combatStart()), moment)))
                .isEqualTo("FFCC_letnev_getAgentSelection_letnevagent");
    }

    @Test
    void usesTheAgentOnAFightTheDieCannotSave() {
        fleets(UnitType.Destroyer, 1, UnitType.Dreadnought, 8);

        assertThat(CombatForecast.extraDie(test.game, letnev, position, SPACE).worthSpending())
                .isFalse();
        assertThat(pressed(ViscountUnlennRules.atRoundStart(context(combatStart()), moment)))
                .isEqualTo("FFCC_letnev_getAgentSelection_letnevagent");
    }

    @Test
    void doesNothingOnceTheAgentIsExhausted() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        letnev.getLeader("letnevagent").orElseThrow().setExhausted(true);

        assertThat(ViscountUnlennRules.atRoundStart(context(combatStart()), moment))
                .isEmpty();
    }

    // The extra die goes to the ship with the best hit chance; a seat with no ships in the fight gets no swing from it.
    @Test
    void anOutsidersFightIsWorthNothingToTheSeat() {
        test.units(tile, SPACE, test.sol, UnitType.Dreadnought, 3);
        test.units(tile, SPACE, test.nekro, UnitType.Dreadnought, 3);

        assertThat(CombatForecast.extraDie(test.game, letnev, position, SPACE)).isEqualTo(CombatForecast.ExtraDie.NONE);
    }

    @Test
    void doesNothingInAGroundCombat() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);

        assertThat(ViscountUnlennRules.atRoundStart(
                        context(combatStart()), new CombatMoment(position, "lodor", true, 0)))
                .isEmpty();
    }

    @Test
    void asksAgainTheNextRoundOnlyWhileTheAgentIsStillReady() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        AiTurnContext first = context(combatStart());
        ViscountUnlennRules.atRoundStart(first, moment);
        ViscountUnlennRules.atRoundStart(context(targets()), moment);
        letnev.getLeader("letnevagent").orElseThrow().setExhausted(true);

        assertThat(ViscountUnlennRules.atRoundStart(
                        context(combatStart()), new CombatMoment(position, SPACE, false, 1)))
                .isEmpty();
    }
}
