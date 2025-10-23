package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;

class DraftManagerStartDraft extends GameStateSubcommand {

    public DraftManagerStartDraft() {
        super(
                Constants.DRAFT_MANAGE_START,
                "Start the draft; if you're having issues, try '/draft manage validate'",
                true,
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        draftManager.tryStartDraft();
    }
}
