package ti4.commands.draft.slice;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

class SliceDraftableModifySlice extends GameStateSubcommand {

    protected SliceDraftableModifySlice() {
        super(
                Constants.DRAFT_SLICE_MODIFY_SLICE,
                "Set the tiles for a slice, listing their tile IDs in order",
                true,
                false);
        addOption(OptionType.STRING, Constants.DRAFT_SLICE_OPTION, "The name of the slice to modify", true, true);
        addOption(
                OptionType.STRING, Constants.DRAFT_TILE_1_OPTION, "The ID of the first tile in the slice", true, true);
        addOption(
                OptionType.STRING,
                Constants.DRAFT_TILE_2_OPTION,
                "The ID of the second tile in the slice",
                false,
                true);
        addOption(
                OptionType.STRING, Constants.DRAFT_TILE_3_OPTION, "The ID of the third tile in the slice", false, true);
        addOption(
                OptionType.STRING,
                Constants.DRAFT_TILE_4_OPTION,
                "The ID of the fourth tile in the slice",
                false,
                true);
        addOption(
                OptionType.STRING, Constants.DRAFT_TILE_5_OPTION, "The ID of the fifth tile in the slice", false, true);
        addOption(
                OptionType.STRING, Constants.DRAFT_TILE_6_OPTION, "The ID of the sixth tile in the slice", false, true);
        addOption(
                OptionType.STRING,
                Constants.DRAFT_TILE_7_OPTION,
                "The ID of the seventh tile in the slice",
                false,
                true);
        addOption(
                OptionType.STRING,
                Constants.DRAFT_TILE_8_OPTION,
                "The ID of the eighth tile in the slice",
                false,
                true);
        addOption(
                OptionType.STRING, Constants.DRAFT_TILE_9_OPTION, "The ID of the ninth tile in the slice", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SliceDraftable draftable = SliceDraftableGroup.getDraftable(getGame());
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Slice isn't draftable; you may need `/draft manage add_draftable Slice`.");
            return;
        }

        List<MiltyDraftTile> sliceTiles = new ArrayList<>();
        List<String> options = List.of(
                Constants.DRAFT_TILE_1_OPTION,
                Constants.DRAFT_TILE_2_OPTION,
                Constants.DRAFT_TILE_3_OPTION,
                Constants.DRAFT_TILE_4_OPTION,
                Constants.DRAFT_TILE_5_OPTION,
                Constants.DRAFT_TILE_6_OPTION,
                Constants.DRAFT_TILE_7_OPTION,
                Constants.DRAFT_TILE_8_OPTION,
                Constants.DRAFT_TILE_9_OPTION);
        for (String optionName : options) {
            if (event.getOption(optionName) != null) {
                MiltyDraftTile tile = SliceDraftableGroup.getTileFromOption(event, optionName);
                if (tile != null) {
                    sliceTiles.add(tile);
                } else {
                    MessageHelper.sendMessageToChannel(
                            event.getChannel(),
                            "Could not find tile with ID '"
                                    + event.getOption(optionName).getAsString() + "'.");
                    return;
                }
            } else {
                break;
            }
        }

        String sliceTemplateError = SliceDraftableGroup.sliceWorksForTemplate(getGame(), sliceTiles);
        if (sliceTemplateError != null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), sliceTemplateError);
            return;
        }

        String sliceName = event.getOption(Constants.DRAFT_SLICE_OPTION).getAsString();
        MiltyDraftSlice slice = draftable.getSliceByName(sliceName);
        if (slice == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Could not find slice with name '" + sliceName + "'.");
            return;
        }

        slice.setTiles(sliceTiles);
        draftable.validateState(getGame().getDraftManager());
        FileUpload summaryImage =
                draftable.generateSummaryImage(getGame().getDraftManager(), "slice_update", List.of(slice.getName()));
        if (summaryImage != null) {
            MessageHelper.sendMessageWithFile(
                    event.getChannel(), summaryImage, "Added slice '" + sliceName + "'.", false, false);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Added slice '" + sliceName + "'.");
        }
    }
}
