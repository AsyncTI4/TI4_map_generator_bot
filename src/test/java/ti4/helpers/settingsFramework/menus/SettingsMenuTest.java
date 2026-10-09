package ti4.helpers.settingsFramework.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.dv8tion.jda.api.entities.Message;
import org.junit.jupiter.api.Test;

class SettingsMenuTest {

    @Test
    void shortContentIsUnchanged() {
        String content = "# Franken Settings";
        assertEquals(content, SettingsMenu.fitToMessageLimit(content));
    }

    @Test
    void contentOverDiscordLimitIsTruncated() {
        // Mirrors the Franken menu overflow when many factions are banned.
        String content = "x".repeat(Message.MAX_CONTENT_LENGTH + 500);
        String fitted = SettingsMenu.fitToMessageLimit(content);
        assertEquals(Message.MAX_CONTENT_LENGTH, fitted.length());
        assertTrue(fitted.endsWith("..."));
    }
}
