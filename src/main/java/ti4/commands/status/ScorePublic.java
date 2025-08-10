package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.objectives.ScorePublicObjectiveService;

class ScorePublic extends GameStateSubcommand {

    public ScorePublic() {
        super(Constants.SCORE_OBJECTIVE, "Score Public Objective", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int poID = event.getOption(Constants.PO_ID).getAsInt();
        ScorePublicObjectiveService.scorePO(event, event.getChannel(), getGame(), getPlayer(), poID);
    }
}
