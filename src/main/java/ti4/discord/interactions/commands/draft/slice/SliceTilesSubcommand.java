package ti4.discord.interactions.commands.draft.slice;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

abstract class SliceTilesSubcommand extends GameStateSubcommand {

    private static final List<String> TILE_OPTIONS = List.of(
            Constants.DRAFT_TILE_1_OPTION,
            Constants.DRAFT_TILE_2_OPTION,
            Constants.DRAFT_TILE_3_OPTION,
            Constants.DRAFT_TILE_4_OPTION,
            Constants.DRAFT_TILE_5_OPTION,
            Constants.DRAFT_TILE_6_OPTION,
            Constants.DRAFT_TILE_7_OPTION,
            Constants.DRAFT_TILE_8_OPTION,
            Constants.DRAFT_TILE_9_OPTION);

    private static final List<String> ORDINALS =
            List.of("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth");

    SliceTilesSubcommand(
            String name,
            String description,
            String sliceOption,
            String sliceOptionDescription,
            boolean sliceOptionAutoComplete) {
        super(name, description, true, false);
        addOption(OptionType.STRING, sliceOption, sliceOptionDescription, true, sliceOptionAutoComplete);
        for (int i = 0; i < TILE_OPTIONS.size(); i++) {
            addOption(
                    OptionType.STRING,
                    TILE_OPTIONS.get(i),
                    "The ID of the " + ORDINALS.get(i) + " tile in the slice",
                    i == 0,
                    true);
        }
    }

    protected List<MiltyDraftTile> parseSliceTiles(SlashCommandInteractionEvent event) {
        List<MiltyDraftTile> sliceTiles = new ArrayList<>();
        for (String optionName : TILE_OPTIONS) {
            if (event.getOption(optionName) == null) break;
            MiltyDraftTile tile = SliceDraftableGroup.getTileFromOption(event, optionName);
            if (tile == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Could not find tile with ID '"
                                + event.getOption(optionName).getAsString() + "'.");
                return null;
            }
            sliceTiles.add(tile);
        }

        String sliceTemplateError = SliceDraftableGroup.sliceWorksForTemplate(getGame(), sliceTiles);
        if (sliceTemplateError != null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), sliceTemplateError);
            return null;
        }
        return sliceTiles;
    }

    protected void reportSliceUpdate(
            SlashCommandInteractionEvent event, SliceDraftable draftable, MiltyDraftSlice slice, String sliceName) {
        draftable.validateState(getGame().getDraftManager());
        FileUpload summaryImage =
                draftable.generateSummaryImage(getGame().getDraftManager(), "slice_update", List.of(slice.getName()));
        String message = "Added slice '" + sliceName + "'.";
        if (summaryImage != null) {
            MessageHelper.sendMessageWithFile(event.getChannel(), summaryImage, message, false, false);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
    }
}
