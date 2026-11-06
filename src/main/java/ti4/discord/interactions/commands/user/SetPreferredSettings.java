package ti4.discord.interactions.commands.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.HyperlaneStyle;
import ti4.settings.users.RefreshMapStyle;
import ti4.settings.users.UserSettingsManager;

class SetPreferredSettings extends Subcommand {

    private static final String CUSTOM_HYPERLANE_STYLE = "custom_hyperlane_style";
    private static final String GAME_DEFAULT = "GAME_DEFAULT";

    public SetPreferredSettings() {
        super("set_preferred_settings", "Set your preferred settings to various questions");
        addOptions(new OptionData(
                OptionType.BOOLEAN, "pre_decline_sc", "True to be prompted to pre-decline on strategy cards"));
        addOptions(new OptionData(OptionType.BOOLEAN, "pillage_msg", "True to get the Pillage flavor text"));
        addOptions(new OptionData(OptionType.BOOLEAN, "sarween_msg", "True to get the Sarween Tools flavor text"));
        addOptions(new OptionData(
                OptionType.BOOLEAN, "join_strat_threads", "True to join strategy threads automatically"));
        addOptions(new OptionData(
                        OptionType.STRING, Constants.VOLTRON_STYLE, "Choose what your Eidolon Maximum looks like")
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                "auto_debt_clearance",
                "True to auto clear debt when you send trade goods or commodities"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                "activity_tracking",
                "False to clear all activity tracking and disable future tracking"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                "pass_on_agenda_stuff",
                "True to pass on \"when\"s and \"after\"s if you have none"));
        addOptions(new OptionData(
                        OptionType.INTEGER,
                        "sabo_decline_median",
                        "Your median hours that the bot will wait for auto \"No Sabo\". Enter 0 to turn off.")
                .setRequiredRange(0, 240));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                "auto_respond_no_secrets",
                "True to auto decline scoring status phase secrets if you can't score any"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                "ephemeral_wrong_button_warning",
                "True to keep the \"wrong button\" warning ephemeral"));
        OptionData refreshMapStyle =
                new OptionData(OptionType.STRING, "refresh_map_style", "What the Refresh Map button shows you");
        for (RefreshMapStyle style : RefreshMapStyle.values()) {
            refreshMapStyle.addChoice(style.getLabel(), style.getValue());
        }
        addOptions(refreshMapStyle);
        OptionData hyperlaneStyle = new OptionData(
                OptionType.STRING,
                CUSTOM_HYPERLANE_STYLE,
                "How one-way custom hyperlanes look on maps drawn just for you (fog)");
        hyperlaneStyle.addChoice("Game default", GAME_DEFAULT);
        for (HyperlaneStyle style : HyperlaneStyle.values()) {
            hyperlaneStyle.addChoice(style.getLabel(), style.name());
        }
        addOptions(hyperlaneStyle);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var userSettings = UserSettingsManager.get(event.getUser().getId());

        Boolean declineSC = event.getOption("pre_decline_sc", null, OptionMapping::getAsBoolean);
        if (declineSC != null) userSettings.setPrefersPrePassOnSC(declineSC);

        Boolean pillage = event.getOption("pillage_msg", null, OptionMapping::getAsBoolean);
        if (pillage != null) userSettings.setPrefersPillageMsg(pillage);

        Boolean debtClearance = event.getOption("auto_debt_clearance", null, OptionMapping::getAsBoolean);
        if (debtClearance != null) userSettings.setPrefersAutoDebtClearance(debtClearance);

        Boolean activityTracking = event.getOption("activity_tracking", null, OptionMapping::getAsBoolean);
        if (activityTracking != null) {
            userSettings.setActivityTracking(activityTracking);
            if (!activityTracking) {
                userSettings.setActiveHours("0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0");
            }
        }

        Boolean sarween = event.getOption("sarween_msg", null, OptionMapping::getAsBoolean);
        if (sarween != null) userSettings.setPrefersSarweenMsg(sarween);

        Boolean strat = event.getOption("join_strat_threads", null, OptionMapping::getAsBoolean);
        if (strat != null) userSettings.setPrefersStrategyThreadJoining(strat);

        String voltron = event.getOption(Constants.VOLTRON_STYLE, null, OptionMapping::getAsString);
        if (voltron != null) userSettings.setVoltronStyle(voltron);

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

        Boolean wrongButtonWarning =
                event.getOption("ephemeral_wrong_button_warning", null, OptionMapping::getAsBoolean);
        if (wrongButtonWarning != null) {
            userSettings.setPrefersWrongButtonEphemeral(wrongButtonWarning);
        }

        String refreshMapStyle = event.getOption("refresh_map_style", null, OptionMapping::getAsString);
        if (refreshMapStyle != null) {
            RefreshMapStyle.fromValue(refreshMapStyle).ifPresent(userSettings::setRefreshMapStyle);
        }

        String hyperlaneStyle = event.getOption(CUSTOM_HYPERLANE_STYLE, null, OptionMapping::getAsString);
        if (hyperlaneStyle != null) {
            userSettings.setCustomHyperlaneStyle(
                    HyperlaneStyle.fromName(hyperlaneStyle).orElse(null));
        }

        Integer sabo = event.getOption("sabo_decline_median", null, OptionMapping::getAsInt);
        if (sabo != null) {
            userSettings.setAutoNoSaboInterval(sabo);
        }
        MessageHelper.sendMessageToEventChannel(event, "Successfully set user settings");
        UserSettingsManager.save(userSettings);
    }
}
