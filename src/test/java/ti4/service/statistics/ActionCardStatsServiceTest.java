package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ActionCardStatsServiceTest extends BaseTi4Test {

    @Test
    void playToWinCorrelationOnlyCountsEligibleGamesAndWinningPlayers() {
        Map<String, ActionCardStatsService.PlayToWinCorrelationCount> counts = new HashMap<>();

        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(buildEligibleGame(), counts);
        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(buildPreTrackingGame(), counts);

        assertThat(counts).containsOnlyKeys(GameStats.OVERRULE, GameStats.SABOTAGE);
        assertThat(counts.get(GameStats.OVERRULE).getTotal()).isEqualTo(1);
        assertThat(counts.get(GameStats.OVERRULE).getWins()).isEqualTo(1);
        assertThat(counts.get(GameStats.SABOTAGE).getTotal()).isEqualTo(1);
        assertThat(counts.get(GameStats.SABOTAGE).getWins()).isZero();
    }

    @Test
    void playToWinCorrelationSectionIncludesTrackingStartNote() {
        StringBuilder message = new StringBuilder();
        Map<String, ActionCardStatsService.PlayToWinCorrelationCount> counts = new HashMap<>();
        var count = new ActionCardStatsService.PlayToWinCorrelationCount();
        count.total++;
        count.wins++;
        counts.put(GameStats.OVERRULE, count);

        ActionCardStatsService.appendPlayToWinCorrelationStats(message, counts);

        assertThat(message)
                .contains("2026-05-22")
                .contains("Overrule: 100.0% (1/1 plays by the eventual winner)");
    }

    private static Game buildEligibleGame() {
        Game game = new Game();
        game.setName("eligible-action-card-stats");
        game.setVp(1);
        game.setHasEnded(true);
        game.setStartedDate(LocalDate.of(2026, 5, 23).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());

        Player winner = game.addPlayer("winner", "Winner");
        winner.setFaction("arborec");
        winner.setColor("red");
        winner.setSecretScored("accept_bribes_pbd100");

        Player loser = game.addPlayer("loser", "Loser");
        loser.setFaction("hacan");
        loser.setColor("blue");

        game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, winner, "leadership");
        game.getGameStats().recordAcPlay(GameStats.SABOTAGE, loser);
        return game;
    }

    private static Game buildPreTrackingGame() {
        Game game = buildEligibleGame();
        game.setName("pretracking-action-card-stats");
        game.setStartedDate(LocalDate.of(2026, 5, 21).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        return game;
    }
}
