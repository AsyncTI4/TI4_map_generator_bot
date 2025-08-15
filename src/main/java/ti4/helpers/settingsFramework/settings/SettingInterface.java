package ti4.helpers.settingsFramework.settings;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.Helper;
import ti4.service.emoji.TI4Emoji;

@Getter
@Setter
public abstract class SettingInterface {
    protected static final Emoji emojiUp = Emoji.fromUnicode("📈"); // Other up options: [⬆️,⏫,☝️,🔺]
    protected static final Emoji emojiDown = Emoji.fromUnicode("📉"); // Other down options: []
    protected static final Emoji emojiToggle = Emoji.fromUnicode("🔁");

    protected String id;
    protected String name;
    protected String emoji;
    protected boolean editable = true;
    protected boolean disabled;
    protected String extraInfo;

    SettingInterface(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract / Protected Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    protected abstract void init(JsonNode json);

    public abstract String modify(GenericInteractionCreateEvent event, String action);

    public abstract void reset();

    protected abstract String shortValue();

    protected abstract String longValue();

    protected abstract List<Button> buttons(String idPrefix);

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Default Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    public void initialize(JsonNode json) {
        if (json == null) return;
        if (json.get("id").asText(id).equals(id)) init(json);
    }

    public List<Button> getButtons(String idPrefix) {
        if (!editable) return new ArrayList<>();
        return buttons(idPrefix);
    }

    // `  id`: shortval
    public String shortSummary(int pad) {
        return "`" + Helper.leftpad(id, pad) + "`: " + shortValue();
    }

    // `               name`: shortval
    // `last pressed button`: long value *extra info*
    public String longSummary(int pad, String extraInfoId) {
        String emote = emoji == null ? "" : emoji;
        String val;
        if (Objects.equals(id, extraInfoId)) val = longValue() + (extraInfo != null ? " *" + extraInfo + "*" : "");
        else val = shortValue();
        return String.format("`%s`%s: %s", Helper.leftpad(name, pad), emote, val);
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public void setEmoji(TI4Emoji emoji) {
        if (emoji == null) {
            this.emoji = null;
        } else {
            this.emoji = emoji.toString();
        }
    }
}
