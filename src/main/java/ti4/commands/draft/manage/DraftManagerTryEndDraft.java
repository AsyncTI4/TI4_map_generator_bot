package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;

class DraftManagerTryEndDraft extends GameStateSubcommand {

    public DraftManagerTryEndDraft() {
        super(Constants.DRAFT_MANAGE_END, "Try to end the draft", true, false);
        addOption(
                OptionType.BOOLEAN,
                Constants.FORCE_OPTION,
                "Attempt to ignore any blocking reason and end anyway",
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        boolean force = event.getOption(Constants.FORCE_OPTION, false, OptionMapping::getAsBoolean);
        if (force) {
            draftManager.endDraft(event);
        } else {
            draftManager.tryEndDraft(event);
        }
    }
}
