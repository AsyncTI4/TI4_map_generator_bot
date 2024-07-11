package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

@Getter
@Setter
@JsonIncludeProperties({ "id", "val" })
public class IntegerSetting extends SettingInterface {
    private int val;
    private int defaultValue;
    private int min;
    private int max;
    private int delta;

    public IntegerSetting(String id, String name, int val, int min, int max, int delta) {
        super(id, name);

        this.defaultValue = this.val = val;
        this.min = min;
        this.max = max;
        this.delta = delta;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    protected void init(JsonNode json) {
        if (json.has("val")) val = json.get("val").asInt(val);
    }

    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.equals("inc" + id)) return increment();
        if (action.equals("dec" + id)) return decrement();
        return "[invalid action: " + action + "]";
    }

    public void reset() {
        val = defaultValue;
    }

    protected String shortValue() {
        return "" + val;
    }

    protected String longValue() {
        return val + " *(limits=[" + min + "-" + max + "], default=" + defaultValue + ")*";
    }

    protected List<Button> buttons(String idPrefix) {
        Button inc = Button.success(idPrefix + "inc" + id, "Increase " + name).withEmoji(emojiUp);
        Button dec = Button.danger(idPrefix + "dec" + id, "Decrease " + name).withEmoji(emojiDown);
        List<Button> ls = new ArrayList<>();
        if (val + delta <= max) ls.add(inc);
        if (val - delta >= min) ls.add(dec);
        return ls;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    public String increment() {
        if (val + delta > max) return "[" + name + " cannot go above " + max + "]";
        val += delta;
        return null;
    }

    public String decrement() {
        if (val - delta < min) return "[" + name + " cannot go below " + min + "]";
        val -= delta;
        return null;
    }
}
