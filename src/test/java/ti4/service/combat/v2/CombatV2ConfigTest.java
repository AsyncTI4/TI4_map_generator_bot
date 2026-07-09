package ti4.service.combat.v2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.game.Game;

class CombatV2ConfigTest {

    @Test
    void toggleDefaultsOffAndPersistsInExistingGameStoredValues() {
        Game game = new Game();

        assertFalse(CombatV2Config.isEnabled(game));

        CombatV2Config.setEnabled(game, true);
        assertTrue(CombatV2Config.isEnabled(game));

        CombatV2Config.setEnabled(game, false);
        assertFalse(CombatV2Config.isEnabled(game));
    }
}
