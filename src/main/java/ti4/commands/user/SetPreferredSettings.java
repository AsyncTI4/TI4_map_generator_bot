package ti4.commands.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettingsManager;

class SetPreferredSettings extends Subcommand {

    public SetPreferredSettings() {
        super("set_preferred_settings", "Set your preferred settings to various questions");
        addOptions(new OptionData(
                OptionType.BOOLEAN, "pre_decline_sc", "True to be prompted to pre-decline on strategy cards"));
        addOptions(new OptionData(OptionType.BOOLEAN, "pillage_msg", "True to get the Pillage flavor text"));
        addOptions(new OptionData(OptionType.BOOLEAN, "sarween_msg", "True to get the Sarween Tools flavor text"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                "pass_on_agenda_stuff",
                "True to pass on \"when\"s and \"after\"s if you have none"));
        addOptions(new OptionData(
                OptionType.INTEGER,
                "sabo_decline_median",
                "Your median hours that the bot will wait for auto \"No Sabo\". Enter 0 to turn off."));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                "auto_respond_no_secrets",
                "True to auto decline scoring status phase secrets if you can't score any"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var userSettings = UserSettingsManager.get(event.getUser().getId());

        Boolean declineSC = event.getOption("pre_decline_sc", null, OptionMapping::getAsBoolean);
        if (declineSC != null) userSettings.setPrefersPrePassOnSC(declineSC);

        Boolean pillage = event.getOption("pillage_msg", null, OptionMapping::getAsBoolean);
        if (pillage != null) userSettings.setPrefersPillageMsg(pillage);

        Boolean sarween = event.getOption("sarween_msg", null, OptionMapping::getAsBoolean);
        if (sarween != null) userSettings.setPrefersSarweenMsg(sarween);

        Boolean agenda = event.getOption("pass_on_agenda_stuff", null, OptionMapping::getAsBoolean);
        if (agenda != null) userSettings.setPrefersPassOnWhensAfters(agenda);

        Boolean secrets = event.getOption("auto_respond_no_secrets", null, OptionMapping::getAsBoolean);
        if (secrets != null) {
            if (secrets) {
                userSettings.setSandbagPref("bot");
            } else {
                userSettings.setSandbagPref("manual");
            }
        }

        Integer sabo = event.getOption("sabo_decline_median", null, OptionMapping::getAsInt);
        if (sabo != null) {
            userSettings.setAutoNoSaboInterval(sabo);
        }
        MessageHelper.sendMessageToEventChannel(event, "Successfully set user settings");
        UserSettingsManager.save(userSettings);
    }
}
