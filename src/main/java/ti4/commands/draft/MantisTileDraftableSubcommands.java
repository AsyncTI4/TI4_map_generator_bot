package ti4.commands.draft;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;
import ti4.service.draft.DraftManager;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.draft.draftables.MantisTileDraftable;

public class MantisTileDraftableSubcommands extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new MantisTileDraftableStartBuilding(),
                    new MantisTileDraftableConfigure(),
                    new MantisTileDraftableSetTiles())
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

    public static class MantisTileDraftableConfigure extends GameStateSubcommand {
        protected MantisTileDraftableConfigure() {
            super(Constants.DRAFT_MANTIS_TILE_CONFIGURE, "Configure the mantis tile draftable", true, false);
            addOption(OptionType.INTEGER, "mulligans", "Number of mulligans during map build", false);
            addOption(OptionType.INTEGER, "extra_blues", "Number of extra blue tiles during map build", false);
            addOption(OptionType.INTEGER, "extra_reds", "Number of extra red tiles during map build", false);
            addOption(
                    OptionType.STRING, "mulliganed_tiles", "Which tiles have been mulliganed (comma-separated)", false);
            addOption(OptionType.STRING, "discarded_tiles", "Which tiles have been discarded (comma-separated)", false);
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
            if (event.getOption("mulligans") != null) {
                draftable.setMulligans(event.getOption("mulligans").getAsInt());
            }
            if (event.getOption("extra_blues") != null) {
                draftable.setExtraBlues(event.getOption("extra_blues").getAsInt());
            }
            if (event.getOption("extra_reds") != null) {
                draftable.setExtraReds(event.getOption("extra_reds").getAsInt());
            }
            if (event.getOption("mulliganed_tiles") != null) {
                String commaSeparatedTiles = event.getOption("mulliganed_tiles").getAsString();
                draftable.getMulliganTileIDs().clear();
                if (commaSeparatedTiles != null && commaSeparatedTiles.trim().isEmpty()) {
                    draftable
                            .getMulliganTileIDs()
                            .addAll(List.of(commaSeparatedTiles.split(",")).stream()
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toList()));
                }
            }
            if (event.getOption("discarded_tiles") != null) {
                String commaSeparatedTiles = event.getOption("discarded_tiles").getAsString();
                draftable.getDiscardedTileIDs().clear();
                if (commaSeparatedTiles != null && !commaSeparatedTiles.trim().isEmpty()) {
                    draftable
                            .getDiscardedTileIDs()
                            .addAll(List.of(commaSeparatedTiles.split(",")).stream()
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toList()));
                }
            }
        }
    }

    public static class MantisTileDraftableSetTiles extends GameStateSubcommand {
        protected MantisTileDraftableSetTiles() {
            super(Constants.DRAFT_MANTIS_TILE_SET_TILES, "Set tiles for the mantis tile draftable", true, false);
            addOption(OptionType.STRING, "tiles", "Which tiles to use for this draft (comma-separated)", true);
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
            if (event.getOption("tiles") == null) {
                return;
            }
            String commaSeparatedTiles = event.getOption("tiles").getAsString();
            if (commaSeparatedTiles == null || commaSeparatedTiles.trim().isEmpty()) {
                return;
            }

            draftable.getBlueTiles().clear();
            draftable.getRedTiles().clear();

            List<String> tileIDs = List.of(commaSeparatedTiles.split(",")).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            for (String tileID : tileIDs) {
                TileModel tile = TileHelper.getTileById(tileID);
                if (tile == null) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown tile ID: " + tileID + ".");
                    return;
                }
                if (tile.getTileBack() != TileBack.BLUE && tile.getTileBack() != TileBack.RED) {
                    MessageHelper.sendMessageToChannel(
                            event.getChannel(), "Tile ID " + tileID + " is not a blue or red tile.");
                    return;
                }
                if (tile.getTileBack().equals(TileBack.BLUE)) {
                    draftable.getBlueTiles().add((BlueTileDraftItem) DraftItem.generate(Category.BLUETILE, tileID));
                } else {
                    draftable.getRedTiles().add((RedTileDraftItem) DraftItem.generate(Category.REDTILE, tileID));
                }
            }

            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Set the draft to use the provided " + tileIDs.size() + " tiles.");
        }
    }
}
