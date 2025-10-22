package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;
import ti4.service.draft.Draftable;

class DraftManagerPostDraftWork extends GameStateSubcommand {

    public DraftManagerPostDraftWork() {
        super(
                Constants.DRAFT_MANAGE_POST_DRAFT_WORK,
                "Have the draft components do (or redo) the post-draft work",
                true,
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        draftManager.getOrchestrator().onDraftEnd(draftManager);
        for (Draftable draftable : draftManager.getDraftables()) {
            draftable.onDraftEnd(draftManager);
        }
    }
}
