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
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Bellum Gloriosum lets the seat produce ground forces and fighters up to the capacity of a ship it just produced, on
// top of its PRODUCTION limit. Sol holds Jord (4 resources, a space dock) with a carrier in orbit.
class BellumGloriosumRulesTest extends BaseTi4Test {

    private static final String DONE_PRODUCING = "FFCC_sol_deleteButtons_solBtBuild_301";

    private AiTestGame test;
    private Player sol;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(4);
        home = test.place("01", AiTestGame.HOME);
        sol.addPlanet("jord");
        test.units(home, "jord", sol, UnitType.Spacedock, 1);
        test.units(home, "space", sol, UnitType.Carrier, 1);
        sol.setFleetCC(5);
        sol.addBreakthrough("solbt");
        sol.setBreakthroughUnlocked("solbt", true);
        test.isActive(sol, "action");
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(sol, Set.of(), NOW, prompts);
    }

    private AiPrompt offer(int limit) {
        return AiTestGame.withContent(
                prompt("offer", PromptSource.PUBLIC, NOW, "solBtBuild_301_" + limit, "deleteButtons"),
                sol.getRepresentation() + " you have the opportunity to produce ground forces and fighters.");
    }

    private static AiPrompt placement() {
        return prompt(
                "place",
                PromptSource.PUBLIC,
                NOW + 1000,
                List.of(
                        "FFCC_sol_place_2gf_jord",
                        "FFCC_sol_place_infantry_jord",
                        "FFCC_sol_place_2ff_301",
                        "FFCC_sol_place_fighter_301",
                        DONE_PRODUCING),
                List.of("2", "1", "2", "1", "Done Producing Units"));
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    @Test
    void producesGroundForcesAndFightersForTheShipItJustBuilt() {
        assertThat(pressed(BellumGloriosumRules.next(context(offer(4))))).isEqualTo("solBtBuild_301_4");

        AiTurnContext placing = context(placement());
        String first = pressed(BellumGloriosumRules.next(placing));
        assertThat(first).startsWith("FFCC_sol_place_");
    }

    @Test
    void neverPlansMoreThanTheShipsCapacity() {
        BuildPlan plan = BellumGloriosumRules.plan(test.game, sol, home, 3);

        assertThat(plan.units()).isPositive().isLessThanOrEqualTo(3);
        BuildPlan roomy = BellumGloriosumRules.plan(test.game, sol, home, 8);
        assertThat(roomy.units()).isLessThanOrEqualTo(8);
    }

    @Test
    void declinesWhenNothingIsAffordable() {
        sol.exhaustPlanet("jord");

        assertThat(pressed(BellumGloriosumRules.next(context(offer(4))))).isEqualTo("deleteButtons");
    }

    @Test
    void asksOncePerOffer() {
        BellumGloriosumRules.next(context(offer(4)));

        assertThat(BellumGloriosumRules.next(context(offer(4)))).containsInstanceOf(AiDecision.Wait.class);
        AiTurnContext muchLater = test.contextFor(sol, Set.of(), NOW + 60_000L, offer(4));
        assertThat(BellumGloriosumRules.next(muchLater)).isEmpty();
    }

    // The build prompt is edited in place, so it keeps the time it was first posted. A main build begun 10 minutes
    // before the offer, this turn, is still open: accepting Bellum Gloriosum then reset its tally, and self-play seed
    // 18
    // looped re-placing the same destroyers and dreadnought until Sol ran out of trade goods.
    @Test
    void waitsForAMainBuildStartedEarlierThisTurn() {
        test.game.setLastActivePlayerChange(new java.util.Date(NOW - 20 * 60_000L));
        AiPrompt mainBuild = prompt(
                "main",
                PromptSource.PUBLIC,
                NOW - 10 * 60_000L,
                List.of("FFCC_sol_place_destroyer_301", "FFCC_sol_deleteButtons_tacticalAction_301"),
                List.of("Destroyer", "Done Producing Units"));

        assertThat(BellumGloriosumRules.next(context(offer(4), mainBuild))).isEmpty();
    }

    @Test
    void waitsForTheNormalBuildToBeDoneBeforeStarting() {
        AiPrompt mainBuild = prompt(
                "main",
                PromptSource.PUBLIC,
                NOW - 1000,
                List.of("FFCC_sol_place_carrier_301", "FFCC_sol_deleteButtons_tacticalAction_301"),
                List.of("Carrier", "Done Producing Units"));

        assertThat(BellumGloriosumRules.next(context(offer(4), mainBuild))).isEmpty();
    }

    @Test
    void ignoresAnOfferMeantForAnotherPlayer() {
        AiPrompt other = AiTestGame.withContent(
                prompt("offer", PromptSource.PUBLIC, NOW, "solBtBuild_301_4", "deleteButtons"),
                test.nekro.getRepresentation() + " you have the opportunity to produce ground forces and fighters.");

        assertThat(BellumGloriosumRules.next(context(other))).isEmpty();
    }

    @Test
    void doesNothingWithoutTheBreakthrough() {
        sol.setBreakthroughUnlocked("solbt", false);

        assertThat(BellumGloriosumRules.next(context(offer(4)))).isEmpty();
    }
}
