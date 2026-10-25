package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Orbital Drop costs the turn's action and a strategy token for 2 infantry on a planet the seat controls. Sol holds
// Jord (its home, with a space dock) and Lodor, three tokens in its strategy pool, and Nekro is still in the round.
class OrbitalDropRulesTest extends BaseTi4Test {

    private static final String JORD = "jord";
    private static final String LODOR = "lodor";

    private AiTestGame test;
    private Player sol;
    private Tile home;
    private Tile lodor;
    private String lodorPosition;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(4);
        home = test.place("01", AiTestGame.HOME);
        sol.addPlanet(JORD);
        test.units(home, JORD, sol, UnitType.Spacedock, 1);
        test.units(home, JORD, sol, UnitType.Infantry, 2);
        lodorPosition = AiTestGame.neighbourOf(AiTestGame.HOME);
        lodor = test.place("26", lodorPosition);
        sol.addPlanet(LODOR);
        sol.setStrategicCC(3);
        sol.setTacticalCC(3);
        sol.setFleetCC(5);
        test.isActive(sol, "action");
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(sol, Set.of(), NOW, prompts);
    }

    private static AiPrompt turnMenu() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_sol_tacticalAction",
                "FFCC_sol_componentAction",
                "FFCC_sol_passForRound");
    }

    private static AiPrompt componentMenu() {
        return prompt(
                "menu",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_sol_componentActionRes_ability_orbitalDrop",
                "FFCC_sol_componentActionRes_ability_generic",
                "deleteButtons");
    }

    private static AiPrompt planets() {
        return prompt(
                "planets",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_sol_placeOneNDone_skipbuildorbital_2gf_jord",
                "FFCC_sol_placeOneNDone_skipbuildorbital_2gf_lodor");
    }

    private static AiPrompt followUp() {
        return prompt(
                "follow",
                PromptSource.PUBLIC,
                NOW,
                List.of("orbitalMechDrop_jord", "orbitalMechDrop_lodor", "finishComponentAction_spitItOut"),
                List.of("Pay 3r for Mech?", "Pay 3r for Mech?", "Finish Orbital Drop"));
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private Optional<AiDecision> beforePassing() {
        AiTurnContext context = context(turnMenu());
        return OrbitalDropRules.beforePassing(context, Prompts.thisTurn(context));
    }

    private void raidersBesideJord(int size) {
        Player sardakk = test.addSeat("200000000000000002", "sardakk", "red");
        sardakk.setFleetCC(5);
        test.place("13", "310");
        sardakk.addPlanet("trenlak");
        sardakk.addPlanet("quinarra");
        String beside = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && !lodorPosition.equals(position))
                .findFirst()
                .orElseThrow();
        Tile staging = test.place("46", beside);
        test.units(staging, "space", sardakk, UnitType.Carrier, size);
        test.units(staging, "space", sardakk, UnitType.Cruiser, size);
        test.units(staging, "space", sardakk, UnitType.Infantry, 2 * size);
    }

    private static AiPrompt planetsLodorFirst() {
        return prompt(
                "planets",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_sol_placeOneNDone_skipbuildorbital_2gf_lodor",
                "FFCC_sol_placeOneNDone_skipbuildorbital_2gf_jord");
    }

    private void walkToThePlanetChoice() {
        beforePassing();
        OrbitalDropRules.next(context(componentMenu()));
    }

    // With tokens to spare and Nekro still acting, the drop is a stall that also puts 2 infantry on the board.
    @Test
    void stallsWithSpareStrategyTokensAndWalksTheWholeChain() {
        assertThat(pressed(beforePassing())).isEqualTo("FFCC_sol_componentAction");

        assertThat(pressed(OrbitalDropRules.next(context(componentMenu()))))
                .isEqualTo("FFCC_sol_componentActionRes_ability_orbitalDrop");
        String planet = pressed(OrbitalDropRules.next(context(planets())));
        assertThat(planet).startsWith("FFCC_sol_placeOneNDone_skipbuildorbital_2gf_");
        assertThat(pressed(OrbitalDropRules.next(context(followUp())))).isEqualTo("finishComponentAction_spitItOut");
        assertThat(OrbitalDropRules.next(context(followUp()))).isEmpty();
    }

    @Test
    void keepsTheLastStrategyTokenForNothing() {
        sol.setStrategicCC(1);

        assertThat(beforePassing()).isEmpty();
    }

    @Test
    void doesNotStallOnceEveryoneElseHasPassed() {
        test.nekro.setPassed(true);

        assertThat(beforePassing()).isEmpty();
    }

    @Test
    void needsAStrategyTokenToSpend() {
        sol.setStrategicCC(0);

        assertThat(beforePassing()).isEmpty();
    }

    @Test
    void waitsForTheFollowUpInsteadOfEndingTheTurnMidChain() {
        beforePassing();
        OrbitalDropRules.next(context(componentMenu()));
        OrbitalDropRules.next(context(planets()));

        assertThat(OrbitalDropRules.next(context())).containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void leavesTheChainWhenTheTurnCanAlreadyEnd() {
        beforePassing();
        OrbitalDropRules.next(context(componentMenu()));
        OrbitalDropRules.next(context(planets()));
        AiPrompt over = prompt("over", PromptSource.PUBLIC, NOW + 1000, "FFCC_sol_turnEnd");

        assertThat(OrbitalDropRules.next(context(over))).isEmpty();
        assertThat(OrbitalDropRules.next(context())).isEmpty();
    }

    @Test
    void dropsOnThePlanetAnEnemyFleetThreatens() {
        raidersBesideJord(2);
        walkToThePlanetChoice();

        assertThat(pressed(OrbitalDropRules.next(context(planetsLodorFirst()))))
                .isEqualTo("FFCC_sol_placeOneNDone_skipbuildorbital_2gf_jord");
    }

    // Against a landing the 2 infantry alone cannot turn, a mech on the planet does, so it pays the 3 resources.
    @Test
    void deploysAMechWhenTheLandingIsBeyondTwoInfantry() {
        raidersBesideJord(3);
        walkToThePlanetChoice();
        assertThat(pressed(OrbitalDropRules.next(context(planetsLodorFirst()))))
                .isEqualTo("FFCC_sol_placeOneNDone_skipbuildorbital_2gf_jord");

        assertThat(pressed(OrbitalDropRules.next(context(followUp())))).isEqualTo("orbitalMechDrop_jord");
        assertThat(PaymentRules.isPending(context())).isTrue();
        assertThat(OrbitalDropRules.next(context(followUp()))).isEmpty();
    }

    // The deploy costs 3 resources for a 2-cost mech; on a quiet planet it is not worth it, so the chain just finishes.
    @Test
    void skipsTheMechOnAQuietPlanet() {
        beforePassing();
        OrbitalDropRules.next(context(componentMenu()));
        OrbitalDropRules.next(context(planets()));

        assertThat(pressed(OrbitalDropRules.next(context(followUp())))).isEqualTo("finishComponentAction_spitItOut");
        assertThat(PaymentRules.isPending(context())).isFalse();
    }

    @Test
    void takesAStrongerTacticalActionInstead() {
        AiTurnContext context = context(turnMenu());
        TacticalRules.remember(
                context, new TacticalPlan(Kind.EXPAND, lodorPosition, List.of(), java.util.Map.of(), 50));

        assertThat(OrbitalDropRules.insteadOfTacticalAction(context, Prompts.thisTurn(context)))
                .isEmpty();
    }

    @Test
    void doesNothingOutsideItsOwnActionPhaseTurn() {
        test.isActive(test.nekro, "action");

        assertThat(beforePassing()).isEmpty();
    }

    @Test
    void asksForTheMenuOnlyOncePerTurn() {
        assertThat(pressed(beforePassing())).isEqualTo("FFCC_sol_componentAction");

        assertThat(beforePassing()).isEmpty();
    }
}
