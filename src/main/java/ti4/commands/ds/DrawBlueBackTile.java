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
import ti4.generator.TileHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TileModel;

public class DrawBlueBackTile extends DiscordantStarsSubcommandData {

    public DrawBlueBackTile() {
        super(Constants.DRAW_BLUE_BACK_TILE, "Draw a random blue back tile (for Star Charts and Decrypted Cartoglyph)");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "How many to draw? Default: 1"));
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALL_ASYNC_TILES, "True to include all async blue back tiles in this list (not just PoK + DS). Default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        boolean includeAllTiles = event.getOption(Constants.INCLUDE_ALL_ASYNC_TILES, false, OptionMapping::getAsBoolean);
        drawBlueBackTiles(event, activeGame, player, count, includeAllTiles);
    }

    public void drawBlueBackTiles(GenericInteractionCreateEvent event, Game activeGame, Player player, int count, boolean includeAllTiles) {
        List<String> tilesToPullFrom = new ArrayList<>(List.of(
            //Source:  https://discord.com/channels/943410040369479690/1009507056606249020/1140518249088434217
            //         https://cdn.discordapp.com/attachments/1009507056606249020/1140518248794820628/Starmap_Roll_Helper.xlsx
            "19",      //Wellon
            "20",      //Vefut II
            "21",      //Thibah
            "22",      //Tar'mann
            "23",      //Saudor
            "24",      //Mehar Xull
            "25",      //Quann
            "26",      //Lodor
            "27",      //Starpoint
            "28",      //Tequ'ran
            "29",      //Qucen'n
            "30",      //Mellon
            "31",      //Lazar
            "32",      //Dal Bootha
            "33",      //Corneeq
            "34",      //Centauri
            "35",      //Bereg
            "36",      //Arnor
            "37",      //Arinham
            "38",      //Abyz
            "59",      //Archon Vail
            "60",      //Perimeter
            "61",      //Ang
            "62",      //Sem-Lore
            "63",      //Vorhal
            "64",      //Atlas
            "65",      //Primor
            "66",      //Hope's End
            "69",      //Accoen
            "70",      //Kraag
            "71",      //Bakal
            "72",      //Lisis
            "73",      //Cealdri
            "74",      //Vega Major
            "75",      //Loki
            "76",      //Rigel I
            "d100",    //Silence
            "d101",    //Echo
            "d102",    //Tarrock
            "d103",    //Prism
            "d105",    //Inan
            "d106",    //Troac
            "d107",    //Etir V
            "d108",    //Vioss
            "d109",    //Fakrenn
            "d110",    //San-Vit
            "d111",    //Dorvok
            "d112",    //Rysaa
            "d113",    //Slin
            "d114",    //Detic
            "d115",    //Qaak
            "d116"     //Mandle
        ));

        // if (includeAllTiles) tilesToPullFrom = TileHelper.getAllTiles().values().stream().filter(tile -> !tile.isAnomaly() && !tile.isHomeSystem() && !tile.isHyperlane()).map(TileModel::getId).toList();
        tilesToPullFrom.removeAll(activeGame.getTileMap().values().stream().map(Tile::getTileID).toList());
        List<String> tileToPullFromUnshuffled = new ArrayList<>(tilesToPullFrom);
        Collections.shuffle(tilesToPullFrom);

        if (tilesToPullFrom.size() < count) {
            sendMessage("Not enough tiles to draw from");
            return;
        }

        List<MessageEmbed> tileEmbeds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String tileID = tilesToPullFrom.get(i);
            TileModel tile = TileHelper.getTile(tileID);
            tileEmbeds.add(tile.getHelpMessageEmbed(false));
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeGame) + " drew " + count + " blue back tiles from this list:\n> " + tileToPullFromUnshuffled.toString());
        event.getMessageChannel().sendMessageEmbeds(tileEmbeds).queue();
    }
    
}
