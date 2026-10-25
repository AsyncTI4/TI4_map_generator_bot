package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class ComponentCardFlowTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.setTacticalCC(3);
        table.nekro.setFleetCC(3);
        table.nekro.setStrategicCC(2);
        messages = Mockito.mockStatic(GameMessageManager.class);
        reacted("nekro");
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private void reacted(String... factions) {
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1), GameMessageType.ACTION_CARD, new LinkedHashSet<>(List.of(factions)), 0L)));
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
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

    private AiPrompt resolve(String handler) {
        return prompt("resolve-" + handler, PromptSource.PUBLIC, NOW, "FFCC_nekro_" + handler);
    }

    private AiPrompt targets(String... ids) {
        return AiTestGame.withContent(
                prompt("targets-" + ids[0], PromptSource.PUBLIC, SETTLED, ids),
                table.nekro.getRepresentationUnfogged() + ", please choose.");
    }

    private AiPrompt targetsFor(Player other, String... ids) {
        return AiTestGame.withContent(
                prompt("other-" + ids[0], PromptSource.PUBLIC, SETTLED, ids),
                other.getRepresentationUnfogged() + ", please choose.");
    }

    private void cardLeavesHand(String alias, String title) {
        table.nekro.getActionCards().remove(alias);
        table.game.getGameStats().recordAcPlay(title, table.nekro);
    }

    private String turnKey() {
        return "acPlay|turn:" + table.test.context().turnKey();
    }

    private Optional<CardPlay> play() {
        return table.test.memory.get(turnKey()).flatMap(CardPlay::decode);
    }

    private Optional<AiDecision> play(String alias) {
        table.nekro.setActionCard(alias, HAND_INDEX);
        return ActionCardRules.playBeforePassing(table.test.context(hand()));
    }

    // The window is closed and the resolve button posted: press it and move on.
    private String resolveCard(String title, String handler) {
        reacted("nekro", "sol", "hacan");
        ActionCardRules.continuePlay(table.test.context(announcement(title), resolve(handler)));
        return pressed(
                ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announcement(title), resolve(handler))));
    }

    // Fighters fit in every system with a dock or a ship that has room, and the card is only played when it adds
    // enough.
    private void carriersInOutposts(int count) {
        List<String> positions = List.of("302", "303", "305", "306");
        for (int i = 0; i < count; i++) {
            Tile outpost = table.system("46", positions.get(i));
            table.ships(table.nekro, outpost, UnitType.Carrier, 1);
        }
    }

    private void threePlayers() {
        table.third("hacan", "yellow");
        reacted("nekro");
    }

    // Spy: play it, declare the target, wait out the window, resolve, pick the player.
    @Test
    void playsSpyDeclaresItsTargetAndPicksThePlayer() {
        threePlayers();
        table.cards(table.sol, "intercept", "rout");
        table.points(table.sol, 8);

        assertThat(pressed(play("spy"))).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        cardLeavesHand("spy", "Spy");

        Optional<AiDecision> announce = ActionCardRules.continuePlay(table.test.context(announcement("Spy")));
        assertThat(announce).containsInstanceOf(AiDecision.Announce.class);
        assertThat(((AiDecision.Announce) announce.get()).text())
                .contains("Spy")
                .contains("Sol");
        assertThat(ActionCardRules.continuePlay(table.test.context(announcement("Spy"))))
                .isEmpty();
        assertThat(ActionCardRules.holdOwnTurn(table.test.context())).containsInstanceOf(AiDecision.Wait.class);

        assertThat(resolveCard("Spy", "resolveSpyStep1")).isEqualTo("FFCC_nekro_resolveSpyStep1");
        assertThat(pressed(ActionCardRules.continuePlay(
                        table.test.contextAt(SETTLED, targets("spyStep2_sol", "spyStep2_hacan")))))
                .isEqualTo("spyStep2_sol");
        assertThat(play().map(CardPlay::stage)).contains(Stage.DONE);
        assertThat(ActionCardRules.holdOwnTurn(table.test.context())).isEmpty();
    }

    // The declaration is made once, right after the card is played.
    @Test
    void declaresTheTargetOnlyOnce() {
        threePlayers();
        table.cards(table.sol, "intercept");
        table.points(table.sol, 8);
        play("spy");
        cardLeavesHand("spy", "Spy");

        assertThat(ActionCardRules.continuePlay(table.test.context(announcement("Spy"))))
                .containsInstanceOf(AiDecision.Announce.class);
        assertThat(ActionCardRules.continuePlay(table.test.context(announcement("Spy"))))
                .isEmpty();
    }

    // A target button in a prompt addressed to somebody else is not this seat's to press.
    @Test
    void neverPressesAnotherSeatsTargetButtons() {
        threePlayers();
        table.cards(table.sol, "intercept");
        play("spy");
        cardLeavesHand("spy", "Spy");
        ActionCardRules.continuePlay(table.test.context(announcement("Spy")));
        resolveCard("Spy", "resolveSpyStep1");

        AiPrompt theirs = targetsFor(table.sol, "spyStep2_hacan", "spyStep2_sol");

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, theirs)))
                .isEmpty();
    }

    // Cards whose only choice is one button after the resolve button: the player, system or tactic pool to aim at.
    private void oneChoiceCard(String alias, String title, String resolveHandler, String chosen, String... offered) {
        play(alias);
        cardLeavesHand(alias, title);
        ActionCardRules.continuePlay(table.test.context(announcement(title)));

        assertThat(resolveCard(title, resolveHandler)).isEqualTo("FFCC_nekro_" + resolveHandler);
        assertThat(pressed(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, targets(offered)))))
                .isEqualTo(chosen);
        assertThat(play().map(CardPlay::stage)).contains(Stage.DONE);
    }

    @Test
    void insubordinationPicksAPlayerWithATacticToken() {
        threePlayers();
        table.sol.setTacticalCC(3);
        table.points(table.sol, 8);

        oneChoiceCard(
                "insub",
                "Insubordination",
                "resolveInsubStep1",
                "insubStep2_sol",
                "insubStep2_hacan",
                "insubStep2_sol");
    }

    @Test
    void ghostShipPicksTheSystemItDeclared() {
        String near = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile alpha = table.system("39", near);
        table.ships(table.nekro, table.game.getTileByPosition(AiTestGame.HOME), UnitType.Cruiser, 1);

        oneChoiceCard(
                "ghost_ship",
                "Ghost Ship",
                "resolveGhostShipStep1",
                "ghostShipStep2_" + alpha.getPosition(),
                "ghostShipStep2_" + AiTestGame.HOME,
                "ghostShipStep2_" + alpha.getPosition());
    }

    @Test
    void explorationProbePicksTheFrontierSystemItDeclared() {
        String near = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile frontier = table.system("41", near);
        frontier.addToken(ti4.model.Mapper.getTokenID(ti4.helpers.Constants.FRONTIER), "space");
        table.ships(table.nekro, table.game.getTileByPosition(AiTestGame.HOME), UnitType.Cruiser, 1);

        oneChoiceCard(
                "probe",
                "Exploration Probe",
                "resolveProbeStep1",
                "probeStep2_" + frontier.getPosition(),
                "probeStep2_" + frontier.getPosition());
    }

    // Uprising has two steps: the planet's owner, then the planet.
    @Test
    void uprisingPicksTheOwnerThenThePlanet() {
        table.solHome();
        table.system("38", "201");
        table.sol.addPlanet("abyz");
        table.system("19", "202");
        table.sol.addPlanet("wellon");

        assertThat(pressed(play("uprising"))).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        cardLeavesHand("uprising", "Uprising");
        ActionCardRules.continuePlay(table.test.context(announcement("Uprising")));

        assertThat(resolveCard("Uprising", "resolveUprisingStep1")).isEqualTo("FFCC_nekro_resolveUprisingStep1");
        assertThat(pressed(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, targets("uprisingStep2_sol")))))
                .isEqualTo("uprisingStep2_sol");
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);

        assertThat(pressed(ActionCardRules.continuePlay(
                        table.test.contextAt(SETTLED, targets("uprisingStep3_sol_wellon", "uprisingStep3_sol_abyz")))))
                .isEqualTo("uprisingStep3_sol_abyz");
        assertThat(play().map(CardPlay::stage)).contains(Stage.DONE);
    }

    // A planet no longer ready by the time the buttons appear is not pressed for; the best one on offer is.
    @Test
    void uprisingFallsBackToTheBestPlanetOnOffer() {
        table.solHome();
        table.system("38", "201");
        table.sol.addPlanet("abyz");
        table.system("19", "202");
        table.sol.addPlanet("wellon");
        play("uprising");
        cardLeavesHand("uprising", "Uprising");
        ActionCardRules.continuePlay(table.test.context(announcement("Uprising")));
        resolveCard("Uprising", "resolveUprisingStep1");

        assertThat(pressed(ActionCardRules.continuePlay(
                        table.test.contextAt(SETTLED, targets("uprisingStep3_sol_wellon")))))
                .isEqualTo("uprisingStep3_sol_wellon");
    }

    // Archaeological Expedition has no resolve button: the bot posts the deck buttons with the card, so the AI waits
    // out
    // the Sabotage window and then picks the deck.
    @Test
    void archaeologicalExpeditionPicksADeckAfterTheWindow() {
        table.system("19", "202");
        table.nekro.addPlanet("wellon");
        table.nekro.addFragment("irf1");
        table.nekro.addFragment("irf2");
        assertThat(pressed(play("arch_expedition"))).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        String planned = play().orElseThrow().extra();
        cardLeavesHand("arch_expedition", "Archaeological Expedition");
        AiPrompt decks = AiTestGame.withContent(
                prompt("decks", PromptSource.PUBLIC, NOW, "arcExp_industrial", "arcExp_cultural", "arcExp_hazardous"),
                table.nekro.getRepresentation() + ", after checking for Sabos, please use buttons.");
        ActionCardRules.continuePlay(table.test.context(announcement("Archaeological Expedition"), decks));

        assertThat(ActionCardRules.continuePlay(
                        table.test.contextAt(NOW + 1_000L, announcement("Archaeological Expedition"), decks)))
                .isEmpty();
        reacted("nekro", "sol");
        ActionCardRules.continuePlay(table.test.context(announcement("Archaeological Expedition"), decks));

        assertThat(pressed(ActionCardRules.continuePlay(
                        table.test.contextAt(SETTLED, announcement("Archaeological Expedition"), decks))))
                .isEqualTo("arcExp_" + planned);
    }

    // Refit Troops posts two identical prompts; each press swaps one infantry.
    @Test
    void refitTroopsPressesOneSwapPerPrompt() {
        Tile home = table.game.getTileByPosition(AiTestGame.HOME);
        table.test.units(home, "mordaiii", table.nekro, UnitType.Infantry, 3);
        play("refit");
        cardLeavesHand("refit", "Refit Troops");
        ActionCardRules.continuePlay(table.test.context(announcement("Refit Troops")));
        reacted("nekro", "sol");

        assertThat(resolveCard("Refit Troops", "resolveRefitTroops")).isEqualTo("FFCC_nekro_resolveRefitTroops");
        String swap = "FFCC_nekro_mitoMechPlacement_" + AiTestGame.HOME + "_mordaiii_refit";
        AiPrompt first = prompt("swap-1", PromptSource.PUBLIC, SETTLED, swap);
        AiPrompt second = prompt("swap-2", PromptSource.PUBLIC, SETTLED, swap);

        assertThat(pressed(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, first, second))))
                .isEqualTo(swap);
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);
        assertThat(pressed(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, second))))
                .isEqualTo(swap);
        assertThat(play().map(CardPlay::stage)).contains(Stage.DONE);
    }

    @Test
    void refitTroopsSwapsNothingOnAPlanetWithoutInfantry() {
        Tile home = table.game.getTileByPosition(AiTestGame.HOME);
        table.test.units(home, "mordaiii", table.nekro, UnitType.Infantry, 1);
        play("refit");
        cardLeavesHand("refit", "Refit Troops");
        ActionCardRules.continuePlay(table.test.context(announcement("Refit Troops")));
        reacted("nekro", "sol");
        resolveCard("Refit Troops", "resolveRefitTroops");
        home.removeUnit("mordaiii", ti4.helpers.Units.getUnitKey(UnitType.Infantry, table.nekro.getColor()), 1);
        String swap = "FFCC_nekro_mitoMechPlacement_" + AiTestGame.HOME + "_mordaiii_refit";

        assertThat(ActionCardRules.continuePlay(
                        table.test.contextAt(SETTLED, prompt("swap-1", PromptSource.PUBLIC, SETTLED, swap))))
                .isEmpty();
    }

    // Fighter Conscription needs no choice: the bot places the fighters when the button is pressed.
    @Test
    void fighterConscriptionEndsAfterTheResolveButton() {
        carriersInOutposts(3);
        assertThat(pressed(play("f_conscription"))).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        cardLeavesHand("f_conscription", "Fighter Conscription");
        ActionCardRules.continuePlay(table.test.context(announcement("Fighter Conscription")));

        assertThat(resolveCard("Fighter Conscription", "fighterConscription"))
                .isEqualTo("FFCC_nekro_fighterConscription");
        assertThat(play().map(CardPlay::stage)).contains(Stage.DONE);
    }

    // A sabotaged card is gone, and its effect must never be applied.
    @Test
    void aSabotagedCardIsNeverResolved() {
        threePlayers();
        table.cards(table.sol, "intercept");
        play("spy");
        cardLeavesHand("spy", "Spy");
        ActionCardRules.continuePlay(table.test.context(announcement("Spy")));
        table.game.getGameStats().markLatestPlayCanceled("Spy");
        reacted("nekro", "sol", "hacan");

        assertThat(ActionCardRules.continuePlay(table.test.context(announcement("Spy"), resolve("resolveSpyStep1"))))
                .isEmpty();
        assertThat(play().map(CardPlay::stage)).contains(Stage.CANCELED);
    }

    // The play button has scrolled out of the AI's thread: it asks the bot to post its cards again, once.
    @Test
    void asksForItsHandBeforePlayingWhenTheButtonIsOutOfView() {
        carriersInOutposts(3);
        table.nekro.setActionCard("f_conscription", HAND_INDEX);
        AiPrompt refresh = prompt("old", PromptSource.AI_THREAD, NOW, "cardsInfo");

        assertThat(pressed(ActionCardRules.playBeforePassing(table.test.context(refresh))))
                .isEqualTo("cardsInfo");
        assertThat(ActionCardRules.playBeforePassing(table.test.context(refresh)))
                .containsInstanceOf(AiDecision.Wait.class);
        assertThat(pressed(ActionCardRules.playBeforePassing(table.test.context(hand()))))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // With nothing better to do the AI plays the card instead of a tactical action; with a tactical action that
    // scores more than the card, the action comes first.
    @Test
    void aTacticalActionWorthMoreThanTheCardComesFirst() {
        threePlayers();
        table.cards(table.sol, "intercept");
        table.points(table.sol, 8);
        table.nekro.setActionCard("spy", HAND_INDEX);

        assertThat(pressed(ActionCardRules.insteadOfTacticalAction(table.test.context(hand()))))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);

        table.test.memory.remove(turnKey());
        table.test.units(table.game.getTileByPosition(AiTestGame.HOME), "space", table.nekro, UnitType.Carrier, 1);
        table.test.units(table.game.getTileByPosition(AiTestGame.HOME), "mordaiii", table.nekro, UnitType.Infantry, 4);
        table.system("26", AiTestGame.neighbourOf(AiTestGame.HOME));

        assertThat(ActionCardRules.insteadOfTacticalAction(table.test.context(hand())))
                .isEmpty();
        assertThat(pressed(ActionCardRules.playBeforePassing(table.test.context(hand()))))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // ---- the turn --------------------------------------------------------------------------

    private AiDecision brainDecision(AiPrompt... prompts) {
        return new NekroBrain().decide(table.test.contextAt(SETTLED, prompts));
    }

    // A component action card is the turn's action: the AI holds its turn while the card is in play, ends it once the
    // card is done, and starts no tactical action in between.
    @Test
    void theTurnEndsOnceTheCardIsDone() {
        threePlayers();
        table.cards(table.sol, "intercept");
        table.points(table.sol, 8);
        play("spy");
        cardLeavesHand("spy", "Spy");
        ActionCardRules.continuePlay(table.test.context(announcement("Spy")));
        AiPrompt endTurn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_turnEnd");

        assertThat(brainDecision(endTurn)).isInstanceOf(AiDecision.Wait.class);

        resolveCard("Spy", "resolveSpyStep1");
        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, targets("spyStep2_sol", "spyStep2_hacan")));
        assertThat(play().map(CardPlay::stage)).contains(Stage.DONE);

        assertThat(AiTestGame.pressedId(brainDecision(endTurn))).isEqualTo("FFCC_nekro_turnEnd");
    }

    // ---- after a system is activated -------------------------------------------------------

    private void solActivatesNekrosHome() {
        threePlayers();
        table.test.isActive(table.sol, "action");
        table.game.setActiveSystem(AiTestGame.HOME);
        table.nekro.setActionCard("fsb", HAND_INDEX);
    }

    // Forward Supply Base: another player activates a system holding the seat's units; the seat plays it, waits out
    // the window, takes the 3 trade goods and gives the other one to the player furthest behind.
    @Test
    void playsForwardSupplyBaseWhenAnotherPlayerActivatesASystemWithItsUnits() {
        solActivatesNekrosHome();
        table.points(table.sol, 3);

        assertThat(pressed(ActionCardRules.afterActivation(table.test.context(hand()))))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        cardLeavesHand("fsb", "Forward Supply Base");
        Optional<AiDecision> announce =
                ActionCardRules.continuePlay(table.test.context(announcement("Forward Supply Base")));
        assertThat(announce).containsInstanceOf(AiDecision.Announce.class);
        assertThat(((AiDecision.Announce) announce.get()).text()).contains("Forward Supply Base");

        assertThat(resolveCard("Forward Supply Base", "forwardSupplyBase")).isEqualTo("FFCC_nekro_forwardSupplyBase");
        assertThat(pressed(ActionCardRules.continuePlay(table.test.contextAt(
                        SETTLED, targets("forwardSupplyBaseStep2_sol", "forwardSupplyBaseStep2_hacan")))))
                .isEqualTo("forwardSupplyBaseStep2_hacan");
    }

    @Test
    void playsForwardSupplyBaseOnlyOncePerActivation() {
        solActivatesNekrosHome();

        assertThat(ActionCardRules.afterActivation(table.test.context(hand()))).isPresent();
        assertThat(ActionCardRules.afterActivation(table.test.context(hand()))).isEmpty();
    }

    @Test
    void doesNotPlayForwardSupplyBaseWhereItHasNoUnits() {
        solActivatesNekrosHome();
        table.game.setActiveSystem("999");

        assertThat(ActionCardRules.afterActivation(table.test.context(hand()))).isEmpty();
    }

    @Test
    void doesNotPlayForwardSupplyBaseOnItsOwnActivation() {
        threePlayers();
        table.game.setActiveSystem(AiTestGame.HOME);
        table.nekro.setActionCard("fsb", HAND_INDEX);

        assertThat(ActionCardRules.afterActivation(table.test.context(hand()))).isEmpty();
    }

    // ---- the seat that does not use the new rules ------------------------------------------

    @Test
    void aLegacySeatPlaysNeitherNewCardsNorForwardSupplyBase() {
        ti4.ai.AiSettings.markLegacyActionCards(table.nekro);
        threePlayers();
        table.cards(table.sol, "intercept");
        table.points(table.sol, 8);
        table.nekro.setActionCard("spy", HAND_INDEX);

        assertThat(ActionCardRules.playBeforePassing(table.test.context(hand())))
                .isEmpty();
        assertThat(ActionCardRules.insteadOfTacticalAction(table.test.context(hand())))
                .isEmpty();

        solActivatesNekrosHome();
        assertThat(ActionCardRules.afterActivation(table.test.context(hand()))).isEmpty();
    }

    // ---- recovery --------------------------------------------------------------------------

    // After an undo or a restart the AI has lost its memory of the play but the board still shows the target buttons.
    @Test
    void picksUpATargetStepAfterLosingItsMemory() {
        threePlayers();
        table.cards(table.sol, "intercept");
        table.game.getGameStats().recordAcPlay("Spy", table.nekro);

        assertThat(pressed(ActionCardRules.continuePlay(
                        table.test.contextAt(SETTLED, targets("spyStep2_sol", "spyStep2_hacan")))))
                .startsWith("spyStep2_");
    }

    // The Technology card posts a button that begins with the same letters as Focused Research's: it is not a play of
    // that card, and a button for a card the seat never played is nobody's to press.
    @Test
    void doesNotMistakeAnotherCardsButtonsForAPlayInProgress() {
        threePlayers();
        AiPrompt research = addressedTo("research", "acquireATechWithSC_first");
        AiPrompt spyTargets = addressedTo("spy", "spyStep2_sol", "spyStep2_hacan");

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, research, spyTargets)))
                .isEmpty();
    }

    private AiPrompt addressedTo(String id, String... customIds) {
        return AiTestGame.withContent(
                prompt(id, PromptSource.PUBLIC, SETTLED, customIds),
                table.nekro.getRepresentationUnfogged() + ", please choose.");
    }

    @Test
    void picksUpAForwardSupplyBaseAfterLosingItsMemory() {
        solActivatesNekrosHome();
        table.nekro.getActionCards().remove("fsb");
        table.game.getGameStats().recordAcPlay("Forward Supply Base", table.nekro);
        reacted("nekro", "sol", "hacan");

        ActionCardRules.continuePlay(
                table.test.context(announcement("Forward Supply Base"), resolve("forwardSupplyBase")));

        assertThat(pressed(ActionCardRules.continuePlay(table.test.contextAt(
                        SETTLED, announcement("Forward Supply Base"), resolve("forwardSupplyBase")))))
                .isEqualTo("FFCC_nekro_forwardSupplyBase");
    }
}
