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
import ti4.ai.sardakk.NorrSupremacyRules.Choice;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// N'orr Supremacy: after the Sardakk win a combat they either gain a command token or research a unit upgrade.
class NorrSupremacyRulesTest extends BaseTi4Test {

    private static final String REMINDER = "FFCC_sardakk_sardakkbtRes";
    private static final String GAIN = "gain_CC_deleteThisMessage";
    private static final String RESEARCH = "acquireAUnitUpgrade_deleteThisMessage";

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
        test.isActive(sardakk, "action");
        test.game.setActiveSystem(position);
        sardakk.addBreakthrough("sardakkbt");
        sardakk.getBreakthroughUnlocked().put("sardakkbt", true);
        sardakk.setTacticalCC(3);
        sardakk.setFleetCC(3);
        sardakk.setStrategicCC(2);
    }

    private void wonSpaceCombat() {
        test.units(tile, "space", sardakk, UnitType.Cruiser, 2);
        test.game.setStoredValue("combatRoundTracker" + sardakk.getFaction() + position + "space", "2");
        test.game.setStoredValue("combatRoundTracker" + test.sol.getFaction() + position + "space", "2");
    }

    private AiPrompt combatButtons() {
        return prompt("combat", PromptSource.COMBAT_THREAD, NOW, REMINDER, "FFCC_sardakk_exhaustAgent_other");
    }

    private AiPrompt choices() {
        return prompt("choices", PromptSource.COMBAT_THREAD, NOW, GAIN, RESEARCH, "deleteButtons");
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(sardakk, Set.of(), NOW, prompts);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private void unitUpgradeWithinReach() {
        sardakk.addTech("ps");
        sardakk.addTech("da");
    }

    @Test
    void resolvesTheBreakthroughOnceTheCombatIsWon() {
        wonSpaceCombat();

        assertThat(pressed(NorrSupremacyRules.next(context(combatButtons())))).isEqualTo(REMINDER);
    }

    @Test
    void doesNothingWithoutTheBreakthroughUnlocked() {
        wonSpaceCombat();
        sardakk.getBreakthroughUnlocked().put("sardakkbt", false);

        assertThat(NorrSupremacyRules.next(context(combatButtons()))).isEmpty();
    }

    @Test
    void waitsWhileTheOpponentStillHasShipsInTheSystem() {
        wonSpaceCombat();
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);

        assertThat(NorrSupremacyRules.next(context(combatButtons()))).isEmpty();
    }

    @Test
    void doesNothingAfterALostCombat() {
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        test.game.setStoredValue("combatRoundTracker" + sardakk.getFaction() + position + "space", "1");

        assertThat(NorrSupremacyRules.next(context(combatButtons()))).isEmpty();
    }

    @Test
    void doesNothingWhenNoDiceWereRolled() {
        test.units(tile, "space", sardakk, UnitType.Cruiser, 2);

        assertThat(NorrSupremacyRules.next(context(combatButtons()))).isEmpty();
    }

    @Test
    void resolvesAGroundCombatWonOnAPlanet() {
        sardakk.addPlanet("lodor");
        test.units(tile, "lodor", sardakk, UnitType.Infantry, 1);
        test.game.setStoredValue("combatRoundTracker" + sardakk.getFaction() + position + "lodor", "1");

        assertThat(pressed(NorrSupremacyRules.next(context(combatButtons())))).isEqualTo(REMINDER);
    }

    @Test
    void resolvesEachWinOnlyOncePerTurn() {
        wonSpaceCombat();
        unitUpgradeWithinReach();
        AiPrompt reminder = combatButtons();
        NorrSupremacyRules.next(context(reminder));
        NorrSupremacyRules.next(context(choices()));

        assertThat(NorrSupremacyRules.next(context(reminder))).isEmpty();
    }

    @Test
    void researchesTheUnitUpgradeWhenOneIsWithinReach() {
        wonSpaceCombat();
        unitUpgradeWithinReach();
        NorrSupremacyRules.next(context(combatButtons()));

        assertThat(pressed(NorrSupremacyRules.next(context(choices())))).isEqualTo(RESEARCH);
    }

    @Test
    void picksTheBestUnitUpgradeThroughTheResearchFlow() {
        wonSpaceCombat();
        unitUpgradeWithinReach();
        NorrSupremacyRules.next(context(combatButtons()));
        NorrSupremacyRules.next(context(choices()));
        AiPrompt list =
                prompt("list", PromptSource.AI_THREAD, NOW, "FFCC_sardakk_getTech_dd2", "FFCC_sardakk_getTech_cr2");

        assertThat(pressed(StrategyCardRules.chooseTechnology(context(list)))).isEqualTo("FFCC_sardakk_getTech_dd2");
    }

    @Test
    void takesACommandTokenWhenNoUnitUpgradeIsWithinReach() {
        wonSpaceCombat();
        NorrSupremacyRules.next(context(combatButtons()));

        assertThat(pressed(NorrSupremacyRules.next(context(choices())))).isEqualTo(GAIN);
    }

    @Test
    void takesNeitherWhenThereIsNothingToResearchAndNoRoomForTokens() {
        wonSpaceCombat();
        sardakk.setTacticalCC(8);
        sardakk.setFleetCC(8);
        sardakk.setStrategicCC(8);
        NorrSupremacyRules.next(context(combatButtons()));

        assertThat(pressed(NorrSupremacyRules.next(context(choices())))).isEqualTo("deleteButtons");
    }

    @Test
    void choiceWeighsTheUpgradeAgainstTheToken() {
        unitUpgradeWithinReach();

        assertThat(NorrSupremacyRules.bestUpgradeWorth(test.game, sardakk)).isGreaterThan(0);
        assertThat(NorrSupremacyRules.tokenWorth(test.game, sardakk)).isGreaterThan(0);
        assertThat(NorrSupremacyRules.choose(test.game, sardakk, true, true)).isEqualTo(Choice.RESEARCH);
    }

    @Test
    void takesTheTokenWhenOnlyTheTokenIsOffered() {
        unitUpgradeWithinReach();

        assertThat(NorrSupremacyRules.choose(test.game, sardakk, false, true)).isEqualTo(Choice.TOKEN);
    }

    @Test
    void takesTheUpgradeWhenOnlyTheUpgradeIsOffered() {
        unitUpgradeWithinReach();

        assertThat(NorrSupremacyRules.choose(test.game, sardakk, true, false)).isEqualTo(Choice.RESEARCH);
    }

    @Test
    void tokensAreWorthMoreWhenTheSeatIsOutOfThem() {
        double stocked = NorrSupremacyRules.tokenWorth(test.game, sardakk);

        sardakk.setTacticalCC(0);
        sardakk.setStrategicCC(0);
        double starved = NorrSupremacyRules.tokenWorth(test.game, sardakk);

        assertThat(starved).isGreaterThanOrEqualTo(stocked);
    }

    @Test
    void gainsTheTokenThroughTheCommandTokenButtons() {
        wonSpaceCombat();
        NorrSupremacyRules.next(context(combatButtons()));
        NorrSupremacyRules.next(context(choices()));
        test.game.setStoredValue("originalCCsFor" + sardakk.getFaction(), sardakk.getCCRepresentation());
        AiPrompt gain = AiTestGame.withContent(
                prompt(
                        "gain",
                        PromptSource.COMBAT_THREAD,
                        NOW,
                        java.util.List.of(
                                "FFCC_sardakk_increase_tactic_cc",
                                "FFCC_sardakk_increase_fleet_cc",
                                "FFCC_sardakk_increase_strategic_cc",
                                "FFCC_sardakk_deleteButtons"),
                        java.util.List.of(
                                "Gain 1 Tactic", "Gain 1 Fleet", "Gain 1 Strategy", "Done Gaining Command Tokens")),
                sardakk.getRepresentationUnfogged()
                        + ", your current command tokens are "
                        + sardakk.getCCRepresentation()
                        + ". Use buttons to gain command tokens.");

        String grow = pressed(NorrSupremacyRules.next(context(gain)));
        assertThat(grow).startsWith("FFCC_sardakk_increase_");

        sardakk.setTacticalCC(sardakk.getTacticalCC() + 1);

        assertThat(pressed(NorrSupremacyRules.next(context(gain)))).isEqualTo("FFCC_sardakk_deleteButtons");
    }

    @Test
    void waitsForTheChoiceInsteadOfEndingTheTurn() {
        wonSpaceCombat();
        NorrSupremacyRules.next(context(combatButtons()));

        assertThat(NorrSupremacyRules.next(context())).containsInstanceOf(AiDecision.Wait.class);
    }
}
