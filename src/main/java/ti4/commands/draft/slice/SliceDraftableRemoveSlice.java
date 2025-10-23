package ti4.commands.draft.slice;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftSlice;

class SliceDraftableRemoveSlice extends GameStateSubcommand {

    protected SliceDraftableRemoveSlice() {
        super(Constants.DRAFT_SLICE_REMOVE_SLICE, "Remove an existing slice by name", true, false);
        addOption(OptionType.STRING, Constants.DRAFT_SLICE_OPTION, "The name of the slice to remove", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SliceDraftable draftable = SliceDraftableGroup.getDraftable(getGame());
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Slice isn't draftable; you may need `/draft manage add_draftable Slice`.");
            return;
        }

        String sliceName = event.getOption(Constants.DRAFT_SLICE_OPTION).getAsString();
        MiltyDraftSlice slice = draftable.getSliceByName(sliceName);
        if (slice == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Slice with name '" + sliceName + "' does not exist.");
            return;
        }

        draftable.getSlices().remove(slice);
        draftable.validateState(getGame().getDraftManager());
    }
}
