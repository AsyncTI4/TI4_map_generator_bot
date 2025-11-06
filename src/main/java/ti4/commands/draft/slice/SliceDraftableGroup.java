package ti4.commands.draft.slice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftTileManager;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftTile;

public class SliceDraftableGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new SliceDraftableModifySlice(), new SliceDraftableAddSlice(), new SliceDraftableRemoveSlice())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public SliceDraftableGroup() {
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
        return (SliceDraftable) draftManager.getDraftable(SliceDraftable.TYPE);
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
}
