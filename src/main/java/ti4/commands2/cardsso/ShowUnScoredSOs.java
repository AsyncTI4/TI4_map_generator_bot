package ti4.commands2.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SecretObjectiveHelper;

class ShowUnScoredSOs extends GameStateSubcommand {

    public ShowUnScoredSOs() {
        super(Constants.SHOW_UNSCORED_SOS, "List any secret objectives that are not scored yet", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SecretObjectiveHelper.showUnscored(getGame(), event);
    }
}
