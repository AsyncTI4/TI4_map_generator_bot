package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Peace Accords gives the Xxcha a free planet after Diplomacy, whether it plays the card or follows it, so Diplomacy
// is worth about a planet to the seat as long as an empty planet lies next to one it controls.
class PeaceAccordsStrategyTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.game.setRound(1);
        test.aiIsActive("action");
        test.nekroHome();
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(3);
    }

    private void emptyPlanetsNextToHome() {
        String neighbour = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position))
                .findFirst()
                .orElseThrow();
        test.place("38", neighbour);
    }

    private static AiPrompt diplomacyCard() {
        return prompt("diplomacy", PromptSource.PUBLIC, NOW, "sc_follow_2", "sc_no_follow_2", "diploRefresh2");
    }

    private void someoneElsePlayedDiplomacy() {
        test.sol.addSC(DIPLOMACY);
        test.game.setSCPlayed(DIPLOMACY, true);
        test.game.setActivePlayerID(test.sol.getUserID());
    }

    private CardPlan diplomacyPlan() {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, test.nekro, PlanMoment.PICK), DIPLOMACY);
    }

    @Test
    void diplomacyIsWorthAFreePlanetToTheSeatWithPeaceAccords() {
        emptyPlanetsNextToHome();
        double without = StrategyCardRanking.value(test.game, test.nekro, DIPLOMACY);

        test.nekro.addAbility("peace_accords");
        test.game.setLastModifiedDate(System.nanoTime());

        assertThat(diplomacyPlan().has(CardUse.DIPLOMACY_PEACE_ACCORDS)).isTrue();
        assertThat(StrategyCardRanking.value(test.game, test.nekro, DIPLOMACY))
                .isGreaterThan(without + CardValueScale.PEACE_ACCORDS_PLAY_MIN);
    }

    @Test
    void diplomacyIsWorthNothingExtraWithoutAPlanetToClaim() {
        test.nekro.addAbility("peace_accords");

        assertThat(diplomacyPlan().has(CardUse.DIPLOMACY_PEACE_ACCORDS)).isFalse();
    }

    @Test
    void otherFactionsGetNoPlanetFromDiplomacy() {
        emptyPlanetsNextToHome();

        assertThat(diplomacyPlan().has(CardUse.DIPLOMACY_PEACE_ACCORDS)).isFalse();
    }

    // The card is played in the first action rather than kept as a fallback, since the planet is the point of it.
    @Test
    void playsDiplomacyAtOnceWhenAPlanetIsFree() {
        emptyPlanetsNextToHome();
        test.nekro.addAbility("peace_accords");
        test.nekro.addSC(DIPLOMACY);

        assertThat(StrategyCardTurn.dueNow(test.context(), DIPLOMACY)).isTrue();
    }

    @Test
    void followsDiplomacyForThePlanetWithNothingToReady() {
        emptyPlanetsNextToHome();
        test.nekro.addAbility("peace_accords");
        someoneElsePlayedDiplomacy();

        assertThat(DiplomacyUse.worthFollowing(test.game, test.nekro)).isTrue();
        assertThat(StrategyCardRules.follow(test.context(diplomacyCard())).map(AiTestGame::pressedId))
                .contains("diploRefresh2");
    }

    @Test
    void doesNotFollowDiplomacyWithoutAPlanetToClaim() {
        test.nekro.addAbility("peace_accords");
        someoneElsePlayedDiplomacy();

        assertThat(StrategyCardRules.follow(test.context(diplomacyCard()))).isEmpty();
    }

    @Test
    void doesNotFollowDiplomacyWithoutThePlanetAbility() {
        emptyPlanetsNextToHome();
        someoneElsePlayedDiplomacy();

        assertThat(StrategyCardRules.follow(test.context(diplomacyCard()))).isEmpty();
    }

    @Test
    void theSecondaryIsWorthAboutThePlanet() {
        emptyPlanetsNextToHome();
        test.nekro.addAbility("peace_accords");

        Optional<Double> value =
                StrategyCardRules.secondaryValue(test.game, test.nekro, ti4.ai.brain.StrategyCard.DIPLOMACY);

        assertThat(value).hasValueSatisfying(worth -> assertThat(worth).isGreaterThan(CardValueScale.STRATEGY_TOKEN));
    }

    // The primary ready-planets button is also what makes the engine offer Peace Accords, so it is pressed even when no
    // planet is exhausted.
    @Test
    void thePrimaryPressesReadyToClaimAPlanetEvenWithNothingExhausted() {
        emptyPlanetsNextToHome();
        test.nekro.addAbility("peace_accords");
        AiPrompt card = diplomacyCard();
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), DIPLOMACY);

        assertThat(StrategyCardRules.resolvePrimary(test.context(card)).map(AiTestGame::pressedId))
                .contains("diploRefresh2");
    }

    @Test
    void thePrimaryDoesNotPressReadyWithNothingToReadyOrClaim() {
        test.nekro.addAbility("peace_accords");
        AiPrompt card = diplomacyCard();
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), DIPLOMACY);

        assertThat(StrategyCardRules.resolvePrimary(test.context(card))).isEmpty();
    }

    @Test
    void encodesTheNewBenefitInASavedPlan() {
        emptyPlanetsNextToHome();
        test.nekro.addAbility("peace_accords");
        CardPlan plan = diplomacyPlan();

        assertThat(CardPlan.decode(plan.card(), plan.initiative(), plan.encode()))
                .hasValueSatisfying(decoded ->
                        assertThat(decoded.has(CardUse.DIPLOMACY_PEACE_ACCORDS)).isTrue());
        assertThat(List.of(CardUse.values())).contains(CardUse.DIPLOMACY_PEACE_ACCORDS);
    }
}
