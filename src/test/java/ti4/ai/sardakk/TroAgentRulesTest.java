package ti4.ai.sardakk;

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
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// T'ro An (the N'orr agent) refreshes every round, so an unused agent is a waste; the bot spends it at the end of its
// own tactical action when that protects something, and otherwise in the last windows of the round.
class TroAgentRulesTest extends BaseTi4Test {

    private static final String LODOR = "lodor";

    private AiTestGame test;
    private Player sardakk;
    private Tile tile;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        sardakk = test.addSeat(AiTestGame.SECOND_AI_ID, "sardakk", "orange");
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        sardakk.addPlanet(LODOR);
        test.units(tile, LODOR, sardakk, UnitType.Infantry, 2);
        test.isActive(sardakk, "action");
        test.game.setActiveSystem(position);
        sardakk.addLeader("sardakkagent");
        sardakk.setTacticalCC(3);
        sardakk.setStrategicCC(2);
    }

    private AiPrompt offer(String... planets) {
        String[] ids = new String[planets.length + 1];
        for (int i = 0; i < planets.length; i++) ids[i] = "FFCC_sardakk_exhaustAgent_sardakkagent_" + planets[i];
        ids[planets.length] = "deleteButtons";
        return prompt("troOffer", PromptSource.PUBLIC, NOW, ids);
    }

    private Optional<AiDecision> afterTacticalAction(AiPrompt... prompts) {
        AiTurnContext context = test.contextFor(sardakk, Set.of(), NOW, prompts);
        return TroAgentRules.afterTacticalAction(context);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private String lodorOffer() {
        return position + "_" + LODOR;
    }

    // A planet taken this round is held by a thin garrison, which is the moment to reinforce it.
    @Test
    void reinforcesAPlanetItJustTook() {
        test.game.setStoredValue("planetsTakenThisRound", LODOR);

        assertThat(pressed(afterTacticalAction(offer(lodorOffer()))))
                .isEqualTo("FFCC_sardakk_exhaustAgent_sardakkagent_" + lodorOffer());
    }

    @Test
    void reinforcesAPlanetAnEnemyFleetCanReach() {
        String adjacent = MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
        Tile sol = test.place("19", adjacent);
        test.units(sol, "space", test.sol, UnitType.Carrier, 2);

        assertThat(pressed(afterTacticalAction(offer(lodorOffer()))))
                .isEqualTo("FFCC_sardakk_exhaustAgent_sardakkagent_" + lodorOffer());
    }

    // Nothing threatens the planet and tokens remain for later actions, so a better window may still come.
    @Test
    void keepsTheAgentForABetterWindowWhileTokensRemain() {
        assertThat(afterTacticalAction(offer(lodorOffer()))).isEmpty();
    }

    // With one token left this is among the last windows of the round, and the agent refreshes next round anyway.
    @Test
    void spendsItOnAnyPlanetInTheLastWindowsOfTheRound() {
        sardakk.setTacticalCC(1);

        assertThat(pressed(afterTacticalAction(offer(lodorOffer()))))
                .isEqualTo("FFCC_sardakk_exhaustAgent_sardakkagent_" + lodorOffer());
    }

    @Test
    void spendsItOnceEveryoneElseHasPassed() {
        test.sol.setPassed(true);
        test.nekro.setPassed(true);

        assertThat(pressed(afterTacticalAction(offer(lodorOffer()))))
                .isEqualTo("FFCC_sardakk_exhaustAgent_sardakkagent_" + lodorOffer());
    }

    @Test
    void doesNothingWithoutTheAgentReady() {
        sardakk.setTacticalCC(0);
        sardakk.getLeader("sardakkagent").orElseThrow().setExhausted(true);

        assertThat(afterTacticalAction(offer(lodorOffer()))).isEmpty();
    }

    @Test
    void doesNothingWithoutAnyInfantryLeftInReinforcements() {
        sardakk.setTacticalCC(0);
        sardakk.setUnitCap("gf", 2);

        assertThat(afterTacticalAction(offer(lodorOffer()))).isEmpty();
    }

    @Test
    void neverReinforcesAPlanetItDoesNotControl() {
        sardakk.setTacticalCC(0);
        sardakk.removePlanet(LODOR);
        test.sol.addPlanet(LODOR);

        assertThat(afterTacticalAction(offer(lodorOffer()))).isEmpty();
    }

    // Mecatol outweighs a quiet frontier planet, and holding it for the Imperial strategy card is worth defending.
    @Test
    void prefersMecatolOverAnOrdinaryPlanet() {
        Tile mecatol = test.place("18", "000");
        sardakk.addPlanet("mr");
        test.units(mecatol, "mr", sardakk, UnitType.Infantry, 1);
        test.game.setStoredValue("planetsTakenThisRound", "mr");
        String lodorOffer = lodorOffer();

        assertThat(pressed(afterTacticalAction(offer(lodorOffer, "000_mr"))))
                .isEqualTo("FFCC_sardakk_exhaustAgent_sardakkagent_000_mr");
    }

    // Expand Borders needs six planets outside the home system; with exactly six, each one is a point at stake.
    @Test
    void reinforcesAPlanetThatKeepsAnObjectiveScoreable() {
        test.place("13", "304");
        sardakk.addPlanet("quinarra");
        sardakk.addPlanet("trenlak");
        String[][] outposts = {
            {"25", "202", "quann"}, {"20", "203", "vefutii"}, {"27", "204", "newalbion"}, {"23", "205", "saudor"}
        };
        for (String[] outpost : outposts) {
            test.place(outpost[0], outpost[1]);
            sardakk.addPlanet(outpost[2]);
        }
        sardakk.addPlanet("starpoint");
        assertThat(afterTacticalAction(offer(lodorOffer()))).isEmpty();

        test.game.getRevealedPublicObjectives().put("expand_borders", 1);

        assertThat(pressed(afterTacticalAction(offer(lodorOffer()))))
                .isEqualTo("FFCC_sardakk_exhaustAgent_sardakkagent_" + lodorOffer());
    }

    // Another player's tactical action, or the agent's "someone else" menu in the cards info thread, is never used:
    // the bot declines unless the agent is its own to spend at the end of its own action.
    @Test
    void neverUsesTheAgentOnSomeoneElse() {
        sardakk.setTacticalCC(0);
        AiPrompt menu = prompt("menu", PromptSource.AI_THREAD, NOW, "getAgentSelection_sardakkagent");
        AiPrompt target = prompt(
                "target", PromptSource.PUBLIC, NOW, "FFCC_sardakk_exhaustAgent_sardakkagent_" + test.sol.getFaction());

        assertThat(afterTacticalAction(menu, target)).isEmpty();
    }

    @Test
    void doesNothingOnAnotherPlayersTurn() {
        sardakk.setTacticalCC(0);
        test.isActive(test.sol, "action");

        assertThat(afterTacticalAction(offer(lodorOffer()))).isEmpty();
    }

    @Test
    void readsOffersFromHiddenPromptsToo() {
        sardakk.setTacticalCC(0);
        AiPrompt hidden = prompt(
                "hiddenOffer",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_sardakk_exhaustAgent_sardakkagent_" + lodorOffer(),
                "deleteButtons");

        assertThat(pressed(afterTacticalAction(hidden)))
                .isEqualTo("FFCC_sardakk_exhaustAgent_sardakkagent_" + lodorOffer());
    }

    @Test
    void doesNotPressTheSameOfferTwice() {
        sardakk.setTacticalCC(0);
        AiPrompt prompt = offer(lodorOffer());
        AiTurnContext context = test.contextFor(
                sardakk, Set.of(AiTurnContext.pressKey(prompt, prompt.buttons().getFirst())), NOW, prompt);

        assertThat(TroAgentRules.afterTacticalAction(context)).isEmpty();
    }
}
