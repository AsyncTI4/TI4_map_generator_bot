package ti4.helpers.settingsFramework.settings;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

@Getter
@Setter
@JsonIncludeProperties({"id", "val"})
public class BooleanSetting extends SettingInterface {
    private boolean val;
    private String whenFalse;
    private String whenTrue;
    private boolean defaultValue;

    public BooleanSetting(String id, String name, boolean val) {
        super(id, name);

        defaultValue = val;
        this.val = val;
        whenFalse = "Enable";
        whenTrue = "Disable";
    }

    public BooleanSetting(String id, String name, boolean val, String whenFalse, String whenTrue) {
        super(id, name);

        defaultValue = val;
        this.val = val;
        this.whenFalse = whenFalse;
        this.whenTrue = whenTrue;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    protected void init(JsonNode json) {
        if (json.has("val")) val = json.get("val").asBoolean(val);
    }

    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.equals("tog" + id)) return toggle();
        return "[invalid action: " + action + "]";
    }

    public void reset() {
        val = defaultValue;
    }

    protected String shortValue() {
        return val ? "✅" : "❌";
    }

    protected String longValue() {
        String val = shortValue();
        val += " *(default=" + (defaultValue ? "✅" : "❌") + ")*";
        return val;
    }

    protected List<Button> buttons(String idPrefix) {
        ButtonStyle style = val ? ButtonStyle.DANGER : ButtonStyle.SUCCESS;
        String labelPre = (val ? whenTrue : whenFalse);
        String label = (labelPre == null ? "Toggle" : labelPre) + " " + name;

        Button tog = Button.of(style, idPrefix + "tog" + id, label, emojiToggle);
        return new ArrayList<>(List.of(tog));
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    private String toggle() {
        val = !val;
        return null;
    }
}
