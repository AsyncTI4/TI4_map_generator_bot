package ti4.discord.interactions.commands.fow;

import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;

class AddVisionToken extends GameStateSubcommand {

    public AddVisionToken() {
        super(
                Constants.ADD_VISION_TOKEN,
                "Place a fog-vision token that reveals a system to some or all players.",
                true,
                true);
        addOptions(new OptionData(
                OptionType.STRING, Constants.POSITION, "Tile position(s) on map, comma separated", true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.TARGET_FACTION_OR_COLOR,
                        "Faction/Color(s) to reveal to - leave empty to reveal to everyone")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        List<String> positions =
                Helper.getListFromCSV(event.getOption(Constants.POSITION).getAsString());

        // Empty target list => reveal to everyone (stored as a blank grant, read as ALL by FoWHelper).
        List<Player> targets = CommandHelper.getTargetPlayersFromOption(game, event);
        String grant = targets.stream().map(Player::getColor).collect(Collectors.joining(","));
        String targetDesc = targets.isEmpty()
                ? "everyone"
                : targets.stream().map(Player::getColor).collect(Collectors.joining(", "));

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
            tile.addToken(Constants.TOKEN_FOWVISION_PNG, Constants.SPACE);
            game.setStoredValue(Constants.FOW_VISION_GRANT_PREFIX + position, grant);
            sb.append("Placed a fog-vision token on ")
                    .append(position)
                    .append(" - revealed to ")
                    .append(targetDesc)
                    .append('\n');
        }
        if (!sb.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        }
    }
}
