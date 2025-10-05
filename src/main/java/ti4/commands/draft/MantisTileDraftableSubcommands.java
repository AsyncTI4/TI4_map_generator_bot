package ti4.commands.draft;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.draft.draftables.MantisTileDraftable;

public class MantisTileDraftableSubcommands extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(new MantisTileDraftableStartBuilding())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    protected MantisTileDraftableSubcommands() {
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

    public static class MantisTileDraftableStartBuilding extends GameStateSubcommand {
        protected MantisTileDraftableStartBuilding() {
            super(Constants.DRAFT_MANTIS_TILE_START_BUILDING, "Send the buttons to build the map", true, false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            MantisTileDraftable draftable = getDraftable(getGame());
            if (draftable == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Mantis tile isn't draftable; you may need `/draft manage add_draftable MantisTile`.");
                return;
            }

            MantisMapBuildService.initializeMapBuilding(getGame().getDraftManager());
        }
    }
}
