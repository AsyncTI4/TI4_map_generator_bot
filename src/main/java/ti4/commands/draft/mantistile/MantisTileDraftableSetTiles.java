package ti4.commands.draft.mantistile;

import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;
import ti4.service.draft.draftables.MantisTileDraftable;

class MantisTileDraftableSetTiles extends GameStateSubcommand {
    protected MantisTileDraftableSetTiles() {
        super(Constants.DRAFT_MANTIS_TILE_SET_TILES, "Set tiles for the mantis tile draftable", true, false);
        addOption(OptionType.STRING, "tiles", "Which tiles to use for this draft (comma-separated)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MantisTileDraftable draftable = MantisTileDraftableGroup.getDraftable(getGame());
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
            if (tile.getTileBack() == TileBack.BLUE) {
                draftable.getBlueTiles().add((BlueTileDraftItem) DraftItem.generate(Category.BLUETILE, tileID));
            } else {
                draftable.getRedTiles().add((RedTileDraftItem) DraftItem.generate(Category.REDTILE, tileID));
            }
        }

        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Set the draft to use the provided " + tileIDs.size() + " tiles.");
    }
}
