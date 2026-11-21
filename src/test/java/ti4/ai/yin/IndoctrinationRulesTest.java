package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;
import static ti4.ai.yin.YinTestBoard.PLANET;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Indoctrination: at the start of a ground combat the Yin pays 2 influence (3 for its mech) to turn one enemy infantry
// into one of its own. The seat uses it when the swing in the fight, with the planet at stake, beats the influence.
class IndoctrinationRulesTest extends BaseTi4Test {

    private static final String START = "FFCC_yin_initialIndoctrination_" + PLANET;

    private YinTestBoard board;

    @BeforeEach
    void setUp() {
        board = new YinTestBoard();
        board.sol.addPlanet(PLANET);
    }

    private void groundForces(int yinInfantry, int solInfantry) {
        board.units(PLANET, board.yin, UnitType.Infantry, yinInfantry);
        board.units(PLANET, board.sol, UnitType.Infantry, solInfantry);
    }

    private AiPrompt start() {
        return prompt("start", PromptSource.COMBAT_THREAD, NOW, START, "combatRoll_" + board.position + "_" + PLANET);
    }

    private AiPrompt choice(String... units) {
        List<String> ids = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();
        for (String unit : units) {
            ids.add("indoctrinate_" + PLANET + "_" + unit);
            labels.add("Indoctrinate 1 Infantry into 1 " + unit);
        }
        ids.add("deleteButtons");
        labels.add("Decline");
        return prompt("choice", PromptSource.COMBAT_THREAD, NOW + 1000, ids, labels);
    }

    private Optional<AiDecision> decide(AiTurnContext context) {
        return IndoctrinationRules.next(context);
    }

    @Test
    void indoctrinatesInAnEvenFightForAPlanet() {
        groundForces(3, 3);

        assertThat(decide(board.context(NOW, start())).map(AiTestGame::pressedId))
                .contains(START);
    }

    @Test
    void keepsItsInfluenceWhenTheFightIsAlreadyWonAndTheCommanderIsUnlocked() {
        board.unlockCommander();
        groundForces(6, 1);

        assertThat(decide(board.context(NOW, start()))).isEmpty();
    }

    @Test
    void keepsItsInfluenceWhenItCannotPay() {
        groundForces(3, 3);
        board.yin.exhaustPlanet("darien");
        board.yin.setTg(0);

        assertThat(decide(board.context(NOW, start()))).isEmpty();
    }

    @Test
    void decidesOncePerCombat() {
        groundForces(3, 3);
        board.unlockCommander();
        board.yin.exhaustPlanet("darien");
        board.yin.setTg(0);
        decide(board.context(NOW, start()));
        board.yin.refreshPlanet("darien");

        assertThat(decide(board.context(NOW, start()))).isEmpty();
    }

    @Test
    void neverStartsOnceTheCombatHasBeenRolled() {
        groundForces(3, 3);
        board.rounds(board.yin, PLANET, 1);

        assertThat(decide(board.context(NOW, start()))).isEmpty();
    }

    @Test
    void waitsForTheChoiceThenPicksTheUnitAndPays() {
        groundForces(3, 3);
        AiTurnContext first = board.context(NOW, start());
        decide(first);

        assertThat(decide(board.context(NOW + 500, start()))).containsInstanceOf(AiDecision.Wait.class);

        AiTurnContext second = board.context(NOW + 2000, choice("infantry", "mech"));
        assertThat(decide(second).map(AiTestGame::pressedId).orElseThrow()).startsWith("indoctrinate_" + PLANET + "_");
        assertThat(PaymentRules.isPending(second)).isTrue();
    }

    @Test
    void fallsBackToInfantryWhenTheMechIsNotOffered() {
        groundForces(3, 3);
        decide(board.context(NOW, start()));

        assertThat(decide(board.context(NOW + 2000, choice("infantry"))).map(AiTestGame::pressedId))
                .contains("indoctrinate_" + PLANET + "_infantry");
    }

    // Moyin's Ashes is the Yin mech: a seat without it (a Nekro copying Indoctrination) only ever converts to infantry.
    @Test
    void withoutMoyinsAshesOnlyInfantryIsConsidered() {
        groundForces(3, 3);
        board.yin.removeOwnedUnitByID(IndoctrinationRules.MECH);

        assertThat(IndoctrinationRules.best(board.test.game, board.yin, board.position, PLANET)
                        .map(IndoctrinationRules.Option::unit))
                .contains("infantry");
    }
}
