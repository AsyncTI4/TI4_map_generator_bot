package ti4.discord.interactions.commands.draft.slice;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

class SliceDraftableAddSlice extends SliceTilesSubcommand {

    SliceDraftableAddSlice() {
        super(
                Constants.DRAFT_SLICE_ADD_SLICE,
                "Add a new slice with the specified tiles",
                Constants.DRAFT_NEW_SLICE_OPTION,
                "The name of the new slice to add",
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SliceDraftable draftable = SliceDraftableGroup.getDraftableOrReply(event, getGame());
        if (draftable == null) return;

        String sliceName = event.getOption(Constants.DRAFT_NEW_SLICE_OPTION).getAsString();
        if (draftable.getSliceByName(sliceName) != null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Slice with name '" + sliceName + "' already exists.");
            return;
        }

        List<MiltyDraftTile> sliceTiles = parseSliceTiles(event);
        if (sliceTiles == null) return;

        MiltyDraftSlice slice = new MiltyDraftSlice();
        slice.setName(sliceName);
        slice.setTiles(sliceTiles);
        draftable.getSlices().add(slice);
        reportSliceUpdate(event, draftable, slice, sliceName);
    }
}
