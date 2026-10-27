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
    void endedGameInTheStatusPhaseOfRoundNineWithoutWinnerQualifies() {
        // The 10th and last objective is flipped in round 8's status phase, so round 9's status phase has none left.
        Game game = endedGame(9);
        game.setPhaseOfGame("statusScoring");

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isTrue();
    }

    @Test
    void endedGameInTheAgendaPhaseOfRoundNineWithoutWinnerQualifies() {
        Game game = endedGame(9);
        game.setPhaseOfGame("agendaVoting");

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isTrue();
    }

    @Test
    void endedGameThatPlayedPastRoundNineQualifiesInAnyPhase() {
        Game game = endedGame(10);
        game.setPhaseOfGame("action");

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isTrue();
    }

    @Test
    void gameEndedDuringRoundNineBeforeItsStatusPhaseDoesNotQualify() {
        Game game = endedGame(9);
        game.setPhaseOfGame("action");

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void gameEndedInTheStatusPhaseOfRoundEightDoesNotQualify() {
        Game game = endedGame(8);
        game.setPhaseOfGame("statusScoring");

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void endedGameWithEmptyStageTwoDeckQualifiesBeforeTheFinalRound() {
        Game game = endedGame(6);
        game.setPublicObjectives2(new ArrayList<>());

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isTrue();
    }

    @Test
    void gameAbortedEarlyWithUnpeekedStageTwoDeckDoesNotQualify() {
        // Most games never set up peekable objectives, so every stage 2 card sits in the main deck.
        Game game = endedGame(3);

        assertThat(game.getPublicObjectives2Peekable()).isEmpty();
        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void gameAbortedWhileObjectivesRemainDoesNotQualify() {
        Game game = endedGame(4);
        game.setPublicObjectives2Peekable(new ArrayList<>(List.of("master_science")));

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void unfinishedGameDoesNotQualify() {
        Game game = endedGame(10);
        game.setHasEnded(false);

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void fogGameDoesNotQualifyBecauseItsPromptNeverPromisedAWinner() {
        Game game = endedGame(10);
        game.setFowMode(true);

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void redTapeGameDoesNotQualify() {
        Game game = endedGame(10);
        game.setRedTapeMode(true);

        assertThat(RunAgainstAllGames.ranOutOfObjectivesWithoutWinner(game)).isFalse();
    }

    @Test
    void gameThatAlreadyHasAWinnerDoesNotQualify() {
        Game game = endedGame(10);
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
        game.setPublicObjectives2(new ArrayList<>(List.of("master_science", "subdue")));
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
