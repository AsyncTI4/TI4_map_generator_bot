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
import ti4.ai.tactical.CombatMoment;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Munitions Reserves costs 2 trade goods for a reroll of a round's misses. The seat pays it when the reroll matters to
// the outcome: a close fight, or one where it saves a valuable ship. Not when the fight is decided either way.
class MunitionsReservesRulesTest extends BaseTi4Test {

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
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        test.isActive(letnev, "action");
        test.game.setActiveSystem(position);
        letnev.setTg(6);
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
                "FFCC_letnev_munitionsReserves",
                "combatRoll_" + position + "_space");
    }

    private AiTurnContext context() {
        return test.contextFor(letnev, Set.of(), NOW, combatStart());
    }

    private Optional<AiDecision> decide() {
        return MunitionsReservesRules.atRoundStart(context(), moment);
    }

    private String pressed() {
        return decide().map(AiTestGame::pressedId).orElse("");
    }

    @Test
    void paysForARerollInAnEvenFightBetweenDreadnoughts() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);

        assertThat(pressed()).isEqualTo("FFCC_letnev_munitionsReserves");
    }

    @Test
    void paysToSaveShipsInAFightItIsAlreadyFavouredIn() {
        fleets(UnitType.Dreadnought, 4, UnitType.Dreadnought, 3);

        assertThat(pressed()).isEqualTo("FFCC_letnev_munitionsReserves");
    }

    @Test
    void keepsItsTradeGoodsInASkirmishOfCheapShips() {
        fleets(UnitType.Destroyer, 1, UnitType.Destroyer, 1);

        assertThat(pressed()).isEmpty();
    }

    @Test
    void keepsItsTradeGoodsInAFightItHasAlreadyWon() {
        fleets(UnitType.Dreadnought, 6, UnitType.Fighter, 1);

        assertThat(pressed()).isEmpty();
    }

    @Test
    void keepsItsTradeGoodsInAHopelessFight() {
        fleets(UnitType.Fighter, 1, UnitType.Dreadnought, 6);

        assertThat(pressed()).isEmpty();
    }

    @Test
    void cannotPayWithoutTwoTradeGoods() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        letnev.setTg(1);

        assertThat(pressed()).isEmpty();
    }

    @Test
    void doesNotPayTwiceForTheSameRound() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        AiTurnContext context = context();

        assertThat(MunitionsReservesRules.atRoundStart(context, moment)).isPresent();
        assertThat(MunitionsReservesRules.atRoundStart(context, moment)).isEmpty();
    }

    @Test
    void asksAgainTheNextRound() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        AiTurnContext context = context();
        MunitionsReservesRules.atRoundStart(context, moment);

        assertThat(MunitionsReservesRules.atRoundStart(context, new CombatMoment(position, SPACE, false, 1)))
                .isPresent();
    }

    @Test
    void doesNotPayWhileTheLastPaymentIsStillWaitingForItsRoll() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        test.game.setStoredValue("munitionsReserves", "letnev");

        assertThat(pressed()).isEmpty();
    }

    @Test
    void paysWhenDefendingToo() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        test.isActive(test.sol, "action");

        assertThat(pressed()).isEqualTo("FFCC_letnev_munitionsReserves");
    }

    @Test
    void doesNothingWithoutTheButtonOnTheTable() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);
        AiPrompt other = prompt("other", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_space");

        assertThat(MunitionsReservesRules.atRoundStart(test.contextFor(letnev, Set.of(), NOW, other), moment))
                .isEmpty();
    }

    @Test
    void doesNothingForAFactionWithoutTheAbility() {
        test.isActive(test.nekro, "action");
        test.units(tile, SPACE, test.nekro, UnitType.Dreadnought, 3);
        test.units(tile, SPACE, test.sol, UnitType.Dreadnought, 3);
        test.nekro.setTg(6);
        AiPrompt start = prompt("start", PromptSource.COMBAT_THREAD, NOW, "FFCC_nekro_munitionsReserves");

        assertThat(MunitionsReservesRules.atRoundStart(test.contextFor(test.nekro, Set.of(), NOW, start), moment))
                .isEmpty();
    }

    @Test
    void doesNothingInAGroundCombat() {
        fleets(UnitType.Dreadnought, 3, UnitType.Dreadnought, 3);

        assertThat(MunitionsReservesRules.atRoundStart(context(), new CombatMoment(position, "lodor", true, 0)))
                .isEmpty();
    }
}
