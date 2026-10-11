package ti4.service.game;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;

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

    @Test
    void shouldParseUndoNumberFromRawSelection() {
        Integer undoNumber = GameUndoNameService.getUndoNumberFromSelection("pbd21740", "pbd21740_1668.txt");
        assertThat(undoNumber).isEqualTo(1668);
    }

    @Test
    void shouldParseUndoNumberFromDecoratedSelection() {
        Integer undoNumber = GameUndoNameService.getUndoNumberFromSelection(
                "pbd21740",
                "pbd21740_1668.txt (00h:20m:38s ago):  gabs2482 pressed button: argentHeroStep4_310_311_space_fighter");
        assertThat(undoNumber).isEqualTo(1668);
    }

    @Test
    void selectionValueCarriesTheSaveLastModifiedDate() {
        String value = new GameUndoNameService.UndoSave("pbd21740_1668.txt", "label", 1765357943476L).selectionValue();

        assertThat(GameUndoNameService.getUndoNumberFromSelection("pbd21740", value))
                .isEqualTo(1668);
        assertThat(GameUndoNameService.getLastModifiedDateFromSelection(value)).isEqualTo(1765357943476L);
    }

    @Test
    void typedSelectionHasNoLastModifiedDate() {
        assertThat(GameUndoNameService.getLastModifiedDateFromSelection("pbd21740_1668.txt"))
                .isNull();
        assertThat(GameUndoNameService.getLastModifiedDateFromSelection("pbd21740_1668.txt@99999999999999999999"))
                .isNull();
    }

    @Test
    void shouldRejectSelectionForAnotherGame() {
        Integer undoNumber = GameUndoNameService.getUndoNumberFromSelection("pbd21740", "pbd21741_1668.txt");
        assertThat(undoNumber).isNull();
    }
}
