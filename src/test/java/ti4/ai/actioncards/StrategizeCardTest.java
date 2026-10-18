package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.strategy.StrategyCardRules;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class StrategizeCardTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;
    private static final double EXACT = 1e-9;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.setStrategicCC(2);
        table.nekro.setTacticalCC(3);
        table.nekro.setFleetCC(3);
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

    private Optional<Plan> plan() {
        return AiActionCards.of("strategize2").orElseThrow().planFor(table.game, table.nekro);
    }

    private int initiativeOf(StrategyCard card) {
        for (int initiative : table.game.getSCList()) {
            if (StrategyCard.of(table.game, initiative) == card) return initiative;
        }
        throw new AssertionError(card + " is not in this game");
    }

    private void onlyThese(StrategyCard... cards) {
        List<StrategyCard> kept = List.of(cards);
        for (int initiative : table.game.getSCList()) {
            if (!kept.contains(StrategyCard.of(table.game, initiative))) table.game.setSCPlayed(initiative, true);
        }
    }

    @Test
    void everyCopyOfStrategizeIsTheSameCard() {
        assertThat(AiActionCards.of("strategize1")).isEqualTo(AiActionCards.of("strategize4"));
    }

    @Test
    void strategizeFollowsImperialForTheSecretWhenThatIsTheBestSecondary() {
        Plan plan = plan().orElseThrow();

        assertThat(plan.target()).isEqualTo("IMPERIAL");
        assertThat(plan.value())
                .isCloseTo(SecretDrawValue.of(table.game, table.nekro) - 1.0 + table.stall(), within(EXACT));
    }

    // Another player will play their Imperial this round, and following it draws the same secret for the same token,
    // so Strategize is kept for a secondary the seat could not get otherwise.
    @Test
    void strategizeSkipsACardAnotherPlayerWillPlay() {
        table.sol.addSC(initiativeOf(StrategyCard.IMPERIAL));

        Optional<Plan> plan = plan();

        assertThat(plan.map(Plan::target)).isNotEqualTo(Optional.of("IMPERIAL"));
    }

    @Test
    void strategizeCanUseItsOwnCardBeforePlayingIt() {
        table.nekro.addSC(initiativeOf(StrategyCard.IMPERIAL));

        assertThat(plan().map(Plan::target)).contains("IMPERIAL");
    }

    @Test
    void strategizeCannotUseACardThatHasBeenPlayed() {
        table.game.setSCPlayed(initiativeOf(StrategyCard.IMPERIAL), true);

        Optional<Plan> plan = plan();

        assertThat(plan.map(Plan::target)).isNotEqualTo(Optional.of("IMPERIAL"));
    }

    @Test
    void strategizeNeedsAStrategyToken() {
        table.nekro.setStrategicCC(0);

        assertThat(plan()).isEmpty();
    }

    @Test
    void strategizeSkipsLeadershipAndTechnology() {
        onlyThese(StrategyCard.LEADERSHIP, StrategyCard.TECHNOLOGY);

        assertThat(plan()).isEmpty();
    }

    // Two secrets scored and the third already scorable: a draw would only replace a secret worth keeping.
    @Test
    void strategizeValuesNoSecretWhenTheFullHandIsWorthKeeping() {
        table.nekro.setSecretScored("dhw");
        table.nekro.setSecretScored("gamf");
        table.game.addLaw("conventions", null);
        table.game.addLaw("minister_war", "sol");
        table.game.addLaw("shard_of_the_throne", "sol");
        table.nekro.setSecret("dp");
        onlyThese(StrategyCard.IMPERIAL);

        assertThat(plan()).isEmpty();
    }

    // ---- the flow ----------------------------------------------------------------------------

    private AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
    }

    private AiPrompt announcement() {
        return prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_Strategize",
                "no_sabotage",
                "sabotage_ac_Strategize_nekro");
    }

    private AiPrompt resolve() {
        return prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_strategize");
    }

    private String turnKey() {
        return "acPlay|turn:" + table.test.context().turnKey();
    }

    // The bot offers every readied card's secondary; the AI takes the one it planned, and leaves Technology and
    // Leadership alone.
    @Test
    void strategizePressesTheSecondaryItPlannedAndNoneOfTheOthers() {
        table.nekro.setActionCard("strategize3", HAND_INDEX);
        assertThat(AiTestGame.pressedId(ActionCardRules.playBeforePassing(table.test.context(hand()))
                        .orElseThrow()))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        table.nekro.getActionCards().remove("strategize3");
        table.game.getGameStats().recordAcPlay("Strategize", table.nekro);
        ActionCardRules.continuePlay(table.test.context(announcement()));
        ActionCardRules.continuePlay(table.test.context(announcement(), resolve()));
        assertThat(AiTestGame.pressedId(
                        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announcement(), resolve()))
                                .orElseThrow()))
                .isEqualTo("FFCC_nekro_strategize");

        AiPrompt secondaries = AiTestGame.withContent(
                prompt(
                        "secondaries",
                        PromptSource.PUBLIC,
                        SETTLED,
                        "leadershipGenerateCCButtons",
                        "acquireATech",
                        "diploRefresh2",
                        "non_sc_draw_so",
                        "draw2 AC"),
                table.nekro.getRepresentationUnfogged() + ", please resolve Strategize using these buttons.");
        Optional<AiDecision> decision = ActionCardRules.continuePlay(table.test.contextAt(SETTLED, secondaries));

        assertThat(AiTestGame.pressedId(decision.orElseThrow())).isEqualTo("non_sc_draw_so");
        assertThat(table.test.memory.get(turnKey()).flatMap(CardPlay::decode).map(CardPlay::stage))
                .contains(Stage.DONE);
    }

    @Test
    void theSpareTokenTestKeepsATokenForABetterFollow() {
        table.nekro.setStrategicCC(1);
        table.test.isActive(table.sol, "action");
        table.sol.addSC(initiativeOf(StrategyCard.IMPERIAL));

        assertThat(StrategyCardRules.hasSpareStrategyToken(table.game, table.nekro, StrategyCard.POLITICS))
                .isFalse();
        assertThat(StrategyCardRules.hasSpareStrategyToken(table.game, table.nekro, StrategyCard.IMPERIAL))
                .isTrue();
    }
}
