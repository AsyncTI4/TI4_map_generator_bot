package ti4.commands.draft.manage;

import java.util.Objects;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerCanEndDraft extends GameStateSubcommand {

    public DraftManagerCanEndDraft() {
        super(Constants.DRAFT_MANAGE_CAN_END, "Check if the draft can be ended now", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String reason = draftManager.whatsStoppingDraftEnd();
        MessageHelper.sendMessageToChannel(
                event.getChannel(), Objects.requireNonNullElse(reason, "Nothing! Ergo, the draft has ended."));
    }
}
