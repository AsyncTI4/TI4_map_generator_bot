package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ManagedGame;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

    @Test
    void shouldOnlyLoadEndedNonFogGamesWithoutWinner() {
        List<ManagedGame> games = List.of(
                managedGame("ended-no-winner", true, false, false),
                managedGame("ended-with-winner", true, true, false),
                managedGame("ended-fog", true, false, true),
                managedGame("active", false, false, false));

        assertThat(RunAgainstAllGames.endedGamesWithoutWinner(games)).containsExactly("ended-no-winner");
    }

    @Test
    void endedGamePastTheFinalRoundWithoutWinnerQualifies() {
        Game game = endedGame(8);

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isTrue();
    }

    @Test
    void endedGameWithEmptyStageTwoDeckQualifiesBeforeTheFinalRound() {
        Game game = endedGame(6);

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isTrue();
    }

    @Test
    void gameAbortedWhileObjectivesRemainDoesNotQualify() {
        Game game = endedGame(4);
        game.setPublicObjectives2Peekable(new ArrayList<>(List.of("master_science")));

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void unfinishedGameDoesNotQualify() {
        Game game = endedGame(8);
        game.setHasEnded(false);

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void fogGameDoesNotQualifyBecauseItsPromptNeverPromisedAWinner() {
        Game game = endedGame(8);
        game.setFowMode(true);

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void redTapeGameDoesNotQualify() {
        Game game = endedGame(8);
        game.setRedTapeMode(true);

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void gameThatAlreadyHasAWinnerDoesNotQualify() {
        Game game = endedGame(8);
        game.setVp(10);
        var player = new Player("winner", "winner", game);
        player.setFaction("arborec");
        player.setColor("green");
        var players = new LinkedHashMap<>(game.getPlayers());
        players.put("winner", player);
        game.setPlayers(players);
        game.scorePublicObjective("winner", game.addCustomPO("winner points", 10));

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    private static Game endedGame(int round) {
        var game = new Game();
        game.setRound(round);
        game.setHasEnded(true);
        return game;
    }

    private static ManagedGame managedGame(String name, boolean hasEnded, boolean hasWinner, boolean fowMode) {
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.getName()).thenReturn(name);
        when(managedGame.isHasEnded()).thenReturn(hasEnded);
        when(managedGame.isHasWinner()).thenReturn(hasWinner);
        when(managedGame.isFowMode()).thenReturn(fowMode);
        return managedGame;
    }
}
