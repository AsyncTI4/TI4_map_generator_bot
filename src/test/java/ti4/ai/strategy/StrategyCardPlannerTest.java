package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.StrategyCard;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Each strategy card is valued by what its primary would concretely do for the seat on this board, plus a small
// nudge from how often human Nekro players pick it in that round.
class StrategyCardPlannerTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int DIPLOMACY = 2;
    private static final int POLITICS = 3;
    private static final int CONSTRUCTION = 4;
    private static final int TRADE = 5;
    private static final int WARFARE = 6;
    private static final int TECHNOLOGY = 7;
    private static final int IMPERIAL = 8;
    private static final String MECATOL = "000";

    private AiTestGame test;
    private Tile home;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.game.setVp(10);
        changed();
    }

    // Plans are cached per game modification; tests that change the board mark the game as modified, as a save
    // would in a live game.
    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private CardPlan plan(int initiative) {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, test.nekro, PlanMoment.PICK), initiative);
    }

    private double value(int initiative) {
        return StrategyCardRanking.value(test.game, test.nekro, initiative);
    }

    private Tile placeMecatol() {
        Tile mecatol = test.place("18", MECATOL);
        changed();
        return mecatol;
    }

    private void solDreadnoughtNextTo(String position) {
        Tile next = test.place("26", AiTestGame.neighbourOf(position));
        test.units(next, "space", test.sol, UnitType.Dreadnought, 1);
        changed();
    }

    private void freePlanetsNextToHome() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        changed();
    }

    // The reported mistake: Imperial picked with nothing to score and no Mecatol Rex. A fresh secret is worth a lot
    // (SecretDrawValue), but the same secret is available to anyone following Imperial for a strategy token, so picking
    // Imperial for it saves only that token, and Imperial falls below every card with a real use.
    @Test
    void valuesImperialOnlyAsASecretWhenItCanScoreNothing() {
        test.game.setRound(3);
        changed();

        assertThat(SecretDrawValue.of(test.game, test.nekro)).isGreaterThan(CardValueScale.STRATEGY_TOKEN);
        assertThat(plan(IMPERIAL).benefits()).extracting(Benefit::use).containsExactly(CardUse.IMPERIAL_SECRET);
        assertThat(plan(IMPERIAL).benefits().getFirst().value()).isEqualTo(CardValueScale.STRATEGY_TOKEN);
        for (int card : List.of(LEADERSHIP, POLITICS, CONSTRUCTION, TRADE)) {
            assertThat(value(IMPERIAL)).as("Imperial against card " + card).isLessThan(value(card));
        }
    }

    // The guard above rests on someone else picking Imperial for the seat to follow. The last seat to pick has no one
    // left after it, so leaving Imperial means nobody plays it this round and its secret is only had by picking it.
    @Test
    void valuesTheWholeSecretWhenNoOneElseIsLeftToPickImperial() {
        test.game.setRound(3);
        test.sol.addSC(LEADERSHIP);
        changed();

        assertThat(test.nekro.getStrategicCC()).isPositive();
        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_SECRET))
                .map(Benefit::value)
                .contains(SecretDrawValue.of(test.game, test.nekro));
    }

    // Human players practically never pick Imperial in round 1, so the last picker keeps the token-only secret there.
    @Test
    void keepsTheTokenOnlySecretForTheLastPickerInRoundOne() {
        test.game.setRound(1);
        test.sol.addSC(LEADERSHIP);
        changed();

        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_SECRET))
                .map(Benefit::value)
                .contains(Math.min(SecretDrawValue.of(test.game, test.nekro), CardValueScale.STRATEGY_TOKEN));
    }

    // Two secrets scored and the third already scorable: a draw would only replace a secret worth keeping.
    @Test
    void valuesNoSecretWhenTheFullHandIsWorthKeeping() {
        test.game.setRound(3);
        test.nekro.setSecretScored("dhw");
        test.nekro.setSecretScored("gamf");
        test.game.addLaw("conventions", null);
        test.game.addLaw("minister_war", "sol");
        test.game.addLaw("shard_of_the_throne", "sol");
        test.nekro.setSecret("dp");
        changed();

        assertThat(plan(IMPERIAL).benefits()).extracting(Benefit::use).doesNotContain(CardUse.IMPERIAL_SECRET);
    }

    @Test
    void addsTheTradeGoodsOnACardOneForOne() {
        double without = value(IMPERIAL);
        test.game.setScTradeGood(IMPERIAL, 3);

        assertThat(value(IMPERIAL)).isEqualTo(without + 3.0);
    }

    @Test
    void cashesTheImperialPointForHoldingMecatolRex() {
        // Nekro Malleon would lift a drawn action card to 2 trade goods; this case values the cards without it.
        test.nekro.getLeader("nekroagent").orElseThrow().setExhausted(true);
        placeMecatol();
        test.nekro.addPlanet("mr");
        changed();

        assertThat(plan(IMPERIAL).main()).map(Benefit::use).contains(CardUse.IMPERIAL_MECATOL);
        assertThat(IntStream.rangeClosed(1, 8).filter(card -> card != IMPERIAL))
                .allSatisfy(card -> assertThat(value(card)).isLessThan(value(IMPERIAL)));
    }

    // A fleet that can reach Mecatol Rex this round may take it before Imperial is played.
    @Test
    void discountsAMecatolPointAnEnemyFleetCanReach() {
        placeMecatol();
        test.nekro.addPlanet("mr");
        solDreadnoughtNextTo(MECATOL);

        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_MECATOL))
                .map(Benefit::value)
                .contains(CardValueScale.MECATOL_THREATENED_SHARE * CardValueScale.VICTORY_POINT);
    }

    // Taking Imperial keeps the point away from a Mecatol Rex holder who has not picked yet.
    @Test
    void deniesImperialToAMecatolHolderStillToPick() {
        placeMecatol();
        test.sol.addPlanet("mr");
        test.game.setSpeaker(test.nekro);
        changed();

        assertThat(StrategyCardRanking.imperialDenial(test.game, test.nekro)).isPositive();

        test.sol.addSC(TRADE);
        assertThat(StrategyCardRanking.imperialDenial(test.game, test.nekro)).isZero();
    }

    // Politics makes someone other than the current speaker the speaker, so the speaker would hand the token away.
    @Test
    void countsTheSpeakerTokenAsALossWhenAlreadySpeaker() {
        test.game.setSpeaker(test.sol);
        changed();
        assertThat(plan(POLITICS).benefit(CardUse.POLITICS_SPEAKER))
                .map(Benefit::value)
                .hasValueSatisfying(value -> assertThat(value).isPositive());

        test.game.setSpeaker(test.nekro);
        changed();
        assertThat(plan(POLITICS).benefit(CardUse.POLITICS_SPEAKER))
                .map(Benefit::value)
                .hasValueSatisfying(value -> assertThat(value).isNegative());
    }

    // Leadership's tokens are worth the actions they unlock: with no tactic tokens left, the free planets next door
    // are out of reach without them.
    @Test
    void valuesLeadershipTokensByTheActionsTheyUnlock() {
        freePlanetsNextToHome();
        test.nekro.setTacticalCC(5);
        changed();
        double plenty = plan(LEADERSHIP).value();

        test.nekro.setTacticalCC(0);
        changed();

        assertThat(plan(LEADERSHIP).value()).isGreaterThan(plenty);
    }

    // Tokens that no plan needs this round still carry over to later rounds, so each is worth a held token.
    @Test
    void valuesLeadershipTokensAtLeastAHeldTokenEachWithoutPlannedActions() {
        test.nekro.setTacticalCC(5);
        changed();

        double tokens = plan(LEADERSHIP).benefits().stream()
                .filter(benefit -> benefit.use() == CardUse.LEADERSHIP_TOKENS)
                .mapToDouble(CardPlan.Benefit::value)
                .sum();

        assertThat(tokens).isGreaterThanOrEqualTo(StrategyCardRules.LEADERSHIP_TOKENS * CardValueScale.HELD_TOKEN);
    }

    // A lone dreadnought in reach cannot land on anything, so locking home is not worth a card. A landing party with
    // ground forces next to the empty home is.
    @Test
    void locksTheHomeSystemWithDiplomacyOnlyAgainstALandingFleetInReach() {
        assertThat(plan(DIPLOMACY).has(CardUse.DIPLOMACY_PROTECT)).isFalse();

        solDreadnoughtNextTo(AiTestGame.HOME);
        assertThat(plan(DIPLOMACY).has(CardUse.DIPLOMACY_PROTECT)).isFalse();

        Tile next = test.game.getTileByPosition(AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(next, "space", test.sol, UnitType.Carrier, 2);
        test.units(next, "space", test.sol, UnitType.Infantry, 6);
        changed();

        Benefit protect = plan(DIPLOMACY).benefit(CardUse.DIPLOMACY_PROTECT).orElseThrow();
        assertThat(protect.target()).isEqualTo(AiTestGame.HOME);
        assertThat(protect.value()).isGreaterThanOrEqualTo(CardValueScale.MIN_LOCK_EXPOSURE);
    }

    // Base-game Diplomacy has no buttons for its primary, so the AI cannot use it beyond the trade goods on it.
    @Test
    void recognisesBaseGameDiplomacyButFindsNoUseForIt() {
        test.game.setStrategyCardSet("base_game");
        solDreadnoughtNextTo(AiTestGame.HOME);

        assertThat(StrategyCard.of(test.game, DIPLOMACY)).isEqualTo(StrategyCard.DIPLOMACY);
        assertThat(plan(DIPLOMACY).benefits()).isEmpty();
    }

    // Thunder's Edge Construction may use a space dock's production instead of its first structure; the PoK card
    // cannot.
    @Test
    void producesAtADockOnlyWithThundersEdgeConstruction() {
        test.nekro.setTg(10);
        changed();
        assertThat(ConstructionUses.bestProduction(PlanInputs.of(test.game, test.nekro, PlanMoment.PICK)))
                .isPresent();

        test.game.setStrategyCardSet("pok");
        changed();

        assertThat(plan(CONSTRUCTION).has(CardUse.CONSTRUCTION_PRODUCE)).isFalse();
    }

    // Thunder's Edge Warfare is a free tactical action: worth most when the seat has no tactic token for the next
    // planet it wants.
    @Test
    void valuesThundersEdgeWarfareAsAnExtraAction() {
        freePlanetsNextToHome();
        test.nekro.setTacticalCC(0);
        changed();

        assertThat(plan(WARFARE).benefit(CardUse.WARFARE_EXTRA_ACTION))
                .map(Benefit::value)
                .hasValueSatisfying(value -> assertThat(value).isPositive());
    }

    @Test
    void valuesPokWarfareForItsCommandToken() {
        test.game.setStrategyCardSet("pok");
        changed();

        assertThat(plan(WARFARE).has(CardUse.WARFARE_TOKEN)).isTrue();
    }

    // Nekro cannot research: Technology's primary gives it Propagation's command tokens instead.
    @Test
    void valuesTechnologyAsPropagationTokensForNekro() {
        assertThat(plan(TECHNOLOGY).benefits())
                .extracting(Benefit::use)
                .containsExactly(CardUse.TECHNOLOGY_PROPAGATION);
    }

    @Test
    void nudgesPicksTowardsWhatNekroPlayersTakeEachRound() {
        assertThat(PickPrior.nudge(StrategyCard.IMPERIAL, 1)).isNegative();
        assertThat(PickPrior.nudge(StrategyCard.WARFARE, 1)).isPositive();
        assertThat(PickPrior.nudge(StrategyCard.IMPERIAL, 4)).isPositive();
    }
}
