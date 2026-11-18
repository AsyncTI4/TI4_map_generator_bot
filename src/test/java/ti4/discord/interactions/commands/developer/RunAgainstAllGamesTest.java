package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.persistence.ManagedGame;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

    @Test
    void shouldOnlyLoadTwilightsFallGamesWhoseStoredRowStillHasThundersEdge() {
        List<ManagedGame> games =
                List.of(managedGame("tf-with-te", true), managedGame("tf-clean", true), managedGame("te-game", false));
        // The stored rows still say Thunder's Edge for these; "te-game" is a plain Thunder's Edge game.
        List<String> persisted = List.of("tf-with-te", "te-game");

        assertThat(RunAgainstAllGames.twilightsFallGamesWithThundersEdge(games, persisted))
                .containsExactly("tf-with-te");
    }

    @Test
    void gameMissingFromTheManagedGamesIsSkipped() {
        assertThat(RunAgainstAllGames.twilightsFallGamesWithThundersEdge(List.of(), List.of("deleted-game")))
                .isEmpty();
    }

    private static ManagedGame managedGame(String name, boolean twilightsFallMode) {
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.getName()).thenReturn(name);
        when(managedGame.isTwilightsFallMode()).thenReturn(twilightsFallMode);
        return managedGame;
    }
}
