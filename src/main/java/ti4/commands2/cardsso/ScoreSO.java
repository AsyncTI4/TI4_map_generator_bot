package ti4.commands2.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SecretObjectiveHelper;

class ScoreSO extends GameStateSubcommand {

    public ScoreSO() {
        super(Constants.SCORE_SO, "Score Secret Objective", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int soID = event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        SecretObjectiveHelper.scoreSO(event, getGame(), getPlayer(), soID, event.getChannel());
    }
}
