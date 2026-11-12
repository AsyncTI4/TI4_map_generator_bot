package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.AgendaCardRules;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.trade.TradeRules;
import ti4.ai.xxcha.QuashRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * The expert exam: hand-built table positions, one or more per bullet of the user's agenda guidance notes
 * (ai-agenda-value-review, ai-agenda-politics-guidance, ai-agenda-timing-windows), each asserting the answer an expert
 * player gives. Every case runs with the AGENDA_VALUE_TABLE and GALACTIC_THREAT_COST experiments on (they are on by
 * default; the setup asserts it).
 *
 * <p>Cases for behaviour the AI does not have yet are {@link Disabled} with the plan milestone (docs/ai/AGENDA_PLAN.md)
 * that delivers it, so the exam doubles as a to-do list. A case disabled with "milestone 1e (follow-up)" is a guidance
 * bullet the AI is supposed to follow already but gets wrong; docs/ai/AGENDA_PLAN.md tracks each one under 1e.
 *
 * <p>Table: AiTestGame always seats Nekro (black, cannot vote because of Galactic Threat) and Sol (blue); most cases add
 * Xxcha (red) and sometimes Hacan (green) or another faction. Games are to 10 points, round 2, on the second agenda of
 * the phase (so no vote has to be kept back) unless a case says otherwise.
 */
class AgendaExamTest extends BaseTi4Test {

    private static final String XXCHA_ID = "200000000000000002";
    private static final String FOURTH_ID = "200000000000000003";
    private static final String FIFTH_ID = "200000000000000004";
    private static final String TIE = "resolveAgendaVote_outcomeTie*_";

    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player xxcha;
    private int customPoints;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        xxcha = test.addSeat(XXCHA_ID, "xxcha", "red");
        test.game.setVp(10);
        test.game.setRound(2);
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setStoredValue("agendaCount", "2");
        test.game.setSpeakerUserID(xxcha.getUserID());
        assertThat(AiExperiments.enabled(sol, Experiment.AGENDA_VALUE_TABLE)).isTrue();
        assertThat(AiExperiments.enabled(sol, Experiment.GALACTIC_THREAT_COST)).isTrue();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private Player seat(String userId, String faction, String color) {
        return test.addSeat(userId, faction, color);
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(
                player.getUserID(), test.game.addCustomPO("Exam points " + ++customPoints, points));
    }

    /** Reveals {@code info} with each seat's votes at the start of the agenda, as "color", votes pairs. */
    private void reveal(String info, Object... colorVotes) {
        test.game.setCurrentAgendaInfo(info);
        StringBuilder json = new StringBuilder("{");
        for (int index = 0; index < colorVotes.length; index += 2) {
            if (index > 0) json.append(',');
            json.append('"').append(colorVotes[index]).append("\":").append(colorVotes[index + 1]);
        }
        test.game.setStoredValue("agendaStartVoteCounts", json.append('}').toString());
    }

    private void hand(Player player, String... aliases) {
        int index = 1;
        for (String alias : aliases) player.setActionCard(alias, index++);
    }

    /** A hand of {@code count} made-up cards, valued at the average card when another seat looks at it. */
    private void anonymousHand(Player player, int count) {
        for (int index = 1; index <= count; index++)
            player.setActionCard("examcard" + index + player.getColor(), index);
    }

    /** The outcome the seat's ballot planner votes, with its planets worth {@code planetVotes}; empty = abstain. */
    private Optional<String> ballot(Player seat, Integer... planetVotes) {
        return BallotPlanner.choose(test.game, seat, AgendaPolicy.currentAgenda(test.game), Arrays.asList(planetVotes))
                .map(BallotPlanner.Choice::outcome);
    }

    private double ownValue(Player voter, String agenda, String outcome, String ownVote) {
        OptionalDouble value = AgendaValues.of(test.game, voter, true, agenda, outcome, ownVote);
        assertThat(value).as("%s / %s", agenda, outcome).isPresent();
        return value.getAsDouble();
    }

    private Optional<String> tieBreak(Player speaker, String... outcomes) {
        String[] ids = Arrays.stream(outcomes).map(outcome -> TIE + outcome).toArray(String[]::new);
        AiPrompt tie = prompt("tie", PromptSource.PUBLIC, NOW, ids);
        return AgendaPolicy.chooseOutcome(test.game, speaker, tie.enabledButtons())
                .map(PromptButton::customId)
                .map(id -> id.substring(TIE.length()));
    }

    private AiTurnContext context(Player seat, AiPrompt... prompts) {
        return test.contextFor(seat, Set.of(), NOW, prompts);
    }

    private static AiPrompt hidden(String id, String... buttons) {
        return prompt(id, PromptSource.AI_THREAD, NOW, buttons);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // ---- point elections ---------------------------------------------------------------------------------------

    /** Review, Elect Player: point agendas. Never hand a rival at goal-1 the point that wins it the game. */
    @Nested
    class PointElections {

        private static final String CENSURE = "Law_Elect Player_7_censure";

        // Xxcha sits at 9 of 10 and will vote itself Political Censure with 5 votes. Sol, with 6, votes itself: it
        // gains a point and keeps Xxcha from winning.
        @Test
        void votesItselfOverARivalOnePointFromWinning() {
            points(xxcha, 9);
            points(sol, 3);
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(CENSURE, "blue", 6, "red", 5, "black", 0);

            assertThat(ballot(sol, 6)).contains("sol");
        }

        // Nekro breaks a tie between Sol and Xxcha: Xxcha at 9 would win with the point, so the point goes to Sol
        // even though Sol, at 7, is the bigger long-run threat on the board.
        @Test
        void theSpeakerNeverBreaksATieForTheRivalAtGoalMinusOne() {
            points(xxcha, 9);
            points(sol, 7);
            test.game.setSpeakerUserID(nekro.getUserID());
            reveal(CENSURE, "blue", 3, "red", 3, "black", 0);

            assertThat(tieBreak(nekro, "xxcha", "sol")).contains("sol");
        }

        // Even with nothing to gain itself (Sol cannot win the vote for itself), it puts its votes on the other
        // rival rather than let Xxcha take the winning point.
        @Test
        void votesForAnotherRivalWhenItCannotWinButTheLeaderWould() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");
            points(xxcha, 9);
            points(hacan, 4);
            test.game.setSpeakerUserID(hacan.getUserID());
            reveal(CENSURE, "blue", 3, "red", 5, "green", 4, "black", 0);
            test.game.setCurrentAgendaVote("xxcha", "red_5");
            test.game.setCurrentAgendaVote("hacan", "green_4");

            assertThat(ballot(sol, 3)).contains("hacan");
        }
    }

    // ---- ministers by round ------------------------------------------------------------------------------------

    /** Review, Elect Player: Minister values that fall with the round, and the faction bonuses. */
    @Nested
    class Ministers {

        @Test
        void ministerOfSciencesIsFourInRoundTwoMinusOnePerRoundWithAFloorOfOne() {
            test.game.setRound(2);
            assertThat(ownValue(sol, "minister_sciences", "sol", "sol")).isEqualTo(4.0);
            test.game.setRound(3);
            assertThat(ownValue(sol, "minister_sciences", "sol", "sol")).isEqualTo(3.0);
            test.game.setRound(6);
            assertThat(ownValue(sol, "minister_sciences", "sol", "sol")).isEqualTo(1.0);
        }

        @Test
        void jolNarGetsTwoMoreFromMinisterOfSciences() {
            Player jolnar = seat(FOURTH_ID, "jolnar", "purple");
            test.game.setRound(2);

            assertThat(ownValue(jolnar, "minister_sciences", "jolnar", "jolnar"))
                    .isEqualTo(6.0);
        }

        @Test
        void ministerOfCommerceIsThreeTwoThenOne() {
            test.game.setRound(2);
            assertThat(ownValue(sol, "minister_commerce", "sol", "sol")).isEqualTo(3.0);
            test.game.setRound(3);
            assertThat(ownValue(sol, "minister_commerce", "sol", "sol")).isEqualTo(2.0);
            test.game.setRound(4);
            assertThat(ownValue(sol, "minister_commerce", "sol", "sol")).isEqualTo(1.0);
        }

        @Test
        void keleresGetsTwoMoreFromMinisterOfCommerce() {
            Player keleres = seat(FOURTH_ID, "keleresa", "purple");
            test.game.setRound(4);

            assertThat(ownValue(keleres, "minister_commerce", "keleresa", "keleresa"))
                    .isEqualTo(3.0);
        }

        @Test
        void ministerOfIndustryIsWorthThreeToCreussBeforeRoundFourAndOneAfter() {
            Player creuss = seat(FOURTH_ID, "ghost", "purple");
            test.game.setRound(3);
            assertThat(ownValue(creuss, "minister_industry", "ghost", "ghost")).isEqualTo(3.0);
            assertThat(ownValue(sol, "minister_industry", "sol", "sol")).isEqualTo(1.0);
            test.game.setRound(4);
            assertThat(ownValue(creuss, "minister_industry", "ghost", "ghost")).isEqualTo(1.0);
        }

