package ti4.commands2.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.FactionRecordOfStrategyCardPickService;

class FactionRecordOfSCPick extends Subcommand {

    public FactionRecordOfSCPick() {
        super(Constants.FACTION_RECORD_OF_SCPICK, "# of times an SC has been picked by a faction, by round");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction That You Want History Of").setRequired(true).setAutoComplete(true));
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FactionRecordOfStrategyCardPickService.queueReply(event);
    }
}
