package ti4.discord.interactions.commands.draft.slice;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.helpers.Constants;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

class SliceDraftableModifySlice extends SliceTilesSubcommand {

    SliceDraftableModifySlice() {
        super(
                Constants.DRAFT_SLICE_MODIFY_SLICE,
                "Set the tiles for a slice, listing their tile IDs in order",
                Constants.DRAFT_SLICE_OPTION,
                "The name of the slice to modify",
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SliceDraftable draftable = SliceDraftableGroup.getDraftableOrReply(event, getGame());
        if (draftable == null) return;

        List<MiltyDraftTile> sliceTiles = parseSliceTiles(event);
        if (sliceTiles == null) return;

        String sliceName = event.getOption(Constants.DRAFT_SLICE_OPTION).getAsString();
        MiltyDraftSlice slice = CommandHelper.requireOrReport(
                event, draftable.getSliceByName(sliceName), "Could not find slice with name '" + sliceName + "'.");
        if (slice == null) return;

        slice.setTiles(sliceTiles);
        reportSliceUpdate(event, draftable, slice, sliceName);
    }
}
