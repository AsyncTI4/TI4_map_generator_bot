package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.persistence.ManagedGame;

class RunAgainstAllGamesTest {

    private static final List<ManagedGame> GAMES = List.of(
            managedGame("on-active", true, false),
            managedGame("on-ended", true, true),
            managedGame("off-active", false, false),
            managedGame("off-ended", false, true));

    @Test
    void shouldOnlyTouchUnfinishedGamesWithRulesLinksOnByDefault() {
        assertThat(RunAgainstAllGames.gamesWithRulesLinksOn(GAMES, false)).containsExactly("on-active");
    }

    @Test
    void shouldIncludeEndedGamesWhenAsked() {
        assertThat(RunAgainstAllGames.gamesWithRulesLinksOn(GAMES, true)).containsExactly("on-active", "on-ended");
    }

    private static ManagedGame managedGame(String name, boolean injectRules, boolean hasEnded) {
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.getName()).thenReturn(name);
        when(managedGame.isInjectRules()).thenReturn(injectRules);
        when(managedGame.isHasEnded()).thenReturn(hasEnded);
        return managedGame;
    }
}
