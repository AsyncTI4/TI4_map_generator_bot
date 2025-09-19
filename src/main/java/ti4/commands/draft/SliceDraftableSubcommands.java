package ti4.commands.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.GameStateSubcommand;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftTileManager;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

public class SliceDraftableSubcommands extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new SliceDraftableModifySlice(), new SliceDraftableAddSlice(), new SliceDraftableRemoveSlice())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    protected SliceDraftableSubcommands() {
        super(Constants.DRAFT_SLICE, "Commands for managing slice drafting");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static SliceDraftable getDraftable(Game game) {
        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            return null;
        }
        return (SliceDraftable) draftManager.getDraftableByType(SliceDraftable.TYPE);
    }

    public static MiltyDraftTile getTileFromOption(SlashCommandInteractionEvent event, String optionName) {
        OptionMapping optionData = event.getOption(optionName);
        if (optionData != null) {
            String tileId = optionData.getAsString();
            return DraftTileManager.findTile(tileId);
        }
        return null;
    }

    public static String sliceWorksForTemplate(Game game, List<MiltyDraftTile> sliceTiles) {
        String mapTemplateId = game.getMapTemplateID();
        if (mapTemplateId == null) {
            return null;
        }
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);
        if (mapTemplate != null) {
            Integer expectedSliceSize = mapTemplate.getTilesPerPlayer();
            if (expectedSliceSize != null && sliceTiles.size() != expectedSliceSize) {
                return "The map template " + mapTemplate.getAlias() + " expects " + expectedSliceSize
                        + " tiles per slice.";
            }
        }
        return null;
    }

    public static class SliceDraftableModifySlice extends GameStateSubcommand {

        protected SliceDraftableModifySlice() {
            super(
                    Constants.DRAFT_SLICE_MODIFY_SLICE,
                    "Set the tiles for a slice, listing their tile IDs in order",
                    true,
                    false);
            addOption(OptionType.STRING, Constants.DRAFT_SLICE_OPTION, "The name of the slice to modify", true, true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_1_OPTION,
                    "The ID of the first tile in the slice",
                    true,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_2_OPTION,
                    "The ID of the second tile in the slice",
                    false,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_3_OPTION,
                    "The ID of the third tile in the slice",
                    false,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_4_OPTION,
                    "The ID of the fourth tile in the slice",
                    false,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_5_OPTION,
                    "The ID of the fifth tile in the slice",
                    false,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_6_OPTION,
                    "The ID of the sixth tile in the slice",
                    false,
                    true);
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
                    OptionType.STRING,
                    Constants.DRAFT_TILE_9_OPTION,
                    "The ID of the ninth tile in the slice",
                    false,
                    true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            SliceDraftable draftable = getDraftable(getGame());
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
                    MiltyDraftTile tile = getTileFromOption(event, optionName);
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

            String sliceTemplateError = sliceWorksForTemplate(getGame(), sliceTiles);
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
            FileUpload summaryImage = draftable.generateSummaryImage(
                    getGame().getDraftManager(), "slice_update", List.of(slice.getName()));
            if (summaryImage != null) {
                MessageHelper.sendMessageWithFile(
                        event.getChannel(), summaryImage, "Added slice '" + sliceName + "'.", false, false);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Added slice '" + sliceName + "'.");
            }
        }
    }

    public static class SliceDraftableAddSlice extends GameStateSubcommand {

        protected SliceDraftableAddSlice() {
            super(Constants.DRAFT_SLICE_ADD_SLICE, "Add a new slice with the specified tiles", true, false);
            addOption(OptionType.STRING, Constants.DRAFT_NEW_SLICE_OPTION, "The name of the new slice to add", true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_1_OPTION,
                    "The ID of the first tile in the slice",
                    true,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_2_OPTION,
                    "The ID of the second tile in the slice",
                    false,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_3_OPTION,
                    "The ID of the third tile in the slice",
                    false,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_4_OPTION,
                    "The ID of the fourth tile in the slice",
                    false,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_5_OPTION,
                    "The ID of the fifth tile in the slice",
                    false,
                    true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFT_TILE_6_OPTION,
                    "The ID of the sixth tile in the slice",
                    false,
                    true);
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
                    OptionType.STRING,
                    Constants.DRAFT_TILE_9_OPTION,
                    "The ID of the ninth tile in the slice",
                    false,
                    true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            SliceDraftable draftable = getDraftable(getGame());
            if (draftable == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Slice isn't draftable; you may need `/draft manage add_draftable Slice`.");
                return;
            }

            String sliceName = event.getOption(Constants.DRAFT_NEW_SLICE_OPTION).getAsString();
            MiltyDraftSlice slice = draftable.getSliceByName(sliceName);
            if (slice != null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Slice with name '" + sliceName + "' already exists.");
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
                    MiltyDraftTile tile = getTileFromOption(event, optionName);
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

            String sliceTemplateError = sliceWorksForTemplate(getGame(), sliceTiles);
            if (sliceTemplateError != null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), sliceTemplateError);
                return;
            }

            slice = new MiltyDraftSlice();
            slice.setName(sliceName);
            slice.setTiles(sliceTiles);
            draftable.getSlices().add(slice);
            draftable.validateState(getGame().getDraftManager());
            FileUpload summaryImage = draftable.generateSummaryImage(
                    getGame().getDraftManager(), "slice_update", List.of(slice.getName()));
            if (summaryImage != null) {
                MessageHelper.sendMessageWithFile(
                        event.getChannel(), summaryImage, "Added slice '" + sliceName + "'.", false, false);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Added slice '" + sliceName + "'.");
            }
        }
    }

    public static class SliceDraftableRemoveSlice extends GameStateSubcommand {

        protected SliceDraftableRemoveSlice() {
            super(Constants.DRAFT_SLICE_REMOVE_SLICE, "Remove an existing slice by name", true, false);
            addOption(OptionType.STRING, Constants.DRAFT_SLICE_OPTION, "The name of the slice to remove", true, true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            SliceDraftable draftable = getDraftable(getGame());
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
}
