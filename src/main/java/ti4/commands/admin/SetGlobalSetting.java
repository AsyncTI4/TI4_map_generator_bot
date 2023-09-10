package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.GlobalSettings;

public class SetGlobalSetting extends AdminSubcommandData {

    public SetGlobalSetting() {
        super(Constants.SET_SETTING, "Set or change a global setting");
        addOptions(new OptionData(OptionType.STRING, Constants.SETTING_NAME, "Setting to set").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SETTING_VALUE, "Value to set").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SETTING_TYPE, "Type of setting").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping setting = event.getOption(Constants.SETTING_NAME);
        OptionMapping value = event.getOption(Constants.SETTING_VALUE);
        OptionMapping type = event.getOption(Constants.SETTING_TYPE);

        if (setting != null && value != null && type != null) {
            if ("string".equals(type.getAsString()))
                GlobalSettings.setSetting(setting.getAsString(), value.getAsString());
            if ("number".equals(type.getAsString()))
                GlobalSettings.setSetting(setting.getAsString(), value.getAsInt());
            if ("bool".equals(type.getAsString()))  //TODO: Fix This, it doesn't work.
                GlobalSettings.setSetting(setting.getAsString(), value.getAsBoolean());
            GlobalSettings.saveSettings();
        } else {
            sendMessage("Bad Command!");
        }
    }

}
