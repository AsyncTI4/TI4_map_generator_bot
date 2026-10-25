package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Elder Qanoj: each planet exhausted to cast votes provides one additional vote, and game effects cannot stop the
// holder voting. The bot's vote count already includes the extra vote, so the AI plans with it, counts planets that
// have no influence at all, and spends them.
class XxchaCommanderVotingTest extends BaseTi4Test {

    private static final String COMMANDER = "xxchacommander";

    private AiTestGame test;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.place("01", "304");
        sol.addPlanet("jord");
        String neighbour = MapPositionMapper.getAdjacentTilePositions("304").stream()
                .filter(position -> !"x".equals(position))
                .filter(position -> test.game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
        test.place("38", neighbour);
        sol.addPlanet("abyz");
        test.isActive(sol, "agendaVoting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
    }

    private void unlockCommander(Player player) {
        player.addLeader(COMMANDER);
        player.unsafeGetLeader(COMMANDER).setLocked(false);
    }

    private int worth(Player player, String planet) {
        return AgendaHelper.getSpecificPlanetsVoteWorth(player, test.game, planet);
    }

    private int planBallotVotes(Player player) {
        return AgendaVoting.plan(test.game, player)
                .map(AgendaVoting.Ballot::votes)
                .orElse(0);
    }

    // Abyz has resources only: with no influence it is worth no votes until the commander adds one.
    @Test
    void everyPlanetProvidesOneAdditionalVote() {
        int jord = worth(sol, "jord");
        assertThat(worth(sol, "abyz")).isZero();

        unlockCommander(sol);

        assertThat(worth(sol, "jord")).isEqualTo(jord + 1);
        assertThat(worth(sol, "abyz")).isEqualTo(1);
    }

    @Test
    void theBallotCountsEveryPlanetsExtraVote() {
        int without = planBallotVotes(sol);

        unlockCommander(sol);

        assertThat(planBallotVotes(sol)).isEqualTo(without + 2);
    }

    // A seat with only influence-less planets has no votes without the commander and two with it.
    @Test
    void aSeatWithOnlyInfluencelessPlanetsCanVoteWithTheCommander() {
        sol.removePlanet("jord");
        assertThat(AgendaVoting.plan(test.game, sol)).isEmpty();

        unlockCommander(sol);

        assertThat(planBallotVotes(sol)).isEqualTo(1);
    }

    // It exhausts the influence-less planet too when the ballot needs the vote it provides.
    @Test
    void exhaustsThePlanetsItNeedsForTheBallot() {
        unlockCommander(sol);
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        AgendaVoting.next(test.contextFor(sol, Set.of(), NOW, start));
        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "exhaustForVotes_planet_jord",
                        "exhaustForVotes_planet_abyz",
                        "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord", "Abyz (1)", "Done exhausting planets."));

        assertThat(spend(planets)).isEqualTo("exhaustForVotes_planet_jord");

        sol.addSpentThing("planet_jord");
        AiPrompt afterJord = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of("exhaustForVotes_planet_abyz", "FFCC_sol_proceedToFinalizingVote"),
                List.of("Abyz (1)", "Done exhausting planets."));
        assertThat(spend(afterJord)).isEqualTo("exhaustForVotes_planet_abyz");

        sol.addSpentThing("planet_abyz");
        AiPrompt afterBoth = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of("FFCC_sol_proceedToFinalizingVote"),
                List.of("Done exhausting planets."));
        assertThat(spend(afterBoth)).isEqualTo("FFCC_sol_proceedToFinalizingVote");
    }

    private String spend(AiPrompt planets) {
        return AgendaVoting.next(test.contextFor(sol, Set.of(), NOW, planets))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    // Galactic Threat keeps a Nekro from voting, unless it holds Elder Qanoj.
    @Test
    void theCommanderLetsANekroVote() {
        test.place("19", AiTestGame.neighbourOf("304"));
        test.nekro.addPlanet("wellon");
        assertThat(AgendaVoting.plan(test.game, test.nekro)).isEmpty();

        unlockCommander(test.nekro);

        assertThat(AgendaVoting.plan(test.game, test.nekro)).isPresent();
    }
}
