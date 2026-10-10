package ti4.ai.promissory;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class PromissoryRulesTest extends BaseTi4Test {

    private static final String THIRD_ID = "200000000000000002";

    private AiTestGame test;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    // The owner keeps the note in its owned set and the AI has it in hand, as after a transaction.
    private void aiHolds(Player owner, String note) {
        owner.addOwnedPromissoryNoteByID(note);
        test.nekro.setPromissoryNote(note);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private static AiPrompt hidden(String messageId, String... customIds) {
        return prompt(messageId, PromptSource.AI_THREAD, NOW, customIds);
    }

    // ---- offers the bot posts in the AI's thread ----

    // The owner of a Trade Agreement the AI holds has just replenished: the AI takes the commodities at once.
    @Test
    void takesTheCommoditiesWithATradeAgreement() {
        aiHolds(test.sol, "blue_ta");
        test.sol.setCommodities(3);

        assertThat(pressed(PromissoryRules.answerOffers(test.context(hidden("ta", "useTA_blue", "deleteButtons")))))
                .isEqualTo("useTA_blue");
    }

    // With nothing to take, it declines and keeps the note for the owner's next replenish.
    @Test
    void keepsATradeAgreementWhoseOwnerHasNoCommodities() {
        aiHolds(test.sol, "blue_ta");
        test.sol.setCommodities(0);

        assertThat(pressed(PromissoryRules.answerOffers(test.context(hidden("ta", "useTA_blue", "deleteButtons")))))
                .isEqualTo("deleteButtons");
    }

    // The bot's play handlers do not check who holds the note, so the AI checks before pressing.
    @Test
    void ignoresAnOfferForANoteItDoesNotHold() {
        test.sol.addOwnedPromissoryNoteByID("blue_ta");
        test.sol.setCommodities(3);

        assertThat(PromissoryRules.answerOffers(test.context(hidden("ta", "useTA_blue", "deleteButtons"))))
                .isEmpty();
    }

    // Jol-Nar researched Gravity Drive, a technology worth having: the AI gains it with Research Agreement.
    @Test
    void gainsAWorthwhileTechnologyWithResearchAgreement() {
        aiHolds(test.addSeat(THIRD_ID, "jolnar", "purple"), "ra");

        assertThat(pressed(PromissoryRules.answerOffers(
                        test.context(hidden("ra", "resolvePNPlay_ra_gd", "deleteButtons")))))
                .isEqualTo("resolvePNPlay_ra_gd");
    }

    // A technology of little use to it is declined; the note waits for a better one.
    @Test
    void keepsResearchAgreementForABetterTechnology() {
        aiHolds(test.addSeat(THIRD_ID, "jolnar", "purple"), "ra");

        assertThat(pressed(PromissoryRules.answerOffers(
                        test.context(hidden("ra", "resolvePNPlay_ra_x89", "deleteButtons")))))
                .isEqualTo("deleteButtons");
    }

    // Military Support is offered at the start of Sol's turn: the AI plays it, then drops the 2 infantry where its
    // space dock can pick them up.
    @Test
    void playsMilitarySupportThenPlacesTheInfantry() {
        test.nekroHome();
        aiHolds(test.sol, "ms");
        test.isActive(test.sol, "action");

        assertThat(pressed(
                        PromissoryRules.answerOffers(test.context(hidden("ms", "resolvePNPlay_ms", "deleteButtons")))))
                .isEqualTo("resolvePNPlay_ms");

        test.nekro.removePromissoryNote("ms");
        AiPrompt targets =
                prompt("targets", PromptSource.PUBLIC, NOW, "FFCC_nekro_placeOneNDone_skipbuild_2gf_mordaiii");
        assertThat(pressed(PromissoryRules.continuePlay(test.context(targets))))
                .isEqualTo("FFCC_nekro_placeOneNDone_skipbuild_2gf_mordaiii");
    }

    // The note's window is the start of Sol's turn: an offer left over from an earlier turn is not taken.
    @Test
    void ignoresAMilitarySupportOfferFromAnEarlierTurn() {
        aiHolds(test.sol, "ms");
        test.isActive(test.sol, "action");
        AiPrompt old = prompt("ms", PromptSource.AI_THREAD, NOW - 60_000L, "resolvePNPlay_ms", "deleteButtons");

        assertThat(PromissoryRules.answerOffers(test.context(old))).isEmpty();
    }

    // The play button in its promissory note hand has the same id as the offer. Only the offer, which comes with a
    // Decline button at the start of Sol's turn, is answered.
    @Test
    void neverPlaysMilitarySupportFromItsHand() {
        aiHolds(test.sol, "ms");
        test.isActive(test.sol, "action");

        assertThat(PromissoryRules.answerOffers(test.context(hidden("hand", "resolvePNPlay_ms"))))
                .isEmpty();
    }

    // Once Sol has activated a system, the start of its turn has passed.
    @Test
    void ignoresMilitarySupportOnceSolHasActed() {
        aiHolds(test.sol, "ms");
        test.isActive(test.sol, "action");
        test.game.setStoredValue("currentActionSummarysol", "sol Activated 301.");

        assertThat(PromissoryRules.answerOffers(test.context(hidden("ms", "resolvePNPlay_ms", "deleteButtons"))))
                .isEmpty();
    }

    // Gift of Prescience goes through the bot's pre-play, which it resolves when the action phase starts; the play
    // button in the hand would start the action phase itself.
    @Test
    void preplaysGiftOfPrescience() {
        aiHolds(test.addSeat(THIRD_ID, "naalu", "green"), "gift");
        test.game.setPhaseOfGame("strategy");

        assertThat(pressed(PromissoryRules.answerOffers(
                        test.context(hidden("gift", "resolvePreassignment_Play Naalu PN", "deleteButtons")))))
                .isEqualTo("resolvePreassignment_Play Naalu PN");
    }

    // ---- notes played at the start of a combat ----

    private void groundCombatAtHome(int nekroInfantry, int solInfantry) {
        Tile home = test.nekroHome();
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, nekroInfantry);
        test.units(home, "mordaiii", test.sol, UnitType.Infantry, solInfantry);
        test.game.setActiveSystem(home.getPosition());
    }

    private static AiPrompt groundRoll() {
        return prompt("roll", PromptSource.COMBAT_THREAD, NOW, "combatRoll_301_mordaiii");
    }

    @Test
    void playsTekklarLegionAtTheStartOfAGroundCombat() {
        aiHolds(test.addSeat(THIRD_ID, "sardakk", "red"), "tekklar");
        groundCombatAtHome(2, 2);

        assertThat(pressed(PromissoryRules.playCombatNotes(
                        test.context(groundRoll(), hidden("hand", "resolvePNPlay_tekklar")))))
                .isEqualTo("resolvePNPlay_tekklar");
    }

    // Six infantry against one win anyway: the note is kept for a closer fight.
    @Test
    void keepsTekklarLegionWhenItAlreadyOutnumbersTheDefenders() {
        aiHolds(test.addSeat(THIRD_ID, "sardakk", "red"), "tekklar");
        groundCombatAtHome(6, 1);

        assertThat(PromissoryRules.playCombatNotes(test.context(groundRoll(), hidden("hand", "resolvePNPlay_tekklar"))))
                .isEmpty();
    }

    // Once either side has rolled, the start of the combat has passed.
    @Test
    void neverPlaysTekklarLegionOnceTheCombatHasStarted() {
        aiHolds(test.addSeat(THIRD_ID, "sardakk", "red"), "tekklar");
        groundCombatAtHome(2, 2);
        test.game.setStoredValue("combatRoundTrackersol301mordaiii", "1");

        assertThat(PromissoryRules.playCombatNotes(test.context(groundRoll(), hidden("hand", "resolvePNPlay_tekklar"))))
                .isEmpty();
    }

    // Sol holds Nekro's Antivirus and could lose Gravity Drive to Technological Singularity: it plays the note as the
    // combat starts.
    @Test
    void playsAntivirusAtTheStartOfACombatAgainstNekro() {
        test.nekro.addOwnedPromissoryNoteByID("antivirus");
        test.sol.setPromissoryNote("antivirus");
        test.sol.addTech("gd");
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.sol, UnitType.Cruiser, 1);
        test.game.setActiveSystem(home.getPosition());
        AiPrompt roll = prompt("roll", PromptSource.COMBAT_THREAD, NOW, "combatRoll_301_space");

        assertThat(pressed(PromissoryRules.playCombatNotes(
                        test.contextFor(test.sol, Set.of(), NOW, roll, hidden("hand", "resolvePNPlay_antivirus")))))
                .isEqualTo("resolvePNPlay_antivirus");
    }

    // Greyfire Mutagen's planet buttons appear in the main channel; the AI waits for them, so it does not roll
    // first, then picks the planet it is fighting on.
    @Test
    void playsGreyfireMutagenThenPicksTheCombatPlanet() {
        aiHolds(test.addSeat(THIRD_ID, "yin", "yellow"), "greyfire");
        groundCombatAtHome(1, 2);

        assertThat(pressed(PromissoryRules.playCombatNotes(
                        test.context(groundRoll(), hidden("hand", "resolvePNPlay_greyfire")))))
                .isEqualTo("resolvePNPlay_greyfire");

        test.nekro.removePromissoryNote("greyfire");
        assertThat(PromissoryRules.continuePlay(test.context())).containsInstanceOf(AiDecision.Wait.class);
        AiPrompt targets = prompt("targets", PromptSource.PUBLIC, NOW, "greyfire_mordaiii");
        assertThat(pressed(PromissoryRules.continuePlay(test.context(targets)))).isEqualTo("greyfire_mordaiii");
    }

    // Greyfire Mutagen is played "at the start of a ground combat", not at the start of each round.
    @Test
    void neverPlaysGreyfireMutagenAfterTheFirstRound() {
        aiHolds(test.addSeat(THIRD_ID, "yin", "yellow"), "greyfire");
        groundCombatAtHome(1, 2);
        test.game.setStoredValue("combatRoundTrackernekro301mordaiii", "1");
        test.game.setStoredValue("combatRoundTrackersol301mordaiii", "1");

        assertThat(PromissoryRules.playCombatNotes(
                        test.context(groundRoll(), hidden("hand", "resolvePNPlay_greyfire"))))
                .isEmpty();
    }

    // If its play did not go through (it still holds the note), it does not sit waiting for planet buttons.
    @Test
    void stopsWaitingForGreyfireTargetsWhenThePlayDidNotHappen() {
        aiHolds(test.addSeat(THIRD_ID, "yin", "yellow"), "greyfire");
        groundCombatAtHome(1, 2);
        PromissoryRules.playCombatNotes(test.context(groundRoll(), hidden("hand", "resolvePNPlay_greyfire")));

        assertThat(PromissoryRules.continuePlay(test.context())).isEmpty();
    }

    // The play buttons have scrolled out of the 50 messages it reads: it asks the bot to post its cards again and
    // gives the new message a moment to arrive before it rolls.
    @Test
    void bringsItsNotesBackIntoViewBeforePlayingOne() {
        aiHolds(test.addSeat(THIRD_ID, "sardakk", "red"), "tekklar");
        groundCombatAtHome(2, 2);

        assertThat(pressed(PromissoryRules.playCombatNotes(test.context(groundRoll(), hidden("buttons", "cardsInfo")))))
                .isEqualTo("cardsInfo");
        assertThat(PromissoryRules.playCombatNotes(test.contextAt(NOW + 1_000L, groundRoll())))
                .containsInstanceOf(AiDecision.Wait.class);
    }

    // A play button it saw earlier can still be pressed after its message scrolled out of view.
    @Test
    void pressesAPlayButtonItRemembered() {
        aiHolds(test.addSeat(THIRD_ID, "sardakk", "red"), "tekklar");
        groundCombatAtHome(2, 2);
        PromissoryRules.observe(test.context(hidden("hand", "resolvePNPlay_tekklar")));

        assertThat(pressed(PromissoryRules.playCombatNotes(test.context(groundRoll()))))
                .isEqualTo("resolvePNPlay_tekklar");
    }

    // ---- agenda notes, queued as "when"s ----

    // Shard of the Throne gives the elected player a point. Sol leads on votes, so it is expected to elect itself.
    private void electionRevealed(int solPoints) {
        xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        givePoints(test.sol, solPoints);
        givePoints(xxcha, 1);
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":9,\"red\":4,\"black\":0}");
    }

    private void givePoints(Player player, int points) {
        test.game.scorePublicObjective(
                player.getUserID(), test.game.addCustomPO("Test points " + player.getFaction(), points));
    }

    private static AiPrompt whenOffer() {
        return hidden("offer", "queueAWhen", "declineToQueueAWhen", "explainQueue");
    }

    // Silencing Sol hands the election to a player with 1 point instead of 6: worth Sol's Political Secret.
    @Test
    void queuesPoliticalSecretToSilenceTheLikelyWinner() {
        electionRevealed(6);
        aiHolds(test.sol, "blue_ps");

        assertThat(pressed(PromissoryRules.queueAgendaNote(test.context(whenOffer()))))
                .isEqualTo("queueAWhen");
        AiPrompt options = hidden("options", "queueWhen_pn_blue_ps", "declineToQueueAWhen");
        assertThat(pressed(PromissoryRules.queueAgendaNote(test.context(options))))
                .isEqualTo("queueWhen_pn_blue_ps");
    }

    // When the next most likely winner is no better for it, the note is kept.
    @Test
    void keepsPoliticalSecretWhenSilencingChangesNothing() {
        electionRevealed(1);
        aiHolds(test.sol, "blue_ps");

        assertThat(PromissoryRules.queueAgendaNote(test.context(whenOffer()))).isEmpty();
    }

    // While the note waits in the queue, the "Pass On Whens" button the bot posts would take it back out.
    @Test
    void holdsAQueuedNoteUntilItResolves() {
        electionRevealed(6);
        test.game.setStoredValue("queuedWhens", "nekro_");
        test.game.setStoredValue("queuedWhensFornekro", "pn_blue_ps");

        assertThat(PromissoryRules.queueAgendaNote(test.context(hidden("queued", "declineToQueueAWhen"))))
                .containsInstanceOf(AiDecision.Wait.class);
    }

    // Once the table moves on to voting (someone pressed "Skip Waiting"), the "when" window is over: holding the note
    // there would keep the seat from ever voting.
    @Test
    void stopsHoldingTheQueuedNoteOnceVotingStarts() {
        electionRevealed(6);
        test.game.setStoredValue("queuedWhens", "nekro_");
        test.game.setStoredValue("queuedWhensFornekro", "pn_blue_ps");
        test.game.setPhaseOfGame("agendaVoting");

        assertThat(PromissoryRules.queueAgendaNote(test.context(hidden("queued", "declineToQueueAWhen"))))
                .isEmpty();
    }

    // A "when" queue stuck behind someone else never holds the seat for more than ten minutes.
    @Test
    void stopsHoldingTheQueuedNoteAfterTenMinutes() {
        electionRevealed(6);
        test.game.setStoredValue("queuedWhens", "nekro_");
        test.game.setStoredValue("queuedWhensFornekro", "pn_blue_ps");
        AiPrompt queued = hidden("queued", "declineToQueueAWhen");
        assertThat(PromissoryRules.queueAgendaNote(test.context(queued))).containsInstanceOf(AiDecision.Wait.class);

        assertThat(PromissoryRules.queueAgendaNote(test.contextAt(NOW + 11 * 60_000L, queued)))
                .isEmpty();
    }

    // An election that hands the leader a point is discarded with Political Favor while Xxcha has a strategy token.
    @Test
    void discardsAnAgendaThatRewardsTheLeaderWithPoliticalFavor() {
        electionRevealed(6);
        aiHolds(xxcha, "favor");
        xxcha.setStrategicCC(1);

        assertThat(pressed(PromissoryRules.queueAgendaNote(test.context(whenOffer()))))
                .isEqualTo("queueAWhen");
        AiPrompt options = hidden("options", "queueWhen_pn_favor", "declineToQueueAWhen");
        assertThat(pressed(PromissoryRules.queueAgendaNote(test.context(options))))
                .isEqualTo("queueWhen_pn_favor");
    }

    // Without a token in Xxcha's strategy pool the bot cannot resolve Political Favor, so it is kept.
    @Test
    void keepsPoliticalFavorWhenXxchaHasNoStrategyToken() {
        electionRevealed(6);
        aiHolds(xxcha, "favor");
        xxcha.setStrategicCC(0);

        assertThat(PromissoryRules.queueAgendaNote(test.context(whenOffer()))).isEmpty();
    }

    // In the brain, queueing the note comes before passing on "when"s.
    @Test
    void theBrainQueuesTheNoteBeforePassingOnWhens() {
        electionRevealed(6);
        aiHolds(test.sol, "blue_ps");

        assertThat(AiTestGame.pressedId(new NekroBrain().decide(test.context(whenOffer()))))
                .isEqualTo("queueAWhen");
    }
}
