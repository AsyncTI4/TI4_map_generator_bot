package ti4.discord.interactions.commands.map;

import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.service.map.AddTileService;

class RotateHyperlane extends GameStateSubcommand {

    public RotateHyperlane() {
        super(Constants.ROTATE_HYPERLANE, "Rotate hyperlane", true, false);
        addOptions(new OptionData(
                OptionType.STRING, Constants.POSITION, "Tile position on map. Accepts comma separated list", true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.ROTATION, "Rotation in degrees, default = 60", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String positionOption = event.getOptions().get(0).getAsString();
        Set<String> positions = Helper.getSetFromCSV(positionOption);

        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position `" + position + "` not allowed");
                return;
            }
        }

        Game game = getGame();

        int rotation = 60;
        if (event.getOptions().size() > 1 && event.getOptions().get(1).getType() == OptionType.INTEGER) {
            rotation = (int) event.getOptions().get(1).getAsInt();
            if (rotation % 60 != 0) {
                MessageHelper.replyToMessage(event, "Rotation must be a multiple of 60");
                return;
            }
        }
        for (String position : positions) {
            Tile originalTile = game.getTileByPosition(position);
            if (originalTile == null) {
                MessageHelper.replyToMessage(event, "No tile found at position `" + position + "`");
                continue;
            }
            String id = originalTile.getTileID();
            if (id.length() == 3) {
                id += rotation;
            } else {
                int currentRotation = Integer.parseInt(id.substring(3));
                int newRotation = (currentRotation + rotation) % 360;
                if (newRotation == 0) {
                    id = id.substring(0, 3);
                } else {
                    id = id.substring(0, 3) + newRotation;
                }
                if (Mapper.getTileID(id) == null) {
                    MessageHelper.replyToMessage(event, "No tile found with ID `" + id + "` after rotation");
                    continue;
                }
            }
            Tile tile = new Tile(id, position);

            AddTileService.addTile(getGame(), tile);
        }

        game.rebuildTilePositionAutoCompleteList();
    }
}
