package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.persistence.ManagedGame;
import ti4.game.persistence.ManagedPlayer;

class SearchGameHelperFilterTest {

    private static final String USER = "user";

    @Test
    void myGamesKeepsHidingActiveFogGames() {
        // /search my_games must stay unchanged: active fog hidden, ended fog shown only with ended games.
        assertThat(SearchGameHelper.gameFilter(USER, false, false, false, false, false)
                        .test(game(true, false)))
                .isFalse();
        assertThat(SearchGameHelper.gameFilter(USER, false, true, false, false, false)
                        .test(game(true, true)))
                .isTrue();
        assertThat(SearchGameHelper.gameFilter(USER, false, false, false, false, false)
                        .test(game(false, false)))
                .isTrue();
    }

    @Test
    void bothelperViewIncludesActiveFogGames() {
        assertThat(SearchGameHelper.gameFilter(USER, false, false, false, false, true)
                        .test(game(true, false)))
                .isTrue();
        assertThat(SearchGameHelper.gameFilter(USER, false, false, false, false, true)
                        .test(game(true, true)))
                .isFalse();
    }

    private static ManagedGame game(boolean fog, boolean ended) {
        ManagedPlayer player = mock(ManagedPlayer.class);
        when(player.getId()).thenReturn(USER);
        ManagedGame game = mock(ManagedGame.class);
        when(game.isFowMode()).thenReturn(fog);
        when(game.isHasEnded()).thenReturn(ended);
        when(game.isHasWinner()).thenReturn(true);
        when(game.getPlayers()).thenReturn(Set.of(player));
        return game;
    }
}
