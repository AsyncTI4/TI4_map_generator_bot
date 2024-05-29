package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

@NoArgsConstructor
@AllArgsConstructor
public class DoubleSetting extends SettingInterface {
    // Will show up in json
    public double val;
    // Will not show up in json
    private static double eps = 0.01;
    private double defaultValue;
    private double min;
    private double max;
    private double delta;

    public DoubleSetting(String id, String name, double defaultValue, double min, double max, double delta, DoubleSetting value) {
        this.val = value == null ? defaultValue : value.val;

        this.id = id;
        this.name = name;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.delta = delta;
    }

    // Abstract methods
    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.equals("inc" + id)) return increment();
        if (action.equals("dec" + id)) return decrement();
        return "[invalid action: " + action + "]";
    }

    protected List<Button> buttons(String idPrefix) {
        Button inc = Button.success(idPrefix + "inc" + id, "Increase " + name).withEmoji(emojiUp);
        Button dec = Button.danger(idPrefix + "dec" + id, "Decrease " + name).withEmoji(emojiDown);
        List<Button> ls = new ArrayList<>();
        if (val - delta >= min - eps) ls.add(dec);
        if (val + delta <= max + eps) ls.add(inc);
        return ls;
    }

    public void reset() {
        val = defaultValue;
    }

    public String shortValue() {
        return "" + val;
    }

    public String longValue() {
        return val + " *(limits=[" + min + "-" + max + "], default=" + defaultValue + ")*";
    }

    // Helper methods
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
