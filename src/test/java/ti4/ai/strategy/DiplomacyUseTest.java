package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class DiplomacyUseTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int TECHNOLOGY = 7;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.aiIsActive("action");
    }

    // Nekro's home system without a space dock: it may score public objectives, but has nowhere to produce.
    private void homeWithoutDock() {
        test.place("08", AiTestGame.HOME);
        test.nekro.addPlanet("mordaiii");
    }

    private void exhausted(String tileId, String position, String... planets) {
        test.place(tileId, position);
        for (String planet : planets) {
            test.nekro.addPlanet(planet);
            test.nekro.exhaustPlanet(planet);
        }
    }

    // Abyz (3/0) and Fria (2/0) lean to resources, Rarron (0/3) and Meer (0/4) to influence; all four are exhausted.
    private void exhaustedMixedPlanets() {
        exhausted("38", "201", "abyz", "fria");
        exhausted("29", "202", "rarron");
        exhausted("37", "203", "meer");
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    private Optional<DiplomacyUse.Choice> best() {
        return DiplomacyUse.best(test.game, test.nekro, test.nekro.getExhaustedPlanets());
    }

    private boolean worthFollowing() {
        return DiplomacyUse.worthFollowing(test.game, test.nekro);
    }

    // A seat that has passed takes no more actions, so readied planets only matter for a status-phase spend objective.
    // Leadership still to come does not change that.
    @Test
    void doesNotFollowAfterPassingWithoutASpendObjective() {
        homeWithoutDock();
        exhaustedMixedPlanets();
        test.sol.addSC(LEADERSHIP);
        test.nekro.setPassed(true);

        assertThat(worthFollowing()).isFalse();
        assertThat(best()).hasValueSatisfying(choice -> assertThat(choice.use()).isZero());
    }

    // Planets readied now stay ready until status-phase scoring. Erect a Monument needs 8 resources: Mordai II pays
    // 4, and only Abyz and Fria together add enough, so that pair is the plan even after passing.
    @Test
    void followsAfterPassingWhenThePairPaysForErectAMonument() {
        homeWithoutDock();
        exhausted("38", "201", "abyz", "fria");
        exhausted("29", "202", "rarron");
        test.nekro.setPassed(true);
        reveal("monument");

        assertThat(worthFollowing()).isTrue();
        assertThat(best()).hasValueSatisfying(choice -> {
            assertThat(choice.unlocksScore()).isTrue();
            assertThat(choice.planets()).containsExactlyInAnyOrder("abyz", "fria");
        });
    }

    // With a space dock at home and nothing to spend, Mordai II (4) and Abyz (3) buy a carrier and four infantry the
    // production planner would build anyway, 5 more than it can build now. The influence planets buy nothing.
    @Test
    void readiesTheResourcePairWhenProductionWouldSpendIt() {
        test.nekroHome();
        test.nekro.exhaustPlanet("mordaiii");
        exhausted("38", "201", "abyz");
        exhausted("29", "202", "rarron");
        exhausted("37", "203", "meer");
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);

        assertThat(worthFollowing()).isTrue();
        assertThat(best()).hasValueSatisfying(choice -> {
            assertThat(choice.planets()).containsExactlyInAnyOrder("mordaiii", "abyz");
            assertThat(choice.use()).isGreaterThanOrEqualTo(3);
        });
    }

    // Production is only a use while the seat still has a tactic token to activate the dock with.
    @Test
    void ignoresProductionWithoutATacticToken() {
        test.nekroHome();
        test.nekro.exhaustPlanet("mordaiii");
        exhausted("38", "201", "abyz");
        test.nekro.setTacticalCC(0);

        assertThat(worthFollowing()).isFalse();
    }

    // Leadership is still to come: Rarron and Meer give 7 influence, 2 command tokens at 3 influence each. Resource
    // planets buy no tokens.
    @Test
    void readiesInfluencePlanetsForLeadershipStillToCome() {
        homeWithoutDock();
        exhaustedMixedPlanets();
        test.sol.addSC(LEADERSHIP);

        assertThat(worthFollowing()).isTrue();
        assertThat(best()).hasValueSatisfying(choice -> {
            assertThat(choice.planets()).containsExactlyInAnyOrder("rarron", "meer");
            assertThat(choice.use()).isEqualTo(6);
        });
    }

    // Its carrier is one system from Mecatol Rex, but the custodians cost 6 influence it does not have; Rarron and
    // Meer cover it.
    @Test
    void readiesInfluencePlanetsToPayTheCustodians() {
        homeWithoutDock();
        exhaustedMixedPlanets();
        Tile rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        Tile next = test.place("26", AiTestGame.neighbourOf("000"));
        test.units(next, "space", test.nekro, UnitType.Carrier, 1);

        assertThat(worthFollowing()).isTrue();
        assertThat(best()).hasValueSatisfying(choice -> {
            assertThat(choice.planets()).containsExactlyInAnyOrder("rarron", "meer");
            assertThat(choice.use()).isEqualTo(TokenPurchase.CUSTODIANS_COST);
        });
    }

    // The custodians are only paid while landing in a tactical action, which takes a tactic token: without one the 6
    // influence would sit unspent.
    @Test
    void ignoresTheCustodiansWithoutATacticToken() {
        homeWithoutDock();
        exhaustedMixedPlanets();
        Tile rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        Tile next = test.place("26", AiTestGame.neighbourOf("000"));
        test.units(next, "space", test.nekro, UnitType.Carrier, 1);
        test.nekro.setTacticalCC(0);

        assertThat(worthFollowing()).isFalse();
        assertThat(best()).hasValueSatisfying(choice -> assertThat(choice.use()).isZero());
    }

    // Abyz (3/0) and Fria (2/0) are exhausted and Nekro has nothing else to spend: together they pay Propagation's 4
    // resources on a Technology follow.
    private void exhaustedResourcesForPropagation(int strategyTokens) {
        exhausted("38", "201", "abyz", "fria");
        test.nekro.setStrategicCC(strategyTokens);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
    }

    // Nekro's own Technology primary is Propagation, which gains its command tokens for free, so readying resources
    // for it is no use.
    @Test
    void ownTechnologyGivesNekroNoUseForResources() {
        exhaustedResourcesForPropagation(2);
        test.nekro.addSC(TECHNOLOGY);

        assertThat(worthFollowing()).isFalse();
        assertThat(best()).hasValueSatisfying(choice -> assertThat(choice.use()).isZero());
    }

    // Following Technology takes a strategy token of its own. With 2 the seat can follow Diplomacy now and Sol's
    // Technology later, so Abyz and Fria are worth Propagation's 4 resources.
    @Test
    void readiesResourcesForATechnologyFollowWithATokenLeftForIt() {
        exhaustedResourcesForPropagation(2);
        test.sol.addSC(TECHNOLOGY);

        assertThat(worthFollowing()).isTrue();
        assertThat(DiplomacyUse.bestToFollow(test.game, test.nekro)).hasValueSatisfying(choice -> {
            assertThat(choice.planets()).containsExactlyInAnyOrder("abyz", "fria");
            assertThat(choice.use()).isEqualTo(StrategyCardRules.FOLLOW_TECH_COST);
        });
    }

    // With 1 strategy token, following Diplomacy would spend the token Technology needs, so the resources have no use
    // for the follow. The free primary keeps the token, so there they still count.
    @Test
    void doesNotReadyResourcesForATechnologyFollowItCouldNotAfford() {
        exhaustedResourcesForPropagation(1);
        test.sol.addSC(TECHNOLOGY);

        assertThat(worthFollowing()).isFalse();
        assertThat(DiplomacyUse.bestToFollow(test.game, test.nekro))
                .hasValueSatisfying(choice -> assertThat(choice.use()).isZero());
        assertThat(best())
                .hasValueSatisfying(choice -> assertThat(choice.use()).isEqualTo(StrategyCardRules.FOLLOW_TECH_COST));
    }

    // Sol's own Technology primary researches one technology for free and a second for 6 resources: Abyz and Fria's 5
    // do not pay for it, Abyz and Lodor's 6 do.
    @Test
    void readiesResourcesForTheSecondTechnologyOfItsOwnPrimary() {
        test.place("38", "201");
        test.place("26", "202");
        for (String planet : List.of("abyz", "fria")) {
            test.sol.addPlanet(planet);
            test.sol.exhaustPlanet(planet);
        }
        test.sol.addSC(TECHNOLOGY);

        assertThat(DiplomacyUse.best(test.game, test.sol, test.sol.getExhaustedPlanets()))
                .hasValueSatisfying(choice -> assertThat(choice.use()).isZero());

        test.sol.addPlanet("lodor");
        test.sol.exhaustPlanet("lodor");
        assertThat(DiplomacyUse.best(test.game, test.sol, test.sol.getExhaustedPlanets()))
                .hasValueSatisfying(choice -> {
                    assertThat(choice.planets()).containsExactlyInAnyOrder("abyz", "lodor");
                    assertThat(choice.use()).isEqualTo(StrategyCardRules.SECOND_TECH_COST);
                });
    }

    // With 4 command tokens in reinforcements, its own Leadership primary gains 3 for free and leaves room to buy only
    // 1 more; following someone else's Leadership could buy 2 with Rarron and Meer's 7 influence.
    @Test
    void countsOnlyTheTokensItsOwnLeadershipLeavesRoomToBuy() {
        homeWithoutDock();
        exhaustedMixedPlanets();
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(7);
        test.nekro.setFleetCC(3);
        test.nekro.addSC(LEADERSHIP);
        assertThat(StrategyCardRules.reinforcements(test.game, test.nekro)).isEqualTo(4);

        assertThat(best())
                .hasValueSatisfying(choice -> assertThat(choice.use()).isEqualTo(TokenPurchase.INFLUENCE_PER_TOKEN));

        test.nekro.removeSC(LEADERSHIP);
        test.sol.addSC(LEADERSHIP);
        assertThat(best())
                .hasValueSatisfying(
                        choice -> assertThat(choice.use()).isEqualTo(2 * TokenPurchase.INFLUENCE_PER_TOKEN));
    }

    // Nothing would spend the planets this round, so a strategy token is not worth it.
    @Test
    void declinesWithoutDemand() {
        homeWithoutDock();
        exhaustedMixedPlanets();

        assertThat(worthFollowing()).isFalse();
        assertThat(best()).hasValueSatisfying(choice -> assertThat(choice.use()).isZero());
    }

    // With one exhausted planet there is no pair: Abyz alone (3) plus Mordai II (4) and a trade good pay for Erect a
    // Monument.
    @Test
    void judgesASingleExhaustedPlanet() {
        homeWithoutDock();
        exhausted("38", "201", "abyz");
        test.nekro.setTg(1);
        reveal("monument");

        assertThat(best()).hasValueSatisfying(choice -> {
            assertThat(choice.planets()).containsExactly("abyz");
            assertThat(choice.unlocksScore()).isTrue();
        });
        assertThat(worthFollowing()).isTrue();
    }

    // The answer is cached until the game is saved again: revealing Erect a Monument without a save keeps the old
    // answer, and the next save (a new last-modified date) brings the new one.
    @Test
    void recomputesOnlyAfterTheGameChanges() {
        homeWithoutDock();
        exhausted("38", "201", "abyz", "fria");
        test.nekro.setPassed(true);
        assertThat(worthFollowing()).isFalse();

        reveal("monument");
        assertThat(worthFollowing()).isFalse();

        test.game.setLastModifiedDate(test.game.getLastModifiedDate() + 1);
        assertThat(worthFollowing()).isTrue();
    }

    // Another game with the same name and seat (as in back-to-back tests) never reuses this game's answer.
    @Test
    void doesNotShareTheCacheBetweenGameInstances() {
        homeWithoutDock();
        exhausted("38", "201", "abyz", "fria");
        test.nekro.setPassed(true);
        reveal("monument");
        assertThat(worthFollowing()).isTrue();

        AiTestGame other = AiTestGame.withSolAi();
        other.aiIsActive("action");
        other.game.setLastModifiedDate(test.game.getLastModifiedDate());

        assertThat(DiplomacyUse.worthFollowing(other.game, other.nekro)).isFalse();
    }

    @Test
    void hasNoChoiceWithoutExhaustedPlanets() {
        homeWithoutDock();

        assertThat(DiplomacyUse.best(test.game, test.nekro, List.of())).isEmpty();
        assertThat(worthFollowing()).isFalse();
    }
}
