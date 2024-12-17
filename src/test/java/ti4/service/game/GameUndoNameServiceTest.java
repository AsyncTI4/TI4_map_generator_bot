package ti4.service.game;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class GameUndoNameServiceTest {

    @Test
    void shouldHandleMultipleUnderscores() {
        int undoNumber = GameUndoNameService.getUndoNumberFromFileName("fow_tourney4_123.txt");
        assertThat(undoNumber).isEqualTo(123);
    }

    @Test
    void shouldHandleSingleUnderscore() {
        int undoNumber = GameUndoNameService.getUndoNumberFromFileName("pbd14_321.txt");
        assertThat(undoNumber).isEqualTo(321);
    }

}