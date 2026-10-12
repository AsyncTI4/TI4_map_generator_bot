package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
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
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class MasterPlanCardTest extends BaseTi4Test {

    private static final long LATER = NOW + 60_000L;
    private static final int HAND_INDEX = 412;
    private static final String FIRST_TARGET = "105";

    private final NekroBrain brain = new NekroBrain();
    private AiTestGame test;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.game.setVp(10);
        test.game.setRound(3);
        test.aiIsActive("action");
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
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
    }

    // Sol's lone infantry on Lodor is an attack to make now, so a second action is worth it.
    private void anUrgentTargetNextDoor() {
        Tile lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
    }

    private void theFirstActionIsDone() {
        test.game.setActiveSystem(FIRST_TARGET);
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + FIRST_TARGET + ".");
        test.game.setStoredValue("gameEventTacticalLogged", "yes");
    }

    private static final Set<String> FIRST_ACTION_PRESSED = Set.of("first|FFCC_nekro_tacticalAction");

    private AiTurnContext afterTheFirstAction(long at) {
        AiPrompt first = prompt("first", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction");
        AiPrompt after = prompt(
                "after",
                PromptSource.PUBLIC,
                NOW + 10,
                "FFCC_nekro_endOfTurnAbilities",
                "FFCC_nekro_confirmSecondAction");
        AiPrompt hand = prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
        return test.contextFor(test.nekro, FIRST_ACTION_PRESSED, at, first, after, hand);
    }

    private String turnKey() {
        return "acPlay|plan:" + test.context().turnKey();
    }

    @Test
    void playsMasterPlanAfterTheFirstActionWhenASecondOneIsUrgent() {
        anUrgentTargetNextDoor();
        theFirstActionIsDone();
        test.nekro.setActionCard("master_plan", HAND_INDEX);

        assertThat(pressedId(brain.decide(afterTheFirstAction(LATER)))).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void holdsMasterPlanWhenNothingCannotWait() {
        theFirstActionIsDone();
        test.nekro.setActionCard("master_plan", HAND_INDEX);

        AiDecision decision = brain.decide(afterTheFirstAction(LATER));

        assertThat(decision).isInstanceOf(AiDecision.Press.class);
        assertThat(pressedId(decision)).isEqualTo("FFCC_nekro_endOfTurnAbilities");
    }

    @Test
    void playsNoMasterPlanBeforeTheFirstActionIsDone() {
        anUrgentTargetNextDoor();
        test.nekro.setActionCard("master_plan", HAND_INDEX);
        AiPrompt hand = prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction");

        assertThat(pressedId(brain.decide(test.context(turn, hand)))).isEqualTo("FFCC_nekro_tacticalAction");
    }

    @Test
    void aSeatWithFleetLogisticsPlaysMasterPlanFirst() {
        test.nekro.addTech("fl");
        anUrgentTargetNextDoor();
        theFirstActionIsDone();
        test.nekro.setActionCard("master_plan", HAND_INDEX);

        assertThat(pressedId(brain.decide(afterTheFirstAction(LATER)))).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // Once the Sabotage window has closed on the card, the extra action is taken the way Fleet Logistics takes one.
    @Test
    void takesTheExtraActionOnceTheWindowHasClosed() {
        anUrgentTargetNextDoor();
        theFirstActionIsDone();
        test.nekro.setActionCard("master_plan", HAND_INDEX);
        brain.decide(afterTheFirstAction(LATER));
        test.nekro.getActionCards().remove("master_plan");
        test.game.getGameStats().recordAcPlay("Master Plan", test.nekro);
        AiPrompt announcement = prompt(
                "announce",
                PromptSource.PUBLIC,
                LATER + 1_000L,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_Master Plan",
                "no_sabotage",
                "sabotage_ac_Master Plan_nekro");

        assertThat(TacticalRules.mayTakeAnotherAction(test.contextAt(LATER))).isFalse();
        for (int step = 1; step <= 4; step++) {
            ActionCardRules.continuePlay(test.contextAt(LATER + step * 20_000L, announcement));
        }

        assertThat(test.memory.get(turnKey()).flatMap(CardPlay::decode).map(CardPlay::stage))
                .contains(Stage.DONE);
        assertThat(TacticalRules.mayTakeAnotherAction(test.contextAt(LATER))).isTrue();
        assertThat(pressedId(brain.decide(afterTheFirstAction(LATER + 120_000L))))
                .isEqualTo("FFCC_nekro_confirmSecondAction");
    }

    @Test
    void aCanceledMasterPlanGivesNoExtraAction() {
        anUrgentTargetNextDoor();
        theFirstActionIsDone();
        test.nekro.setActionCard("master_plan", HAND_INDEX);
        brain.decide(afterTheFirstAction(LATER));
        test.nekro.getActionCards().remove("master_plan");
        test.game.getGameStats().recordAcPlay("Master Plan", test.nekro);
        test.game.getGameStats().markLatestPlayCanceled("Master Plan");

        for (int step = 1; step <= 4; step++) {
            ActionCardRules.continuePlay(test.contextAt(LATER + step * 20_000L));
        }

        assertThat(TacticalRules.mayTakeAnotherAction(test.contextAt(LATER))).isFalse();
        assertThat(test.memory.get(turnKey()).flatMap(CardPlay::decode).map(CardPlay::stage))
                .contains(Stage.CANCELED);
    }
}
