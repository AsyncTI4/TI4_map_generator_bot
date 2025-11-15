package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;

class DraftManagerRemoveDraftable extends GameStateSubcommand {

    public DraftManagerRemoveDraftable() {
        super(Constants.DRAFT_MANAGE_REMOVE_DRAFTABLE, "Remove a draftable from the draft manager", true, false);
        addOption(OptionType.STRING, Constants.DRAFTABLE_TYPE_OPTION, "Type of draftable to remove", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String draftableTypeStr = event.getOption(Constants.DRAFTABLE_TYPE_OPTION, OptionMapping::getAsString);
        DraftableType draftableType = DraftableType.of(draftableTypeStr);

        boolean removed = draftManager.getDraftables().removeIf(d -> d.getType().equals(draftableType));

        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Removed draftable of type: " + draftableTypeStr);
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "No draftable of type: " + draftableTypeStr + " found to remove.");
        }
    }
}
