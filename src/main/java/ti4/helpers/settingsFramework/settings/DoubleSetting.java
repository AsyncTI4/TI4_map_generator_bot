package ti4.helpers.settingsFramework.settings;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;

@Getter
@Setter
@JsonIncludeProperties({"id", "val"})
public class DoubleSetting extends SettingInterface {
    private double val;
    private double defaultValue;
    private double min;
    private double max;
    private double delta;
    private static final double eps = 0.01; // needed to deal with floating point errors

    public DoubleSetting(String id, String name, double defaultValue, double min, double max, double delta) {
        super(id, name);

        this.defaultValue = this.val = defaultValue;
        this.min = min;
        this.max = max;
        this.delta = delta;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    protected void init(JsonNode json) {
        if (json.has("val")) val = json.get("val").asDouble(val);
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
        Button inc = Buttons.green(idPrefix + "inc" + id, "Increase " + name).withEmoji(emojiUp);
        Button dec = Buttons.red(idPrefix + "dec" + id, "Decrease " + name).withEmoji(emojiDown);
        List<Button> ls = new ArrayList<>();
        if (val + delta <= max + eps) ls.add(inc);
        if (val - delta >= min - eps) ls.add(dec);
        return ls;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    public String increment() {
        if (val + delta > max + eps) return String.format("[%s cannot go above %0.1f]", name, max);
        val += delta;
        return null;
    }

    public String decrement() {
        if (val - delta < min - eps) return String.format("[%s cannot go below %0.1f]", name, min);
        val -= delta;
        return null;
    }
}
