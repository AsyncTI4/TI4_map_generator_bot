package ti4.spring.service.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameEntityTest {

    @Test
    void fracturedTiglFlagDefaultsFalseWhenDatabaseValueIsNull() {
        GameEntity gameEntity = new GameEntity();

        gameEntity.setTwilightImperiumGlobalLeagueFractured(null);

        assertFalse(gameEntity.isTwilightImperiumGlobalLeagueFractured());
    }

    @Test
    void fracturedTiglFlagStillReturnsPersistedBooleanValue() {
        GameEntity gameEntity = new GameEntity();

        gameEntity.setTwilightImperiumGlobalLeagueFractured(true);
        assertTrue(gameEntity.isTwilightImperiumGlobalLeagueFractured());

        gameEntity.setTwilightImperiumGlobalLeagueFractured(false);
        assertFalse(gameEntity.isTwilightImperiumGlobalLeagueFractured());
    }
}
