package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.image.PositionMapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RemoveFogTile extends FOWSubcommandData {
    public RemoveFogTile() {
        super(Constants.REMOVE_FOG_TILE, "Remove Fog of War tiles from the map.");
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile positions on map").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to remove from").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        String positionMapping = event.getOption(Constants.POSITION, null, OptionMapping::getAsString);
        if (positionMapping == null) {
            MessageHelper.replyToMessage(event, "Specify position");
            return;
        }

        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player to remove tiles from was not found.");
            return;
        }

        String[] positions = positionMapping.replace(" ", "").split(",");
        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Tile position is not allowed");
                return;
            }

            //remove the custom tile from the player
            player.removeFogTile(position);
        }
        GameSaveLoadManager.saveGame(game, event);
    }
}
