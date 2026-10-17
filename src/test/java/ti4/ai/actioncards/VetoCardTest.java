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
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class VetoCardTest extends BaseTi4Test {

    private static final String THIRD_ID = "200000000000000002";
    private static final long SETTLED = NOW + 11_000L;
    private static final String VETO = "veto";

    private final NekroBrain brain = new NekroBrain();
    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player xxcha;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.game.setSpeakerUserID(xxcha.getUserID());
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":9,\"red\":4,\"black\":0}");
        test.game.setStoredValue("lastAgendaReactTime", String.valueOf(NOW - 60_000L));
        givePoints(sol, 7);
        givePoints(xxcha, 1);
        nekro.setActionCard(VETO, 9);
        messages = Mockito.mockStatic(GameMessageManager.class);
        reacted("nekro");
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private void givePoints(Player player, int points) {
        test.game.scorePublicObjective(
                player.getUserID(), test.game.addCustomPO("Test points " + player.getFaction(), points));
    }

    private void reacted(String... factions) {
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1), GameMessageType.ACTION_CARD, new LinkedHashSet<>(List.of(factions)), 0L)));
    }

    private static AiPrompt hidden(String id, String... buttons) {
        return prompt(id, PromptSource.AI_THREAD, NOW, buttons);
    }

    private AiTurnContext context(long now, AiPrompt... prompts) {
        return test.contextFor(nekro, Set.of(), now, prompts);
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return context(NOW, prompts);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private static AiPrompt whenOffer() {
        return hidden("offer", "queueAWhen", "declineToQueueAWhen", "explainQueue");
    }

    // ---- queueing ------------------------------------------------------------------------------------------------

    // The rule is Political Favor's: an election that costs the seat five units or more is discarded.
    @Test
    void queuesAVetoForAnElectionItWouldLoseBy() {
        assertThat(pressed(AgendaCardRules.queueVeto(context(whenOffer())))).isEqualTo("queueAWhen");

        AiPrompt options = hidden("options", "queueWhen_ac_veto", "declineToQueueAWhen");
        assertThat(pressed(AgendaCardRules.queueVeto(context(options)))).isEqualTo("queueWhen_ac_veto");
    }

    @Test
    void keepsTheVetoForAnElectionThatDoesNotHurt() {
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":0,\"red\":4,\"black\":9}");

        assertThat(AgendaCardRules.queueVeto(context(whenOffer()))).isEmpty();
    }

    @Test
    void doesNotQueueAVetoOverAQueuedEntry() {
        test.game.setStoredValue("queuedWhens", "nekro_");
        test.game.setStoredValue("queuedWhensFornekro", "pn_blue_ps");

        assertThat(AgendaCardRules.queueVeto(context(whenOffer()))).isEmpty();
    }

    @Test
    void doesNotQueueAVetoOnceTheWhensAreResolved() {
        test.game.setStoredValue("whensResolved", "Yes");

        assertThat(AgendaCardRules.queueVeto(context(whenOffer()))).isEmpty();
    }

    @Test
    void aSeatOnTheEarlierRulesQueuesNoVeto() {
        ti4.ai.AiSettings.markLegacyActionCards(nekro);

        assertThat(AgendaCardRules.queueVeto(context(whenOffer()))).isEmpty();
    }

    @Test
    void doesNotQueueAVetoWithoutOne() {
        nekro.getActionCards().clear();

        assertThat(AgendaCardRules.queueVeto(context(whenOffer()))).isEmpty();
    }

    @Test
    void theBrainQueuesTheVetoBeforePassingOnWhens() {
        assertThat(AiTestGame.pressedId(brain.decide(context(whenOffer())))).isEqualTo("queueAWhen");
    }

    // Political Favor discards the agenda too, and keeps the card, so when both could be played the note goes first.
    @Test
    void leavesTheElectionToAPoliticalFavorWhenItHoldsOne() {
        xxcha.addOwnedPromissoryNoteByID("favor");
        nekro.setPromissoryNote("favor");
        xxcha.setStrategicCC(1);
        AiPrompt options = hidden("options", "queueWhen_pn_favor", "queueWhen_ac_veto", "declineToQueueAWhen");

        assertThat(AiTestGame.pressedId(brain.decide(context(options)))).isEqualTo("queueWhen_pn_favor");
    }

    // The queued Veto stays queued: its "Pass On Whens" button is left alone.
    @Test
    void doesNotPassOnWhensWhileTheVetoIsQueued() {
        test.game.setStoredValue("queuedWhens", "nekro_");
        test.game.setStoredValue("queuedWhensFornekro", "ac_veto");

        assertThat(brain.decide(context(hidden("queued", "declineToQueueAWhen"))))
                .isInstanceOf(AiDecision.Idle.class);
    }

    // The queue has played the Veto and stopped: the Veto's player must not pass on "when"s (which would set the queue
    // going again) until the Veto is resolved.
    @Test
    void doesNotPassOnWhensWhileThePlayedVetoIsUnresolvedAndPassesOnceItIsCancelled() {
        test.game.setStoredValue("queuedWhensFornekro", "ac_veto");
        vetoPlayed();
        AiPrompt passOn = hidden("queued", "declineToQueueAWhen");

        assertThat(brain.decide(context(passOn, announcement(), resolveButton())))
                .isInstanceOf(AiDecision.Idle.class);

        test.game.getGameStats().markLatestPlayCanceled("Veto");
        assertThat(AiTestGame.pressedId(brain.decide(context(passOn)))).isEqualTo("declineToQueueAWhen");
    }

    // A card the bot refused (the seat is over the limit) never posts its buttons; the seat gives up after five minutes
    // and passing on "when"s then sets the stuck queue going again.
    @Test
    void stopsHoldingAVetoThatNeverShowsItsButtons() {
        test.game.setStoredValue("queuedWhensFornekro", "ac_veto");
        vetoPlayed();
        AiPrompt passOn = hidden("queued", "declineToQueueAWhen");

        assertThat(brain.decide(context(passOn))).isInstanceOf(AiDecision.Idle.class);
        assertThat(AiTestGame.pressedId(brain.decide(context(NOW + 6 * 60_000L, passOn))))
                .isEqualTo("declineToQueueAWhen");
    }

    // Every seat holds its own Veto: another seat's declining sets the halted queue going, and the next queued Veto is
    // played at once, so the seats whose Veto is not the last played still have one to resolve.
    @Test
    void holdsAVetoThatIsNotTheLastOneTheQueuePlayed() {
        test.game.setStoredValue("lastPlayerToPlayAWhen", "sol");
        test.game.setStoredValue("queuedWhensFornekro", "ac_veto");
        vetoPlayed();

        assertThat(AgendaCardRules.holdsPlayedVeto(context(hidden("queued", "declineToQueueAWhen"))))
                .isTrue();
    }

    // Waiting on its own Veto must not keep the seat from answering the Sabotage window of another seat's Veto: those
    // windows wait for every seat, and seats that played a Veto each at once would otherwise wait for one another.
    @Test
    void stillAnswersAnotherSeatsSabotageWindowWhileItsOwnVetoIsPlayed() {
        test.game.setStoredValue("queuedWhensFornekro", "ac_veto");
        vetoPlayed();
        reacted();
        AiPrompt theirs = prompt("their-window", PromptSource.PUBLIC, NOW - 3_600_000L, "no_sabotage");

        assertThat(AiTestGame.pressedId(brain.decide(context(theirs)))).isEqualTo("no_sabotage");
    }

    // A Veto still waiting in the queue, or one the seat passed on, is not a played Veto.
    @Test
    void holdsNothingForAVetoThatIsQueuedOrWasPassedOn() {
        test.game.setStoredValue("queuedWhensFornekro", "ac_veto");
        test.game.setStoredValue("queuedWhens", "nekro_");
        assertThat(AgendaCardRules.holdsPlayedVeto(context())).isFalse();

        test.game.setStoredValue("queuedWhens", "");
        test.game.setStoredValue("declinedWhens", "nekro_");
        assertThat(AgendaCardRules.holdsPlayedVeto(context())).isFalse();
    }

    @Test
    void theVetoIsWorthItsKeepPriorInTheHand() {
        assertThat(AiActionCards.usable(VETO)).isTrue();
        assertThat(ActionCardValue.keepValue(test.game, nekro, VETO)).isEqualTo(ActionCardPrior.keepPrior(VETO));
    }

    // ---- resolving -----------------------------------------------------------------------------------------------

    private void vetoPlayed() {
        nekro.getActionCards().clear();
        test.game.getGameStats().recordAcPlay("Veto", nekro);
    }

    private static AiPrompt announcement() {
        return prompt(
                "announce-Veto",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_Veto",
                "no_sabotage",
                "sabotage_ac_Veto_nekro");
    }

    private static AiPrompt resolveButton() {
        return prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_resolveVeto");
    }

    // The queue plays the Veto and the bot posts the card's buttons. The AI waits out the Sabotage window like it does
    // for any card, then reveals the next agenda.
    @Test
    void revealsTheNextAgendaOnceTheSabotageWindowCloses() {
        vetoPlayed();

        assertThat(ActionCardRules.continuePlay(context(announcement(), resolveButton())))
                .isEmpty();

        reacted("nekro", "sol", "xxcha");
        assertThat(ActionCardRules.continuePlay(context(announcement(), resolveButton())))
                .isEmpty();
        assertThat(pressed(ActionCardRules.continuePlay(context(SETTLED, announcement(), resolveButton()))))
                .isEqualTo("FFCC_nekro_resolveVeto");
    }

    // A sabotaged Veto reveals nothing.
    @Test
    void neverRevealsAfterASabotagedVeto() {
        vetoPlayed();
        ActionCardRules.continuePlay(context(announcement(), resolveButton()));
        reacted("nekro", "sol", "xxcha");
        test.game.getGameStats().markLatestPlayCanceled("Veto");

        assertThat(ActionCardRules.continuePlay(context(SETTLED, announcement(), resolveButton())))
                .isEmpty();
    }

    // The bot refuses to reveal an agenda within six seconds of the last reveal, and has already counted the Veto
    // against the agendas of the phase by then.
    @Test
    void waitsSixSecondsAfterTheRevealBeforePressing() {
        test.game.setStoredValue("lastAgendaReactTime", String.valueOf(SETTLED - 3_000L));
        vetoPlayed();
        reacted("nekro", "sol", "xxcha");
        ActionCardRules.continuePlay(context(announcement(), resolveButton()));

        assertThat(ActionCardRules.continuePlay(context(SETTLED, announcement(), resolveButton())))
                .isEmpty();
        assertThat(pressed(ActionCardRules.continuePlay(context(SETTLED + 5_000L, announcement(), resolveButton()))))
                .isEqualTo("FFCC_nekro_resolveVeto");
    }

    @Test
    void ignoresAVetoButtonFromAnEarlierAgenda() {
        vetoPlayed();
        reacted("nekro", "sol", "xxcha");
        AiPrompt stale = prompt("stale", PromptSource.PUBLIC, NOW - 3_600_000L, "FFCC_nekro_resolveVeto");

        assertThat(ActionCardRules.continuePlay(context(SETTLED, stale))).isEmpty();
    }
}
