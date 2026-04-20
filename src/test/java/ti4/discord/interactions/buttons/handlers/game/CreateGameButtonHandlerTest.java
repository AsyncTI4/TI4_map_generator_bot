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

        List<ManagedGame> games = List.of(
                mockManagedGame(List.of(userId, "user-2"), false, false, false),
                mockManagedGame(List.of(userId, "user-2"), true, true, false),
                mockManagedGame(List.of(userId, "user-2", "user-3"), false, false, false),
                mockManagedGame(List.of(userId, "user-2", "user-3"), true, true, false),
                mockManagedGame(List.of(userId, "user-2", "user-3"), true, false, false),
                mockManagedGame(List.of(userId, "user-2", "user-3"), false, false, true));

        assertThat(CreateGameButtonHandler.countGamesThatAffectJoinLimit(userId, false, games))
                .isEqualTo(1);
        assertThat(CreateGameButtonHandler.countGamesThatAffectJoinLimit(userId, true, games))
                .isEqualTo(2);
    }

    private static ManagedGame mockManagedGame(
            List<String> realPlayerIds, boolean hasEnded, boolean hasWinner, boolean fowMode) {
        ManagedGame managedGame = mock(ManagedGame.class);
        List<ManagedPlayer> realPlayers = realPlayerIds.stream()
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
