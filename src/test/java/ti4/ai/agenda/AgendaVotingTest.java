package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AgendaVotingTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.place("01", "304");
        sol.addPlanet("jord");
        // The second agenda of the phase, so no vote has to be kept back for a later one.
        test.game.setStoredValue("agendaCount", "2");
        test.isActive(sol, "agendaVoting");
    }

    private String press(AiPrompt... prompts) {
        return AgendaVoting.next(test.contextFor(sol, java.util.Set.of(), NOW, prompts))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    // An agenda that gives the elected player a victory point: it votes, elects itself, spends its planets, then
    // confirms.
    @Test
    void electsItselfForShardOfTheThroneWithAllItsVotes() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        assertThat(press(start)).isEqualTo("FFCC_sol_vote");

        AiPrompt outcomes = prompt("outcomes", PromptSource.PUBLIC, NOW, "outcome_nekro", "outcome_sol");
        assertThat(press(outcomes)).isEqualTo("outcome_sol");

        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of("exhaustForVotes_planet_jord", "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord (4)", "Done exhausting planets."));
        assertThat(press(planets)).isEqualTo("exhaustForVotes_planet_jord");

        // The bot records a planet spent for votes as "planet_<name>".
        sol.addSpentThing("planet_jord");
        assertThat(press(planets)).isEqualTo("FFCC_sol_proceedToFinalizingVote");

        AiPrompt confirm = prompt("confirm", PromptSource.PUBLIC, NOW, "FFCC_sol_resolveAgendaVote_4");
        assertThat(press(confirm)).isEqualTo("FFCC_sol_resolveAgendaVote_4");
    }

    // Predictive Intelligence adds 3 votes but stays exhausted when its outcome loses, so it is used only when the 3
    // votes decide the outcome. Xxcha already cast 4 votes for itself: Jord's 2 lose, Jord plus the card's 3 win.
    @Test
    void addsPredictiveIntelligenceVotesWhenTheyDecideTheOutcome() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setSpeakerUserID(sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":5,\"red\":4,\"black\":0}");
        test.game.setCurrentAgendaVote("xxcha", "red_4");
        sol.addTech("pi");
        assertThat(everythingSolHas()).isEqualTo(2);
        assertThat(AgendaVoting.plan(test.game, sol)).contains(new AgendaVoting.Ballot("sol", "", 5, true));

        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        assertThat(press(start)).isEqualTo("FFCC_sol_vote");
        AiPrompt planets = predictivePrompt();
        assertThat(press(planets)).isEqualTo("exhaustForVotes_predictive_3");

        sol.addSpentThing("predictive_3");
        assertThat(press(planets)).isEqualTo("exhaustForVotes_planet_jord");
    }

    // Alone on the agenda Sol wins with Jord's votes whatever it does, so it keeps Predictive Intelligence ready.
    @Test
    void keepsPredictiveIntelligenceWhenItsVotesDecideNothing() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        sol.addTech("pi");
        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::predictive))
                .contains(false);

        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        assertThat(press(start)).isEqualTo("FFCC_sol_vote");
        assertThat(press(predictivePrompt())).isEqualTo("exhaustForVotes_planet_jord");
    }

    // A promise for 3 votes or fewer never uses the card, since its 3 votes only add to at least one planet vote; a
    // promise for more counts them.
    @Test
    void aSmallVotePromiseNeverAddsPredictiveIntelligence() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        sol.addTech("pi");
        ti4.ai.promise.PromiseBook.save(test.game, promiseOfVotes(1).accepted(test.game));

        assertThat(AgendaVoting.plan(test.game, sol)).contains(new AgendaVoting.Ballot("nekro", "", 1, false));
    }

    @Test
    void aPromiseOfThreeVotesCastsPlanetsWithoutPredictiveIntelligence() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");
        sol.addTech("pi");
        assertThat(AgendaVoting.castableVotes(test.game, sol)).isGreaterThanOrEqualTo(3);
        ti4.ai.promise.PromiseBook.save(test.game, promiseOfVotes(3).accepted(test.game));

        assertThat(AgendaVoting.plan(test.game, sol)).contains(new AgendaVoting.Ballot("nekro", "", 3, false));
    }

    // Predictive Intelligence adds 3 votes to votes cast, so it never makes a ballot alone: once the card is used, a
    // seat that has spent no planet still exhausts one, even when the card's 3 already cover its planned votes. The
    // seat here plays without the expert value table, which uses the card on every ballot (the self-play Mutiny
    // ballot of 3 card votes and no planet).
    @Test
    void predictiveIntelligenceNeverCastsWithoutAPlanetVote() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaCount", "1");
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");
        ti4.ai.AiExperiments.disable(sol, java.util.Set.of(ti4.ai.AiExperiments.Experiment.AGENDA_VALUE_TABLE));
        sol.addTech("pi");
        AgendaVoting.Ballot ballot = new AgendaVoting.Ballot("sol", "", 1, false);
        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "exhaustForVotes_planet_jord",
                        "exhaustForVotes_planet_wellon",
                        "exhaustForVotes_predictive_3",
                        "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord (4)", "Wellon (2)", "Use Predictive Intelligence Votes (3)", "Done exhausting planets."));
        assertThat(pressWith(ballot, planets)).isEqualTo("exhaustForVotes_predictive_3");

        sol.addSpentThing("predictive_3");
        assertThat(pressWith(ballot, planets)).startsWith("exhaustForVotes_planet_");
    }

    private String pressWith(AgendaVoting.Ballot ballot, AiPrompt... prompts) {
        ti4.ai.brain.AiTurnContext context = test.contextFor(sol, java.util.Set.of(), NOW, prompts);
        context.memory().put("agendaBallot|" + test.game.getCurrentAgendaInfo(), ballot.encode());
        return AgendaVoting.next(context).map(AiTestGame::pressedId).orElse("");
    }

    // With no planet it may exhaust (its only planet is kept back for the second agenda), the card is never used.
    @Test
    void predictiveIntelligenceWaitsForAPlanetItMayExhaust() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaCount", "1");
        ti4.ai.AiExperiments.disable(sol, java.util.Set.of(ti4.ai.AiExperiments.Experiment.AGENDA_VALUE_TABLE));
        sol.addTech("pi");
        AgendaVoting.Ballot ballot = new AgendaVoting.Ballot("sol", "", 1, false);

        assertThat(pressWith(ballot, predictivePrompt())).isEqualTo("FFCC_sol_proceedToFinalizingVote");
    }

    @Test
    void aPromiseOfMoreThanThreeVotesCountsPredictiveIntelligenceInThem() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");
        sol.addTech("pi");
        assertThat(AgendaVoting.castableVotes(test.game, sol)).isGreaterThanOrEqualTo(4);
        ti4.ai.promise.PromiseBook.save(test.game, promiseOfVotes(4).accepted(test.game));

        assertThat(AgendaVoting.plan(test.game, sol)).contains(new AgendaVoting.Ballot("nekro", "", 4, true));
    }

    private ti4.ai.promise.Promise promiseOfVotes(int votes) {
        return ti4.ai.promise.Promise.offer(
                test.game,
                ti4.ai.promise.Promise.Term.VOTE_FOR,
                sol,
                test.nekro,
                test.nekro,
                PromisedVotes.agendaKey(test.game),
                "nekro",
                votes,
                1,
                1,
                60_000);
    }

    private AiPrompt predictivePrompt() {
        return prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "exhaustForVotes_planet_jord",
                        "exhaustForVotes_predictive_3",
                        "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord (4)", "Use Predictive Intelligence Votes (3)", "Done exhausting planets."));
    }

    // Galactic Threat: Nekro cannot vote, so it never plans a ballot, even on an agenda it would like to win and
    // with planets that have influence.
    @Test
    void nekroNeverPlansABallot() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.place("19", AiTestGame.neighbourOf("304"));
        test.nekro.addPlanet("wellon");

        assertThat(AgendaVoting.plan(test.game, test.nekro)).isEmpty();
        assertThat(AgendaVoting.plan(test.game, sol)).isPresent();
    }

    // Planet agendas restricted to a trait only take a planet with that trait, even though the card's target just
    // says "Elect Planet": a Research Team: Biotic goes on industrial Wellon, never on Jord, and Holy Planet of Ixth
    // (cultural) has no eligible planet of Sol's to vote for.
    @Test
    void votesOnlyForPlanetsWithTheRequiredTrait() {
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");

        test.game.setCurrentAgendaInfo("Law_Elect Planet_7_rt_biotic");
        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .contains("wellon");

        test.game.setCurrentAgendaInfo("Law_Elect Planet_8_holy_planet_of_ixth");
        assertThat(AgendaVoting.plan(test.game, sol)).isEmpty();
    }

    // Execution Directive punishes whoever is elected, so Sol would never elect itself without Drive the Debate; with
    // it,
    // the election is worth a point. Hacan leads on points and Xxcha, the rival, puts its votes on the leader, so only
    // Sol's votes can stop that.
    private void driveTheDebateElection(int solVotes, int rivalVotes) {
        Player leader = test.addSeat("200000000000000003", "hacan", "green");
        test.addSeat("200000000000000002", "xxcha", "red");
        test.game.scorePublicObjective(leader.getUserID(), test.game.addCustomPO("Test points", 2));
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setSpeakerUserID(sol.getUserID());
        test.game.setCurrentAgendaInfo("Directive_Elect Player_7_execution");
        test.game.setStoredValue(
                "agendaStartVoteCounts",
                "{\"blue\":" + solVotes + ",\"red\":" + rivalVotes + ",\"green\":0,\"black\":0}");
        sol.setSecret("dtd");
    }

    private int everythingSolHas() {
        return ti4.helpers.AgendaHelper.getSpecificPlanetsVoteWorth(sol, test.game, "jord");
    }

    // Sol holds Drive the Debate but Xxcha has six times its votes and is backing the leader: on the first agenda Sol
    // cannot win the election, so it does not spend its votes on itself (it can still back the leader, as it would
    // without the card).
    @Test
    void castsNoVotesInAnElectionItCannotWinWithDriveTheDebate() {
        driveTheDebateElection(everythingSolHas(), everythingSolHas() * 6);
        test.game.setStoredValue("agendaCount", "1");

        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .isNotEqualTo(Optional.of("sol"));
    }

    // The same election with the votes on Sol's side, on the first agenda of the phase: it spends only enough to beat
    // the two votes against it (a tie goes to Sol as speaker), not everything it has, and keeps a planet back.
    @Test
    void spendsOnlyEnoughToWinTheFirstAgendaWithDriveTheDebate() {
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");
        int everything =
                everythingSolHas() + ti4.helpers.AgendaHelper.getSpecificPlanetsVoteWorth(sol, test.game, "wellon");
        driveTheDebateElection(everything, 2);
        test.game.setStoredValue("agendaCount", "1");

        Optional<AgendaVoting.Ballot> ballot = AgendaVoting.plan(test.game, sol);

        assertThat(ballot.map(AgendaVoting.Ballot::outcome)).contains("sol");
        assertThat(ballot.map(AgendaVoting.Ballot::votes).orElse(0)).isBetween(2, everything - 1);
    }

    // On the second agenda there is nothing left to save votes for, so the same election gets everything.
    @Test
    void spendsEverythingOnTheSecondAgenda() {
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");
        int everything =
                everythingSolHas() + ti4.helpers.AgendaHelper.getSpecificPlanetsVoteWorth(sol, test.game, "wellon");
        driveTheDebateElection(everything, 2);

        assertThat(AgendaVoting.plan(test.game, sol)).contains(new AgendaVoting.Ballot("sol", "", everything));
    }

    // On the first agenda the last planet with votes is always kept for the second agenda (which could be Mutiny, or
    // anything else where voting matters), even for a point: with only Jord, Sol casts nothing.
    @Test
    void keepsItsOnlyPlanetForTheSecondAgenda() {
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");

        assertThat(AgendaVoting.plan(test.game, sol)).isEmpty();
    }

    // Once Mutiny is in the discard pile no later agenda can force a vote, so the first agenda may use the last planet.
    @Test
    void spendsItsOnlyPlanetOnTheFirstAgendaOnceMutinyIsDiscarded() {
        test.game.setStoredValue("agendaCount", "1");
        test.game.getDiscardAgendas().put("mutiny", 1);
        test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"red\":1,\"black\":0}");
        test.game.setCurrentAgendaVote("xxcha", "red_1");

        assertThat(AgendaVoting.plan(test.game, sol))
                .hasValueSatisfying(ballot -> assertThat(ballot.outcome()).isEqualTo("sol"));
    }

    // With two planets on the first agenda it may cast votes, but never exhausts the last ready planet with votes:
    // once Wellon is spent, it finishes instead of pressing Jord.
    @Test
    void neverExhaustsTheLastPlanetOnTheFirstAgenda() {
        test.game.setStoredValue("agendaCount", "1");
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");
        test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":6,\"red\":1,\"black\":0}");
        test.game.setCurrentAgendaVote("xxcha", "red_1");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        assertThat(press(start)).isEqualTo("FFCC_sol_vote");
        AiPrompt outcomes = prompt("outcomes", PromptSource.PUBLIC, NOW, "outcome_nekro", "outcome_sol");
        assertThat(press(outcomes)).isEqualTo("outcome_sol");
        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "exhaustForVotes_planet_jord",
                        "exhaustForVotes_planet_wellon",
                        "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord", "Wellon", "Done exhausting planets."));
        String first = press(planets);
        assertThat(first).startsWith("exhaustForVotes_planet_");

        sol.addSpentThing("planet_" + first.substring("exhaustForVotes_planet_".length()));
        assertThat(press(planets)).isEqualTo("FFCC_sol_proceedToFinalizingVote");
    }

    // Without Drive the Debate Sol never elects itself to Execution Directive, however many votes it has.
    @Test
    void neverElectsItselfToExecutionDirectiveWithoutDriveTheDebate() {
        driveTheDebateElection(everythingSolHas(), 2);
        sol.removeSecret(sol.getSecretsUnscored().get("dtd"));

        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .isNotEqualTo(Optional.of("sol"));
    }

    private static final String ARMS_REDUCTION = "Directive_For/Against_7_arms_reduction";

    // A rival one point from winning, and an agenda the seat has no view on: Sol has votes and the rival has a rider,
    // so it casts no votes of its own.
    private void rivalRacesOnForWith(String rider) {
        Player rival = test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setVp(10);
        test.game.scorePublicObjective(rival.getUserID(), test.game.addCustomPO("Test points", 9));
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"red\":0,\"black\":0}");
        test.game.setCurrentAgendaVote("for", "red_" + rider);
    }

    // A rival's Imperial Rider on "for" would win the rival the game; the AI, which has no view on the directive, votes
    // it down with everything it has.
    @Test
    void votesDownARivalsImperialRiderWithAllItsVotes() {
        rivalRacesOnForWith("Imperial Rider");

        int everything = ti4.helpers.AgendaHelper.getSpecificPlanetsVoteWorth(sol, test.game, "jord");
        assertThat(everything).isPositive();
        assertThat(AgendaVoting.plan(test.game, sol)).contains(new AgendaVoting.Ballot("against", "", everything));
    }

    @Test
    void leavesTheVoteAloneWithoutARivalsImperialRider() {
        // Other rival riders are weighed by RIVAL_RIDER_STAKES (see the RivalRiders exams); this case is the Imperial
        // stake alone.
        ti4.ai.AiExperiments.disable(sol, java.util.Set.of(ti4.ai.AiExperiments.Experiment.RIVAL_RIDER_STAKES));
        rivalRacesOnForWith("Trade Rider");

        assertThat(AgendaVoting.plan(test.game, sol)).isEmpty();

        test.game.setCurrentAgendaVote("for", "blue_Imperial Rider");
        assertThat(AgendaVoting.plan(test.game, sol)).isEmpty();
    }

    // Mutiny pays a point to everyone who voted "for" when it passes, so with Sol's votes deciding it alone, Sol
    // votes "for".
    @Test
    void votesForMutinyWhenItsVotesCarryIt() {
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_mutiny");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"black\":0}");

        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .contains("for");
    }

    // But a rival one point from winning holds an Imperial Rider on "for": Sol's own point is not worth the rival's
    // win, so it votes "against" instead.
    @Test
    void votesMutinyDownWhenARivalsImperialRiderRidesOnFor() {
        rivalRacesOnForWith("Imperial Rider");
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_mutiny");

        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .contains("against");
    }

    // The speaker's tie-break buttons share the confirm prefix; a ballot left in memory must not press them.
    @Test
    void neverConfirmsVotesOnATieBreakButton() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        press(start);

        AiPrompt tie = prompt("tie", PromptSource.PUBLIC, NOW, "FFCC_sol_resolveAgendaVote_outcomeTie*sol");
        assertThat(press(tie)).isEmpty();
    }

    // With no preference it leaves the vote to the abstain rule.
    @Test
    void abstainsWhenNoOutcomeMatters() {
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_arms_reduction");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");

        assertThat(press(start)).isEmpty();
    }

    // After a rider the bot can show the outcomes straight away; with nothing to gain the AI picks any outcome and
    // casts no votes, which the bot records as an abstention.
    @Test
    void castsNoVotesWhenTheOutcomesAreOfferedDirectly() {
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_arms_reduction");
        AiPrompt outcomes =
                prompt("outcomes", PromptSource.PUBLIC, NOW, "FFCC_sol_outcome_for", "FFCC_sol_outcome_against");
        assertThat(press(outcomes)).isEqualTo("FFCC_sol_outcome_for");

        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of("exhaustForVotes_planet_jord", "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord (4)", "Done exhausting planets."));
        assertThat(press(planets)).isEqualTo("FFCC_sol_proceedToFinalizingVote");
    }

    // Shard of the Throne: Xxcha has already cast 6 votes for itself and Sol's Jord (influence 2) has 2. Gila the
    // Silvertongue (the Hacan commander) casts 2 more votes per trade good, so 3 trade goods (8 votes) win Sol the
    // point; a 4th would buy nothing.
    private void outvotedForTheShard(int tradeGoods) {
        Player xxcha = test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":2,\"red\":6,\"black\":0}");
        test.game.setCurrentAgendaVote("xxcha", "red_6");
        sol.addLeader("hacancommander");
        sol.unsafeGetLeader("hacancommander").setLocked(false);
        sol.setTg(tradeGoods);
        assertThat(xxcha.getFaction()).isEqualTo("xxcha");
    }

    @Test
    void gilaSpendsJustEnoughTradeGoodsToSwingAPoint() {
        outvotedForTheShard(6);

        assertThat(GilaVotes.tradeGoodsToSpend(test.game, sol, "sol", 2)).isEqualTo(3);
    }

    // With Gila, Jord spent and 3 trade goods planned, it spends them one press at a time, then finishes.
    @Test
    void gilaPressesForEachTradeGoodAfterThePlanets() {
        outvotedForTheShard(6);
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        assertThat(press(start)).isEqualTo("FFCC_sol_vote");
        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "exhaustForVotes_planet_jord",
                        "exhaustForVotes_hacanCommanderTg",
                        "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord (2)", "Spend Trade Goods for 2 Votes Each", "Done exhausting planets."));
        assertThat(press(planets)).isEqualTo("exhaustForVotes_planet_jord");
        sol.addSpentThing("planet_jord");

        assertThat(press(planets)).isEqualTo("exhaustForVotes_hacanCommanderTg");
        sol.setTg(5);
        sol.increaseTgsSpentThisWindow(1);
        assertThat(press(planets)).isEqualTo("exhaustForVotes_hacanCommanderTg");
        sol.setTg(4);
        sol.increaseTgsSpentThisWindow(1);
        assertThat(press(planets)).isEqualTo("exhaustForVotes_hacanCommanderTg");
        sol.setTg(3);
        sol.increaseTgsSpentThisWindow(1);
        assertThat(press(planets)).isEqualTo("FFCC_sol_proceedToFinalizingVote");
    }

    // Trade goods a revealed spend objective will need next round are kept: Negotiate Trade Routes takes 5, so of 6
    // only 1 is spare, too few to swing the vote, and Gila spends nothing. Without the commander nothing is spent.
    @Test
    void gilaKeepsTradeGoodsASpendObjectiveNeeds() {
        outvotedForTheShard(6);
        test.game.getRevealedPublicObjectives().put("trade_routes", 5);

        assertThat(GilaVotes.spendable(test.game, sol)).isEqualTo(1);
        assertThat(GilaVotes.tradeGoodsToSpend(test.game, sol, "sol", 2)).isZero();

        test.game.getRevealedPublicObjectives().remove("trade_routes");
        sol.unsafeGetLeader("hacancommander").setLocked(true);
        assertThat(GilaVotes.tradeGoodsToSpend(test.game, sol, "sol", 2)).isZero();
    }
}
