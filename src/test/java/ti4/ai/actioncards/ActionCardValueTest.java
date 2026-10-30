package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.Stall;
import ti4.testUtils.BaseTi4Test;

class ActionCardValueTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
    }

    private double keep(String alias) {
        return ActionCardValue.keepValue(test.game, test.nekro, alias);
    }

    // The AI has no rule that plays a card like Intercept, so holding it is worth nothing and it is the first to go.
    @Test
    void aCardNoRulePlaysIsWorthNothing() {
        assertThat(keep("intercept")).isZero();
        assertThat(keep("rout")).isZero();
        assertThat(keep("bunker")).isZero();
    }

    @Test
    void aCardARulePlaysIsWorthItsPriorBeforeTheBoardAsksForIt() {
        test.sol.setPassed(true);

        assertThat(keep("industrial_initiative")).isEqualTo(ActionCardPrior.keepPrior("industrial_initiative"));
        assertThat(keep("sabo1"))
                .isEqualTo(Math.max(ActionCardPrior.keepPrior("sabo1"), SabotagePolicy.OVERRULE_RESERVE));
    }

    // A component action card that can be played is at least a stall while another player still acts.
    @Test
    void aPlayableComponentCardIsWorthAtLeastTheStall() {
        assertThat(keep("industrial_initiative")).isEqualTo(Stall.ACTION_VALUE);
    }

    @Test
    void anEconomyCardTheBoardNeedsNowIsWorthWhatItWouldPlayFor() {
        test.nekro.setActionCard("mining_initiative", 1);

        double playValue = ActionCardValue.playValue(test.game, test.nekro, "mining_initiative");

        assertThat(playValue).isGreaterThan(ActionCardPrior.keepPrior("mining_initiative"));
        assertThat(keep("mining_initiative"))
                .isEqualTo(Math.min(playValue + Stall.ACTION_VALUE, ActionCardValue.ECONOMY_CAP));
    }

    private double economyPlanValue(String alias) {
        return AiActionCards.of(alias)
                .orElseThrow()
                .planFor(test.game, test.nekro)
                .orElseThrow()
                .value();
    }

    @Test
    void anEconomyCardIsWorthWhatItGainsPlusTheStallWhileAnotherPlayerStillActs() {
        double gain = ActionCardValue.playValue(test.game, test.nekro, "mining_initiative");

        assertThat(gain).isPositive();
        assertThat(economyPlanValue("mining_initiative")).isEqualTo(gain + Stall.ACTION_VALUE);
    }

    @Test
    void anEconomyCardIsWorthOnlyWhatItGainsOnceEveryOtherPlayerHasPassed() {
        test.sol.setPassed(true);
        double gain = ActionCardValue.playValue(test.game, test.nekro, "mining_initiative");

        assertThat(economyPlanValue("mining_initiative")).isEqualTo(gain);
    }

    @Test
    void anEconomyCardThatGainsNothingIsStillAStall() {
        assertThat(ActionCardValue.playValue(test.game, test.nekro, "industrial_initiative"))
                .isZero();
        assertThat(economyPlanValue("industrial_initiative")).isEqualTo(Stall.ACTION_VALUE);
        assertThat(economyPlanValue("economic_initiative")).isEqualTo(Stall.ACTION_VALUE);
    }

    @Test
    void aPureStallIsWorthNothingOnceEveryOtherPlayerHasPassed() {
        test.sol.setPassed(true);

        assertThat(economyPlanValue("industrial_initiative")).isZero();
    }

    // Rise of a Messiah needs a planet to put an infantry on, Frontline Deployment and Mining a planet to use, and
    // War Effort a system to put the cruiser in: without one the card cannot be played at all.
    @Test
    void anEconomyCardWithNothingToUseItOnCannotBePlayed() {
        test.nekro.getPlanets().clear();

        assertThat(AiActionCards.of("messiah").orElseThrow().planFor(test.game, test.nekro))
                .isEmpty();
        assertThat(AiActionCards.of("f_deployment").orElseThrow().planFor(test.game, test.nekro))
                .isEmpty();
        assertThat(AiActionCards.of("mining_initiative").orElseThrow().planFor(test.game, test.nekro))
                .isEmpty();
        assertThat(AiActionCards.of("industrial_initiative").orElseThrow().planFor(test.game, test.nekro))
                .isPresent();
    }

    // Both gain nothing, so both are the same 1.5 stall; the one worth less in the hand is the one to spend.
    @Test
    void theCardWorthLeastInHandIsSpentAsAPureStallFirst() {
        test.place("37", "202");
        test.nekro.getPlanets().clear();
        test.nekro.addPlanet("meer");
        test.nekro.setActionCard("mining_initiative", 1);
        test.nekro.setActionCard("industrial_initiative", 2);

        assertThat(economyPlanValue("mining_initiative")).isEqualTo(Stall.ACTION_VALUE);
        assertThat(ActionCardPrior.keepPrior("industrial_initiative"))
                .isLessThan(ActionCardPrior.keepPrior("mining_initiative"));
        assertThat(ActionCardValue.bestComponentPlay(test.game, test.nekro))
                .hasValueSatisfying(best -> assertThat(best.alias()).isEqualTo("industrial_initiative"));
    }

    // A card that gains a lot is the better play even though it is also the better card to keep: the margin is
    // what it gains over holding it.
    @Test
    void aCardThatGainsMoreHasTheLargerMarginEvenIfItIsWorthMoreInHand() {
        test.nekro.setActionCard("mining_initiative", 1);
        test.nekro.setActionCard("industrial_initiative", 2);

        assertThat(ActionCardValue.bestComponentPlay(test.game, test.nekro))
                .hasValueSatisfying(best -> assertThat(best.alias()).isEqualTo("mining_initiative"));
    }

    @Test
    void aLegacySeatValuesAnEconomyCardWithoutTheStall() {
        AiSettings.markLegacyActionCards(test.nekro);
        test.nekro.setActionCard("mining_initiative", 1);

        assertThat(ActionCardValue.bestComponentPlay(test.game, test.nekro))
                .hasValueSatisfying(best -> assertThat(best.value())
                        .isEqualTo(ActionCardValue.playValue(test.game, test.nekro, "mining_initiative")));
    }

    @Test
    void aSecondSabotageIsWorthLessThanTheFirst() {
        test.nekro.setActionCard("sabo1", 1);
        test.nekro.setActionCard("sabo2", 2);

        assertThat(keep("sabo2")).isLessThan(keep("sabo1"));
        assertThat(keep("sabo2")).isEqualTo(0.6 * ActionCardPrior.keepPrior("sabo2"));
    }

    @Test
    void aLoneSabotageIsWorthTheFullPriorOnceOverruleHasBeenSeen() {
        test.nekro.setActionCard("sabo3", 3);
        test.game.getGameStats().recordAcPlay("Overrule", test.sol);

        assertThat(keep("sabo3")).isEqualTo(ActionCardPrior.keepPrior("sabo3"));
    }

    @Test
    void aLoneSabotageIsWorthTheReserveWhileOverruleIsUnseen() {
        test.nekro.setActionCard("sabo3", 3);

        assertThat(keep("sabo3")).isEqualTo(SabotagePolicy.OVERRULE_RESERVE);
    }

    // Overrule has not been seen, so the only Sabotage is kept for it (worth 3), above a stall card (worth 1.5).
    @Test
    void aCardNoRulePlaysIsDiscardedBeforeOneThatIsUsable() {
        test.nekro.setActionCard("rout", 1);
        test.nekro.setActionCard("industrial_initiative", 2);
        test.nekro.setActionCard("sabo1", 3);

        assertThat(ActionCardValue.discardOrder(test.game, test.nekro))
                .containsExactly("rout", "industrial_initiative", "sabo1");
    }

    // Among cards that are worth nothing the weaker one goes first.
    @Test
    void cardsNoRulePlaysAreSortedByTheirPrior() {
        test.nekro.setActionCard("rout", 1);
        test.nekro.setActionCard("courageous", 2);
        test.nekro.setActionCard("bunker", 3);

        assertThat(ActionCardValue.discardOrder(test.game, test.nekro)).containsExactly("courageous", "bunker", "rout");
    }

    @Test
    void theExtraSabotageGoesBeforeTheFirstOne() {
        test.nekro.setActionCard("sabo1", 1);
        test.nekro.setActionCard("sabo2", 2);
        test.nekro.setActionCard("industrial_initiative", 3);

        List<String> order = ActionCardValue.discardOrder(test.game, test.nekro);

        assertThat(order.indexOf("sabo2")).isLessThan(order.indexOf("sabo1"));
    }

    @Test
    void aLegacySeatKeepsValuingCardsTheOldWay() {
        AiSettings.markLegacyActionCards(test.nekro);

        assertThat(keep("sabo1")).isEqualTo(1.5);
        assertThat(keep("mb1")).isEqualTo(1.0);
        assertThat(keep("intercept")).isZero();
        assertThat(keep("mjets1")).isEqualTo(0.5);
        assertThat(keep("industrial_initiative")).isEqualTo(3.0);
    }

    @Test
    void aSeatIsLegacyOnlyWhenMarked() {
        assertThat(AiSettings.usesLegacyActionCards(test.nekro)).isFalse();

        AiSettings.markLegacyActionCards(test.nekro);

        assertThat(AiSettings.usesLegacyActionCards(test.nekro)).isTrue();
        assertThat(AiSettings.usesLegacyActionCards(test.sol)).isFalse();
    }
}
