package ti4.service.bothelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;

class PlayerGamesReportServiceTest {

    @Test
    void activeFogGameNeverReadsThePlayersSeat() {
        Game game = fogGame(false);
        Player player = mock(Player.class);

        String block = PlayerGamesReportService.activeGameBlock(game, player, true);

        // Any read of the seat (faction, color, VP, turn) could leak it, so the player must stay untouched.
        verifyNoInteractions(player);
        assertThat(block).contains("fow42").doesNotContain("VP");
    }

    @Test
    void fowNamedGameWithFogSwitchedOffIsStillTreatedAsFog() {
        // fow9 on the dev bot had fow_mode false but kept its fog channels and seats.
        Game game = fogGame(false);
        when(game.isFowMode()).thenReturn(false);
        Player player = mock(Player.class);

        PlayerGamesReportService.activeGameBlock(game, player, false);

        verifyNoInteractions(player);
    }

    @Test
    void endedFogGameNeverReadsThePlayersSeat() {
        Game game = fogGame(true);
        Player player = mock(Player.class);

        String line = PlayerGamesReportService.endedGameLine(game, player);

        verifyNoInteractions(player);
        assertThat(line).contains("fow42").contains("completed").doesNotContain("winner");
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
        when(game.getRound()).thenReturn(3);
        when(game.hasWinner()).thenReturn(true);
        when(game.getCreationDateTime()).thenReturn(0L);
        when(game.getEndedDate()).thenReturn(86_400_000L * 40);
        return game;
    }
}
