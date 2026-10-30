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
import ti4.ai.scoring.PaymentRules;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Dunlain Reaper swaps an infantry for a mech at the start of a ground combat round for 2 resources. The mech sustains
// damage, so the seat pays only when the swap changes the odds of a fight that is worth the money.
class DunlainReaperRulesTest extends BaseTi4Test {

    private static final String PLANET = "lodor";

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
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        letnev.addPlanet(PLANET);
        letnev.addPlanet("arcprime");
        test.isActive(test.sol, "action");
        test.game.setActiveSystem(position);
        moment = new CombatMoment(position, PLANET, true, 0);
    }

    private void groundForces(int letnevInfantry, int solInfantry) {
        test.units(tile, PLANET, letnev, UnitType.Infantry, letnevInfantry);
        test.units(tile, PLANET, test.sol, UnitType.Infantry, solInfantry);
    }

    private AiPrompt combatStart() {
        return prompt(
                "start",
                PromptSource.COMBAT_THREAD,
                NOW,
                "FFCC_letnev_letnevMechRes_" + PLANET + "_mech",
                "combatRoll_" + position + "_" + PLANET);
    }

    private AiTurnContext context() {
        return test.contextFor(letnev, Set.of(), NOW, combatStart());
    }

    private Optional<AiDecision> decide(AiTurnContext context) {
        return DunlainReaperRules.atRoundStart(context, moment);
    }

    @Test
    void deploysTheMechInACloseFightForAPlanetItHolds() {
        groundForces(3, 4);
        AiTurnContext context = context();

        assertThat(decide(context).map(AiTestGame::pressedId)).contains("FFCC_letnev_letnevMechRes_lodor_mech");
        assertThat(PaymentRules.isPending(context)).isTrue();
    }

    @Test
    void keepsItsResourcesWhenTheFightIsAlreadyDecided() {
        groundForces(6, 1);
        AiTurnContext context = context();

        assertThat(decide(context)).isEmpty();
        assertThat(PaymentRules.isPending(context)).isFalse();
    }

    @Test
    void keepsItsResourcesWhenItCannotAffordTheMech() {
        groundForces(3, 4);
        letnev.exhaustPlanet(PLANET);
        letnev.exhaustPlanet("arcprime");
        letnev.setTg(0);

        assertThat(decide(context())).isEmpty();
    }

    @Test
    void asksOncePerRound() {
        groundForces(3, 4);
        AiTurnContext context = context();
        decide(context);

        assertThat(decide(context)).isEmpty();
    }

    @Test
    void doesNothingWithoutTheButton() {
        groundForces(3, 4);
        AiPrompt roll = prompt("roll", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_" + PLANET);

        assertThat(decide(test.contextFor(letnev, Set.of(), NOW, roll))).isEmpty();
    }

    @Test
    void doesNothingInSpace() {
        groundForces(3, 4);

        assertThat(DunlainReaperRules.atRoundStart(context(), new CombatMoment(position, "space", false, 0)))
                .isEmpty();
    }
}
