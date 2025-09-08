package ti4.commands.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.PlayerTitleHelper;

class Survey extends Subcommand {

    public Survey() {
        super(Constants.SURVEY, "Take the 5 question survey about preferences");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PlayerTitleHelper.answerSurvey(event, "spoof_yes_1");
    }
}
