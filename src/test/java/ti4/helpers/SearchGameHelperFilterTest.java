package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.persistence.ManagedGame;
import ti4.game.persistence.ManagedPlayer;

class SearchGameHelperFilterTest {

    private static final String USER = "user";

    // Last flag: false = /search my_games (active fog hidden), true = /bothelper player_games (active fog shown).
    @Test
    void activeFogGamesOnlyAppearInTheBothelperView() {
        assertThat(SearchGameHelper.gameFilter(USER, false, false, false, false, false)
                        .test(game(true, false)))
                .isFalse();
        assertThat(SearchGameHelper.gameFilter(USER, false, false, false, false, true)
                        .test(game(true, false)))
                .isTrue();
        assertThat(SearchGameHelper.gameFilter(USER, false, true, false, false, false)
                        .test(game(true, true)))
                .isTrue();
    }

    @Test
    void teammateFindsTheGameAndItsTurn() {
        ManagedGame game = game(false, false);
        when(game.getSeatUserId("teammate")).thenReturn(USER);
        when(game.getActivePlayerId()).thenReturn(USER);

        assertThat(SearchGameHelper.gameFilter("teammate", true, false, false, false, false)
                        .test(game))
                .isTrue();
        assertThat(SearchGameHelper.gameFilter("stranger", false, false, false, false, false)
                        .test(game))
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
        when(game.getSeatUserId(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return game;
    }
}
