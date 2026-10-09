package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class ActionCardRulesTest extends BaseTi4Test {

    // The bot needs the window to stay closed for a moment before the AI resolves, in case a Sabotage is landing.
    private static final long SETTLED = NOW + 11_000L;

    private AiTestGame test;
    private Tile home;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        home = test.nekroHome();
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        messages = Mockito.mockStatic(GameMessageManager.class);
        reacted("nekro");
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    // Who has answered the Sabotage window so far; the window stays open until every other player has.
    private void reacted(String... factions) {
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1), GameMessageType.ACTION_CARD, new LinkedHashSet<>(List.of(factions)), 0L)));
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private AiPrompt hand(String... ids) {
        return prompt("hand", PromptSource.AI_THREAD, NOW, ids);
    }

    private AiPrompt announcement(String title) {
        return prompt(
                "announce-" + title,
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_" + title,
                "no_sabotage",
                "sabotage_ac_" + title + "_nekro");
    }

    private void cardLeavesHand(String alias, String title) {
        test.nekro.getActionCards().remove(alias);
        test.game.getGameStats().recordAcPlay(title, test.nekro);
    }

    private String turnKey() {
        return "acPlay|turn:" + test.context().turnKey();
    }

    // Mining Initiative turns the best planet's resources into trade goods; with nothing better to do, the AI plays it
    // instead of passing.
    @Test
    void playsMiningInitiativeBeforePassing() {
        test.nekro.setActionCard("mining_initiative", 412);

        assertThat(pressed(ActionCardRules.playBeforePassing(test.context(hand("ac_play_from_hand_412")))))
                .isEqualTo("ac_play_from_hand_412");
    }

    // The bot posts the resolve button straight away, but resolving before every other player has passed on
    // Sabotage would be cheating; the AI holds its turn (so it doesn't press End Turn) until the window closes.
    @Test
    void waitsForTheSabotageWindowBeforeResolving() {
        test.nekro.setActionCard("mining_initiative", 412);
        ActionCardRules.playBeforePassing(test.context(hand("ac_play_from_hand_412")));
        cardLeavesHand("mining_initiative", "Mining Initiative");
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_miningInitiative");
        AiPrompt endTurn = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_endOfTurnAbilities");

        assertThat(ActionCardRules.continuePlay(test.context(announcement("Mining Initiative"), resolve, endTurn)))
                .isEmpty();
        assertThat(ActionCardRules.holdOwnTurn(test.context(endTurn))).containsInstanceOf(AiDecision.Wait.class);

        reacted("nekro", "sol");
        assertThat(ActionCardRules.continuePlay(test.context(announcement("Mining Initiative"), resolve)))
                .isEmpty();
        assertThat(pressed(ActionCardRules.continuePlay(
                        test.contextAt(SETTLED, announcement("Mining Initiative"), resolve))))
                .isEqualTo("FFCC_nekro_miningInitiative");
        assertThat(ActionCardRules.holdOwnTurn(test.contextAt(SETTLED, endTurn)))
                .isEmpty();
    }

    // The main channel can move fast while other players decide on Sabotage (Summit plays at the start of the busy
    // strategy phase): the AI remembers the resolve button it saw and still presses it once it is out of view.
    @Test
    void resolvesAButtonThatScrolledOutOfView() {
        test.nekro.setActionCard("mining_initiative", 412);
        ActionCardRules.playBeforePassing(test.context(hand("ac_play_from_hand_412")));
        cardLeavesHand("mining_initiative", "Mining Initiative");
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_miningInitiative");
        assertThat(ActionCardRules.continuePlay(test.context(announcement("Mining Initiative"), resolve)))
                .isEmpty();

        reacted("nekro", "sol");
        ActionCardRules.continuePlay(test.context());
        assertThat(pressed(ActionCardRules.continuePlay(test.contextAt(SETTLED))))
                .isEqualTo("FFCC_nekro_miningInitiative");
    }

    // A sabotaged card is gone, and its effect must never be applied.
    @Test
    void neverResolvesASabotagedCard() {
        test.nekro.setActionCard("mining_initiative", 412);
        ActionCardRules.playBeforePassing(test.context(hand("ac_play_from_hand_412")));
        cardLeavesHand("mining_initiative", "Mining Initiative");
        test.game.getGameStats().markLatestPlayCanceled("Mining Initiative");
        reacted("nekro", "sol");
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_miningInitiative");

        assertThat(ActionCardRules.continuePlay(test.context(announcement("Mining Initiative"), resolve)))
                .isEmpty();
        assertThat(CardPlay.decode(test.memory.get(turnKey()).orElse("")).map(CardPlay::stage))
                .contains(Stage.CANCELED);
    }

    // A card whose value is small is kept rather than spent on nothing.
    @Test
    void keepsACardThatIsNotWorthPlaying() {
        test.nekro.setActionCard("industrial_initiative", 9);

        assertThat(ActionCardRules.playBeforePassing(test.context(hand("ac_play_from_hand_9"))))
                .isEmpty();
    }

    // Form a Spy Network needs 5 cards in hand.
    @Test
    void keepsItsCardsForFormASpyNetwork() {
        test.nekro.setSecret("fsn");
        for (int card = 1; card <= 4; card++) test.nekro.setActionCard("sabo" + card, card);
        test.nekro.setActionCard("mining_initiative", 412);

        assertThat(ActionCardRules.playBeforePassing(test.context(hand("ac_play_from_hand_412"))))
                .isEmpty();
    }

    // Frontline Deployment's 3 infantry go where a dock or carrier can pick them up.
    @Test
    void placesFrontlineInfantryWhereTheyCanBeCarried() {
        Tile wellon = test.place("19", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("wellon");
        test.memory.put(turnKey(), new CardPlay(Stage.TARGET, NOW, NOW, "f_deployment", "1", true, 0, "").encode());
        AiPrompt targets = prompt(
                "targets",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_placeOneNDone_skipbuild_3gf_wellon",
                "FFCC_nekro_placeOneNDone_skipbuild_3gf_mordaiii");

        assertThat(pressed(ActionCardRules.continuePlay(test.context(targets))))
                .isEqualTo("FFCC_nekro_placeOneNDone_skipbuild_3gf_mordaiii");
        assertThat(wellon).isNotNull();
    }

    // War Effort's cruiser joins a fleet only where the fleet pool allows another ship; the bot doesn't check.
    @Test
    void placesTheWarEffortCruiserWithinFleetSupply() {
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        Tile neighbour = test.place("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(neighbour, "space", test.nekro, UnitType.Destroyer, 1);
        test.memory.put(turnKey(), new CardPlay(Stage.TARGET, NOW, NOW, "war_effort", "1", true, 0, "").encode());
        AiPrompt targets = prompt(
                "targets",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_placeOneNDone_skipbuild_cruiser_" + AiTestGame.HOME,
                "FFCC_nekro_placeOneNDone_skipbuild_cruiser_" + neighbour.getPosition());

        assertThat(pressed(ActionCardRules.continuePlay(test.context(targets))))
                .isEqualTo("FFCC_nekro_placeOneNDone_skipbuild_cruiser_" + neighbour.getPosition());
    }

    // Summit is pre-played so the bot plays it at the start of the strategy phase; after the Sabotage window the AI
    // resolves it and takes its 2 command tokens.
    @Test
    void preplaysSummitAndCollectsItsTokens() {
        test.nekro.setActionCard("summit", 5);
        AiPrompt preset = prompt("preset", PromptSource.AI_THREAD, NOW, "resolvePreassignment_Summit", "deleteButtons");
        assertThat(pressed(ActionCardRules.presetSummit(test.context(preset))))
                .isEqualTo("resolvePreassignment_Summit");

        cardLeavesHand("summit", "Summit");
        reacted("nekro", "sol");
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_resolveSummit");
        ActionCardRules.continuePlay(test.context(announcement("Summit"), resolve));
        assertThat(pressed(ActionCardRules.continuePlay(test.contextAt(SETTLED, announcement("Summit"), resolve))))
                .isEqualTo("FFCC_nekro_resolveSummit");

        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        AiPrompt gain = prompt(
                "gain",
                PromptSource.PUBLIC,
                SETTLED,
                List.of(
                        "FFCC_nekro_increase_tactic_cc",
                        "FFCC_nekro_increase_fleet_cc",
                        "FFCC_nekro_increase_strategy_cc",
                        "FFCC_nekro_deleteButtons"),
                List.of("Gain Tactic", "Gain Fleet", "Gain Strategy", "Done Gaining Command Tokens"));
        assertThat(pressed(ActionCardRules.continuePlay(test.contextAt(SETTLED, gain))))
                .startsWith("FFCC_nekro_increase_");

        test.nekro.setStrategicCC(4);
        assertThat(pressed(ActionCardRules.continuePlay(test.contextAt(SETTLED, gain))))
                .isEqualTo("FFCC_nekro_deleteButtons");
    }

    // After an undo or a restart the AI's memory of the play is gone, but its turn still shows the card's resolve
    // button and announcement: it picks the play up again instead of ending its turn with the card unresolved.
    @Test
    void picksUpAPlayAfterLosingItsMemory() {
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_miningInitiative");
        AiPrompt endTurn = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_endOfTurnAbilities");
        test.game.getGameStats().recordAcPlay("Mining Initiative", test.nekro);

        assertThat(ActionCardRules.continuePlay(test.context(announcement("Mining Initiative"), resolve, endTurn)))
                .isEmpty();
        assertThat(ActionCardRules.holdOwnTurn(test.context(endTurn))).containsInstanceOf(AiDecision.Wait.class);

        reacted("nekro", "sol");
        ActionCardRules.continuePlay(test.context(announcement("Mining Initiative"), resolve));
        assertThat(pressed(ActionCardRules.continuePlay(
                        test.contextAt(SETTLED, announcement("Mining Initiative"), resolve))))
                .isEqualTo("FFCC_nekro_miningInitiative");
    }

    // A card it would rather discard comes first; between two cards that tie, the one with nothing to do now goes.
    @Test
    void discardsTheDeadCardBeforeOneThatWouldScore() {
        test.nekro.setActionCard("industrial_initiative", 1);
        test.nekro.setActionCard("mining_initiative", 2);
        AiPrompt discards =
                prompt("discard", PromptSource.AI_THREAD, NOW, "ac_discard_from_hand_1", "ac_discard_from_hand_2");

        assertThat(ActionCardValue.worstDiscard(test.game, test.nekro, discards, "ac_discard_from_hand_", "", b -> true)
                        .map(button -> button.customId()))
                .contains("ac_discard_from_hand_1");
    }

    // Discards go to cards the AI can never use before the ones it plays.
    @Test
    void discardsTheLeastUsefulCardFirst() {
        test.nekro.setActionCard("mining_initiative", 1);
        test.nekro.setActionCard("intercept", 2);
        AiPrompt discards =
                prompt("discard", PromptSource.AI_THREAD, NOW, "ac_discard_from_hand_1", "ac_discard_from_hand_2");

        assertThat(ActionCardValue.worstDiscard(test.game, test.nekro, discards, "ac_discard_from_hand_", "", b -> true)
                        .map(button -> button.customId()))
                .contains("ac_discard_from_hand_2");
    }

    // With no action cards there is nothing to hand over, and pressing would make the bot's handler fail.
    @Test
    void ignoresASpyWithAnEmptyHand() {
        test.nekro.getActionCards().clear();
        AiPrompt spy = prompt("spy", PromptSource.AI_THREAD, NOW, "spyStep3_sol");

        assertThat(ActionCardResponses.respond(test.context(spy))).isEmpty();
    }

    // Another player's Spy takes a random card: the AI hands it over.
    @Test
    void handsOverACardToASpy() {
        test.nekro.setActionCard("intercept", 3);
        AiPrompt spy = prompt("spy", PromptSource.AI_THREAD, NOW, "spyStep3_sol");

        assertThat(pressed(ActionCardResponses.respond(test.context(spy)))).isEqualTo("spyStep3_sol");
        assertThat(ActionCardResponses.respond(test.context(Set.of("spy|spyStep3_sol"), spy)))
                .isEmpty();
    }

    // Diplomatic Pressure makes the AI give a promissory note of its choice: it keeps Support for the Throne.
    @Test
    void givesTheLeastHarmfulNoteToDiplomaticPressure() {
        test.nekro.setPromissoryNote("black_sftt", 11);
        test.nekro.setPromissoryNote("black_cf", 12);
        AiPrompt forced = AiTestGame.withContent(
                prompt("forced", PromptSource.AI_THREAD, NOW, "naaluHeroSend_sol_11", "naaluHeroSend_sol_12"),
                test.nekro.getRepresentation() + ", you are being forced to give a promissory note to Sol.");

        assertThat(pressed(ActionCardResponses.respond(test.context(forced)))).isEqualTo("naaluHeroSend_sol_12");
    }

    // Extreme Duress: before acting, the AI gives in and plays its strategy card rather than losing its hand.
    @Test
    void playsAStrategyCardUnderExtremeDuress() {
        AiPrompt duress = prompt("duress", PromptSource.PUBLIC, NOW, "FFCC_nekro_concedeToED_sol", "deleteButtons");
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_strategicAction_8");

        assertThat(pressed(ActionCardResponses.respond(test.context(duress, turn))))
                .isEqualTo("deleteButtons");
        assertThat(pressed(ActionCardResponses.respond(test.context(turn)))).isEqualTo("FFCC_nekro_strategicAction_8");
    }
}
