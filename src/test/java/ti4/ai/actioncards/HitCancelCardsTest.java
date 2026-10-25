package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.CombatRules;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

// Shields Holding ("Before you assign hits to your ships during a space combat", cancel up to 2) and Maneuvering Jets
// ("Before you assign hits produced by another player's SPACE CANNON roll", cancel 1). The engine posts the seat's
// Auto-assign button with a "Cancel a Hit" button beside it (CombatRollService.sendSpaceCannonOffenceHitAssignment and
// the space combat hit message); each press edits the message to one hit fewer with an unowned cancel button
// (CombatButtonHandler.cancelSpaceHits / cancelPDSOffenseHits).
class HitCancelCardsTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 515;
    private static final String HITS_MESSAGE = "hits";

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;
    private String position;
    private Tile tile;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        position = CardTable.SOL_SPOT;
        tile = table.system("26", position);
        table.game.setActiveSystem(position);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1),
                        GameMessageType.ACTION_CARD,
                        new LinkedHashSet<>(List.of("nekro", "sol")),
                        0L)));
    }

    @AfterEach
    void tearDown() {
        messages.close();
        System.clearProperty(AiSettings.COMBAT_CARDS_PROPERTY);
    }

    private AiPrompt spaceHits(int hits) {
        return prompt(
                HITS_MESSAGE,
                PromptSource.COMBAT_THREAD,
                NOW,
                "FFCC_nekro_autoAssignSpaceHits_" + position + "_" + hits,
                "getDamageButtons_" + position + "deleteThis_spacecombat",
                "FFCC_nekro_cancelSpaceHits_" + position + "_" + hits);
    }

    private AiPrompt spaceHitsAfterACancel(int hits) {
        return prompt(
                HITS_MESSAGE,
                PromptSource.COMBAT_THREAD,
                NOW,
                "FFCC_nekro_autoAssignSpaceHits_" + position + "_" + hits,
                "getDamageButtons_" + position + "_spacecombat",
                "cancelSpaceHits_" + position + "_" + hits);
    }

    private AiPrompt cannonHits(int hits) {
        return prompt(
                HITS_MESSAGE,
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_autoAssignSpaceCannonOffenceHits_" + position + "_" + hits,
                "getDamageButtons_" + position + "deleteThis_pds",
                "FFCC_nekro_cancelPdsOffenseHits_" + position + "_" + hits);
    }

    private static AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
    }

    private static AiPrompt announcement(String title) {
        return prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_" + title,
                "no_sabotage",
                "sabotage_ac_" + title + "_nekro");
    }

    private Optional<AiDecision> cancelHits(AiPrompt hits) {
        return CombatCardRules.cancelHits(table.test.context(hits, hand()));
    }

    private String key() {
        return PlayMemory.combatKey(position, "hits" + HITS_MESSAGE, 0);
    }

    private Optional<Stage> stage() {
        return table.test.memory.get(key()).flatMap(CardPlay::decode).map(CardPlay::stage);
    }

    private void evenSpaceFight() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 4);
        table.ships(table.sol, tile, UnitType.Dreadnought, 4);
    }

    private void played(String title) {
        table.nekro.getActionCards().clear();
        table.game.getGameStats().recordAcPlay(title, table.nekro);
    }

    @Test
    void shieldsHoldingIsPlayedOnTwoHitsInACloseFight() {
        evenSpaceFight();
        table.nekro.setActionCard("sh1", HAND_INDEX);

        assertThat(pressedId(cancelHits(spaceHits(2)).orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        assertThat(stage()).contains(Stage.PLAYED);
    }

    @Test
    void shieldsHoldingIsKeptForASingleHit() {
        evenSpaceFight();
        table.nekro.setActionCard("sh1", HAND_INDEX);

        assertThat(cancelHits(spaceHits(1))).isEmpty();
    }

    // Six dreadnoughts against one fighter: the fight is won anyway.
    @Test
    void shieldsHoldingIsKeptWhenTheFightIsWonAnyway() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 6);
        table.ships(table.sol, tile, UnitType.Fighter, 1);
        table.nekro.setActionCard("sh1", HAND_INDEX);

        assertThat(cancelHits(spaceHits(2))).isEmpty();
    }

    // One dreadnought against eight: two cancelled hits do not save the fight.
    @Test
    void shieldsHoldingIsKeptWhenTheFightIsLostAnyway() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 1);
        table.ships(table.sol, tile, UnitType.Dreadnought, 8);
        table.nekro.setActionCard("sh1", HAND_INDEX);

        assertThat(cancelHits(spaceHits(2))).isEmpty();
    }

    @Test
    void shieldsHoldingIsNeverPlayedOnSpaceCannonHits() {
        evenSpaceFight();
        table.nekro.setActionCard("sh1", HAND_INDEX);

        assertThat(cancelHits(cannonHits(2))).isEmpty();
    }

    @Test
    void maneuveringJetsIsPlayedOnASpaceCannonHit() {
        evenSpaceFight();
        table.nekro.setActionCard("mjets1", HAND_INDEX);

        assertThat(pressedId(cancelHits(cannonHits(1)).orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void maneuveringJetsIsNeverPlayedOnSpaceCombatHits() {
        evenSpaceFight();
        table.nekro.setActionCard("mjets1", HAND_INDEX);

        assertThat(cancelHits(spaceHits(2))).isEmpty();
    }

    private void invasionUnderSolsPds() {
        evenSpaceFight();
        table.test.units(tile, "lodor", table.sol, UnitType.Pds, 1);
        TacticalRules.remember(
                table.test.context(),
                new TacticalPlan(TacticalPlan.Kind.ATTACK, position, List.of(), Map.of("lodor", 2), 5.0));
    }

    // The tactical action goes on to land on a planet with Sol's PDS; the only copy is kept for that shot.
    @Test
    void maneuveringJetsIsKeptForTheInvasionsSpaceCannonShot() {
        invasionUnderSolsPds();
        table.nekro.setActionCard("mjets1", HAND_INDEX);

        assertThat(cancelHits(cannonHits(1))).isEmpty();
    }

    @Test
    void aSecondCopyOfManeuveringJetsIsPlayedBeforeTheInvasion() {
        invasionUnderSolsPds();
        table.nekro.setActionCard("mjets1", HAND_INDEX);
        table.nekro.setActionCard("mjets2", HAND_INDEX + 1);

        assertThat(pressedId(cancelHits(cannonHits(1)).orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void nothingIsPlayedWhenTheEngineOffersNoCancelButton() {
        evenSpaceFight();
        table.nekro.setActionCard("sh1", HAND_INDEX);
        AiPrompt dummy = prompt(
                HITS_MESSAGE, PromptSource.COMBAT_THREAD, NOW, "FFCC_nekro_autoAssignSpaceHits_" + position + "_2");

        assertThat(cancelHits(dummy)).isEmpty();
    }

    @Test
    void noHitIsCancelledInAFogOfWarGame() {
        evenSpaceFight();
        table.nekro.setActionCard("sh1", HAND_INDEX);
        table.game.setFowMode(true);

        assertThat(cancelHits(spaceHits(2))).isEmpty();
    }

    @Test
    void hitCancelsCanBeSwitchedOffWithTheOtherCombatCards() {
        evenSpaceFight();
        table.nekro.setActionCard("sh1", HAND_INDEX);
        System.setProperty(AiSettings.COMBAT_CARDS_PROPERTY, "false");

        assertThat(cancelHits(spaceHits(2))).isEmpty();
    }

    @Test
    void shieldsHoldingCancelsTwoHitsOnlyAfterItsSabotageWindowCloses() {
        evenSpaceFight();
        table.nekro.setActionCard("sh1", HAND_INDEX);
        cancelHits(spaceHits(3));
        played("Shields Holding");
        AiPrompt announce = announcement("Shields Holding");

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(NOW + 1_000L, announce, spaceHits(3))))
                .isEmpty();
        assertThat(CombatCardRules.hold(table.test.contextAt(NOW + 1_000L, announce, spaceHits(3))))
                .isPresent();

        assertThat(pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, spaceHits(3)))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_cancelSpaceHits_" + position + "_3");
        assertThat(stage()).contains(Stage.TARGET);
        assertThat(pressedId(
                        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, spaceHitsAfterACancel(2)))
                                .orElseThrow()))
                .isEqualTo("cancelSpaceHits_" + position + "_2");
        assertThat(stage()).contains(Stage.DONE);

        AiPrompt rest = spaceHitsAfterACancel(1);
        assertThat(CombatCardRules.hold(table.test.contextAt(SETTLED, rest))).isEmpty();
        assertThat(CombatCardRules.cancelHits(table.test.contextAt(SETTLED, rest)))
                .isEmpty();
        assertThat(pressedId(
                        CombatRules.next(table.test.contextAt(SETTLED, rest)).orElseThrow()))
                .isEqualTo("FFCC_nekro_autoAssignSpaceHits_" + position + "_1");
    }

    @Test
    void aSabotagedShieldsHoldingCancelsNoHit() {
        evenSpaceFight();
        table.nekro.setActionCard("sh1", HAND_INDEX);
        cancelHits(spaceHits(2));
        played("Shields Holding");
        table.game.getGameStats().markLatestPlayCanceled("Shields Holding");
        AiPrompt announce = announcement("Shields Holding");

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, spaceHits(2))))
                .isEmpty();

        assertThat(stage()).contains(Stage.CANCELED);
        assertThat(CombatCardRules.hold(table.test.contextAt(SETTLED, spaceHits(2))))
                .isEmpty();
        assertThat(pressedId(CombatRules.next(table.test.contextAt(SETTLED, spaceHits(2)))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_autoAssignSpaceHits_" + position + "_2");
    }

    @Test
    void maneuveringJetsCancelsOneSpaceCannonHit() {
        evenSpaceFight();
        table.nekro.setActionCard("mjets3", HAND_INDEX);
        cancelHits(cannonHits(2));
        played("Maneuvering Jets");
        AiPrompt announce = announcement("Maneuvering Jets");
        ActionCardRules.continuePlay(table.test.contextAt(NOW + 1_000L, announce, cannonHits(2)));

        assertThat(pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, cannonHits(2)))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_cancelPdsOffenseHits_" + position + "_2");
        assertThat(stage()).contains(Stage.DONE);
    }

    @Test
    void theFourCardsHaveRules() {
        for (String alias : List.of("sh1", "sh4", "mjets1", "mjets4", "war_machine1", "war_machine4")) {
            assertThat(AiActionCards.usable(alias)).as(alias).isTrue();
        }
        assertThat(AiActionCards.targetPrefixes()).contains("cancelSpaceHits_", "cancelPdsOffenseHits_");
    }
}