        // Peace and War are flat 5: the seat spends its votes on itself for either in any round.
        @Test
        void votesItselfMinisterOfWar() {
            test.game.setRound(5);
            reveal("Law_Elect Player_7_minister_war", "blue", 3, "red", 2, "black", 0);

            assertThat(ownValue(sol, "minister_war", "sol", "sol")).isEqualTo(5.0);
            assertThat(ballot(sol, 3)).contains("sol");
        }
    }

    // ---- Public Execution --------------------------------------------------------------------------------------

    /** Review, Public Execution: the speaker is the usual target; Yssaril and Ral Nel are also good targets. */
    @Nested
    class PublicExecution {

        private static final String EXECUTION = "Directive_Elect Player_7_execution";

        // Equal points and equal hands: Xxcha holds the speaker token, so executing it moves everyone up in speaker
        // order and costs it the token. Sol votes for the speaker.
        @Test
        void executesTheSpeaker() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");
            for (Player rival : List.of(nekro, xxcha, hacan)) anonymousHand(rival, 3);
            reveal(EXECUTION, "blue", 3, "red", 3, "green", 3, "black", 0);

            assertThat(ballot(sol, 3)).contains("xxcha");
        }

        // Sol holds the token itself, so among the rivals Yssaril is the target.
        @Test
        void executesYssarilWhenTheSpeakerIsItself() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");
            Player yssaril = seat(FIFTH_ID, "yssaril", "yellow");
            for (Player rival : List.of(nekro, xxcha, hacan, yssaril)) anonymousHand(rival, 3);
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(EXECUTION, "blue", 3, "red", 3, "green", 3, "yellow", 3, "black", 0);

