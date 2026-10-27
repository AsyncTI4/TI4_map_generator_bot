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

class TeamSeatServiceTest {

    @Test
    void onlyUsersOnTheSameSeatShareIt() {
        Player team = seat("primary", "teammate");
        Game game = game(team, seat("other"));

        assertThat(TeamSeatService.sharedSeat(game, "primary", "teammate")).isSameAs(team);
        assertThat(TeamSeatService.sharedSeat(game, "teammate", "primary")).isSameAs(team);
        // Another player at the same table must not see the target's fog seat.
        assertThat(TeamSeatService.sharedSeat(game, "primary", "other")).isNull();
        assertThat(TeamSeatService.sharedSeat(game, "primary", "outsider")).isNull();
    }

    // A GM or spectator entry must not hide the seat the user plays as a teammate, and is never shared itself.
    @Test
    void spectatorEntriesAreSkipped() {
        Player spectator = seat("dev");
        when(spectator.isSpectator()).thenReturn(true);
        Player team = seat("primary", "dev");

        assertThat(TeamSeatService.sharedSeat(game(spectator, team), "dev", "dev"))
                .isSameAs(team);
        assertThat(TeamSeatService.sharedSeat(game(spectator), "dev", "dev")).isNull();
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
