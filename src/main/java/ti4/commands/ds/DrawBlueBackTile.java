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
import ti4.commands.PlayerGameStateSubcommand;
import ti4.commands.milty.MiltyDraftTile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TileModel;

public class DrawBlueBackTile extends PlayerGameStateSubcommand {

    public DrawBlueBackTile() {
        super(Constants.DRAW_BLUE_BACK_TILE, "Draw a random blue back tile (for Star Charts and Decrypted Cartoglyph)", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "How many to draw? Default: 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        drawBlueBackTiles(event, game, player, count);
    }

    public static void drawBlueBackTiles(GenericInteractionCreateEvent event, Game game, Player player, int count) {
        List<MiltyDraftTile> unusedBlueTiles = new ArrayList<>(Helper.getUnusedTiles(game).stream()
            .filter(tile -> tile.getTierList().isBlue())
            .toList());

        List<MiltyDraftTile> tileToPullFromUnshuffled = new ArrayList<>(unusedBlueTiles);
        Collections.shuffle(unusedBlueTiles);

        if (unusedBlueTiles.size() < count) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Not enough tiles to draw from");
            return;
        }

        List<MessageEmbed> tileEmbeds = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Tile tile = unusedBlueTiles.get(i).getTile();
            TileModel tileModel = tile.getTileModel();
            tileEmbeds.add(tileModel.getHelpMessageEmbed(false));
            ids.add(tile.getTileID());
        }
        String tileString = String.join(",", tileToPullFromUnshuffled.stream().map(t -> t.getTile().getTileID()).toList());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " drew " + count + " blue back tiles from this list:\n> " + tileString);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Use /map add_tile to add it to the map.");

        event.getMessageChannel().sendMessageEmbeds(tileEmbeds).queue();
        if (ids.size() == 1) {
            if (game.isDiscordantStarsMode()) {
                ButtonHelper.starChartStep1(game, player, ids.getFirst());
            } else {
                ButtonHelper.detTileAdditionStep1(player, ids.getFirst());
            }
        } else {
            ButtonHelper.starChartStep0(player, ids);
        }
    }

}
