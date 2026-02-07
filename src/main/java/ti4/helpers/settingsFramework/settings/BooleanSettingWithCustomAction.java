package ti4.helpers.settingsFramework.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import java.util.function.Consumer;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;

@JsonIncludeProperties({"id", "val"})
public class BooleanSettingWithCustomAction extends BooleanSetting {
    @JsonIgnore
    private final Consumer<Boolean> customAction;

    public BooleanSettingWithCustomAction(String id, String name, boolean val, Consumer<Boolean> customAction) {
        super(id, name, val);
        this.customAction = customAction;
    }

    public BooleanSettingWithCustomAction(
            String id, String name, boolean val, String whenFalse, String whenTrue, Consumer<Boolean> customAction) {
        super(id, name, val, whenFalse, whenTrue);
        this.customAction = customAction;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract Methods
    // ---------------------------------------------------------------------------------------------------------------------------------

    @Override
    public String modify(GenericInteractionCreateEvent event, String action) {
        String result = super.modify(event, action);
        customAction.accept(isVal());
        return result;
    }
}
