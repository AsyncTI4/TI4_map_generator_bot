package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerCanSetupPlayers extends GameStateSubcommand {

    public DraftManagerCanSetupPlayers() {
        super(Constants.DRAFT_MANAGE_CAN_SETUP_PLAYERS, "Check if the draft is ready for player setup", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String reason = draftManager.whatsStoppingSetup();
        if (reason == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "The draft should have set up players already.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), reason);
        }
    }
}
