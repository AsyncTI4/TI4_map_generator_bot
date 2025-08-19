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
@JsonIncludeProperties({"id", "valLow", "valHigh"})
public class IntegerRangeSetting extends SettingInterface {
    private int valHigh;
    private int defaultHigh;
    private int minHigh;
    private int maxHigh;

    private int valLow;
    private int defaultLow;
    private int minLow;
    private int maxLow;

    private int delta;

    public IntegerRangeSetting(
            String id,
            String name,
            int valLow,
            int minLow,
            int maxLow,
            int valHigh,
            int minHigh,
            int maxHigh,
            int delta) {
        super(id, name);

        defaultHigh = this.valHigh = valHigh;
        defaultLow = this.valLow = valLow;
        this.minHigh = minHigh;
        this.maxHigh = maxHigh;
        this.minLow = minLow;
        this.maxLow = maxLow;
        this.delta = delta;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    protected void init(JsonNode json) {
        if (json.has("valHigh")) valHigh = json.get("valHigh").asInt(valHigh);
        if (json.has("valLow")) valLow = json.get("valLow").asInt(valLow);
    }

    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.equals("incHigh" + id)) return incrementHigh();
        if (action.equals("decHigh" + id)) return decrementHigh();
        if (action.equals("incLow" + id)) return incrementLow();
        if (action.equals("decLow" + id)) return decrementLow();
        return "[invalid action: " + action + "]";
    }

    public void reset() {
        valHigh = defaultHigh;
        valLow = defaultLow;
    }

    protected String shortValue() {
        return "[" + valLow + "-" + valHigh + "]";
    }

    protected String longValue() {
        String def = "[" + defaultLow + "-" + defaultHigh + "]";
        return shortValue() + " *(limits=[" + minLow + "-" + maxHigh + "], default=" + def + ")*";
    }

    protected List<Button> buttons(String idPrefix) {
        Button incLow =
                Buttons.green(idPrefix + "incLow" + id, "Increase Min " + name).withEmoji(emojiUp);
        Button decLow =
                Buttons.red(idPrefix + "decLow" + id, "Decrease Min " + name).withEmoji(emojiDown);
        Button incHigh =
                Buttons.green(idPrefix + "incHigh" + id, "Increase Max " + name).withEmoji(emojiUp);
        Button decHigh =
                Buttons.red(idPrefix + "decHigh" + id, "Decrease Max " + name).withEmoji(emojiDown);
        List<Button> ls = new ArrayList<>();
        if (valLow < maxLow && valLow < valHigh) ls.add(incLow);
        if (valLow > minLow) ls.add(decLow);
        if (valHigh < maxHigh) ls.add(incHigh);
        if (valHigh > minHigh && valHigh > valLow) ls.add(decHigh);
        return ls;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    private String incrementHigh() {
        if (valHigh + delta > maxHigh) return String.format("[max %s cannot go above %u]", name, maxHigh);
        valHigh += delta;
        return null;
    }

    private String decrementHigh() {
        if (valHigh - delta < minHigh) return String.format("[max %s cannot go below %u]", name, minHigh);
        if (valHigh - delta < valLow) return String.format("[max %s cannot go below min %s]", name, name);
        valHigh -= delta;
        return null;
    }

    private String incrementLow() {
        if (valLow + delta > maxLow) return String.format("[min %s cannot go above %u]", name, maxLow);
        if (valHigh - delta < valLow) return String.format("[min %s cannot go above max %s]", name, name);
        valLow += delta;
        return null;
    }

    private String decrementLow() {
        if (valLow - delta < minLow) return String.format("[min %s cannot go below %u]", name, minLow);
        valLow -= delta;
        return null;
    }
}
