package ti4.commands.custom;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class CustomizationOptionsTest extends BaseTi4Test {

    @Test
    void customizationCommandIsNotPlayerScoped() throws Exception {
        var command = new CustomizationOptions();
        Field commandGameStateField = command.getClass().getSuperclass().getDeclaredField("commandGameState");
        commandGameStateField.setAccessible(true);

        Object commandGameState = commandGameStateField.get(command);
        Field isPlayerCommandField = commandGameState.getClass().getDeclaredField("isPlayerCommand");
        isPlayerCommandField.setAccessible(true);

        assertThat(isPlayerCommandField.getBoolean(commandGameState)).isFalse();
    }
}
