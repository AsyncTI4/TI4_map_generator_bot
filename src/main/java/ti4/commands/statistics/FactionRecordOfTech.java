package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.FactionRecordOfTechService;

class FactionRecordOfTech extends Subcommand {

    public FactionRecordOfTech() {
        super(Constants.FACTION_RECORD_OF_TECH, "Number of times a technology has been acquired by a faction");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction that you want the technology history Of").setRequired(true).setAutoComplete(true));
        addOptions(GameStatisticsFilterer.gameStatsFilters());
        addOptions(new OptionData(OptionType.STRING, GameStatisticsFilterer.WINNING_FACTION_FILTER, "Filter games by if the game was won by said faction")
            .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FactionRecordOfTechService.queueReply(event);
    }
}
