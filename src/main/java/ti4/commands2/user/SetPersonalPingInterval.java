package ti4.commands2.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.Subcommand;
import ti4.service.PingIntervalService;
import ti4.users.UserSettingsManager;

class SetPersonalPingInterval extends Subcommand {

    public SetPersonalPingInterval() {
        super("set_personal_ping_interval", "Set your personal ping interval");
        addOption(OptionType.INTEGER, "hours", "The number of hours between turn reminder pings. Set to 0 to disable your personal preference", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int pingInterval = event.getOption("hours", 0, OptionMapping::getAsInt);
        var userSettings = UserSettingsManager.get(event.getUser().getId());
        PingIntervalService.set(event, userSettings, pingInterval);
    }
}
