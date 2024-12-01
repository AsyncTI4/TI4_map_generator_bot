package ti4.commands2.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.FactionRecordOfTechService;

class FactionRecordOfTech extends Subcommand {

    private static final String FACTION_WON_FILTER = "faction_won";

    public FactionRecordOfTech() {
        super(Constants.FACTION_RECORD_OF_TECH, "# of times a tech has been acquired by a faction");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction That You Want Tech History Of").setRequired(true).setAutoComplete(true));
        addOptions(GameStatisticsFilterer.gameStatsFilters());
        addOptions(new OptionData(OptionType.BOOLEAN, FACTION_WON_FILTER, "Only include games where the faction won"));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FactionRecordOfTechService.queueReply(event);
    }
}
