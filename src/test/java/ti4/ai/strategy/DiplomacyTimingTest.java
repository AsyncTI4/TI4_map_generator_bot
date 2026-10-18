package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// A seat holding Diplomacy with Erect a Monument (8 resources) revealed should spend first and ready the planets it
// just exhausted afterwards, so the status-phase spend objective still finds them ready.
class DiplomacyTimingTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;
    private static final int TECHNOLOGY = 7;
    private static final String DIPLOMACY_BUTTON = "FFCC_nekro_strategicAction_2";
    private static final String TACTICAL_BUTTON = "FFCC_nekro_tacticalAction";
    private static final String PASS_BUTTON = "FFCC_nekro_passForRound";

    private final NekroBrain brain = new NekroBrain();
    private AiTestGame test;
    private Tile home;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.game.setVp(10);
        test.aiIsActive("action");
        test.nekro.addSC(DIPLOMACY);
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private AiPrompt turnStart() {
        return prompt("turn", PromptSource.PUBLIC, NOW, TACTICAL_BUTTON, DIPLOMACY_BUTTON, PASS_BUTTON);
    }

    private void exhausted(String tileId, String position, String... planets) {
        test.place(tileId, position);
        for (String planet : planets) {
            test.nekro.addPlanet(planet);
            test.nekro.exhaustPlanet(planet);
        }
        changed();
    }

    private void readied(String tileId, String position, String... planets) {
        test.place(tileId, position);
        for (String planet : planets) test.nekro.addPlanet(planet);
        changed();
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
        changed();
    }

    private boolean dueNow() {
        return StrategyCardTurn.dueNow(test.context(turnStart()), DIPLOMACY);
    }

    private double readyValue() {
        StrategyCardReplanner.refresh(test.context(turnStart()));
        return CardPlanMemory.read(test.context(turnStart()), DIPLOMACY)
                .flatMap(plan -> plan.benefit(CardUse.DIPLOMACY_READY))
                .map(CardPlan.Benefit::value)
                .orElse(0.0);
    }

    // Mordai II (4) and Lodor (3) are ready; Abyz (3) and Fria (2) were exhausted by the last agenda vote. Readying
    // Abyz and Fria alone would pay for Erect a Monument, so the old timing played Diplomacy at once.
    private void monumentWithPlanetsToSpend() {
        readied("26", "201", "lodor");
        exhausted("38", "202", "abyz", "fria");
        reveal("monument");
    }

    private Tile threatenHome() {
        Tile next = test.place("27", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(next, "space", test.sol, UnitType.Carrier, 2);
        test.units(next, "space", test.sol, UnitType.Infantry, 6);
        changed();
        return next;
    }

    private void productionFundedByReadying() {
        test.nekro.exhaustPlanet("mordaiii");
        exhausted("38", "202", "abyz");
        test.nekro.setFleetCC(3);
        changed();
    }

    @Test
    void readiesAtOnceWhenNoSpendObjectiveIsRevealed() {
        productionFundedByReadying();

        assertThat(dueNow()).isTrue();
    }

    @Test
    void defersWhileASpendObjectiveIsRevealedAndTheSeatStillHasSpendingToDo() {
        monumentWithPlanetsToSpend();

        assertThat(test.nekro.getTacticalCC()).isPositive();
        assertThat(readyValue()).isGreaterThan(CardValueScale.READY_SHARE * 3);
        assertThat(dueNow()).isFalse();
    }

    @Test
    void readiesAtOnceWhenTheSeatHasScoredTheSpendObjectiveAlready() {
        productionFundedByReadying();
        reveal("monument");
        test.game.getScoredPublicObjectives().put("monument", new ArrayList<>(List.of(test.nekro.getUserID())));
        changed();

        assertThat(dueNow()).isTrue();
    }

    @Test
    void readiesAtOnceWhenAThreatenedSystemNeedsTheLock() {
        monumentWithPlanetsToSpend();
        threatenHome();

        StrategyCardReplanner.refresh(test.context(turnStart()));
        assertThat(CardPlanMemory.read(test.context(turnStart()), DIPLOMACY)
                        .orElseThrow()
                        .has(CardUse.DIPLOMACY_PROTECT))
                .isTrue();
        assertThat(dueNow()).isTrue();
    }

    @Test
    void readiesAtOnceWhenItIsOutOfTacticTokens() {
        monumentWithPlanetsToSpend();
        test.nekro.setTacticalCC(0);
        changed();

        assertThat(dueNow()).isTrue();
    }

    // Mordai II is the only ready planet and the reserve for the objective needs all of it: nothing else will spend,
    // so readying now to unlock the score is still right.
    @Test
    void readiesAtOnceWhenTheOnlyReadyFundsAreReservedForTheObjective() {
        exhausted("38", "202", "abyz", "fria");
        reveal("monument");

        assertThat(test.nekro.getTacticalCC()).isPositive();
        assertThat(readyValue()).isGreaterThan(CardValueScale.READY_SHARE * 3);
        assertThat(dueNow()).isTrue();
    }

    // Out of tactic tokens, but a Technology follow is still to come and would use the ready planets: wait for it.
    @Test
    void stillDefersWhenOnlyTechnologyIsLeftToSpendOn() {
        monumentWithPlanetsToSpend();
        test.nekro.setTacticalCC(0);
        test.sol.addSC(TECHNOLOGY);
        test.nekro.setStrategicCC(2);
        changed();

        assertThat(dueNow()).isFalse();
    }

    @Test
    void anAiSeatPlaysTheDeferredCardBeforePassingWhenNoTacticalActionIsWorthIt() {
        monumentWithPlanetsToSpend();
        test.nekro.setTacticalCC(1);
        changed();

        AiDecision decision = brain.decide(test.context(turnStart()));

        assertThat(AiTestGame.pressedId(decision)).isEqualTo(DIPLOMACY_BUTTON);
    }

    @Test
    void spendsOnATacticalActionBeforePlayingTheDeferredCard() {
        monumentWithPlanetsToSpend();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("27", AiTestGame.neighbourOf(AiTestGame.HOME));
        changed();

        AiDecision decision = brain.decide(test.context(turnStart()));

        assertThat(AiTestGame.pressedId(decision)).isEqualTo(TACTICAL_BUTTON);
    }

    // The seat spends Mordai II (4) first. Once nothing is left to spend, Diplomacy is due and readies Mordai II with
    // Abyz: the planet it just spent counts towards the 8 resources, which Lodor (3) alone could not reach.
    @Test
    void readiesThePlanetItJustSpentOnceTheSpendingIsDone() {
        monumentWithPlanetsToSpend();
        assertThat(dueNow()).isFalse();

        test.nekro.exhaustPlanet("mordaiii");
        changed();

        assertThat(dueNow()).isTrue();
        assertThat(DiplomacyUse.best(test.game, test.nekro, test.nekro.getExhaustedPlanets()))
                .hasValueSatisfying(choice -> {
                    assertThat(choice.unlocksScore()).isTrue();
                    assertThat(choice.planets()).containsExactlyInAnyOrder("mordaiii", "abyz");
                });
        AiDecision decision = brain.decide(test.context(Set.of(), turnStart()));
        assertThat(AiTestGame.pressedId(decision)).isEqualTo(DIPLOMACY_BUTTON);
    }
}
