package ti4.service.bothelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;

class PlayerGamesReportServiceTest {

    // Any read of the seat (faction, color, VP, turn) could leak it, so fog rows must leave the player untouched.
    // fow9 on the dev bot had fow_mode false but kept its fog channels, hence the name-based case.
    @Test
    void fogRowsNeverReadThePlayersSeat() {
        Player player = mock(Player.class);
        Game fowNamedWithFogOff = fogGame(false);
        when(fowNamedWithFogOff.isFowMode()).thenReturn(false);

        assertThat(PlayerGamesReportService.activeGameBlock(fogGame(false), player, "target", true))
                .contains("fow42")
                .doesNotContain("VP");
        PlayerGamesReportService.activeGameBlock(fowNamedWithFogOff, player, "target", false);
        assertThat(PlayerGamesReportService.endedGameLine(fogGame(true), player, "target"))
                .contains("completed")
                .doesNotContain("winner");
        verifyNoInteractions(player);
    }

    @Test
    void trackRecordIsSplitIntoNotes() {
        assertThat(PlayerGamesReportService.trackRecordNotes("Got replaced in game pbd1. Dropped out of pbd2. "))
                .containsExactly("Got replaced in game pbd1.", "Dropped out of pbd2.");
        assertThat(PlayerGamesReportService.trackRecordNotes("")).isEmpty();
    }

    private static Game fogGame(boolean ended) {
        Game game = mock(Game.class);
        when(game.isFowMode()).thenReturn(true);
        when(game.isHasEnded()).thenReturn(ended);
        when(game.getName()).thenReturn("fow42");
        when(game.hasWinner()).thenReturn(true);
        when(game.getEndedDate()).thenReturn(86_400_000L * 40);
        return game;
    }
}
