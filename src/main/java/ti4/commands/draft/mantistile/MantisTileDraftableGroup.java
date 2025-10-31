package ti4.commands.draft.mantistile;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.MantisTileDraftable;

public class MantisTileDraftableGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new MantisTileDraftableStartBuilding(),
                    new MantisTileDraftableConfigure(),
                    new MantisTileDraftableSetTiles())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public MantisTileDraftableGroup() {
        super(Constants.DRAFT_MANTIS_TILE, "Commands for managing mantis tile drafting and building");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static MantisTileDraftable getDraftable(Game game) {
        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            return null;
        }
        return (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
    }
}
