package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

@NoArgsConstructor
@AllArgsConstructor
public class IntegerRangeSetting extends SettingInterface {
    // Will show up in json
    public int valHigh;
    public int valLow;
    // Will not show up in json
    private int defaultHigh;
    private int defaultLow;
    private int minHigh;
    private int minLow;
    private int maxHigh;
    private int maxLow;
    private int delta;

    public IntegerRangeSetting(String id, String name, int valLow, int minLow, int maxLow, int valHigh, int minHigh, int maxHigh, int delta, IntegerRangeSetting value) {
        this.valHigh = value == null ? valHigh : value.valHigh;
        this.valLow = value == null ? valLow : value.valLow;

        this.id = id;
        this.name = name;
        this.defaultHigh = valHigh;
        this.minHigh = minHigh;
        this.maxHigh = maxHigh;
        this.defaultLow = valLow;
        this.minLow = minLow;
        this.maxLow = maxLow;
        this.delta = delta;
    }

    // Abstract methods
    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.equals("incHigh" + id)) return incrementHigh();
        if (action.equals("decHigh" + id)) return decrementHigh();
        if (action.equals("incLow" + id)) return incrementLow();
        if (action.equals("decLow" + id)) return decrementLow();
        return "[invalid action: " + action + "]";
    }

    protected List<Button> buttons(String idPrefix) {
        Button decLow = Button.danger(idPrefix + "decLow" + id, "Decrease Min " + name).withEmoji(emojiDown);
        Button incLow = Button.success(idPrefix + "incLow" + id, "Increase Min " + name).withEmoji(emojiUp);
        Button decHigh = Button.danger(idPrefix + "decHigh" + id, "Decrease Max " + name).withEmoji(emojiDown);
        Button incHigh = Button.success(idPrefix + "incHigh" + id, "Increase Max " + name).withEmoji(emojiUp);
        List<Button> ls = new ArrayList<>();
        if (valLow > minLow)
            ls.add(decLow);
        if (valLow < maxLow && valLow < valHigh)
            ls.add(incLow);
        if (valHigh > minHigh && valHigh > valLow)
            ls.add(decHigh);
        if (valHigh < maxHigh)
            ls.add(incHigh);
        return ls;
    }

    public void reset() {
        valHigh = defaultHigh;
        valLow = defaultLow;
    }

    public String shortValue() {
        return "[" + valLow + "-" + valHigh + "]";
    }

    public String longValue() {
        String def = "[" + defaultLow + "-" + defaultHigh + "]";
        return shortValue() + " *(limits=[" + minLow + "-" + maxHigh + "], default=" + def + ")*";
    }

    // Helper methods
    public String incrementHigh() {
        if (valHigh + delta > maxHigh) return String.format("[max %s cannot go above %u]", name, maxHigh);
        valHigh += delta;
        return null;
    }

    public String decrementHigh() {
        if (valHigh - delta < minHigh) return String.format("[max %s cannot go below %u]", name, minHigh);
        if (valHigh - delta < valLow) return String.format("[max %s cannot go below min %s]", name, name);
        valHigh -= delta;
        return null;
    }

    public String incrementLow() {
        if (valLow + delta > maxLow) return String.format("[min %s cannot go above %u]", name, maxLow);
        if (valHigh - delta < valLow) return String.format("[min %s cannot go above max %s]", name, name);
        valLow += delta;
        return null;
    }

    public String decrementLow() {
        if (valLow - delta < minLow) return String.format("[min %s cannot go below %u]", name, minLow);
        valLow -= delta;
        return null;
    }
}
