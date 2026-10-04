package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.persistence.ManagedGame;

class RunAgainstAllGamesTest {

    @Test
    void onlyGamesNotFlaggedAsEndedThatStillHaveAnEndDateAreTouched() {
        List<ManagedGame> games = List.of(
                managedGame("reopened", false, 5_000L),
                managedGame("ended", true, 5_000L),
                managedGame("active", false, 0L),
                // Flagged as ended without a date: left alone, this command only clears stale dates.
                managedGame("ended-without-date", true, 0L));

        assertThat(RunAgainstAllGames.unendedGamesWithAnEndDate(games)).containsExactly("reopened");
    }

    private static ManagedGame managedGame(String name, boolean hasEnded, long endedDate) {
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.getName()).thenReturn(name);
        when(managedGame.isHasEnded()).thenReturn(hasEnded);
        when(managedGame.getEndedDate()).thenReturn(endedDate);
        return managedGame;
    }
}
