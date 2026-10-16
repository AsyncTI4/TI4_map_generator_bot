package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.persistence.GameManager;

class ReloadCorruptedSavesTest {

    @Test
    void onlyCorruptGamesAreSelectedForReload() {
        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isCorrupt("pbd2")).thenReturn(true);
            gameManager.when(() -> GameManager.isCorrupt("pbd1")).thenReturn(true);

            // "healthy" loads fine and "missing" has no file; neither is corrupt, so neither is touched.
            assertThat(ReloadCorruptedSaves.corruptGameNames(List.of("healthy", "pbd2", "missing", "pbd1")))
                    .containsExactly("pbd1", "pbd2");
            gameManager.verify(() -> GameManager.reload(anyString()), never());
        }
    }
}
