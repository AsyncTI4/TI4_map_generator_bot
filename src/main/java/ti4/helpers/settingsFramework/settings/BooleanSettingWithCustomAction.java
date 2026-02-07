package ti4.helpers.settingsFramework.settings;

import java.util.function.Consumer;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;

public class BooleanSettingWithCustomAction extends BooleanSetting {
    final private Consumer<Boolean> customAction;
    public BooleanSettingWithCustomAction(String id, String name, boolean val, Consumer<Boolean> customAction) {
        super(id, name, val);
        this.customAction = customAction;
    }

    public BooleanSettingWithCustomAction(String id, String name, boolean val, String whenFalse, String whenTrue, Consumer<Boolean> customAction) {
        super(id, name, val, whenFalse, whenTrue);
        this.customAction = customAction;   
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract Methods
    // ---------------------------------------------------------------------------------------------------------------------------------

    public String modify(GenericInteractionCreateEvent event, String action) {
        String result = super.modify(event, action);
        customAction.accept(isVal());
        return result;
    }

}
