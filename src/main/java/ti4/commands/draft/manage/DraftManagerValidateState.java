package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;

class DraftManagerValidateState extends GameStateSubcommand {

    DraftManagerValidateState() {
        super(Constants.DRAFT_MANAGE_VALIDATE, "Get any reason the draft is in a bad state", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        draftManager.validateState();
    }
}
