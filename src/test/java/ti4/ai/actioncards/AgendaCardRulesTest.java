package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AgendaCardRulesTest extends BaseTi4Test {

    private static final String THIRD_ID = "200000000000000002";
    private static final String ARMS_REDUCTION = "Directive_For/Against_7_arms_reduction";
    private static final String SHARD = "Law_Elect Player_7_shard_of_the_throne";
    private static final String IMPERIAL = "imp_rider";

    private final NekroBrain brain = new NekroBrain();
    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setSpeakerUserID(xxcha.getUserID());
        agenda(ARMS_REDUCTION, 0, 3, 0);
        test.game.setStoredValue("whensResolved", "Yes");
    }

    private void agenda(String info, int nekroVotes, int solVotes, int xxchaVotes) {
        test.game.setCurrentAgendaInfo(info);
        test.game.setStoredValue(
                "agendaStartVoteCounts",
                "{\"black\":" + nekroVotes + ",\"blue\":" + solVotes + ",\"red\":" + xxchaVotes + "}");
    }

    private static AiPrompt hidden(String id, String... buttons) {
        return prompt(id, PromptSource.AI_THREAD, NOW, buttons);
    }

    private static AiPrompt riderPrompt(String faction, String title) {
        return prompt(
                "riders-" + title,
                PromptSource.PUBLIC,
                NOW,
                "FFCC_" + faction + "_rider_fa;for_" + title,
                "FFCC_" + faction + "_rider_fa;against_" + title);
    }

    private AiTurnContext context(Player seat, long now, AiPrompt... prompts) {
        return test.contextFor(seat, Set.of(), now, prompts);
    }

    private AiTurnContext context(Player seat, AiPrompt... prompts) {
        return context(seat, NOW, prompts);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private void holds(Player seat, String alias) {
        seat.setActionCard(alias, 7);
    }

    private void played(Player seat, String title) {
        seat.getActionCards().clear();
        test.game.getGameStats().recordAcPlay(title, seat);
    }

    // ---- queueing ----------------------------------------------------------------------------------------------

    // A seat that casts no votes loses nothing by riding. Sol, the only voter, has no view on the directive, but a
    // rider on one side leans it a little the other way, and a two in five chance of a point is worth the card.
    @Test
    void aSeatThatCannotVoteQueuesAnImperialRiderOnAnAgendaItCanCallEvenly() {
        holds(nekro, IMPERIAL);
        AiPrompt window = hidden("after", "queueAnAfter", "declineToQueueAnAfter");

        assertThat(pressed(AgendaCardRules.queueRider(context(nekro, window)))).isEqualTo("queueAnAfter");

        AiPrompt options = hidden("options", "queueAfter_ac_imp_rider", "declineToQueueAnAfter");
        assertThat(pressed(AgendaCardRules.queueRider(context(nekro, options)))).isEqualTo("queueAfter_ac_imp_rider");
    }

    @Test
    void thePressingOfAfterOptionsWinsOverDecliningInTheBrain() {
        holds(nekro, IMPERIAL);
        AiPrompt window = hidden("after", "queueAnAfter", "declineToQueueAnAfter");

        assertThat(AiTestGame.pressedId(brain.decide(context(nekro, window)))).isEqualTo("queueAnAfter");
    }

    @Test
    void passesOnAftersWithoutARider() {
        AiPrompt window = hidden("after", "queueAnAfter", "declineToQueueAnAfter");

        assertThat(AiTestGame.pressedId(brain.decide(context(sol, window)))).isEqualTo("declineToQueueAnAfter");
    }

    // The seat's own votes decide an election it wants: sitting out the vote for a rider costs more than the rider can
    // bring, so Sol keeps the card while Nekro, which cannot vote, plays it.
    @Test
    void aRiderIsHeldBackWhenTheSeatsVotesDecideAnElectionItWants() {
        test.place("01", "304");
        sol.addPlanet("jord");
        agenda(SHARD, 0, 2, 1);
        holds(sol, IMPERIAL);
        holds(nekro, IMPERIAL);
        AiPrompt window = hidden("after", "queueAnAfter", "declineToQueueAnAfter");

        assertThat(AgendaCardRules.bestRider(context(sol, window))).isEmpty();
        assertThat(AgendaCardRules.bestRider(context(nekro, window))).isPresent();
    }

    // The Galactic Threat prediction already queued is not overwritten by a rider.
    @Test
    void doesNotQueueARiderOverAQueuedEntry() {
        holds(nekro, IMPERIAL);
        test.game.setStoredValue("queuedAfters", "nekro_");
        test.game.setStoredValue("queuedAftersFornekro", "ability_galactic_threat");
        AiPrompt window = hidden("after", "queueAnAfter", "declineToQueueAnAfter");

        assertThat(AgendaCardRules.queueRider(context(nekro, window))).isEmpty();
    }

    @Test
    void queuesOnlyOneRiderForEachAgenda() {
        holds(nekro, IMPERIAL);
        AiPrompt options = hidden("options", "queueAfter_ac_imp_rider", "declineToQueueAnAfter");
        assertThat(AgendaCardRules.queueRider(context(nekro, options))).isPresent();

        nekro.getActionCards().clear();
        holds(nekro, "trade_rider");
        assertThat(AgendaCardRules.queueRider(context(nekro, options))).isEmpty();
    }

    // A seat on the rules from before the statistics (the A/B flag) plays none of the agenda cards.
    @Test
    void aSeatOnTheEarlierRulesQueuesNoRider() {
        holds(nekro, IMPERIAL);
        ti4.ai.AiSettings.markLegacyActionCards(nekro);
        AiPrompt window = hidden("after", "queueAnAfter", "declineToQueueAnAfter");

        assertThat(AgendaCardRules.queueRider(context(nekro, window))).isEmpty();
    }

    @Test
    void doesNotQueueARiderOutsideTheAfterWindow() {
        holds(nekro, IMPERIAL);
        test.game.setPhaseOfGame("agendaVoting");
        AiPrompt window = hidden("after", "queueAnAfter", "declineToQueueAnAfter");

        assertThat(AgendaCardRules.queueRider(context(nekro, window))).isEmpty();
    }

    @Test
    void doesNotQueueARiderForAnAgendaItCannotForecast() {
        holds(nekro, IMPERIAL);
        test.game.setCurrentAgendaInfo("Directive_Elect Strategy Card_7_crisis");
        AiPrompt window = hidden("after", "queueAnAfter", "declineToQueueAnAfter");

        assertThat(AgendaCardRules.queueRider(context(nekro, window))).isEmpty();
    }

    // ---- locking in and placing ----------------------------------------------------------------------------------

    @Test
    void locksInAQueuedRider() {
        test.game.setStoredValue("queuedAfters", "nekro_");
        test.game.setStoredValue("queuedAftersFornekro", "ac_imp_rider");

        assertThat(pressed(AgendaCardRules.riders(context(nekro, hidden("lock", "lockAftersIn")))))
                .isEqualTo("lockAftersIn");
    }

    // The bot posts the outcome buttons as soon as the card is played, and the AI places the rider on the spot, before
    // the Sabotage window closes.
    @Test
    void placesAPlayedRiderOnTheOutcomeThatPaysBest() {
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        played(nekro, "Imperial Rider");

        String placement = pressed(AgendaCardRules.riders(context(nekro, riderPrompt("nekro", "Imperial Rider"))));

        assertThat(placement).startsWith("FFCC_nekro_rider_fa;");
        assertThat(placement).endsWith("_Imperial Rider");
    }

    @Test
    void placesARiderOnPlayerOutcomes() {
        agenda(SHARD, 0, 9, 4);
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        played(nekro, "Imperial Rider");
        AiPrompt outcomes = prompt(
                "outcomes",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_rider_player;sol_Imperial Rider",
                "FFCC_nekro_rider_player;xxcha_Imperial Rider",
                "FFCC_nekro_rider_player;nekro_Imperial Rider");

        assertThat(pressed(AgendaCardRules.riders(context(nekro, outcomes)))).startsWith("FFCC_nekro_rider_player;");
    }

    @Test
    void placesNothingOnceTheRiderIsPlacedOrCancelled() {
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        played(nekro, "Imperial Rider");
        AiPrompt riders = riderPrompt("nekro", "Imperial Rider");

        test.game.setCurrentAgendaVote("for", "nekro_Imperial Rider");
        assertThat(AgendaCardRules.riders(context(nekro, riders))).isEmpty();

        test.game.removeOutcomeAgendaVote("for");
        test.game.getGameStats().markLatestPlayCanceled("Imperial Rider");
        assertThat(AgendaCardRules.riders(context(nekro, riders))).isEmpty();
    }

    // Prompts from before the agenda was revealed are not this agenda's.
    @Test
    void ignoresRiderButtonsOlderThanTheAgenda() {
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        played(nekro, "Imperial Rider");
        AiPrompt stale =
                prompt("stale", PromptSource.PUBLIC, NOW - 3_600_000L, "FFCC_nekro_rider_fa;for_Imperial Rider");

        assertThat(AgendaCardRules.riders(context(nekro, stale))).isEmpty();
    }

    @Test
    void placesARiderOnlyOnItsOwnersButtons() {
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        played(nekro, "Imperial Rider");

        assertThat(AgendaCardRules.riders(context(nekro, riderPrompt("sol", "Imperial Rider"))))
                .isEmpty();
    }

    // ---- holding the decline until the rider is placed ------------------------------------------------------------

    @Test
    void doesNotPassOnAftersWhileThePlayedRiderIsNotPlaced() {
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        AiPrompt decline = hidden("queued", "declineToQueueAnAfter");
        played(nekro, "Imperial Rider");

        assertThat(AgendaCardRules.holdsUnplacedRider(context(nekro, decline))).isTrue();
        assertThat(brain.decide(context(nekro, decline))).isInstanceOf(AiDecision.Idle.class);

        test.game.setCurrentAgendaVote("for", "nekro_Imperial Rider");
        assertThat(AgendaCardRules.holdsUnplacedRider(context(nekro, decline))).isFalse();
        assertThat(AiTestGame.pressedId(brain.decide(context(nekro, decline)))).isEqualTo("declineToQueueAnAfter");
    }

    @Test
    void passesOnAftersWhenTheRiderWasSabotaged() {
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        played(nekro, "Imperial Rider");
        test.game.getGameStats().markLatestPlayCanceled("Imperial Rider");

        assertThat(AgendaCardRules.holdsUnplacedRider(context(nekro))).isFalse();
    }

    // The queue played the card but the bot refused it (the seat is over the limit): it is still in hand and the seat
    // has to decline for the queue to move on.
    @Test
    void passesOnAftersWhenTheBotRefusedTheCard() {
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));

        assertThat(AgendaCardRules.holdsUnplacedRider(context(nekro))).isFalse();
    }

    @Test
    void givesUpOnAnUnplacedRiderAfterFiveMinutes() {
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        played(nekro, "Imperial Rider");

        assertThat(AgendaCardRules.holdsUnplacedRider(context(nekro))).isTrue();
        assertThat(AgendaCardRules.holdsUnplacedRider(context(nekro, NOW + 4 * 60_000L)))
                .isTrue();
        assertThat(AgendaCardRules.holdsUnplacedRider(context(nekro, NOW + 6 * 60_000L)))
                .isFalse();
    }

    // ---- voting has started --------------------------------------------------------------------------------------

    @Test
    void dropsAQueuedRiderOnceVotingHasStartedAndStillPlacesAPlayedOne() {
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setActivePlayerID(sol.getUserID());
        test.game.setStoredValue("queuedAfters", "nekro_");
        test.game.setStoredValue("queuedAftersFornekro", "ac_imp_rider");

        assertThat(pressed(AgendaCardRules.riders(context(nekro, hidden("queued", "declineToQueueAnAfter")))))
                .isEqualTo("declineToQueueAnAfter");

        test.game.removeStoredValue("queuedAfters");
        holds(nekro, IMPERIAL);
        AgendaCardRules.queueRider(context(nekro, hidden("options", "queueAfter_ac_imp_rider")));
        test.game.setPhaseOfGame("agendaVoting");
        played(nekro, "Imperial Rider");

        assertThat(pressed(AgendaCardRules.riders(context(nekro, riderPrompt("nekro", "Imperial Rider")))))
                .startsWith("FFCC_nekro_rider_fa;");
    }

    // ---- keep values ---------------------------------------------------------------------------------------------

    @Test
    void theFiveRidersAreWorthTheirKeepPriorNotNothing() {
        for (String alias : new String[] {"imp_rider", "trade_rider", "politic_rider", "tech_rider", "lead_rider"}) {
            holds(nekro, alias);

            assertThat(AiActionCards.usable(alias)).isTrue();
            assertThat(ActionCardValue.keepValue(test.game, nekro, alias)).isEqualTo(ActionCardPrior.keepPrior(alias));
        }
    }
}