            assertThat(ballot(sol, 3)).contains("yssaril");
        }

        @Test
        void executesRalNelWhenTheSpeakerIsItself() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");
            Player ralnel = seat(FIFTH_ID, "ralnel", "yellow");
            for (Player rival : List.of(nekro, xxcha, hacan, ralnel)) anonymousHand(rival, 3);
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(EXECUTION, "blue", 3, "red", 3, "green", 3, "yellow", 3, "black", 0);

            assertThat(ballot(sol, 3)).contains("ralnel");
        }

        // The speaker breaking a tie never executes itself.
        @Test
        void theSpeakerNeverExecutesItself() {
            anonymousHand(xxcha, 3);
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(EXECUTION, "blue", 0, "red", 0, "black", 0);

            assertThat(tieBreak(sol, "sol", "xxcha")).contains("xxcha");
        }

        @Disabled("milestone 10: avoidance bribes on Public Execution are deferred until the simple vote-buying loop"
                + " shows it helps")
        @Test
        void theSpeakerPaysTheTableNotToBeExecuted() {
            fail("expected the AI speaker to offer trade goods to the voters so they elect someone else");
        }
    }

    // ---- Galactic Threat ---------------------------------------------------------------------------------------

    /** Politics answers: voting for the outcome Nekro predicted costs 1 TG, or 0.5 TG once others voted that way. */
    @Nested
    class GalacticThreat {

        private static final String EXECUTION = "Directive_Elect Player_7_execution";

        // Two equal rivals to execute: the 1 TG of disincentive moves Sol off the one Nekro predicted.
        @Test
        void avoidsNekrosPredictionWhenTheChoiceIsOtherwiseEven() {
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(EXECUTION, "blue", 9, "red", 4, "black", 0);
            int index = 1;
            for (Player rival : List.of(nekro, xxcha)) {
                for (String card : List.of("sabo1", "sabo2", "sabo3"))
                    rival.setActionCard(card + rival.getColor(), index++);
            }
            String unpredicted = ballot(sol, 3, 2, 4).orElseThrow();
            test.game.setCurrentAgendaVote(unpredicted, "black_" + GalacticThreatCost.RIDER);

            assertThat(ballot(sol, 3, 2, 4)).isPresent().get().isNotEqualTo(unpredicted);
        }

        // Once another seat has voted for the prediction Nekro gets its technology anyway, so the cost halves.
        @Test
        void joiningOthersOnThePredictionCostsHalf() {
            reveal("Law_Elect Player_7_minister_war", "blue", 9, "red", 4, "black", 0);
            test.game.setCurrentAgendaVote("xxcha", "black_" + GalacticThreatCost.RIDER);
            assertThat(GalacticThreatCost.ofBallot(test.game, sol, "xxcha", 3)).isEqualTo(1.0);

            test.game.setCurrentAgendaVote("xxcha", "black_" + GalacticThreatCost.RIDER + ";red_4");
            assertThat(GalacticThreatCost.ofBallot(test.game, sol, "xxcha", 3)).isEqualTo(0.5);
        }

        // A 5 TG minister is worth more than the 1 TG: Sol still votes itself after Nekro predicted Sol.
        @Test
        void aBigStakeStillVotesForThePrediction() {
            reveal("Law_Elect Player_7_minister_war", "blue", 4, "red", 2, "black", 0);
            test.game.setCurrentAgendaVote("sol", "black_" + GalacticThreatCost.RIDER);

            assertThat(ballot(sol, 4)).contains("sol");
        }

        private static final AiPrompt AFTER_WINDOW = hidden("window", "queueAnAfter", "declineToQueueAnAfter");

        private String nekroAfterWindow() {
            return pressed(GalacticThreatRules.next(context(nekro, AFTER_WINDOW)));
        }

        private void openMinisterOfWar(String agendaCount) {
            seat(FOURTH_ID, "hacan", "green");
            sol.addTech("gd");
            test.game.setStoredValue("agendaCount", agendaCount);
            reveal("Law_Elect Player_7_minister_war", "blue", 6, "red", 6, "green", 6, "black", 0);
        }

        // Traces-4b (aiselfplay945838 r1 #1): Nekro predicted Sol on a first-agenda Minister of War at a 14% chance and
        // lost. "Use it on an outcome they are pretty sure other players will vote on; if not sure, on an agenda with 2
        // or fewer outcomes." Four open candidates: Nekro keeps it for the second agenda.
        @Test
        void keepsGalacticThreatForTheSecondAgendaOnAnOpenElection() {
            openMinisterOfWar("1");

            assertThat(nekroAfterWindow()).isEmpty();
        }

        // "Nekro should always use their Galactic Threat every agenda phase: if they didn't use it on the first agenda,
        // use it on the second no matter what."
        @Test
        void usesGalacticThreatOnTheSecondAgendaNoMatterWhat() {
            openMinisterOfWar("2");

            assertThat(nekroAfterWindow()).endsWith("queueAnAfter");
        }

        // Traces-4b (aiselfplay293998 r1 #1): Executive Sanctions on the first agenda has two outcomes, so Nekro
        // predicts
        // even without a sure voter.
        @Test
        void usesGalacticThreatOnAFirstAgendaForAgainst() {
            sol.addTech("gd");
            test.game.setStoredValue("agendaCount", "1");
            reveal("Law_For/Against_7_sanctions", "blue", 6, "red", 6, "black", 0);

            assertThat(nekroAfterWindow()).endsWith("queueAnAfter");
        }

        // Traces-4b (aiselfplay293998 r1 #1): "Nekro bought Hacan's 1 vote for 4 TG, paid only if For won" after it
        // predicted For. The user: "cap it at 2 TGs."
        @Test
        void paysAtMostTwoTradeGoodsForAVoteTowardItsPrediction() {
            test.place("01", "304");
            sol.addPlanet("jord");
            test.place("19", "310");
            sol.addPlanet("wellon");
            sol.addTech("gd");
            nekro.setTg(10);
            test.game.setStoredValue("agendaCount", "1");
            test.game.setSpeakerUserID(sol.getUserID());
            reveal("Law_For/Against_7_sanctions", "blue", 6, "black", 0);
            test.game.setCurrentAgendaVote("for", "black_" + GalacticThreatCost.RIDER);

            assertThat(answerVoteOffer(4)).contains("aiPromiseNo_");
            assertThat(answerVoteOffer(2)).contains("aiPromiseYes_");
        }

        private String answerVoteOffer(int price) {
            Promise offer = Promise.offer(
                    test.game,
                    Promise.Term.VOTE_FOR,
                    sol,
                    nekro,
                    sol,
                    PromisedVotes.agendaKey(test.game),
                    "for",
                    1,
                    price,
                    NOW,
                    NOW + 60_000);
            PromiseBook.save(test.game, offer);
            AiPrompt prompt = prompt(
                    "offer" + price,
                    PromptSource.PUBLIC,
                    NOW,
                    "FFCC_nekro_aiPromiseYes_" + offer.id(),
                    "FFCC_nekro_aiPromiseNo_" + offer.id());
            return pressed(TradeRules.answerPromises(context(nekro, prompt)));
        }

        @Disabled("milestones 5 and 10: any seat may offer TG to voters who vote the way its rider points; Nekro"
                + " offering 1-2 TG to back its prediction is one case")
        @Test
        void nekroOffersTradeGoodsToVotersWhoBackItsPrediction() {
            fail("expected the Nekro AI to offer 1-2 TG to seats voting for its predicted outcome");
        }
    }

    // ---- Veto and Quash ----------------------------------------------------------------------------------------

    /** Politics guidance: stop an outcome that is very bad for you with Veto or Quash. */
    @Nested
    class VetoAndQuash {

        private static final String SHARD = "Law_Elect Player_7_shard_of_the_throne";

        // Sol, the 7 point leader with 9 votes, will elect itself to Shard of the Throne.
        private void leaderTakesTheShard() {
            points(sol, 7);
            points(xxcha, 1);
            reveal(SHARD, "blue", 9, "red", 4, "black", 0);
        }

        private AiPrompt whenOffer() {
            return hidden("offer", "queueAWhen", "declineToQueueAWhen", "explainQueue");
        }

        @Test
        void vetoesAShardOfTheThroneTheLeaderWillTake() {
            leaderTakesTheShard();
            hand(nekro, "veto");

            assertThat(AgendaPolicy.veryBadFor(test.game, nekro)).isTrue();
            assertThat(pressed(AgendaCardRules.queueVeto(context(nekro, whenOffer()))))
                    .isEqualTo("queueAWhen");
            AiPrompt options = hidden("options", "queueWhen_ac_veto", "declineToQueueAWhen");
            assertThat(pressed(AgendaCardRules.queueVeto(context(nekro, options))))
                    .isEqualTo("queueWhen_ac_veto");
        }

        // Minister of Exploration is a 1 TG election: not worth a Veto even when the leader takes it.
        @Test
        void keepsTheVetoForAMinorElection() {
            leaderTakesTheShard();
            test.game.setCurrentAgendaInfo("Law_Elect Player_7_minister_exploration");
            hand(nekro, "veto");

            assertThat(AgendaCardRules.queueVeto(context(nekro, whenOffer()))).isEmpty();
        }

        @Test
        void xxchaQuashesTheLeadersShard() {
            leaderTakesTheShard();
            xxcha.setStrategicCC(2);

            assertThat(QuashRules.canQuash(xxcha)).isTrue();
            assertThat(QuashRules.worthQuashing(test.game, xxcha)).isTrue();
        }

        @Test
        void xxchaKeepsItsTokenOnAMinorElection() {
            leaderTakesTheShard();
            test.game.setCurrentAgendaInfo("Law_Elect Player_7_minister_exploration");
            xxcha.setStrategicCC(2);

            assertThat(QuashRules.worthQuashing(test.game, xxcha)).isFalse();
        }

        @Disabled("milestone 11: paying for Veto and Quash (holder announces, table pools 1-3 TG for Veto, 3-5 TG for"
                + " Quash)")
        @Test
        void aSeatThatHatesTheOutcomePaysTheVetoHolder() {
            fail("expected the AI losing more than about 3 TG to pay an announced Veto holder");
        }
    }

    // ---- riders ------------------------------------------------------------------------------------------------

    /** Politics guidance: a rider on an Elect agenda with only one option is free value and is always played. */
    @Nested
    class Riders {

        private AiPrompt afterOffer() {
            return hidden("after", "queueAnAfter", "declineToQueueAnAfter");
        }

        // Classified Document Leaks with only one scored secret on the table: the only outcome is certain, so Sol's
        // Trade Rider pays 5 TG for nothing (its votes cannot change the result).
        @Test
        void playsARiderOnAnElectionWithOneOption() {
            xxcha.setSecretScored("gamf");
            test.game.setStoredValue("whensResolved", "Yes");
            reveal("Law_Elect Secret Objective_7_classified", "blue", 3, "red", 3, "black", 0);
            assertThat(AgendaOutcomes.of(test.game)).containsExactly("gamf");
            hand(sol, "trade_rider");

            assertThat(pressed(AgendaCardRules.queueRider(context(sol, afterOffer()))))
                    .isEqualTo("queueAnAfter");
            AiPrompt options = hidden("options", "queueAfter_ac_trade_rider", "declineToQueueAnAfter");
            assertThat(pressed(AgendaCardRules.queueRider(context(sol, options))))
                    .isEqualTo("queueAfter_ac_trade_rider");
        }

        // The Mutiny of self-play game aiselfplay945838 (round 1, second agenda): Xxcha, Sardakk and Letnev carry For.
        // Hacan played a Leadership Rider on For there and so could not vote, losing the point Mutiny gives For voters.
        // Three command tokens are worth less than that point: Hacan keeps the rider and votes For.
        @Test
        void hacanVotesForOnMutinyRatherThanPlayALeadershipRider() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");
            test.place("16", "101");
            test.place("25", "102");
            test.place("26", "103");
            test.place("27", "104");
            for (String planet : List.of("hercant", "kamdorn", "quann", "lodor", "newalbion")) hacan.addPlanet(planet);
            test.game.setRound(1);
            test.game.setStoredValue("whensResolved", "Yes");
            reveal("Directive_For/Against_7_mutiny", "blue", 0, "red", 14, "green", 5, "black", 0);
            hand(hacan, "lead_rider");

            assertThat(AgendaForecast.of(test.game, hacan).chance("for")).isGreaterThan(0.7);
            assertThat(pressed(AgendaCardRules.queueRider(context(hacan, afterOffer()))))
                    .isEmpty();
            assertThat(ballot(hacan, 5)).contains("for");
        }

        @Disabled("milestone 5c: with a single outcome a rider is always played, skipping the keep-prior gate, even a"
                + " minor one")
        @Test
        void playsEvenAMinorRiderOnAnElectionWithOneOption() {
            xxcha.setSecretScored("gamf");
            test.game.setStoredValue("whensResolved", "Yes");
            reveal("Law_Elect Secret Objective_7_classified", "blue", 3, "red", 3, "black", 0);
            hand(sol, "war_rider");

            assertThat(pressed(AgendaCardRules.queueRider(context(sol, afterOffer()))))
                    .isEqualTo("queueAnAfter");
        }

        @Disabled("milestone 5a: a rival Politics Rider is weighed by each seat's change in speaker order")
        @Test
        void votesAgainstARivalPoliticsRiderThatWorsensItsPickOrder() {
            fail("expected a seat with good speaker order to vote down the outcome carrying a rival Politics Rider");
        }

        @Disabled("milestone 5a: an Imperial Rider played as a poison pill flips a two-outcome agenda")
        @Test
        void anImperialPoisonPillPushesTheTableToTheOtherOutcome() {
            fail("expected an Imperial Rider on one side to move the indifferent voters to the other side");
        }
    }

    // ---- For/Against laws --------------------------------------------------------------------------------------

    /** Review, Anti-Intellectual Revolution: For costs about 1.5 TG per round left for everyone except Nekro. */
    @Nested
    class AntiIntellectualRevolution {

        private static final String REVOLUTION = "Law_For/Against_7_revolution";

        @Test
        void aNonNekroSeatVotesAgainst() {
            reveal(REVOLUTION, "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).contains("against");
        }

        @Test
        void nekroWantsItAndBreaksTheTieFor() {
            test.game.setSpeakerUserID(nekro.getUserID());
            reveal(REVOLUTION, "blue", 0, "red", 0, "black", 0);

            assertThat(AgendaUtility.of(test.game, nekro, true, "revolution", "for"))
                    .isGreaterThan(AgendaUtility.of(test.game, nekro, true, "revolution", "against"));
            assertThat(tieBreak(nekro, "for", "against")).contains("for");
        }

        @Test
        void forCostsOnePointFivePerRoundLeft() {
            assertThat(ExpertLawValues.researchCost(test.game, sol))
                    .isCloseTo(1.5 * ti4.ai.eval.GameHorizon.roundsLeft(test.game), within(1e-9));
            assertThat(ExpertLawValues.researchCost(test.game, nekro)).isZero();
        }
    }

    /** Review, Executive Sanctions: For costs everything but your 3 best action cards; Yssaril is exempt. */
    @Nested
    class ExecutiveSanctions {

        private static final String SANCTIONS = "Law_For/Against_7_sanctions";

        @Test
        void yssarilVotesForWhileTheTableHoldsBigHands() {
            Player yssaril = seat(FOURTH_ID, "yssaril", "yellow");
            hand(yssaril, "sabo1", "sabo2", "sabo3", "sabo4", "veto", "veto2", "veto3");
            for (Player rival : List.of(nekro, sol, xxcha)) anonymousHand(rival, 7);
            reveal(SANCTIONS, "blue", 3, "red", 3, "yellow", 3, "black", 0);

            assertThat(ballot(yssaril, 3)).contains("for");
        }

        @Test
        void aSeatWithABigHandVotesAgainst() {
            hand(sol, "sabo1", "sabo2", "sabo3", "sabo4", "veto", "veto2", "veto3");
            anonymousHand(xxcha, 2);
            reveal(SANCTIONS, "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).contains("against");
        }
    }

    /**
     * Review, Conventions of War and Representative Government: Against only costs the players who voted Against, so
     * the table abstains and the speaker picks Against for free (the speaker's tie-break is not a vote).
     */
    @Nested
    class AgainstOnlyCostsAgainstVoters {

        private static final String CONVENTIONS = "Law_For/Against_7_conventions";
        private static final String REP_GOVT = "Law_For/Against_7_representative_government";

        @Test
        void conventionsAgainstCostsOnlyThoseWhoVotedAgainst() {
            hand(sol, "sabo1", "sabo2", "veto");
            reveal(CONVENTIONS, "blue", 3, "red", 3, "black", 0);

            assertThat(ownValue(sol, "conventions", "against", "")).isZero();
            assertThat(ownValue(sol, "conventions", "against", "for")).isZero();
            assertThat(ownValue(sol, "conventions", "against", "against")).isNegative();
        }

        // Nobody cares enough to vote: the seat abstains and leaves Against to the speaker.
        @Test
        void abstainsOnConventions() {
            hand(sol, "sabo1", "sabo2", "veto");
            reveal(CONVENTIONS, "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).isEmpty();
        }

        @Test
        void representativeGovernmentAgainstCostsOnlyThoseWhoVotedAgainst() {
            test.place("26", "000");
            sol.addPlanet("lodor");
            reveal(REP_GOVT, "blue", 3, "red", 3, "black", 0);

            assertThat(ownValue(sol, "representative_government", "against", ""))
                    .isZero();
            assertThat(ownValue(sol, "representative_government", "against", "against"))
                    .isNegative();
        }

        @Test
        void abstainsOnRepresentativeGovernment() {
            test.place("26", "000");
            sol.addPlanet("lodor");
            reveal(REP_GOVT, "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).isEmpty();
        }

        @Test
        void theSpeakerPicksAgainstForFreeOnConventions() {
            hand(sol, "sabo1", "sabo2", "veto");
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(CONVENTIONS, "blue", 0, "red", 0, "black", 0);

            assertThat(tieBreak(sol, "for", "against")).contains("against");
        }

        @Test
        void theForecastExpectsAgainstWhenTheTableIsIndifferent() {
            hand(sol, "sabo1", "sabo2", "veto");
            for (Player seat : List.of(nekro, xxcha)) anonymousHand(seat, 3);
            reveal(CONVENTIONS, "blue", 3, "red", 3, "black", 0);

            assertThat(AgendaForecast.of(test.game, sol).chance("against")).isGreaterThan(0.9);
        }

        // Sol and the speaker Xxcha each have six dreadnoughts at Lodor (an alpha wormhole), so For costs both of them
        // a
        // fleet worth more than the free technology. Against only costs the seats that voted Against a command token.
        // Nobody needs to pay that: Sol abstains and Xxcha, voting last, picks Against on the tie for free.
        @Test
        void abstainsOnWormholeResearchAndLetsTheSpeakerPickAgainst() {
            Tile lodor = test.place("26", "000");
            test.units(lodor, "space", sol, UnitType.Dreadnought, 6);
            test.units(lodor, "space", xxcha, UnitType.Dreadnought, 6);
            for (Player seat : List.of(nekro, sol, xxcha)) seat.setTacticalCC(3);
            reveal("Directive_For/Against_7_wormhole_research", "blue", 3, "red", 3, "black", 0);

            assertThat(ownValue(sol, "wormhole_research", "for", "for")).isNegative();
            assertThat(ballot(sol, 3)).isEmpty();
            assertThat(AgendaForecast.of(test.game, sol).without(sol).chance("against"))
                    .isGreaterThan(0.9);
        }
    }

    /**
     * Wormhole Research in self-play game aiselfplay524170 (round 3, first agenda): every other seat abstained, and the
     * speaker Nekro (voting through the Xxcha commander, its Galactic Threat on For) cast 1 Against, taking the Against
     * voters' command token loss. Breaking the 0-0 tie is not voting, so Against was free.
     */
    @Nested
    class SpeakerFreePick {

        @Test
        void theSpeakerAbstainsAndPicksAgainstOnWormholeResearch() {
            test.game.setRound(3);
            test.game.setStoredValue("agendaCount", "1");
            test.game.setSpeakerUserID(nekro.getUserID());
            nekro.addLeader("xxchacommander");
            nekro.unsafeGetLeader("xxchacommander").setLocked(false);
            Tile lodor = test.place("26", "000");
            test.units(lodor, "space", nekro, UnitType.Dreadnought, 3);
            test.units(lodor, "space", sol, UnitType.Dreadnought, 2);
            for (Player seat : List.of(nekro, sol, xxcha)) seat.setTacticalCC(3);
            reveal("Directive_For/Against_7_wormhole_research", "blue", 6, "red", 6, "black", 6);
            test.game.setCurrentAgendaVote("for", "black_" + GalacticThreatCost.RIDER);
            test.game.setPhaseOfGame("agendaVoting");
            test.game.setActivePlayerID(nekro.getUserID());

            assertThat(ownValue(nekro, "wormhole_research", "against", "against"))
                    .isNegative();
            assertThat(ballot(nekro, 2, 2, 2)).isEmpty();
            assertThat(tieBreak(nekro, "for", "against")).contains("against");
        }

        // Conventions of War on the last agenda: nobody else voted, so the speaker's own Against ballot would only
        // cost it its hand. It abstains and breaks the tie Against.
        @Test
        void theSpeakerAbstainsOnTheLastAgendaWhenVotingCostsIt() {
            hand(sol, "sabo1", "sabo2", "veto");
            test.game.setSpeakerUserID(sol.getUserID());
            reveal("Law_For/Against_7_conventions", "blue", 6, "red", 6, "black", 0);
            test.game.setPhaseOfGame("agendaVoting");
            test.game.setActivePlayerID(sol.getUserID());

            assertThat(ballot(sol, 3, 3)).isEmpty();
            assertThat(tieBreak(sol, "for", "against")).contains("against");
        }
    }

    /** Review, Articles of War, pattern (1): strong-mech factions vote Against, mediocre-mech factions vote For. */
    @Nested
    class ArticlesOfWar {

        private static final String ARTICLES = "Law_For/Against_7_articles_war";

        // Mahact is the user's own strong-mech rating (ai-mech-notes); Letnev's rating is still a WIP prior.
        @Test
        void aStrongMechFactionDislikesFor() {
            Player mahact = seat(FOURTH_ID, "mahact", "green");
            reveal(ARTICLES, "blue", 3, "red", 3, "green", 3, "black", 0);

            assertThat(ownValue(mahact, "articles_war", "for", "")).isNegative();
        }

        // The strong-mech seat values For at only a few TG once the rivals' smaller losses are netted off, which is no
        // more than the 3 TG every For voter collects when Against wins. So it votes For as a hedge, and its own 3
        // votes
        // push the law towards passing.
        @Disabled("milestone 1e (follow-up): the strong-mech seat votes For to collect the 3 TG For-voter payout, which"
                + " outweighs its netted For loss; the expert votes Against")
        @Test
        void aStrongMechFactionVotesAgainst() {
            Player mahact = seat(FOURTH_ID, "mahact", "green");
            reveal(ARTICLES, "blue", 3, "red", 3, "green", 3, "black", 0);

            assertThat(ballot(mahact, 3)).contains("against");
        }

        @Test
        void aMediocreMechFactionVotesForToShutTheStrongOnesDown() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");
            seat(FIFTH_ID, "mahact", "yellow");
            reveal(ARTICLES, "blue", 3, "red", 3, "green", 3, "yellow", 3, "black", 0);

            assertThat(ballot(hacan, 3)).contains("for");
        }

        @Disabled(
                "milestone 12: the boat float (everyone but one puts 1 vote For, one seat ties it Against, the speaker"
                        + " breaks for Against, the For voters each pass 1 TG to the Against seat)")
        @Test
        void joinsTheBoatFloat() {
            fail("expected the AI to cast exactly 1 vote For as part of an agreed boat float");
        }
    }

    // ---- Classified Document Leaks -----------------------------------------------------------------------------

    /** Review, Classified Document Leaks: you usually want your own secret chosen (about 3 TG). */
    @Nested
    class ClassifiedDocumentLeaks {

        private void ownAndRivalSecretScored() {
            sol.setSecretScored("gamf");
            xxcha.setSecretScored("otf");
            reveal("Law_Elect Secret Objective_7_classified", "blue", 3, "red", 3, "black", 0);
        }

        // Round 4 with the table at 4-5 points (about 2 rounds left): the rivals have little time to score the leaked
        // objective, so the freed slot wins (own secret about +1.8 TG, Xxcha's about -0.9 TG).
        @Test
        void votesForItsOwnScoredSecretMidGame() {
            test.game.setRound(4);
            points(sol, 5);
            points(xxcha, 5);
            points(nekro, 4);
            ownAndRivalSecretScored();

            assertThat(ownValue(sol, "classified", "gamf", "gamf"))
                    .isGreaterThan(ownValue(sol, "classified", "otf", "otf"));
            assertThat(ballot(sol, 3)).contains("gamf");
        }

        // Round 2 with five law rounds left: each rival's chance to score the leaked Gather a Mighty Fleet comes out at
        // the 50% "later" cap (no dreadnoughts on the board), worth exactly the 3 TG of the freed slot, so Sol is
        // indifferent between its own secret and Xxcha's and abstains.
        @Disabled("milestone 1e (follow-up): in round 2 the rivals' flat 50% later-score chance (MAX_LATER_CHANCE)"
                + " cancels the own secret's 3 TG, so the seat abstains instead of voting its own secret")
        @Test
        void votesForItsOwnScoredSecretEarly() {
            test.game.setRound(2);
            ownAndRivalSecretScored();

            assertThat(ballot(sol, 3)).contains("gamf");
        }
    }

    // ---- point laws leaving play (milestone 1a) ----------------------------------------------------------------

    /**
     * Review, Judicial Abolishment, Miscount Disclosed and New Constitution: Political Censure, Shard of the Throne and
     * Holy Planet of Ixth do not keep their point when the law leaves play, so discarding a rival's point law takes a
     * point away from it and discarding your own costs you one.
     */
    @Nested
    class PointLawsLeavingPlay {

        private static final String ABOLISHMENT = "Directive_Elect Law_7_abolishment";
        private static final String MISCOUNT = "Directive_Elect Law_7_miscount";
        private static final String CONSTITUTION = "Directive_For/Against_7_constitution";

        // Xxcha holds Political Censure and Sol holds Minister of War: Sol discards the Censure, not its own office.
        @Test
        void abolishesARivalsPoliticalCensure() {
            points(xxcha, 4);
            test.game.addLaw("censure", "xxcha");
            test.game.addLaw("minister_war", "sol");
            reveal(ABOLISHMENT, "blue", 3, "red", 3, "black", 0);

            assertThat(ownValue(sol, "abolishment", "censure", "censure")).isPositive();
            assertThat(ballot(sol, 3)).contains("censure");
        }

        @Test
        void abolishesARivalsShardOfTheThrone() {
            points(xxcha, 4);
            test.game.addLaw("shard_of_the_throne", "xxcha");
            test.game.addLaw("minister_war", "sol");
            reveal(ABOLISHMENT, "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).contains("shard_of_the_throne");
        }

        // Holy Planet of Ixth is elected onto a planet: whoever controls Lodor holds the point.
        @Test
        void abolishesARivalsHolyPlanet() {
            test.place("26", "000");
            xxcha.addPlanet("lodor");
            points(xxcha, 4);
            test.game.addLaw("holy_planet_of_ixth", "lodor");
            test.game.addLaw("minister_war", "sol");
            reveal(ABOLISHMENT, "blue", 3, "red", 3, "black", 0);

            assertThat(ownValue(sol, "abolishment", "holy_planet_of_ixth", "holy_planet_of_ixth"))
                    .isPositive();
            assertThat(ballot(sol, 3)).contains("holy_planet_of_ixth");
        }

        // Sol's own Shard is a point it would lose: it votes to discard Xxcha's office instead.
        @Test
        void neverAbolishesItsOwnShard() {
            test.game.addLaw("shard_of_the_throne", "sol");
            test.game.addLaw("minister_war", "xxcha");
            reveal(ABOLISHMENT, "blue", 3, "red", 3, "black", 0);

            assertThat(ownValue(sol, "abolishment", "shard_of_the_throne", "shard_of_the_throne"))
                    .isNegative();
            assertThat(ballot(sol, 3)).contains("minister_war");
        }

        // A revote on Xxcha's Censure may hand the point to someone else, so Sol picks it over its own office.
        @Test
        void miscountRevotesARivalsPoliticalCensure() {
            points(xxcha, 4);
            test.game.addLaw("censure", "xxcha");
            test.game.addLaw("minister_war", "sol");
            reveal(MISCOUNT, "blue", 3, "red", 3, "black", 0);

            assertThat(ownValue(sol, "miscount", "censure", "censure")).isPositive();
            assertThat(ballot(sol, 3)).contains("censure");
        }

        @Test
        void aNewConstitutionThatStripsARivalsPointLawsIsGood() {
            points(xxcha, 4);
            test.game.addLaw("censure", "xxcha");
            test.game.addLaw("shard_of_the_throne", "xxcha");

            assertThat(ownValue(sol, "constitution", "for", "for")).isPositive();
            assertThat(ownValue(xxcha, "constitution", "for", "for")).isNegative();
        }

        @Test
        void votesAgainstANewConstitutionThatStripsItsOwnShard() {
            test.game.addLaw("shard_of_the_throne", "sol");
            reveal(CONSTITUTION, "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).contains("against");
        }
    }

    // ---- elections by holder -----------------------------------------------------------------------------------

    /** Review, Elect Player: the holder-dependent numbers (hand quality, secrets, round, faction, game end). */
    @Nested
    class ElectionsByHolder {

        @Test
        void politicalCensureIsWorthFiveWithAWeakHandAndThreeWithAGoodOne() {
            assertThat(ownValue(sol, "censure", "sol", "sol")).isCloseTo(5, within(1e-9));
            sol.setActionCard("overrule");
            assertThat(ownValue(sol, "censure", "sol", "sol")).isCloseTo(3, within(1e-9));
        }

        // Two hard secrets in hand: both Search Warrant draws count, 2 TG each.
        @Test
        void searchWarrantIsWorthFourWithHardSecretsInHand() {
            sol.setSecret("ose");
            sol.setSecret("sb");

            assertThat(ownValue(sol, "warrant", "sol", "sol")).isCloseTo(4, within(1e-9));
        }

        // Dictate Policy with three laws in play is an easy action-phase secret: revealing it costs the holder.
        @Test
        void searchWarrantLosesValueWhenItRevealsAnEasyActionPhaseSecret() {
            test.game.getLaws().put("minister_war", 1);
            test.game.getLaws().put("conventions", 2);
            test.game.getLaws().put("regulations", 3);
            sol.setSecret("dp");

            assertThat(ownValue(sol, "warrant", "sol", "sol")).isLessThan(4);
        }

        @Test
        void committeeFormationIsFourTwoThenOne() {
            test.game.setRound(2);
            assertThat(ownValue(sol, "committee", "sol", "sol")).isEqualTo(4.0);
            test.game.setRound(3);
            assertThat(ownValue(sol, "committee", "sol", "sol")).isEqualTo(2.0);
            test.game.setRound(4);
            assertThat(ownValue(sol, "committee", "sol", "sol")).isEqualTo(1.0);
        }

        @Test
        void ministerOfPolicyIsWorthTwoMoreToYssaril() {
            Player yssaril = seat(FOURTH_ID, "yssaril", "yellow");

            assertThat(ownValue(sol, "minister_policy", "sol", "sol")).isEqualTo(1.0);
            assertThat(ownValue(yssaril, "minister_policy", "yssaril", "yssaril"))
                    .isEqualTo(3.0);
        }

        // Xxcha at 9 of 10 is about to end the game: Minister of Policy has no rounds left to pay out.
        @Test
        void ministerOfPolicyIsWorthNothingWhenTheGameEndsSoon() {
            Player yssaril = seat(FOURTH_ID, "yssaril", "yellow");
            test.game.setRound(7);
            points(xxcha, 9);
            assertThat(AgendaValueMath.endsSoon(test.game)).isTrue();

            assertThat(ownValue(sol, "minister_policy", "sol", "sol")).isZero();
            assertThat(ownValue(yssaril, "minister_policy", "yssaril", "yssaril"))
                    .isZero();
        }

        @Test
        void ministerOfIndustryIsWorthThreeToKeleresBeforeRoundFour() {
            Player keleres = seat(FOURTH_ID, "keleresa", "purple");
            test.game.setRound(3);
            assertThat(ownValue(keleres, "minister_industry", "keleresa", "keleresa"))
                    .isEqualTo(3.0);
            test.game.setRound(4);
            assertThat(ownValue(keleres, "minister_industry", "keleresa", "keleresa"))
                    .isEqualTo(1.0);
        }

        @Test
        void votesItselfImperialArbiter() {
            reveal("Law_Elect Player_7_arbiter", "blue", 3, "red", 2, "black", 0);

            assertThat(ownValue(sol, "arbiter", "sol", "sol")).isEqualTo(5.0);
            assertThat(ballot(sol, 3)).contains("sol");
        }
    }

    // ---- planet elections --------------------------------------------------------------------------------------

    /** Review, Elect Planet: Holy Planet is 3 TG; Colonial Redistribution and Compensated Disarmament hit the leader. */
    @Nested
    class PlanetElections {

        private static final String LODOR = "lodor";

        private Player hacan;
        private Tile lodorTile;

        // Lodor (cultural) beside Nekro's home; Hacan joins the table. Sol leads on 6 and Hacan trails on 2.
        @BeforeEach
        void board() {
            hacan = seat(FOURTH_ID, "hacan", "green");
            test.nekroHome();
            lodorTile = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
            points(sol, 6);
            points(hacan, 2);
            for (Player player : List.of(sol, hacan, xxcha)) player.setTg(8);
        }

        private void garrison(Player owner) {
            for (Player player : List.of(sol, hacan, xxcha)) {
                player.removePlanet(LODOR);
                lodorTile.removeAllUnits(player.getColor());
            }
            owner.addPlanet(LODOR);
            test.units(lodorTile, LODOR, owner, UnitType.Infantry, 2);
        }

        @Test
        void holyPlanetIsWorthThreeToItsOwner() {
            xxcha.addPlanet(LODOR);

            assertThat(ownValue(xxcha, "holy_planet_of_ixth", LODOR, LODOR)).isCloseTo(3, within(1e-9));
        }

        @Test
        void colonialRedistributionIsAimedAtTheLeader() {
            garrison(sol);
            double onLeader = ownValue(xxcha, "redistribution", LODOR, LODOR);
            garrison(hacan);
            double onTrailer = ownValue(xxcha, "redistribution", LODOR, LODOR);

            assertThat(onLeader).isGreaterThan(onTrailer);
        }

        // Xxcha has plenty of trade goods, so it aims Compensated Disarmament at the leader's garrison.
        @Test
        void compensatedDisarmamentIsAimedAtTheLeaderWhenTheVoterHasMoney() {
            garrison(sol);
            double onLeader = ownValue(xxcha, "disarmament", LODOR, LODOR);
            garrison(hacan);
            double onTrailer = ownValue(xxcha, "disarmament", LODOR, LODOR);

            assertThat(onLeader).isPositive().isGreaterThan(onTrailer);
        }

        // Short of trade goods, Xxcha would rather cash in its own infantry.
        @Test
        void compensatedDisarmamentOnItsOwnUnitsWhenShortOfMoney() {
            garrison(xxcha);
            xxcha.setTg(0);

            assertThat(ownValue(xxcha, "disarmament", LODOR, LODOR)).isPositive();
        }
    }

    // ---- vote power and spare votes ---------------------------------------------------------------------------

    /**
     * Review, Representative Government: For (one vote each) wipes out a vote-rich seat's edge, so Xxcha, with its
     * commander's extra vote on every planet, votes Against or abstains, never For. Vote power is real vote capacity,
     * not planet influence. Politics guidance: keep votes back on a first agenda nobody contests.
     */
    @Nested
    class VotePower {

        private static final String REP_GOVT = "Law_For/Against_7_representative_government";
        private static final String NEXUS = "Law_For/Against_7_nexus";

        // Xxcha and Sol hold the same influence (4), but Xxcha's commander makes it 6 votes against Sol's 4.
        private void evenInfluenceWithTheXxchaCommander() {
            test.place("14", "101");
            test.place("01", "304");
            test.place("26", "102");
            test.place("27", "103");
            xxcha.addPlanet("archonren");
            xxcha.addPlanet("archontau");
            sol.addPlanet("jord");
            sol.addPlanet("lodor");
            sol.addPlanet("newalbion");
            xxcha.addLeader("xxchacommander");
            xxcha.unsafeGetLeader("xxchacommander").setLocked(false);
        }

        private int votesOf(Player seat, String planet) {
            return ti4.helpers.AgendaHelper.getSpecificPlanetsVoteWorth(seat, test.game, planet);
        }

        @Test
        void aVoteRichSeatValuesRepresentativeGovernmentForBelowZero() {
            evenInfluenceWithTheXxchaCommander();
            reveal(REP_GOVT, "blue", 4, "red", 6, "black", 0);

            assertThat(VoteCapacity.of(test.game, xxcha)).isGreaterThan(VoteCapacity.of(test.game, sol));
            assertThat(ownValue(xxcha, "representative_government", "for", "for"))
                    .isNegative();
            assertThat(ownValue(sol, "representative_government", "for", "for")).isPositive();
        }

        @Test
        void aVoteRichSeatNeverVotesForRepresentativeGovernmentInRoundOne() {
            evenInfluenceWithTheXxchaCommander();
            test.game.setRound(1);
            test.game.setStoredValue("agendaCount", "1");
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(REP_GOVT, "blue", 4, "red", 6, "black", 0);

            assertThat(ballot(xxcha, votesOf(xxcha, "archonren"), votesOf(xxcha, "archontau")))
                    .isNotEqualTo(Optional.of("for"));
        }

        // Xxcha holds Mallice and wants Nexus Sovereignty; nobody else cares, so nobody else votes and the smallest
        // ballot Xxcha can cast already wins. It keeps the rest for the second agenda instead of piling on.
        @Test
        void castsNoMoreThanItNeedsOnAnUncontestedFirstAgenda() {
            xxcha.addPlanet("mallice");
            test.game.setStoredValue("agendaCount", "1");
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(NEXUS, "blue", 4, "red", 11, "black", 0);

            int votes = BallotPlanner.choose(test.game, xxcha, "nexus", List.of(4, 2, 3, 2))
                    .map(BallotPlanner.Choice::votes)
                    .orElse(0);

            assertThat(votes).isLessThanOrEqualTo(2);
        }

        // Swords to Plowshares trace: the speaker votes last on the first agenda with one 6-vote planet, facing 6
        // Against. Keeping a vote back is a price, not a cap: spending all 6 ties, and the speaker breaks the tie its
        // way, which is worth far more than a vote for the next agenda.
        @Test
        void theSpeakerSpendsItsLastVoteWhenItDecidesTheAgenda() {
            xxcha.addPlanet("mallice");
            test.game.setStoredValue("agendaCount", "1");
            reveal(NEXUS, "blue", 6, "red", 6, "black", 0);
            test.game.setCurrentAgendaVote("against", "blue_6");

            Optional<BallotPlanner.Choice> choice = BallotPlanner.choose(test.game, xxcha, "nexus", List.of(6));

            assertThat(choice.map(BallotPlanner.Choice::outcome)).contains("for");
            assertThat(choice.map(BallotPlanner.Choice::votes)).contains(6);
        }
    }

    // ---- For/Against laws by faction and position --------------------------------------------------------------

    /** Review, For/Against laws: the faction and board leanings behind each law. */
    @Nested
    class LawLeanings {

        @Test
        void nobodyGainsFromHomelandDefenseActFor() {
            for (Player voter : List.of(nekro, sol, xxcha))
                assertThat(ownValue(voter, "defense_act", "for", "")).isZero();
        }

        // Sol owns war suns and holds a hand: Against would discard the hand, so it votes For.
        @Test
        void aWarSunOwnerVotesForPublicizeWeaponSchematics() {
            sol.addTech("ws");
            hand(sol, "bribery", "cripple", "dh1");
            reveal("Law_For/Against_7_schematics", "blue", 3, "red", 3, "black", 0);

            assertThat(ownValue(sol, "schematics", "for", ""))
                    .isGreaterThan(ownValue(sol, "schematics", "against", ""));
            assertThat(ballot(sol, 3)).contains("for");
        }

        @Test
        void cabalLikesRegulatedConscriptionAndFighterHeavySolDislikesIt() {
            Player cabal = seat(FOURTH_ID, "cabal", "green");

            assertThat(ExpertLawValues.conscriptionGain(test.game, cabal, 0)).isPositive();
            assertThat(ExpertLawValues.conscriptionGain(test.game, sol, 0)).isNegative();
        }

        @Test
        void fleetRegulationsHurtLetnevLessThanOthers() {
            Player letnev = seat(FOURTH_ID, "letnev", "green");
            letnev.setFleetCC(5);
            xxcha.setFleetCC(5);

            assertThat(ExpertLawValues.fleetCapCost(test.game, letnev))
                    .isLessThan(ExpertLawValues.fleetCapCost(test.game, xxcha));
        }

        // Auto-Factories puts a command token in Hacan's fleet pool whenever it builds 3 ships; Fleet Regulations caps
        // the pool at 4, so Hacan never wants For, even with a small fleet pool.
        @Test
        void hacanVotesAgainstFleetRegulations() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");
            hacan.addBreakthrough("hacanbt");
            hacan.setFleetCC(3);
            xxcha.setFleetCC(3);
            reveal("Law_For/Against_7_regulations", "blue", 3, "red", 3, "green", 3, "black", 0);

            assertThat(ownValue(hacan, "regulations", "for", "for"))
                    .isLessThan(ownValue(hacan, "regulations", "against", "against") - 2);
            assertThat(ballot(hacan, 3)).contains("against");
        }

        // Mahact's Imperia works through other players' tokens in its fleet pool, so a 4-token cap hurts it a little
        // more than a seat with the same fleet pool.
        @Test
        void mahactDislikesFleetRegulationsABit() {
            Player mahact = seat(FOURTH_ID, "mahact", "green");
            mahact.setFleetCC(3);
            xxcha.setFleetCC(3);

            double extra =
                    ExpertLawValues.fleetCapCost(test.game, mahact) - ExpertLawValues.fleetCapCost(test.game, xxcha);
            assertThat(extra).isPositive().isLessThan(ExpertLawValues.fleetCapCost(test.game, xxcha) * 3);
        }

        @Test
        void creussVotesForTheTravelBan() {
            Player creuss = seat(FOURTH_ID, "ghost", "green");
            reveal("Law_For/Against_7_travel_ban", "blue", 3, "red", 3, "green", 3, "black", 0);

            assertThat(ownValue(creuss, "travel_ban", "for", "")).isPositive();
            assertThat(ballot(creuss, 3)).contains("for");
        }

        // Checks and Balances suits the last seat in speaker order and hurts the speaker.
        @Test
        void checksAndBalancesSuitsTheLastSeatAndHurtsTheSpeaker() {
            List<Player> order = ti4.helpers.Helper.getSpeakerOrFullPriorityOrder(test.game);

            assertThat(ownValue(order.getLast(), "checks", "for", "")).isPositive();
            assertThat(ownValue(order.getFirst(), "checks", "for", "")).isNegative();
        }

        @Test
        void theMalliceHolderAndCreussLikeNexusSovereignty() {
            Player creuss = seat(FOURTH_ID, "ghost", "green");
            assertThat(ownValue(creuss, "nexus", "for", "")).isPositive();
            assertThat(ownValue(sol, "nexus", "for", "")).isZero();

            sol.addPlanet("mallice");
            assertThat(ownValue(sol, "nexus", "for", "")).isPositive();
        }
    }

    // ---- For/Against directives by position --------------------------------------------------------------------

    /**
     * Politics notes after the traced self-play: "vote what the forecast assumed". A seat that feels safe only because
     * it assumes its allies cast X votes needs those allies to cast X; minimal ballots that each lean on everyone
     * else's full stack lose to a speaker who votes last.
     */
    @Nested
    class VoteWhatTheForecastAssumed {

        private void castInOrder(List<Player> order, int... stacks) {
            test.game.setPhaseOfGame("agendaVoting");
            for (int index = 0; index < order.size(); index++) {
                Player seat = order.get(index);
                test.game.setActivePlayerID(seat.getUserID());
                BallotPlanner.choose(
                                test.game,
                                seat,
                                AgendaPolicy.currentAgenda(test.game),
                                java.util.Collections.nCopies(stacks[index], 1))
                        .ifPresent(choice -> {
                            String earlier = test.game.getCurrentAgendaVotes().getOrDefault(choice.outcome(), "");
                            test.game.setCurrentAgendaVote(
                                    choice.outcome(),
                                    (earlier.isEmpty() ? "" : earlier + ";") + seat.getColor() + "_" + choice.votes());
                        });
            }
        }

        private int votesBy(String outcome, Player... seats) {
            int total = 0;
            for (String piece :
                    test.game.getCurrentAgendaVotes().getOrDefault(outcome, "").split(";")) {
                for (Player seat : seats) {
                    if (piece.startsWith(seat.getColor() + "_"))
                        total += Integer.parseInt(piece.substring(piece.indexOf('_') + 1));
                }
            }
            return total;
        }

        // The Mutiny of self-play game aiselfplay337972: Xxcha 6, Sardakk 4, Hacan 9, Letnev 5, then Sol (speaker) 5,
        // on the first agenda, with Xxcha and Hacan on a point each. In the game the For voters cast 1, 1 and 3, and
        // Sol
        // tied it with 5 Against and broke the tie Against. Played out now, the seats voting For before Letnev and Sol
        // put more on For than those two have left, so the speaker can neither tie nor win it.
        @Test
        void mutinyForVotersOutvoteWhatTheSpeakerAndLaterSeatsStillHold() {
            Player sardakk = seat(FOURTH_ID, "sardakk", "orange");
            Player hacan = seat(FIFTH_ID, "hacan", "yellow");
            Player letnev = seat("200000000000000005", "letnev", "purple");
            test.game.setRound(1);
            test.game.setStoredValue("agendaCount", "1");
            test.game.setSpeakerUserID(sol.getUserID());
            points(xxcha, 1);
            points(hacan, 1);
            reveal("Directive_For/Against_7_mutiny", "red", 6, "orange", 4, "yellow", 9, "purple", 5, "blue", 5);

            castInOrder(List.of(xxcha, sardakk, hacan, letnev, sol), 6, 4, 9, 5, 5);

            assertThat(votesBy("for", xxcha, sardakk, hacan)).isGreaterThan(10);
            assertThat(votesBy("for", xxcha, sardakk, hacan, letnev, sol))
                    .isGreaterThan(votesBy("against", xxcha, sardakk, hacan, letnev, sol));
        }
    }

    /**
     * Politics notes after the traced self-play (2026-11-12): vote prices by agenda, vote deals that pay only if the
     * outcome wins, and paying to kill a doomed loss.
     */
    @Nested
    class VoteAndDiscardDeals {

        private void board() {
            test.place("01", "304");
            sol.addPlanet("jord");
            test.place("14", "310");
            test.place("19", "311");
            xxcha.addPlanet("archonren");
            xxcha.addPlanet("archontau");
            xxcha.addPlanet("wellon");
        }

        // "On the second (last) agenda, a buyer buys all of a seller's votes for the same price as any number, since
        // unspent votes vanish."
        @Test
        void aLastAgendaDealBuysTheSellersWholeStack() {
            board();
            reveal("Law_Elect Player_7_shard_of_the_throne", "blue", 2, "red", 6, "black", 0);

            assertThat(VoteDeals.valued(test.game, nekro, xxcha, sol, "sol")
                            .orElseThrow()
                            .votes())
                    .isEqualTo(AgendaVoting.castableVotes(test.game, xxcha));
        }

        // "The usual deal is 'I pay you 1 TG to put your votes on For, if it resolves For': payment happens after
        // resolution and only when the outcome wins."
        @Test
        void aKeptVoteDealIsOwedOnlyWhenItsOutcomeWins() {
            reveal("Directive_For/Against_7_economic_equality", "blue", 2, "red", 6, "black", 0);
            Promise kept = Promise.offer(
                            test.game,
                            Promise.Term.VOTE_FOR,
                            xxcha,
                            nekro,
                            nekro,
                            PromisedVotes.agendaKey(test.game),
                            "for",
                            0,
                            1,
                            NOW,
                            NOW + 60_000)
                    .accepted(test.game)
                    .with(Promise.Status.KEPT);
            test.game.setStoredValue("resolvedAgendaId", "economic_equality");

            test.game.setStoredValue("resolvedAgendaOutcome", "against");
            assertThat(PromisedVotes.settlement(test.game, kept)).isEmpty();

            test.game.setStoredValue("resolvedAgendaOutcome", "for");
            assertThat(PromisedVotes.settlement(test.game, kept)).contains(Promise.Status.OWED);
        }

        // "A seat that will lose N TG to an agenda whatever it votes (Economic Equality going For) should offer up to
        // N TG for a Veto." Nekro, holding Veto and 7 trade goods, barely minds Economic Equality, so it keeps the
        // card; a pooled 3 trade good bounty from the rich seats makes the Veto worth playing.
        @Test
        void aVetoHolderPlaysForABountyThatCoversItsCard() {
            board();
            reveal("Directive_For/Against_7_economic_equality", "blue", 2, "red", 6, "black", 0);
            sol.setTg(12);
            xxcha.setTg(0);
            nekro.setTg(7);
            hand(nekro, "veto");
            OptionalDouble loss = AgendaPolicy.signedLoss(test.game, nekro);
            assertThat(loss.orElseThrow()).isLessThan(1.0);
            assertThat(AgendaCardRules.worthVetoing(test.game, nekro)).isFalse();

            DiscardBounties.save(
                    test.game,
                    new DiscardBounties.Bounty(
                            test.game.getRound(),
                            PromisedVotes.agendaKey(test.game),
                            sol.getFaction(),
                            DiscardBounties.VETO_CAP,
                            0,
                            0,
                            "",
                            -1,
                            DiscardBounties.Status.OPEN));

            assertThat(AgendaCardRules.worthVetoing(test.game, nekro)).isTrue();
        }
    }

    /** Review, For/Against directives: who likes which side of Incentive Program, Plowshares and Clandestine. */
    @Nested
    class DirectiveLeanings {

        private double forLean(Player voter, String agenda) {
            return ownValue(voter, agenda, "for", "") - ownValue(voter, agenda, "against", "");
        }

        // Sol leads by 3: Incentive For (a stage I) suits it more than it suits the trailing Xxcha.
        @Test
        void theLeaderLikesIncentiveProgramForMoreThanTheTrailer() {
            points(sol, 5);
            points(xxcha, 2);
            points(nekro, 2);

            assertThat(forLean(sol, "incentive")).isGreaterThan(forLean(xxcha, "incentive"));
        }

        @Test
        void hacanLeansAgainstIncentiveProgram() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");

            assertThat(forLean(hacan, "incentive")).isLessThan(forLean(sol, "incentive"));
        }

        @Test
        void aContenderLikesSwordsToPlowsharesMore() {
            double before = ownValue(sol, "plowshares", "for", "");
            points(sol, 9);

            assertThat(ownValue(sol, "plowshares", "for", "")).isGreaterThan(before);
        }

        // Against drops every fleet pool by one: Xxcha with three cruisers on a 3-token fleet pool loses a ship as
        // well as the token, so it dislikes Against while Sol, with no fleet out, likes it.
        @Test
        void clandestineOperationsAgainstHurtsTheSeatOverItsNewFleetLimit() {
            test.nekroHome();
            Tile lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
            for (Player seat : List.of(nekro, sol, xxcha)) {
                seat.setTacticalCC(3);
                seat.setFleetCC(3);
                seat.setStrategicCC(2);
            }
            test.units(lodor, "space", xxcha, UnitType.Cruiser, 3);

            assertThat(ownValue(xxcha, "cladenstine", "against", "")).isNegative();
            assertThat(ownValue(sol, "cladenstine", "against", "")).isPositive();
        }

        // Rearmament Against turns every mech on the board into infantry: Arborec with three mechs out fights it.
        @Test
        void aSeatWithSeveralMechsOutDislikesRearmamentAgainst() {
            Player arborec = seat(FOURTH_ID, "arborec", "green");
            Tile field = test.place("26", "000");
            test.units(field, "space", arborec, UnitType.Mech, 3);

            assertThat(ownValue(arborec, "rearmament", "against", "")).isNegative();
        }

        // Galactic Crisis Pact: Diplomacy's secondary does nothing in the agenda phase.
        @Test
        void diplomacyIsWorthNothingOnGalacticCrisisPact() {
            assertThat(ownValue(sol, "crisis", "2", "2")).isZero();
        }
    }

    // ---- not implemented yet -----------------------------------------------------------------------------------

    /** Politics guidance and timing notes the AI does not follow yet. */
    @Nested
    class ToDo {

        @Disabled("milestone 3: a Dictate Policy holder pushes laws For until 3 are in play, even mildly bad ones")
        @Test
        void aDictatePolicyHolderVotesForAMildlyBadLaw() {
            sol.setSecret("dp");
            test.game.addLaw("regulations", null);
            test.game.addLaw("travel_ban", null);
            reveal("Law_For/Against_7_conventions", "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).contains("for");
        }

        @Disabled("milestone 3: a Covert Legislation non-speaker values the announced target type from the deck"
                + " composition, never the hidden card")
        @Test
        void aCovertNonSpeakerDoesNotReadTheHiddenAgenda() {
            fail("expected the non-speaker's Covert value to stay the same when the hidden agenda is swapped");
        }

        @Test
        void opponentsKeepAVoteBackOnTheFirstAgenda() {
            test.game.setStoredValue("agendaCount", "1");
            test.place("14", "000");
            xxcha.addPlanet("archonren");
            xxcha.addPlanet("archontau");
            int all = ti4.helpers.AgendaHelper.getSpecificPlanetsVoteWorth(xxcha, test.game, "archonren")
                    + ti4.helpers.AgendaHelper.getSpecificPlanetsVoteWorth(xxcha, test.game, "archontau");
            reveal("Law_Elect Player_7_minister_war", "blue", 3, "red", all, "black", 0);

            assertThat(AgendaForecast.of(test.game, sol).votesLeft(xxcha)).isLessThan(all);
        }

        @Disabled("milestone 10: when the AI cares about an outcome it buys votes (often 1 TG each) from seats that"
                + " don't care")
        @Test
        void buysVotesFromAnIndifferentSeat() {
            fail("expected the AI to offer TG for an indifferent seat's votes on an outcome it cares about");
        }

        @Disabled("milestone 6: Committee Formation is worth 2 TG more when the holder knows a good Elect Player agenda"
                + " is next")
        @Test
        void committeeFormationIsWorthMoreWhenAGoodElectionIsKnownToBeNext() {
            fail("expected +2 TG on Committee Formation after a Politics peek shows a good Elect Player agenda");
        }

        @Disabled(
                "milestone 4a: the forecast's voting order follows AgendaHelper.getVotingOrder, so Zeal (Argent first)"
                        + " never zeroes the votes of seats that have not voted yet")
        @Test
        void seatsAfterAnArgentZealVoterKeepTheirVotes() {
            fail("expected the seats after Argent in the Zeal voting order to keep their votes in the forecast");
        }

        @Disabled("milestone 4d: the Argent AI casts its Zeal votes, and votes light on the first agenda")
        @Test
        void argentCastsZealVotes() {
            fail("expected the Argent AI to press exhaustForVotes_zeal_N and count those votes in its plan");
        }

        @Disabled("milestone 4e: Blood Pact is cast when both seats vote the same outcome, and the Xxcha hero's votes"
                + " when they are decisive")
        @Test
        void castsBloodPactAndTheXxchaHeroWhenTheyDecideTheOutcome() {
            fail("expected Blood Pact on a shared outcome and the Xxcha hero's votes when decisive");
        }

        @Disabled(
                "milestone 5b: rider payoffs value Warfare (dreadnought), Construction (space dock), Diplomacy and the"
                        + " Keleres Rider (1 action card + 2 TG)")
        @Test
        void ridersArePricedByWhatTheyActuallyPay() {
            fail("expected RiderPayoff to value each rider by its real payout");
        }

        @Disabled("milestone 5d: Warfare, Construction and Diplomacy rider payouts are pressed by the AI")
        @Test
        void collectsWarfareConstructionAndDiplomacyRiderPayouts() {
            fail("expected the AI to press the Warfare, Construction and Diplomacy rider payout buttons");
        }

        @Disabled("milestone 7: Predictive Intelligence is pressed only when its 3 votes change the outcome")
        @Test
        void playsPredictiveIntelligenceOnlyWhenDecisive() {
            fail("expected Predictive Intelligence to stay ready unless +3 votes materially change the outcome");
        }

        @Disabled("milestone 7: while holding Bribery the AI casts at least 1 vote and pays the smallest TG that flips"
                + " or secures the outcome")
        @Test
        void castsAtLeastOneVoteWhileHoldingBribery() {
            fail("expected a ballot of at least 1 vote and a Bribery payment that just secures the outcome");
        }

        @Disabled("milestone 8: Deadly Plot is played when the winning outcome's loss beats the card and the planets it"
                + " exhausts")
        @Test
        void playsDeadlyPlotAgainstAVeryBadOutcome() {
            fail("expected Deadly Plot when the winning outcome costs more than the card plus the exhausted planets");
        }

        @Disabled("milestone 8: Assassinate Representative targets the rival whose removal helps most, strongest on"
                + " Mutiny, and skips Xxcha seats that can still vote")
        @Test
        void assassinatesTheRepresentativeWhoseVotesMatterMost() {
            fail("expected Assassinate Representative on the rival whose votes change the outcome most");
        }

        @Disabled("milestone 8: Confounding Legal Text steals an election worth more than the card (Censure, Arbiter)")
        @Test
        void playsConfoundingLegalTextToStealPoliticalCensure() {
            fail("expected Confounding Legal Text when electing itself to Censure is worth more than the card");
        }

        @Disabled("milestone 9: Mutiny, abstain when later or stronger voters could punish; otherwise go For if it"
                + " cannot win")
        @Test
        void abstainsOnMutinyWhenTheSpeakerCanPunish() {
            fail("expected an abstain ballot on Mutiny when the voters after it could flip the outcome");
        }

        @Disabled("milestone 9: Seed of an Empire leans Against unless the AI's own side can form a majority block")
        @Test
        void seedOfAnEmpireLeansAgainstWithoutAMajorityBlock() {
            fail("expected Against on Seed of an Empire when the AI's points block cannot win the vote");
        }

        @Disabled("milestone 9: Unconventional Measures follows the Mutiny rule with lower stakes")
        @Test
        void unconventionalMeasuresFollowsTheMutinyRule() {
            fail("expected the Mutiny abstain/For rule on Unconventional Measures");
        }

        @Disabled("milestone 9: on Miscount Disclosed the AI counts the votes left and puts a few votes on a winnable"
                + " outcome to steer the table")
        @Test
        void steersAMiscountRevoteTowardsAWinnableOutcome() {
            fail("expected a few votes on a winnable outcome of the Miscount revote");
        }

        @Disabled("milestone 13: the Imperial Arbiter holder keeps it until the likely last round, then swaps a poor"
                + " last-round strategy card (4, 5 or 7) to a rival")
        @Test
        void savesImperialArbiterForTheLastRound() {
            fail("expected the Arbiter swap only when GameHorizon.roundsLeft <= 1");
        }

        @Disabled("milestone 13: the Prophecy of Ixth holder values fighters with the extra roll when it builds")
        @Test
        void prophecyOfIxthRaisesFighterValueForItsHolder() {
            fail("expected production planners to add the Prophecy roll to fighter value for the holder");
        }
    }
}
