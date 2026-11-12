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
import ti4.ai.xxcha.QuashRules;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * The expert exam: hand-built table positions, one or more per bullet of the user's agenda guidance notes
 * (ai-agenda-value-review, ai-agenda-politics-guidance, ai-agenda-timing-windows), each asserting the answer an expert
 * player gives. Every case runs with the AGENDA_VALUE_TABLE and GALACTIC_THREAT_COST experiments on (they are on by
 * default; the setup asserts it).
 *
 * <p>Cases for behaviour the AI does not have yet are {@link Disabled} with the plan milestone (docs/ai/AGENDA_PLAN.md)
 * that delivers it, so the exam doubles as a to-do list. A case disabled with "fails today" is a guidance bullet the AI
 * is supposed to follow already but gets wrong.
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

        @Test
        void neverVotesAgainstOnConventions() {
            hand(sol, "sabo1", "sabo2", "veto");
            reveal(CONVENTIONS, "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).isNotEqualTo(Optional.of("against"));
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
        void neverVotesAgainstOnRepresentativeGovernment() {
            test.place("26", "000");
            sol.addPlanet("lodor");
            reveal(REP_GOVT, "blue", 3, "red", 3, "black", 0);

            assertThat(ballot(sol, 3)).isNotEqualTo(Optional.of("against"));
        }

        @Disabled("milestone 4b: the speaker's tie-break is judged on abstained values, so Against is free for it")
        @Test
        void theSpeakerPicksAgainstForFreeOnConventions() {
            hand(sol, "sabo1", "sabo2", "veto");
            test.game.setSpeakerUserID(sol.getUserID());
            reveal(CONVENTIONS, "blue", 0, "red", 0, "black", 0);

            assertThat(tieBreak(sol, "for", "against")).contains("against");
        }

        @Disabled("milestone 4b: the forecast gives seats a real abstain choice, so an indifferent table abstains and"
                + " Against wins on the speaker's tie-break")
        @Test
        void theForecastExpectsAgainstWhenTheTableIsIndifferent() {
            hand(sol, "sabo1", "sabo2", "veto");
            for (Player seat : List.of(nekro, xxcha)) anonymousHand(seat, 3);
            reveal(CONVENTIONS, "blue", 3, "red", 3, "black", 0);

            assertThat(AgendaForecast.of(test.game, sol).chance("against")).isGreaterThan(0.9);
        }

        @Disabled("milestone 4b: the AI non-speaker abstains on Wormhole Research when the speaker gains from Against")
        @Test
        void abstainsOnWormholeResearchAndLetsTheSpeakerPickAgainst() {
            fail("expected an abstain ballot on Wormhole Research when Against is the speaker's free pick");
        }
    }

    /** Review, Articles of War, pattern (1): strong-mech factions vote Against, mediocre-mech factions vote For. */
    @Nested
    class ArticlesOfWar {

        private static final String ARTICLES = "Law_For/Against_7_articles_war";

        @Test
        void aStrongMechFactionDislikesFor() {
            Player letnev = seat(FOURTH_ID, "letnev", "green");
            reveal(ARTICLES, "blue", 3, "red", 3, "green", 3, "black", 0);

            assertThat(ownValue(letnev, "articles_war", "for", "")).isNegative();
        }

        // Letnev values For at only about -2.9 TG once the rivals' smaller losses are netted off, which is no more than
        // the 3 TG every For voter collects when Against wins. So it votes For as a hedge, and its own 3 votes push the
        // law towards passing.
        @Disabled("fails today: the strong-mech seat votes For to collect the 3 TG For-voter payout (For valued about"
                + " -2.9 TG relative to rivals, payout +3 TG); the expert votes Against")
        @Test
        void aStrongMechFactionVotesAgainst() {
            Player letnev = seat(FOURTH_ID, "letnev", "green");
            reveal(ARTICLES, "blue", 3, "red", 3, "green", 3, "black", 0);

            assertThat(ballot(letnev, 3)).contains("against");
        }

        @Test
        void aMediocreMechFactionVotesForToShutTheStrongOnesDown() {
            Player hacan = seat(FOURTH_ID, "hacan", "green");
            seat(FIFTH_ID, "letnev", "yellow");
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
        @Disabled("fails today: in round 2 the rivals' flat 50% later-score chance (MAX_LATER_CHANCE) cancels the own"
                + " secret's 3 TG, so the seat abstains instead of voting its own secret")
        @Test
        void votesForItsOwnScoredSecretEarly() {
            test.game.setRound(2);
            ownAndRivalSecretScored();

            assertThat(ballot(sol, 3)).contains("gamf");
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

        @Disabled("milestone 4c: on the first agenda the forecast has opponents keep a vote back for the second")
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
    }
}
