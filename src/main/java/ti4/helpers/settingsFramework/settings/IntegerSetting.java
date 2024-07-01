package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

@NoArgsConstructor
@AllArgsConstructor
public class IntegerSetting extends SettingInterface {
    // Will show up in json
    public int val;
    // Will not show up in json
    private int defaultValue;
    private int min;
    private int max;
    private int delta;

    public IntegerSetting(String id, String name, int val, int min, int max, int delta, IntegerSetting value) {
        this.val = value == null ? val : value.val;

        this.id = id;
        this.name = name;
        this.defaultValue = val;
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
        if (val - delta >= min) ls.add(dec);
        if (val + delta <= max) ls.add(inc);
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
