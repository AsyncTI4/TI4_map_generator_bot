package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.List;

import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

@NoArgsConstructor
public class BooleanSetting extends SettingInterface {
    // Will show up in json
    public boolean val;
    // Will not show up in json
    private String whenFalse;
    private String whenTrue;
    private boolean defaultValue;

    public BooleanSetting(String id, String name, boolean val, BooleanSetting value) {
        this.id = id;
        this.name = name;
        this.defaultValue = val;
        this.val = value == null ? val : value.val;
        this.whenFalse = "Enable";
        this.whenTrue = "Disable";
    }

    public BooleanSetting(String id, String name, boolean val, String whenFalse, String whenTrue, BooleanSetting value) {
        this.id = id;
        this.name = name;
        this.defaultValue = val;
        this.val = value == null ? val : value.val;
        this.whenFalse = whenFalse;
        this.whenTrue = whenTrue;
    }

    public String shortValue() {
        return val ? "✅" : "❌";
    }

    public String longValue() {
        String val = shortValue();
        val += " *(default=" + (defaultValue ? "✅" : "❌") + ")*";
        return val;
    }

    // Abstract methods
    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.equals("tog" + id)) return toggle();
        return "[invalid action: " + action + "]";
    }

    protected List<Button> buttons(String idPrefix) {
        ButtonStyle style = val ? ButtonStyle.DANGER : ButtonStyle.SUCCESS;
        String labelPre = (val ? whenTrue : whenFalse);
        String label = (labelPre == null ? "Toggle" : labelPre) + " " + name;

        Button tog = Button.of(style, idPrefix + "tog" + id, label, emojiToggle);
        return new ArrayList<>(List.of(tog));
    }

    public void reset() {
        val = defaultValue;
    }

    // Helper methods
    public String toggle() {
        val = !val;
        return null;
    }
}
