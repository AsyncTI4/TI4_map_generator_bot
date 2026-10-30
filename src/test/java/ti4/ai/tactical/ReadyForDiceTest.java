package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
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
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Human players press "Declare Ready To Throw Dice" once they have no more cards or abilities to play. The AI does the
// same at the start of every round, at a moment that never depends on its hand, and rolls first only once the
// opponent has said the same.
class ReadyForDiceTest extends BaseTi4Test {

    private static final String THREAD = "combat-thread";
    private static final long GRACE = CombatRules.ROLL_GRACE_MILLIS;
    private static final long HUMAN_DECISION_LATEST = NOW + 45_001L;
    private static final long AI_DECISION_LATEST = NOW + 10_001L;

    private AiTestGame test;
    private String position;
    private final List<CombatMoment> offered = new ArrayList<>();
    private final CombatCardHook noCard = CombatCardHook.NONE;
    private final CombatCardHook spy = (context, moment) -> {
        offered.add(moment);
        return Optional.empty();
    };

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        arrange();
    }

    private void arrange() {
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile tile = test.place("26", position);
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        test.aiIsActive("action");
        test.game.setActiveSystem(position);
    }

    private AiPrompt message(String id, long created, String content, String... customIds) {
        AiPrompt base = prompt(id, PromptSource.COMBAT_THREAD, created, customIds);
        return new AiPrompt(THREAD, id, PromptSource.COMBAT_THREAD, content, base.buttons(), created);
    }

    private AiPrompt readyButton() {
        return message("ready-button", NOW - 5_000L, "", "announceReadyForDice_black_blue");
    }

    private AiPrompt roll(String holder) {
        return message("roll-" + holder, NOW, "Round started", "combatRoll_" + position + "_" + holder);
    }

    private AiPrompt declaredTo(Player player, long created) {
        return message(
                "declared-" + created,
                created,
                player.getRepresentation()
                        + ", your opponent has declared they have no more abilities to resolve at this step and are ready to proceed to dice rolling when you are.");
    }

    private Optional<AiDecision> next(AiTurnContext context, CombatCardHook hook) {
        return CombatRules.next(context, hook);
    }

    private long decisionMoment(AiTurnContext context, CombatCardHook hook) {
        AiDecision decision = next(context, hook).orElseThrow();
        assertThat(decision).isInstanceOf(AiDecision.Wait.class);
        return ((AiDecision.Wait) decision).untilMillis();
    }

    private void tracker(String faction, int rounds) {
        test.game.setStoredValue("combatRoundTracker" + faction + position + "space", String.valueOf(rounds));
    }

    @Test
    void declaresReadyAtItsSeededMomentAgainstAHuman() {
        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);

        assertThat(moment).isBetween(NOW + 15_000L, HUMAN_DECISION_LATEST);
        assertThat(pressedId(next(test.contextAt(moment, readyButton(), roll("space")), noCard)
                        .orElseThrow()))
                .isEqualTo("announceReadyForDice_black_blue");
    }

    @Test
    void declaresReadyQuicklyAgainstAnotherAi() {
        test = AiTestGame.withSolAi();
        arrange();

        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);

        assertThat(moment).isBetween(NOW + 3_000L, AI_DECISION_LATEST);
    }

    // Holding an action card must not show: the moment is the same, and the card is offered at that moment.
    @Test
    void theDecisionMomentIsTheSameWhateverTheSeatHolds() {
        long withoutCard = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);

        CombatCardHook holdsCard = (context, moment) -> {
            offered.add(moment);
            return Optional.of(new AiDecision.Wait(0, "a card"));
        };
        long withCard = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), holdsCard);

        assertThat(withCard).isEqualTo(withoutCard);
        assertThat(offered).isEmpty();

        next(test.contextAt(withoutCard, readyButton(), roll("space")), holdsCard);

        assertThat(offered).containsExactly(new CombatMoment(position, "space", false, 0));
    }

    @Test
    void doesNotDeclareTwiceInARound() {
        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);
        AiDecision first = next(test.contextAt(moment, readyButton(), roll("space")), noCard)
                .orElseThrow();
        assertThat(pressedId(first)).isEqualTo("announceReadyForDice_black_blue");

        AiDecision second = next(test.contextAt(moment + 1_000L, readyButton(), roll("space")), spy)
                .orElseThrow();

        assertThat(second).isInstanceOf(AiDecision.Wait.class);
        assertThat(offered).isEmpty();
    }

    @Test
    void declaresAgainInTheNextRound() {
        long first = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);
        next(test.contextAt(first, readyButton(), roll("space")), noCard);
        tracker("nekro", 1);
        tracker("sol", 1);
        long later = NOW + 600_000L;
        AiPrompt roundTwo = message("roll-two", later, "Round 1 ended", "combatRoll_" + position + "_space");

        long moment = decisionMoment(test.contextAt(later, readyButton(), roundTwo), noCard);

        assertThat(pressedId(next(test.contextAt(moment, readyButton(), roundTwo), noCard)
                        .orElseThrow()))
                .isEqualTo("announceReadyForDice_black_blue");
    }

    @Test
    void waitsForAHumanOpponentToBeReadyThenRollsAtOnce() {
        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);
        next(test.contextAt(moment, readyButton(), roll("space")), noCard);

        AiDecision waiting = next(test.contextAt(moment + 1_000L, readyButton(), roll("space")), noCard)
                .orElseThrow();
        assertThat(waiting).isInstanceOf(AiDecision.Wait.class);

        AiPrompt humanReady = declaredTo(test.nekro, moment + 2_000L);
        assertThat(pressedId(next(test.contextAt(moment + 3_000L, readyButton(), roll("space"), humanReady), noCard)
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void aReadyMessageFromBeforeTheRoundDoesNotCount() {
        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);
        next(test.contextAt(moment, readyButton(), roll("space")), noCard);
        AiPrompt stale = declaredTo(test.nekro, NOW - 60_000L);

        assertThat(next(test.contextAt(moment + 3_000L, readyButton(), roll("space"), stale), noCard)
                        .orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void aReadyMessageNamingTheOpponentIsNotTheOpponentBeingReady() {
        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);
        next(test.contextAt(moment, readyButton(), roll("space")), noCard);
        AiPrompt ownDeclaration = declaredTo(test.sol, moment + 1_000L);

        assertThat(next(test.contextAt(moment + 3_000L, readyButton(), roll("space"), ownDeclaration), noCard)
                        .orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void aHumanWhoNeverPressesReadyIsOnlyWaitedOnForTheUsualGrace() {
        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);
        next(test.contextAt(moment, readyButton(), roll("space")), noCard);

        assertThat(next(test.contextAt(NOW + GRACE - 1_000L, readyButton(), roll("space")), noCard)
                        .orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);
        assertThat(pressedId(next(test.contextAt(NOW + GRACE, readyButton(), roll("space")), noCard)
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void rollsAtOnceWhenTheOpponentHasRolledWithoutDeclaringReady() {
        tracker("sol", 1);

        assertThat(pressedId(next(test.contextAt(NOW, readyButton(), roll("space")), noCard)
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void behavesAsBeforeWhenThereIsNoReadyButton() {
        assertThat(next(test.contextAt(NOW, roll("space")), noCard).orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);
        assertThat(pressedId(
                        next(test.contextAt(NOW + GRACE, roll("space")), noCard).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void anAiAttackerWaitsForTheAiDefenderAndGivesUpAfterThreeMinutes() {
        test = AiTestGame.withSolAi();
        arrange();
        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);
        next(test.contextAt(moment, readyButton(), roll("space")), noCard);

        AiDecision waiting = next(test.contextAt(moment + 20_000L, readyButton(), roll("space")), noCard)
                .orElseThrow();
        assertThat(waiting).isInstanceOf(AiDecision.Wait.class);

        long giveUp = moment + ReadyForDice.AI_CEILING_MILLIS;
        assertThat(next(test.contextAt(giveUp - 1_000L, readyButton(), roll("space")), noCard)
                        .orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);
        assertThat(pressedId(next(test.contextAt(giveUp, readyButton(), roll("space")), noCard)
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void anAiAttackerRollsOnceTheAiDefenderHasDeclared() {
        test = AiTestGame.withSolAi();
        arrange();
        long moment = decisionMoment(test.contextAt(NOW, readyButton(), roll("space")), noCard);
        next(test.contextAt(moment, readyButton(), roll("space")), noCard);
        AiPrompt defenderReady = declaredTo(test.nekro, moment + 1_000L);

        assertThat(pressedId(next(test.contextAt(moment + 2_000L, readyButton(), roll("space"), defenderReady), noCard)
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
    }

    // The defender used to wait until the attacker had rolled and so never saw a round-start window.
    @Test
    void anAiDefenderDecidesBeforeTheAttackerRollsButDoesNotRollFirst() {
        test = AiTestGame.withSolAi();
        arrange();
        AiTurnContext early = test.contextFor(test.sol, Set.of(), NOW, readyButton(), roll("space"));
        AiDecision wait = next(early, noCard).orElseThrow();
        assertThat(wait).isInstanceOf(AiDecision.Wait.class);
        long moment = ((AiDecision.Wait) wait).untilMillis();

        AiTurnContext atMoment = test.contextFor(test.sol, Set.of(), moment, readyButton(), roll("space"));
        assertThat(pressedId(next(atMoment, spy).orElseThrow())).isEqualTo("announceReadyForDice_black_blue");
        assertThat(offered).containsExactly(new CombatMoment(position, "space", false, 0));

        AiTurnContext afterwards = test.contextFor(test.sol, Set.of(), moment + 5_000L, readyButton(), roll("space"));
        assertThat(next(afterwards, noCard)).isEmpty();

        tracker("nekro", 1);
        assertThat(pressedId(next(afterwards, noCard).orElseThrow())).isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void declaresReadyForAGroundCombatThatOnlyHasAnAutomationOffer() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 2);
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
        AiPrompt offer = message(
                "offer",
                NOW,
                "You may automate the entire combat",
                "automateGroundCombat_nekro_sol_lodor_unconfirmed",
                "combatRoll_" + position + "_lodor");

        long moment = decisionMoment(test.contextAt(NOW, readyButton(), offer), spy);
        assertThat(pressedId(
                        next(test.contextAt(moment, readyButton(), offer), spy).orElseThrow()))
                .isEqualTo("announceReadyForDice_black_blue");
        assertThat(offered).containsExactly(new CombatMoment(position, "lodor", true, 0));
    }

    // Confirming the automation is the round-0 decision: the seat first has its window, and confirms after the same
    // pause whether or not it played anything.
    @Test
    void confirmsTheAutomationAfterItsRoundZeroDecision() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 2);
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
        AiPrompt asked = message(
                "asked",
                NOW,
                "Your opponent has voted to automate the entire combat. Press to confirm:",
                "FFCC_nekro_automateGroundCombat_nekro_sol_lodor_confirmed",
                "combatRoll_" + position + "_lodor");

        long moment = decisionMoment(test.contextAt(NOW, readyButton(), asked), spy);
        assertThat(moment).isBetween(NOW + 15_000L, HUMAN_DECISION_LATEST);
        assertThat(offered).isEmpty();

        assertThat(pressedId(
                        next(test.contextAt(moment, readyButton(), asked), spy).orElseThrow()))
                .isEqualTo("FFCC_nekro_automateGroundCombat_nekro_sol_lodor_confirmed");
        assertThat(offered).containsExactly(new CombatMoment(position, "lodor", true, 0));
    }

    @Test
    void thePauseBeforeConfirmingIsTheSameWhateverTheSeatHolds() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 2);
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
        AiPrompt asked = message(
                "asked",
                NOW,
                "Press to confirm:",
                "FFCC_nekro_automateGroundCombat_nekro_sol_lodor_confirmed",
                "combatRoll_" + position + "_lodor");
        CombatCardHook holdsCard = (context, moment) -> Optional.of(new AiDecision.Wait(0, "a card"));

        assertThat(decisionMoment(test.contextAt(NOW, asked), holdsCard))
                .isEqualTo(decisionMoment(test.contextAt(NOW, asked), noCard));
    }

    @Test
    void confirmsAtOnceWhenTheCombatIsNotAtTheStartOfARound() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 2);
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
        test.game.setStoredValue("combatRoundTrackersol" + position + "lodor", "1");
        AiPrompt asked = message(
                "asked",
                NOW,
                "Press to confirm:",
                "FFCC_nekro_automateGroundCombat_nekro_sol_lodor_confirmed",
                "combatRoll_" + position + "_lodor");

        assertThat(pressedId(next(test.contextAt(NOW, asked), noCard).orElseThrow()))
                .isEqualTo("FFCC_nekro_automateGroundCombat_nekro_sol_lodor_confirmed");
    }
}
