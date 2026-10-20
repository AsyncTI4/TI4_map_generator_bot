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

    // Predictive Intelligence adds 3 votes that only cost anything if the outcome loses, so they go in first, on top
    // of the planets.
    @Test
    void addsPredictiveIntelligenceVotesBeforeItsPlanets() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        sol.addTech("pi");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        assertThat(press(start)).isEqualTo("FFCC_sol_vote");
        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "exhaustForVotes_planet_jord",
                        "exhaustForVotes_predictive_3",
                        "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord (4)", "Use Predictive Intelligence Votes (3)", "Done exhausting planets."));

        assertThat(press(planets)).isEqualTo("exhaustForVotes_predictive_3");

        sol.addSpentThing("predictive_3");
        assertThat(press(planets)).isEqualTo("exhaustForVotes_planet_jord");
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

    // Sol holds Drive the Debate but Xxcha has six times its votes and is backing the leader: Sol cannot win the
    // election, so it does not spend its votes on itself (it can still back the leader, as it would without the card).
    @Test
    void castsNoVotesInAnElectionItCannotWinWithDriveTheDebate() {
        driveTheDebateElection(everythingSolHas(), everythingSolHas() * 6);

        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .isNotEqualTo(Optional.of("sol"));
    }

    // The same election with the votes on Sol's side: it spends only enough to beat the two votes against it (a tie
    // goes to Sol as speaker), not everything it has.
    @Test
    void spendsOnlyEnoughToWinTheElectionWithDriveTheDebate() {
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");
        int everything =
                everythingSolHas() + ti4.helpers.AgendaHelper.getSpecificPlanetsVoteWorth(sol, test.game, "wellon");
        driveTheDebateElection(everything, 2);

        Optional<AgendaVoting.Ballot> ballot = AgendaVoting.plan(test.game, sol);

        assertThat(ballot.map(AgendaVoting.Ballot::outcome)).contains("sol");
        assertThat(ballot.map(AgendaVoting.Ballot::votes).orElse(0)).isBetween(2, everything - 1);
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
        rivalRacesOnForWith("Trade Rider");

        assertThat(AgendaVoting.plan(test.game, sol)).isEmpty();

        test.game.setCurrentAgendaVote("for", "blue_Imperial Rider");
        assertThat(AgendaVoting.plan(test.game, sol)).isEmpty();
    }

    // Mutiny keeps its own rule, "for" when it would carry.
    @Test
    void mutinyKeepsItsRuleWhateverTheRiders() {
        rivalRacesOnForWith("Imperial Rider");
        test.game.setCurrentAgendaInfo("Law_For/Against_7_mutiny");

        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .contains("for");
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
