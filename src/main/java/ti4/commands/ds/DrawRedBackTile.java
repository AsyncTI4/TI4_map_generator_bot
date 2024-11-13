package ti4.commands.ds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.generator.TileHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TileModel;

public class DrawRedBackTile extends GameStateSubcommand {

    public DrawRedBackTile() {
        super(Constants.DRAW_RED_BACK_TILE, "Draw a random red back tile (for Dane's mystery tweet)", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "How many to draw? Default: 1"));
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALL_ASYNC_TILES, "True to include all async blue back tiles in this list (not just PoK + DS). Default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        drawRedBackTiles(event, game, player, count);
    }

    public static void drawRedBackTiles(GenericInteractionCreateEvent event, Game game, Player player, int count) {
        List<String> tilesToPullFrom = new ArrayList<>(List.of(
            //Source:  https://discord.com/channels/943410040369479690/1009507056606249020/1140518249088434217
            //         https://cdn.discordapp.com/attachments/1009507056606249020/1140518248794820628/Starmap_Roll_Helper.xlsx

            "39",
            "40",
            "41",
            "42",
            "43",
            "44",
            "45",
            "46",
            "47",
            "48",
            "49",
            "67",
            "68",
            "77",
            "78",
            "79",
            "80",
            "d117",
            "d118",
            "d119",
            "d120",
            "d121",
            "d122",
            "d123"));

        // if (includeAllTiles) tilesToPullFrom = TileHelper.getAllTiles().values().stream().filter(tile -> !tile.isAnomaly() && !tile.isHomeSystem() && !tile.isHyperlane()).map(TileModel::getId).toList();
        tilesToPullFrom.removeAll(game.getTileMap().values().stream().map(Tile::getTileID).toList());
        if (!game.isDiscordantStarsMode()) {
            tilesToPullFrom.removeAll(tilesToPullFrom.stream().filter(tileID -> tileID.contains("d")).toList());
        }
        List<String> tileToPullFromUnshuffled = new ArrayList<>(tilesToPullFrom);
        Collections.shuffle(tilesToPullFrom);

        if (tilesToPullFrom.size() < count) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Not enough tiles to draw from");
            return;
        }

        List<MessageEmbed> tileEmbeds = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String tileID = tilesToPullFrom.get(i);
            ids.add(tileID);
            TileModel tile = TileHelper.getTileById(tileID);
            tileEmbeds.add(tile.getHelpMessageEmbed(false));
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " drew " + count + " red back tiles from this list:\n> " + tileToPullFromUnshuffled);

        event.getMessageChannel().sendMessageEmbeds(tileEmbeds).queue();
        if (ids.size() == 1) {
            ButtonHelper.detTileAdditionStep1(player, ids.getFirst());
        }
    }

}
