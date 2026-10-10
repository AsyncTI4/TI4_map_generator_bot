package ti4.ai.nekro;

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
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class NekroLeaderRulesTest extends BaseTi4Test {

    private static final List<String> LOSE_TOKEN_BUTTONS = List.of(
            "FFCC_nekro_decrease_tactic_cc",
            "FFCC_nekro_decrease_fleet_cc",
            "FFCC_nekro_decrease_strategy_cc",
            "FFCC_nekro_deleteButtons",
            "FFCC_nekro_resetCCs");
    private static final List<String> LOSE_TOKEN_LABELS = List.of(
            "Lose 1 Tactic Token",
            "Lose 1 Fleet Token",
            "Lose 1 Strategy Token",
            "Done Losing Command Tokens",
            "Reset Tokens");

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        home = test.nekroHome();
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private AiPrompt turnPrompt() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_componentAction",
                "FFCC_nekro_exhaustAgent_nekroagent",
                "FFCC_nekro_passingAbilities");
    }

    private AiPrompt picker(long created) {
        AiPrompt picker = prompt(
                "picker", PromptSource.PUBLIC, created, "nekroAgentRes_nekro", "nekroAgentRes_sol", "ultimateUndo_1");
        return AiTestGame.withContent(
                picker,
                test.nekro.getRepresentationUnfogged()
                        + ", please choose the faction on which you wish to use Nekro Malleon, the Nekro agent.");
    }

    private AiPrompt discardPrompt() {
        AiPrompt discard =
                prompt("discard", PromptSource.AI_THREAD, NOW, "ac_discard_from_hand_1", "ac_discard_from_hand_2");
        return AiTestGame.withContent(discard, test.nekro.getRepresentationUnfogged() + " use buttons to discard");
    }

    private AiPrompt loseTokens(String content) {
        return AiTestGame.withContent(
                prompt("tokens", PromptSource.PUBLIC, NOW, LOSE_TOKEN_BUTTONS, LOSE_TOKEN_LABELS), content);
    }

    private void holdActionCards(int count) {
        for (int card = 1; card <= count; card++) test.nekro.setActionCard("sabo" + card, card);
    }

    // The agent turns a spare action card into 2 trade goods. The AI never plays action cards, so it uses the agent
    // at the start of its turn and names itself as the player who pays.
    @Test
    void usesTheAgentOnItselfWithASpareActionCard() {
        holdActionCards(2);

        assertThat(pressed(NekroLeaderRules.startAgent(test.context(turnPrompt()))))
                .isEqualTo("FFCC_nekro_exhaustAgent_nekroagent");
        assertThat(pressed(NekroLeaderRules.chooseAgentTarget(test.context(picker(NOW)))))
                .isEqualTo("nekroAgentRes_nekro");
    }

    // Form a Spy Network needs 5 action cards in hand, so the agent never eats into them.
    @Test
    void keepsFiveActionCardsWhileItHoldsFormASpyNetwork() {
        holdActionCards(5);
        test.nekro.setSecret("fsn");

        assertThat(NekroLeaderRules.startAgent(test.context(turnPrompt()))).isEmpty();
    }

    @Test
    void neverUsesTheAgentWithoutAnActionCardToSpare() {
        assertThat(NekroLeaderRules.startAgent(test.context(turnPrompt()))).isEmpty();
    }

    // The bot hands over the 2 trade goods before anything is paid, so the target pays on its own: it discards an
    // action card and then closes the token prompt without losing a token.
    @Test
    void paysForTheAgentWithAnActionCard() {
        holdActionCards(2);
        AiPrompt tokens = loseTokens(test.nekro.getRepresentationUnfogged()
                + "! Your current command tokens are 3/3/2. Use buttons to lose tokens.");

        assertThat(pressed(NekroLeaderRules.payAgentCost(test.context(discardPrompt(), tokens))))
                .isEqualTo("ac_discard_from_hand_1");

        test.nekro.getActionCards().remove("sabo1");
        assertThat(pressed(NekroLeaderRules.payAgentCost(test.context(tokens)))).isEqualTo("FFCC_nekro_deleteButtons");
    }

    // The discard prompt goes to its thread and the token prompt to the main channel, so the token prompt can arrive
    // first. The seat waits for the discard prompt rather than closing the token prompt unpaid.
    @Test
    void waitsForTheDiscardPromptInsteadOfClosingTheCostUnpaid() {
        holdActionCards(2);
        AiPrompt tokens = loseTokens(test.nekro.getRepresentationUnfogged()
                + "! Your current command tokens are 3/3/2. Use buttons to lose tokens.");

        assertThat(NekroLeaderRules.payAgentCost(test.context(tokens))).containsInstanceOf(AiDecision.Wait.class);
        assertThat(pressed(NekroLeaderRules.payAgentCost(test.context(discardPrompt(), tokens))))
                .isEqualTo("ac_discard_from_hand_1");
    }

    // Without an action card it gives up a command token instead. The bot rewrites the token prompt after the first
    // token, so the AI recognises the prompt by its message afterwards.
    @Test
    void paysForTheAgentWithATokenWhenItHasNoActionCards() {
        test.nekro.setTacticalCC(4);
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        AiPrompt tokens = loseTokens(test.nekro.getRepresentationUnfogged()
                + "! Your current command tokens are 4/3/2. Use buttons to lose tokens.");

        assertThat(pressed(NekroLeaderRules.payAgentCost(test.context(tokens))))
                .isEqualTo("FFCC_nekro_decrease_tactic_cc");

        test.nekro.setTacticalCC(3);
        AiPrompt rewritten = loseTokens(
                test.nekro.getRepresentation() + " command tokens have gone from 4/3/2 -> 3/3/2. Net gain of: -1.");
        assertThat(pressed(NekroLeaderRules.payAgentCost(
                        test.context(Set.of("tokens|FFCC_nekro_decrease_tactic_cc"), rewritten))))
                .isEqualTo("FFCC_nekro_deleteButtons");
    }

    // Any AI seat pays when another player's Nekro agent names it, not only the Nekro AI.
    @Test
    void anyTargetedSeatPays() {
        AiTestGame solTable = AiTestGame.withSolAi();
        solTable.sol.setTacticalCC(5);
        solTable.game.setStoredValue("originalCCsForsol", solTable.sol.getCCRepresentation());
        AiPrompt tokens = AiTestGame.withContent(
                prompt(
                        "tokens",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of(
                                "FFCC_sol_decrease_tactic_cc",
                                "FFCC_sol_decrease_strategy_cc",
                                "FFCC_sol_deleteButtons"),
                        List.of("Lose 1 Tactic Token", "Lose 1 Strategy Token", "Done Losing Command Tokens")),
                solTable.sol.getRepresentationUnfogged()
                        + "! Your current command tokens are 5/3/2. Use buttons to lose tokens.");

        assertThat(pressed(NekroLeaderRules.payAgentCost(solTable.contextFor(solTable.sol, Set.of(), NOW, tokens))))
                .isEqualTo("FFCC_sol_decrease_tactic_cc");
    }

    // A seat that does not own the agent never touches another player's agent buttons.
    @Test
    void otherSeatsNeverUseTheNekroAgent() {
        AiTestGame solTable = AiTestGame.withSolAi();
        solTable.isActive(solTable.sol, "action");
        for (int card = 1; card <= 3; card++) solTable.sol.setActionCard("sabo" + card);
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_sol_exhaustAgent_nekroagent");

        assertThat(NekroLeaderRules.startAgent(solTable.contextFor(solTable.sol, Set.of(), NOW, turn)))
                .isEmpty();
    }

    // The hero is a component action that needs no tactic token: on a planet with a technology specialty in a system
    // with its units, it destroys the other player's units, takes trade goods and a technology of that colour.
    @Test
    void playsTheHeroThroughTheComponentActionMenu() {
        Tile wellon = test.place("19", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(wellon, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(wellon, "wellon", test.sol, UnitType.Infantry, 2);
        test.nekro.getLeader("nekrohero").orElseThrow().setLocked(false);

        assertThat(pressed(NekroLeaderRules.startHero(test.context(turnPrompt()))))
                .isEqualTo("FFCC_nekro_componentAction");

        AiPrompt waiting = turnPrompt();
        assertThat(NekroLeaderRules.continueHero(test.context(waiting))).containsInstanceOf(AiDecision.Wait.class);

        AiPrompt menu = prompt(
                "menu",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_nekro_componentActionRes_leader_nekrohero",
                "FFCC_nekro_componentActionRes_ability_x",
                "deleteButtons");
        assertThat(pressed(NekroLeaderRules.continueHero(test.context(menu))))
                .isEqualTo("FFCC_nekro_componentActionRes_leader_nekrohero");

        AiPrompt planets = prompt("planets", PromptSource.AI_THREAD, NOW, "nekroHeroStep2_wellon");
        assertThat(pressed(NekroLeaderRules.continueHero(test.context(planets))))
                .isEqualTo("nekroHeroStep2_wellon");

        AiPrompt techs = prompt(
                "techs", PromptSource.AI_THREAD, NOW, "FFCC_nekro_getTech_st__noPay", "FFCC_nekro_getTech_det__noPay");
        assertThat(pressed(NekroLeaderRules.continueHero(test.context(techs))))
                .isEqualTo("FFCC_nekro_getTech_st__noPay");

        assertThat(NekroLeaderRules.continueHero(test.context(techs))).isEmpty();
        assertThat(NekroLeaderRules.startHero(test.context(turnPrompt()))).isEmpty();
    }

    // The hero is purged as soon as it is played, so it is never started without a planet to use it on.
    @Test
    void keepsTheHeroWhenNoPlanetQualifies() {
        test.nekro.getLeader("nekrohero").orElseThrow().setLocked(false);

        assertThat(NekroLeaderRules.startHero(test.context(turnPrompt()))).isEmpty();
    }

    @Test
    void neverPlaysALockedHero() {
        Tile wellon = test.place("19", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(wellon, "space", test.nekro, UnitType.Destroyer, 1);

        assertThat(NekroLeaderRules.startHero(test.context(turnPrompt()))).isEmpty();
    }
}
