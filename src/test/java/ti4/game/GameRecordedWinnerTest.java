package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class GameRecordedWinnerTest extends BaseTi4Test {

    @Test
    void mostPointsWinnerIsTheSoleLeader() {
        var game = new Game();
        var leader = addPlayer(game, "leader", "arborec", "green", Set.of(5), 9);
        addPlayer(game, "runnerUp", "jolnar", "red", Set.of(1), 8);
        addPlayer(game, "trailing", "naalu", "blue", Set.of(3), 6);

        assertThat(game.getMostPointsWinner()).contains(leader);
    }

    @Test
    void mostPointsTieGoesToTheEarliestInitiative() {
        var game = new Game();
        addPlayer(game, "tiedLate", "arborec", "green", Set.of(6, 8), 9);
        var tiedEarly = addPlayer(game, "tiedEarly", "jolnar", "red", Set.of(4, 7), 9);
        // Holds the lowest strategy card overall, but is not tied for the most points.
        addPlayer(game, "trailing", "naalu", "blue", Set.of(1, 2), 8);

        assertThat(game.getMostPointsWinner()).contains(tiedEarly);
    }

    @Test
    void mostPointsTieWithoutStrategyCardsHasNoWinner() {
        var game = new Game();
        addPlayer(game, "withCard", "arborec", "green", Set.of(2), 9);
        // Strategy cards were already returned (e.g. status clean-up ran), so the tie cannot be broken.
        addPlayer(game, "withoutCard", "jolnar", "red", Set.of(), 9);

        assertThat(game.getMostPointsWinner()).isEmpty();
    }

    @Test
    void recordedWinnerOnlyCountsOnceTheGameHasEnded() {
        var game = new Game();
        var leader = addPlayer(game, "leader", "arborec", "green", Set.of(5), 9);
        addPlayer(game, "runnerUp", "jolnar", "red", Set.of(1), 8);

        game.recordWinner(leader);

        assertThat(game.getWinner()).isEmpty();
        assertThat(game.hasWinner()).isFalse();

        game.setHasEnded(true);

        assertThat(game.getWinner()).contains(leader);
        assertThat(game.getWinners()).containsExactly(leader);
        assertThat(game.hasWinner()).isTrue();
    }

    @Test
    void winnersOnceEndedIncludeTheRecordedWinnerBeforeTheEndedFlagIsSet() {
        // EndGameService commits the GAME_ENDED event before it marks the game as ended.
        var game = new Game();
        var leader = addPlayer(game, "leader", "arborec", "green", Set.of(5), 9);
        addPlayer(game, "runnerUp", "jolnar", "red", Set.of(1), 8);

        assertThat(game.getWinnersOnceEnded()).isEmpty();

        game.recordWinner(leader);

        assertThat(game.getWinners()).isEmpty();
        assertThat(game.getWinnersOnceEnded()).containsExactly(leader);
    }

    @Test
    void recordedWinnerIsKeyedByFactionSoItSurvivesAReplacement() {
        var game = new Game();
        var original = addPlayer(game, "original", "arborec", "green", Set.of(5), 9);
        addPlayer(game, "runnerUp", "jolnar", "red", Set.of(1), 8);
        game.recordWinner(original);
        game.setHasEnded(true);

        game.getPlayers().remove("original");
        var replacement = addPlayer(game, "replacement", "arborec", "green", Set.of(5), 0);

        assertThat(game.getWinner()).contains(replacement);
    }

    @Test
    void reopeningTheGameClearsTheRecordedWinner() {
        var game = new Game();
        var leader = addPlayer(game, "leader", "arborec", "green", Set.of(5), 9);
        game.recordWinner(leader);
        game.setHasEnded(true);

        game.reopen();

        assertThat(game.isHasEnded()).isFalse();
        assertThat(game.getStoredValue("recordedWinner")).isEmpty();

        // Ending again by some other route must not resurrect the old most-points winner.
        game.setHasEnded(true);
        assertThat(game.getWinner()).isEmpty();
    }

    @Test
    void playerReachingTheVictoryPointGoalStillWins() {
        var game = new Game();
        game.setVp(10);
        var vpWinner = addPlayer(game, "vpWinner", "arborec", "green", Set.of(5), 10);
        var other = addPlayer(game, "other", "jolnar", "red", Set.of(1), 7);

        assertThat(game.getWinner()).contains(vpWinner);

        game.recordWinner(other);
        game.setHasEnded(true);

        assertThat(game.getWinner()).contains(vpWinner);
    }

    @Test
    void endedGameWithoutARecordedWinnerOrVictoryPointGoalHasNoWinner() {
        var game = new Game();
        addPlayer(game, "leader", "arborec", "green", Set.of(5), 9);
        game.setHasEnded(true);

        assertThat(game.getWinner()).isEmpty();
    }

    private static Player addPlayer(
            Game game, String userId, String faction, String color, Set<Integer> strategyCards, int victoryPoints) {
        var player = new Player(userId, userId, game);
        player.setFaction(faction);
        player.setColor(color);
        player.setSCs(strategyCards);
        if (victoryPoints > 0) {
            Integer objective = game.addCustomPO(userId + " points", victoryPoints);
            game.scorePublicObjective(userId, objective);
        }
        var players = new LinkedHashMap<>(game.getPlayers());
        players.put(userId, player);
        game.setPlayers(players);
        return player;
    }
}
