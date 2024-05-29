package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.Helper;

@NoArgsConstructor
@AllArgsConstructor
public abstract class SettingInterface {
    protected static final Emoji emojiUp = Emoji.fromUnicode("📈"); // Other up options: [⬆️,⏫,☝️,🔺]
    protected static final Emoji emojiDown = Emoji.fromUnicode("📉"); // Other down options: []
    protected static final Emoji emojiToggle = Emoji.fromUnicode("🔁");

    @JsonIgnore
    public String id;
    @JsonIgnore
    public String name;
    @Setter
    @JsonIgnore
    public String emoji = null;
    @Setter
    @JsonIgnore
    public boolean editable = true;
    @Setter
    @JsonIgnore
    private String extraInfo = null;

    /**
     * 
     * @param event
     * @param action
     * @return null if successful, error message if not successful
     */
    public abstract String modify(GenericInteractionCreateEvent event, String action);

    protected abstract List<Button> buttons(String idPrefix);

    public abstract void reset();

    public abstract String shortValue();

    public abstract String longValue();

    // default methods
    @JsonIgnore
    public List<Button> getButtons(String idPrefix) {
        if (!editable)
            return new ArrayList<>();
        return buttons(idPrefix);
    }

    public String shortSummary(int pad) {
        return "`" + Helper.leftpad(id, pad) + "`: " + shortValue();
    }

    public String longSummary(int pad, String extraInfoId) {
        String emote = emoji == null ? "" : emoji;
        String val;
        if (Objects.equals(id, extraInfoId))
            val = longValue() + (extraInfo != null ? " *" + extraInfo + "*" : "");
        else
            val = shortValue();
        return String.format("`%s`%s: %s", Helper.leftpad(name, pad), emote, val);
    }
}
