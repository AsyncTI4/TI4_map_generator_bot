package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;

class TeamMateAccessServiceTest {

    // Removing someone from one seat must not strip the game role they still need as a player or on another team.
    @Test
    void removedTeammateKeepsGameAccessOnlyWhileStillInTheGame() {
        Player team = seat("primary", "teammate", "doubleTeam");
        Player otherTeam = seat("other", "doubleTeam");
        Player alsoPlaying = seat("alsoPlaying");
        Game game = game(team, otherTeam, alsoPlaying);

        assertThat(TeamMateAccessService.keepsGameAccess(game, team, "teammate"))
                .isFalse();
        assertThat(TeamMateAccessService.keepsGameAccess(game, team, "doubleTeam"))
                .isTrue();
        // Any entry in the player map counts, spectators and the fog GM included.
        assertThat(TeamMateAccessService.keepsGameAccess(game, team, "alsoPlaying"))
                .isTrue();
    }

    private static Player seat(String primary, String... teammates) {
        Player player = mock(Player.class);
        when(player.getUserID()).thenReturn(primary);
        List<String> ids = new ArrayList<>(List.of(primary));
        ids.addAll(List.of(teammates));
        when(player.getTeamMateIDs()).thenReturn(ids);
        return player;
    }

    private static Game game(Player... seats) {
        Map<String, Player> players = new LinkedHashMap<>();
        for (Player seat : seats) {
            players.put(seat.getUserID(), seat);
        }
        Game game = mock(Game.class);
        when(game.getPlayers()).thenReturn(players);
        when(game.getPlayer(anyString())).thenAnswer(invocation -> players.get(invocation.<String>getArgument(0)));
        return game;
    }
}
