package ti4.commands.fow;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AddFogTile extends FOWSubcommandData {
    public AddFogTile() {
        super(Constants.ADD_FOG_TILE, "Add a Fog of War tile to the map.");
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name"));
        addOptions(new OptionData(OptionType.STRING, Constants.LABEL, "How you want the tile to be labeled").setRequired(false).setMaxLength(10));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);

        MessageChannel channel = event.getChannel();
        if (player == null) {
            MessageHelper.sendMessageToChannel(channel, "You're not a player of this game");
            return;
        }

        OptionMapping planetTileMapping = event.getOption(Constants.TILE_NAME);
        if (planetTileMapping == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify tile");
            return;
        }

        OptionMapping positionMapping = event.getOption(Constants.POSITION);
        if (positionMapping == null) {
            MessageHelper.replyToMessage(event, "Specify position");
            return;
        }

        OptionMapping labelMapping = event.getOption(Constants.LABEL);
        String label = labelMapping == null ? "" : labelMapping.getAsString();

        String position = positionMapping.getAsString().toLowerCase();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Tile position is not allowed");
            return;
        }

        String planetTileName = AliasHandler.resolveTile(planetTileMapping.getAsString().toLowerCase());
        String tileName = Mapper.getTileID(planetTileName);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return;
        }

        //add the custom tile to the player
        player.addFogTile(planetTileName, position, label);
        GameSaveLoadManager.saveGame(game, event);
    }
}
