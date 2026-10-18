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
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.secrets.SecretDrawValue;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class SecretCardTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
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
        return AiActionCards.of("impersonation").orElseThrow().planFor(table.game, table.nekro);
    }

    private void holdsInfluence() {
        table.system("37", "202");
        table.nekro.addPlanet("meer");
    }

    @Test
    void impersonationIsWorthASecretDrawLessTheInfluenceItCostsPlusTheStall() {
        holdsInfluence();

        Plan plan = plan().orElseThrow();

        assertThat(plan.value()).isGreaterThan(table.stall());
        assertThat(plan.value()).isLessThan(SecretDrawValue.of(table.game, table.nekro) + table.stall());
    }

    @Test
    void impersonationNeedsThreeInfluenceToPay() {
        assertThat(plan()).isEmpty();
    }

    @Test
    void impersonationIsPlayedForASecretWhileThereIsTimeToScoreIt() {
        holdsInfluence();
        table.nekro.setActionCard("impersonation", HAND_INDEX);

        assertThat(AiTestGame.pressedId(ActionCardRules.playBeforePassing(table.test.context(
                                prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX)))
                        .orElseThrow()))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // Two secrets scored and the third already scorable: a draw would only replace a secret worth keeping.
    @Test
    void impersonationIsHeldWhenTheHandIsFullOfSecretsWorthKeeping() {
        holdsInfluence();
        table.nekro.setSecretScored("dhw");
        table.nekro.setSecretScored("gamf");
        table.game.addLaw("conventions", null);
        table.game.addLaw("minister_war", "sol");
        table.game.addLaw("shard_of_the_throne", "sol");
        table.nekro.setSecret("dp");
        table.nekro.setActionCard("impersonation", HAND_INDEX);

        assertThat(ActionCardRules.playBeforePassing(table.test.context(
                        prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX))))
                .isEmpty();
    }

    @Test
    void impersonationExpectsTheThreeInfluenceTheMomentItIsResolved() {
        holdsInfluence();
        table.nekro.setActionCard("impersonation", HAND_INDEX);
        ActionCardRules.playBeforePassing(
                table.test.context(prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX)));
        table.nekro.getActionCards().remove("impersonation");
        table.game.getGameStats().recordAcPlay("Impersonation", table.nekro);
        var announcement = prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_Impersonation",
                "no_sabotage",
                "sabotage_ac_Impersonation_nekro");
        var resolve = prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_resolveImpersonation");
        ActionCardRules.continuePlay(table.test.context(announcement));
        ActionCardRules.continuePlay(table.test.context(announcement, resolve));

        assertThat(AiTestGame.pressedId(
                        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announcement, resolve))
                                .orElseThrow()))
                .isEqualTo("FFCC_nekro_resolveImpersonation");
        assertThat(PaymentRules.isPending(table.test.contextAt(SETTLED))).isTrue();
        String key = "acPlay|turn:" + table.test.context().turnKey();
        assertThat(table.test.memory.get(key).flatMap(CardPlay::decode).map(CardPlay::stage))
                .contains(Stage.DONE);
    }
}
