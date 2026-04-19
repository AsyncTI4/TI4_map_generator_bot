package ti4.discord.interactions.commands.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class TwilightsFallSpliceWinRateStatisticsServiceTest extends BaseTi4Test {

    @Test
    void buildReportCountsAbilitiesUnitUpgradesAndGenomes() {
        Game firstGame = createTwilightsFallGame(
                "1",
                "winner-1",
                "loser-1",
                "tf-mitosis",
                "experimentalagent",
                "tf-dawncrusher",
                "tf-armada",
                "hyperagent",
                "tf-swa");
        Game secondGame = createTwilightsFallGame(
                "2",
                "winner-2",
                "loser-2",
                "tf-mitosis",
                "hyperagent",
                "tf-swa",
                "tf-armada",
                "experimentalagent",
                "tf-dawncrusher");

        String report = TwilightsFallSpliceWinRateStatisticsService.buildReport(List.of(firstGame, secondGame));

        assertTrue(report.contains("Games analyzed: 2"));
        assertTrue(report.contains("- Mitosis: 2/2 (100%)"));
        assertTrue(report.contains("- Armada: 0/2 (0%)"));
        assertTrue(report.contains("- Dawncrusher: 1/2 (50%)"));
        assertTrue(report.contains("- Strike Wing Alpha (TF): 1/2 (50%)"));
        assertTrue(report.contains("- Experimental Genome: 1/2 (50%)"));
        assertTrue(report.contains("- Hyper Genome: 1/2 (50%)"));
        assertFalse(report.contains("Carrier II"));
    }

    @Test
    void buildReportPrintsEntireGenomeDeck() {
        String report = TwilightsFallSpliceWinRateStatisticsService.buildReport(List.of());
        String noGamesMessage = "No Twilight's Fall games matched the selected filters.";
        assertEquals(noGamesMessage, report);

        Game game = createTwilightsFallGame(
                "3",
                "winner-3",
                "loser-3",
                "tf-mitosis",
                "experimentalagent",
                "tf-dawncrusher",
                "tf-armada",
                "hyperagent",
                "tf-swa");

        String reportWithGame = TwilightsFallSpliceWinRateStatisticsService.buildReport(List.of(game));

        Mapper.getDeck(Constants.TF_GENOME).getNewDeck().stream()
                .map(Mapper::getLeader)
                .map(leader -> "- " + leader.getTFNameIfAble() + ": ")
                .forEach(linePrefix -> assertTrue(reportWithGame.contains(linePrefix), linePrefix));
    }

    private static Game createTwilightsFallGame(
            String suffix,
            String winnerUserId,
            String loserUserId,
            String winnerAbility,
            String winnerGenome,
            String winnerUnitUpgrade,
            String loserAbility,
            String loserGenome,
            String loserUnitUpgrade) {
        Game game = new Game();
        game.setName("tf-stats-" + suffix);
        game.setTwilightsFallMode(true);
        game.setVp(1);
        game.setRound(3);
        game.setHasEnded(true);

        Player winner = game.addPlayer(winnerUserId, "Winner " + suffix);
        winner.setFaction("winner" + suffix);
        winner.setColor("red");
        winner.addTech(winnerAbility);
        winner.addLeader(winnerGenome);
        winner.addOwnedUnitByID(winnerUnitUpgrade);
        winner.setSecretScored("so_winner_" + suffix);

        Player loser = game.addPlayer(loserUserId, "Loser " + suffix);
        loser.setFaction("loser" + suffix);
        loser.setColor("blue");
        loser.addTech(loserAbility);
        loser.addLeader(loserGenome);
        loser.addOwnedUnitByID(loserUnitUpgrade);

        return game;
    }
}
