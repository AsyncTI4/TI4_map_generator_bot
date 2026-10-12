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
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.SecondaryQueue;
import ti4.ai.strategy.StrategyCardRules;
import ti4.helpers.Helper;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class OverruleCardTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.removeAbility("propagation");
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
        return AiActionCards.of("overrule").orElseThrow().planFor(table.game, table.nekro);
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
    void overruleIsPlayedOnImperialWhileItIsStillToBePlayed() {
        Plan plan = plan().orElseThrow();

        assertThat(plan.target()).isEqualTo(initiativeOf(StrategyCard.IMPERIAL) + "_overrule");
        assertThat(plan.value()).isGreaterThan(ActionCardPrior.keepPrior("overrule"));
    }

    @Test
    void overruleCannotUseACardThatHasBeenPlayed() {
        table.game.setSCPlayed(initiativeOf(StrategyCard.IMPERIAL), true);

        assertThat(plan().map(Plan::target))
                .isNotEqualTo(Optional.of(initiativeOf(StrategyCard.IMPERIAL) + "_overrule"));
    }

    // Only Imperial and Technology are performed: their steps are the ones the bot can run for a card nobody played.
    @Test
    void overruleLeavesTheOtherCardsAlone() {
        onlyThese(StrategyCard.LEADERSHIP, StrategyCard.POLITICS, StrategyCard.WARFARE, StrategyCard.TRADE);

        assertThat(plan()).isEmpty();
    }

    @Test
    void overrulePrefersTechnologyWhenItIsTheOnlyCardWorthDoing() {
        onlyThese(StrategyCard.TECHNOLOGY, StrategyCard.LEADERSHIP);

        assertThat(plan().orElseThrow().target()).isEqualTo(initiativeOf(StrategyCard.TECHNOLOGY) + "_overrule");
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
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_Overrule",
                "no_sabotage",
                "sabotage_ac_Overrule_nekro");
    }

    private AiPrompt resolve() {
        return prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_overruleStart");
    }

    private String turnKey() {
        return "acPlay|turn:" + table.test.context().turnKey();
    }

    private AiPrompt cardChoices(int... initiatives) {
        String[] ids = java.util.Arrays.stream(initiatives)
                .mapToObj(initiative -> "winnuHero_" + initiative + "_overrule")
                .toArray(String[]::new);
        return prompt("choices", PromptSource.PUBLIC, SETTLED, ids);
    }

    private void playsAndResolves() {
        table.nekro.setActionCard("overrule", HAND_INDEX);
        assertThat(AiTestGame.pressedId(ActionCardRules.playBeforePassing(table.test.context(hand()))
                        .orElseThrow()))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        table.nekro.getActionCards().remove("overrule");
        table.game.getGameStats().recordAcPlay("Overrule", table.nekro);
        ActionCardRules.continuePlay(table.test.context(announcement()));
        ActionCardRules.continuePlay(table.test.context(announcement(), resolve()));
        assertThat(AiTestGame.pressedId(
                        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announcement(), resolve()))
                                .orElseThrow()))
                .isEqualTo("FFCC_nekro_overruleStart");
    }

    @Test
    void overrulePicksTheCardItPlannedAndStartsItsPrimary() {
        onlyThese(StrategyCard.TECHNOLOGY, StrategyCard.LEADERSHIP);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        int leadership = initiativeOf(StrategyCard.LEADERSHIP);

        playsAndResolves();
        Optional<AiDecision> pick =
                ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(leadership, technology)));

        assertThat(AiTestGame.pressedId(pick.orElseThrow())).isEqualTo("winnuHero_" + technology + "_overrule");
        assertThat(table.test.memory.get(turnKey()).flatMap(CardPlay::decode).map(CardPlay::stage))
                .contains(Stage.DONE);
        assertThat(StrategyCardRules.playedThisTurn(table.test.contextAt(SETTLED)))
                .isTrue();
    }

    @Test
    void overruleNeverPicksACardItDoesNotPerform() {
        onlyThese(StrategyCard.TECHNOLOGY, StrategyCard.LEADERSHIP);
        int leadership = initiativeOf(StrategyCard.LEADERSHIP);

        playsAndResolves();

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(leadership))))
                .isEmpty();
    }

    // Nobody follows an overruled card, so the seat must not wait for the secondaries of a card another player holds.
    @Test
    void anOverruledPrimaryIsNotAwaitedByAnySecondary() {
        onlyThese(StrategyCard.TECHNOLOGY);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        table.sol.addSC(technology);
        playsAndResolves();
        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(technology)));

        assertThat(StrategyCardRules.awaitingSecondaries(table.test.contextAt(SETTLED)))
                .isFalse();
        assertThat(SecondaryQueue.blockerFor(table.game, table.sol, technology, SETTLED))
                .isEmpty();
    }

    // The card's message names the card as overruled and has no "no follow" button; its primary buttons are the
    // seat's own. The seat still runs the primary from it.
    @Test
    void theOverruledCardsPrimaryIsRunFromItsOwnedButtons() {
        onlyThese(StrategyCard.TECHNOLOGY);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        playsAndResolves();
        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(technology)));
        AiPrompt message = AiTestGame.withContent(
                prompt("overruled", PromptSource.PUBLIC, SETTLED, "FFCC_nekro_acquireATechWithSC_first"),
                "**" + Helper.getSCName(technology, table.game) + "** overruled by " + table.nekro.getRepresentation()
                        + ".");

        Optional<AiDecision> primary = StrategyCardRules.resolvePrimary(table.test.contextAt(SETTLED, message));

        assertThat(AiTestGame.pressedId(primary.orElseThrow())).isEqualTo("FFCC_nekro_acquireATechWithSC_first");
    }

    @Test
    void overruleIsPickedUpAgainAfterTheMemoryIsLost() {
        onlyThese(StrategyCard.TECHNOLOGY);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        table.game.getGameStats().recordAcPlay("Overrule", table.nekro);

        Optional<AiDecision> pick =
                ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(technology)));

        assertThat(AiTestGame.pressedId(pick.orElseThrow())).isEqualTo("winnuHero_" + technology + "_overrule");
    }
}
