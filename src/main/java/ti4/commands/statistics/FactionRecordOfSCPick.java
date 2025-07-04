package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.FactionRecordOfStrategyCardPickService;

class FactionRecordOfSCPick extends Subcommand {

    public FactionRecordOfSCPick() {
        super(Constants.FACTION_RECORD_OF_SCPICK, "number of times a strategy card has been picked by a faction, by round");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction that you wish to get history of").setRequired(true).setAutoComplete(true));
        addOptions(GameStatisticsFilterer.gameStatsFilters());
        addOptions(new OptionData(OptionType.STRING, GameStatisticsFilterer.WINNING_FACTION_FILTER, "Filter games by if the game was won by said faction")
            .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FactionRecordOfStrategyCardPickService.queueReply(event);
    }
}
