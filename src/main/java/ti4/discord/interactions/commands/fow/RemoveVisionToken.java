package ti4.discord.interactions.commands.fow;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;

class RemoveVisionToken extends GameStateSubcommand {

    public RemoveVisionToken() {
        super(Constants.REMOVE_VISION_TOKEN, "Remove a fog-vision token from a system.", true, true);
        addOptions(new OptionData(
                OptionType.STRING, Constants.POSITION, "Tile position(s) on map, comma separated", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        List<String> positions =
                Helper.getListFromCSV(event.getOption(Constants.POSITION).getAsString());

        StringBuilder sb = new StringBuilder();
        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Tile position '" + position + "' is invalid");
                continue;
            }
            Tile tile = game.getTileByPosition(position);
            if (tile == null) {
                MessageHelper.replyToMessage(event, "No tile found at position '" + position + "'");
                continue;
            }
            boolean removed = tile.removeToken(Constants.TOKEN_FOWVISION_PNG, Constants.SPACE);
            game.removeStoredValue(Constants.FOW_VISION_GRANT_PREFIX + position);
            sb.append(removed ? "Removed the fog-vision token from " : "No fog-vision token was on ")
                    .append(position)
                    .append('\n');
        }
        if (!sb.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        }
    }
}
