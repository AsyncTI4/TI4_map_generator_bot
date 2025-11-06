package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.draft.BagDraft;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenDraftBagService;

class ApplyDraftBags extends GameStateSubcommand {

    public ApplyDraftBags() {
        super("apply_draft_bags", "Begin selecting items from draft bags to apply them to your faction.", true, false);
        addOption(
                OptionType.BOOLEAN,
                Constants.FORCE,
                "Force apply current bags, even if the bag draft is not complete.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        BagDraft draft = game.getActiveBagDraft();

        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!draft.isDraftStageComplete() && !force) {
            String message =
                    "The draft stage of the FrankenDraft is NOT complete. Please finish the draft or rerun the command with the force option set.";
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
            return;
        }

        FrankenDraftBagService.applyDraftBags(event, game);
    }
}
