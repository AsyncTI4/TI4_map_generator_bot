package ti4.discord.interactions.commands.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.testUtils.BaseTi4Test;

class RecreateGameTest extends BaseTi4Test {

    @Test
    void recreateGameUsesGameStateCommandFlowSaving() throws Exception {
        RecreateGame command = new RecreateGame();

        assertTrue(command instanceof GameStateSubcommand);
        assertTrue(getBooleanField(command, "saveGame"));
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field field = GameStateSubcommand.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }
}
