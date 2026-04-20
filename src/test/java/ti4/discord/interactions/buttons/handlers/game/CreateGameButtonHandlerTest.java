package ti4.discord.interactions.buttons.handlers.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.persistence.ManagedGame;
import ti4.game.persistence.ManagedPlayer;

class CreateGameButtonHandlerTest {

    @Test
    void countGamesThatAffectJoinLimitIgnoresGamesWithFewerThanThreePlayers() {
        String userId = "user-1";

        // getRealPlayers() now returns both real AND eliminated players (via ManagedGame change),
        // so the realAndEliminatedPlayerIds parameter simulates that combined set.
        List<ManagedGame> games = List.of(
                // 2-player games (below threshold) — should be ignored regardless of state
                mockManagedGame(List.of(userId, "user-2"), false, false, false),
                mockManagedGame(List.of(userId, "user-2"), true, true, false),
                // 3-player ongoing, non-FoW — counts as ongoing
                mockManagedGame(List.of(userId, "user-2", "user-3"), false, false, false),
                // 3-player ended with winner — counts as completed
                mockManagedGame(List.of(userId, "user-2", "user-3"), true, true, false),
                // 3-player ended without winner (aborted) — excluded by ignoreAborted filter
                mockManagedGame(List.of(userId, "user-2", "user-3"), true, false, false),
                // 3-player ongoing FoW — excluded by onlyEndedFoW filter
                mockManagedGame(List.of(userId, "user-2", "user-3"), false, false, true));

        // ongoing only: the one non-FoW, non-ended, 3-player game
        assertThat(CreateGameButtonHandler.countOngoingGamesThatAffectJoinLimit(userId, false, games))
                .isEqualTo(1);
        // ongoing + completed: the ongoing one + the ended-with-winner one
        assertThat(CreateGameButtonHandler.countOngoingGamesThatAffectJoinLimit(userId, true, games))
                .isEqualTo(2);
    }

    @Test
    void countGamesThatAffectJoinLimitIncludesEliminatedPlayersInOngoingGames() {
        String userId = "user-1";

        // Simulate a 6-player ongoing game where 3 players (including userId) were eliminated.
        // getRealPlayers() now includes eliminated players, so all 6 show up.
        List<ManagedGame> games = List.of(mockManagedGame(
                List.of(userId, "user-2", "user-3", "user-4", "user-5", "user-6"), false, false, false));

        // The game has >= 3 real+eliminated players and userId is among them — should count
        assertThat(CreateGameButtonHandler.countOngoingGamesThatAffectJoinLimit(userId, false, games))
                .isEqualTo(1);
    }

    @Test
    void countGamesThatAffectJoinLimitExcludesUserNotInGame() {
        String userId = "user-1";

        List<ManagedGame> games = List.of(mockManagedGame(List.of("user-2", "user-3", "user-4"), false, false, false));

        assertThat(CreateGameButtonHandler.countOngoingGamesThatAffectJoinLimit(userId, false, games))
                .isEqualTo(0);
    }

    private static ManagedGame mockManagedGame(
            List<String> realAndEliminatedPlayerIds, boolean hasEnded, boolean hasWinner, boolean fowMode) {
        ManagedGame managedGame = mock(ManagedGame.class);
        List<ManagedPlayer> realPlayers = realAndEliminatedPlayerIds.stream()
                .map(CreateGameButtonHandlerTest::mockManagedPlayer)
                .toList();
        when(managedGame.getRealPlayers()).thenReturn(realPlayers);
        when(managedGame.isHasEnded()).thenReturn(hasEnded);
        when(managedGame.isHasWinner()).thenReturn(hasWinner);
        when(managedGame.isFowMode()).thenReturn(fowMode);
        return managedGame;
    }

    private static ManagedPlayer mockManagedPlayer(String userId) {
        ManagedPlayer managedPlayer = mock(ManagedPlayer.class);
        when(managedPlayer.getId()).thenReturn(userId);
        return managedPlayer;
    }
}
