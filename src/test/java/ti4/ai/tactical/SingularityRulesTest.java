package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// When Nekro starts a combat the bot posts a "copy a technology" reminder in its private thread right away, but
// Technological Singularity only applies once an enemy unit is destroyed. The AI remembers how many units the
// victim had when the reminder appeared and presses it only after that number drops.
class SingularityRulesTest extends BaseTi4Test {

    private static final String REMINDER = "FFCC_nekro_nekroStealTech_sol";

    private AiTestGame test;
    private Tile battle;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        battle = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(battle, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(battle, "space", test.sol, UnitType.Cruiser, 2);
        test.aiIsActive("action");
    }

    @Test
    void firstSightOfTheReminderOnlyRecordsABaseline() {
        AiPrompt reminder = reminder(NOW);

        assertThat(SingularityRules.next(test.context(reminder))).isEmpty();
        assertThat(test.memory.get(SingularityRules.BASELINE_KEY + "reminder")).contains("2");
        assertThat(SingularityRules.next(test.context(reminder))).isEmpty();
    }

    @Test
    void copiesATechnologyOnceAnEnemyUnitIsDestroyed() {
        AiPrompt reminder = reminder(NOW);
        SingularityRules.next(test.context(reminder));

        battle.removeUnit("space", Units.getUnitKey(UnitType.Cruiser, "blue"), 1);

        assertThat(pressedId(SingularityRules.next(test.context(reminder)).orElseThrow()))
                .isEqualTo(REMINDER);
        assertThat(test.memory.get(SingularityRules.PRESSED_KEY)).contains(NOW + "|" + AiTestGame.HUMAN_ID);
        assertThat(test.memory.has(SingularityRules.BASELINE_KEY + "reminder")).isFalse();
    }

    // A retreat keeps every enemy unit on the board, so nothing was destroyed and the ability does not apply.
    @Test
    void doesNotCopyWhenTheEnemyMerelyRetreats() {
        AiPrompt reminder = reminder(NOW);
        SingularityRules.next(test.context(reminder));

        Tile refuge = test.place("19", "000");
        battle.removeUnit("space", Units.getUnitKey(UnitType.Cruiser, "blue"), 2);
        test.units(refuge, "space", test.sol, UnitType.Cruiser, 2);

        assertThat(SingularityRules.next(test.context(reminder))).isEmpty();
    }

    // Reminders from an earlier turn linger in the thread; a unit lost now has nothing to do with them.
    @Test
    void ignoresRemindersPostedBeforeTheTurnStarted() {
        AiPrompt stale = reminder(NOW - 3_600_000L);
        assertThat(SingularityRules.next(test.context(stale))).isEmpty();

        battle.removeUnit("space", Units.getUnitKey(UnitType.Cruiser, "blue"), 1);

        assertThat(SingularityRules.next(test.context(stale))).isEmpty();
        assertThat(test.memory.has(SingularityRules.BASELINE_KEY + "stale")).isFalse();
    }

    @Test
    void ignoresAReminderOutsideItsPrivateThread() {
        AiPrompt reminder = prompt("public", PromptSource.PUBLIC, NOW, REMINDER);
        SingularityRules.next(test.context(reminder));

        battle.removeUnit("space", Units.getUnitKey(UnitType.Cruiser, "blue"), 1);

        assertThat(SingularityRules.next(test.context(reminder))).isEmpty();
    }

    // After the reminder, the bot offers technologies to copy. It may offer some the victim does not have or Nekro
    // already owns; only the copyable ones count, and the most valuable of those is taken.
    @Test
    void choosesTheBestTechnologyItCanCopyFromTheVictim() {
        test.sol.addTech("gd");
        test.memory.put(SingularityRules.PRESSED_KEY, NOW + "|" + AiTestGame.HUMAN_ID);
        AiPrompt choice = AiTestGame.withContent(
                prompt(
                        "choice",
                        PromptSource.PUBLIC,
                        NOW + 1000,
                        "getTech_fl__noPay",
                        "getTech_dxa__noPay",
                        "getTech_gd",
                        "getTech_amd__noPay",
                        "getTech_gd__noPay"),
                test.nekro.getRepresentation() + ", please choose a technology to copy.");

        assertThat(pressedId(SingularityRules.next(test.contextAt(NOW + 2000, choice))
                        .orElseThrow()))
                .isEqualTo("getTech_gd__noPay");
        assertThat(test.memory.has(SingularityRules.PRESSED_KEY)).isFalse();
    }

