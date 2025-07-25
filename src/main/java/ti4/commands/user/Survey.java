package ti4.commands.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PlayerTitleHelper;

class Survey extends GameStateSubcommand {

    public Survey() {
        super(Constants.SURVEY, "Take the 5 question survey about preferences", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PlayerTitleHelper.answerSurvey(event, "spoof_yes_1");
    }
}
