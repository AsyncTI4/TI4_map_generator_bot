package ti4.helpers.settingsFramework.settings;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import tools.jackson.databind.JsonNode;

@Getter
@Setter
@JsonIncludeProperties("id")
public class ReadOnlyTextSetting extends SettingInterface {
    private String display;

    public ReadOnlyTextSetting(String id, String name, String display) {
        super(id, name);

        this.display = display;
    }

    public ReadOnlyTextSetting(String id, String name) {
        super(id, name);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    protected String shortValue() {
        return display;
    }

    protected String longValue() {
        String val = shortValue();
        return val;
    }

    protected List<Button> buttons(String idPrefix) {
        return List.of();
    }

    @Override
    protected void init(JsonNode json) {
        // Do nothing
    }

    @Override
    public String modify(GenericInteractionCreateEvent event, String action) {
        // Do nothing
        return null;
    }

    @Override
    public void reset() {
        // Do nothing
    }
}