    // Mid-combat, a technology that changes the rest of the fight is worth more. With a dreadnought and six fighters in
    // a close fight against five cruisers, Fighter II beats the higher-rated Antimass Deflectors; once the fight is
    // over, the rating decides.
    @Test
    void prefersATechnologyThatHelpsTheFightInProgress() {
        test.units(battle, "space", test.nekro, UnitType.Fighter, 6);
        test.units(battle, "space", test.sol, UnitType.Cruiser, 3);
        test.sol.addTech("ff2");
        test.sol.addTech("amd");
        test.game.setActiveSystem(battle.getPosition());
        AiPrompt choice = AiTestGame.withContent(
                prompt("choice", PromptSource.PUBLIC, NOW + 1000, "getTech_amd__noPay", "getTech_ff2__noPay"),
                test.nekro.getRepresentation() + ", please choose a technology to copy.");

        test.memory.put(SingularityRules.PRESSED_KEY, NOW + "|" + AiTestGame.HUMAN_ID);
        assertThat(pressedId(SingularityRules.next(test.contextAt(NOW + 2000, choice))
                        .orElseThrow()))
                .isEqualTo("getTech_ff2__noPay");

        battle.removeUnit("space", Units.getUnitKey(UnitType.Cruiser, "blue"), 5);
        test.memory.put(SingularityRules.PRESSED_KEY, NOW + "|" + AiTestGame.HUMAN_ID);
        assertThat(pressedId(SingularityRules.next(test.contextAt(NOW + 2000, choice))
                        .orElseThrow()))
                .isEqualTo("getTech_amd__noPay");
    }

    @Test
    void onlyChoosesFromAPromptAddressedToItself() {
        test.memory.put(SingularityRules.PRESSED_KEY, NOW + "|" + AiTestGame.HUMAN_ID);
        AiPrompt someoneElses = AiTestGame.withContent(
                prompt("choice", PromptSource.PUBLIC, NOW + 1000, "getTech_amd__noPay"),
                test.sol.getRepresentation() + ", please choose a technology.");

        assertThat(SingularityRules.next(test.contextAt(NOW + 2000, someoneElses)))
                .isEmpty();
        assertThat(test.memory.has(SingularityRules.PRESSED_KEY)).isTrue();
    }

    // ButtonHelperFactionSpecific.nekroStealTech posts the choice as player.getRepresentation() + ", please choose
    // ...". Player.getPing() looks the user up in JDA, which never knows an AI seat id, so the message names the AI
    // only by its faction and color, never by user id. The JDA mock in BaseTi4Test returns no users either, so the
    // content below is exactly what an AI seat gets in production.
    @Test
    void choosesFromTheRealTechnologyChoiceMessage() {
        test.memory.put(SingularityRules.PRESSED_KEY, NOW + "|" + AiTestGame.HUMAN_ID);
        String content = test.nekro.getRepresentation()
                + ", please choose which of your opponent's technologies you wish to copy.";
        AiPrompt choice = AiTestGame.withContent(
                prompt("choice", PromptSource.PUBLIC, NOW + 1000, "getTech_amd__noPay", "getTech_nm__noPay"), content);

        assertThat(SingularityRules.next(test.contextAt(NOW + 2000, choice))).isPresent();
    }

    @Test
    void ignoresTechnologyButtonsWhenNoneIsCopyable() {
        test.memory.put(SingularityRules.PRESSED_KEY, NOW + "|" + AiTestGame.HUMAN_ID);
        AiPrompt choice = AiTestGame.withContent(
                prompt("choice", PromptSource.PUBLIC, NOW + 1000, "getTech_fl__noPay", "getTech_dxa__noPay"),
                test.nekro.getRepresentation() + ", please choose a technology.");

        assertThat(SingularityRules.next(test.contextAt(NOW + 2000, choice))).isEmpty();
    }

    @Test
    void forgetsThePendingChoiceAfterItsWindowCloses() {
        test.memory.put(SingularityRules.PRESSED_KEY, NOW + "|" + AiTestGame.HUMAN_ID);
        AiPrompt choice = AiTestGame.withContent(
                prompt("choice", PromptSource.PUBLIC, NOW + 1000, "getTech_amd__noPay"),
                test.nekro.getRepresentation() + ", please choose a technology.");

        assertThat(SingularityRules.next(test.contextAt(NOW + 3_600_000L, choice)))
                .isEmpty();
        assertThat(test.memory.has(SingularityRules.PRESSED_KEY)).isFalse();
    }

    private static AiPrompt reminder(long created) {
        return prompt(created == NOW ? "reminder" : "stale", PromptSource.AI_THREAD, created, REMINDER);
    }
}
