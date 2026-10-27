package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.ScoringStatisticsService;

class ScoringStats extends Subcommand {

    ScoringStats() {
        super(Constants.SCORING, "The most common winning paths and how often winners scored each type of point");
        addOptions(new OptionData(
                OptionType.BOOLEAN, Constants.POK_ONLY, "Show Prophecy of Kings games without Thunder's Edge"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.POK_COMPARE,
                "Show Thunder's Edge games with the percent change from Prophecy of Kings"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ScoringStatisticsService.queueReply(event);
    }
}
