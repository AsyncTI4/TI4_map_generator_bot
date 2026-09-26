package ti4.discord.interactions.commands.fow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
                        "Faction/Color(s) to reveal to (adds to existing) - leave empty to reveal to everyone")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        List<String> positions =
                Helper.getListFromCSV(event.getOption(Constants.POSITION).getAsString());

        // Empty colors => reveal to everyone (stored as no grant). Every named target must resolve:
        // silently dropping a typo would otherwise fall through to "everyone" and leak the system.
        String targetOption = event.getOption(Constants.TARGET_FACTION_OR_COLOR, "", OptionMapping::getAsString);
        Set<String> newColors = new LinkedHashSet<>();
        if (!targetOption.isBlank() && !Constants.ALL.equalsIgnoreCase(targetOption.trim())) {
            List<String> entries = Helper.getListFromCSV(targetOption);
            List<Player> targets = CommandHelper.getTargetPlayersFromOption(game, event);
            if (targets.size() != entries.size()) {
                MessageHelper.replyToMessage(
                        event,
                        "Could not match every entry in `" + targetOption
                                + "` to a player faction/color. No vision token placed.");
                return;
            }
            targets.forEach(p -> newColors.add(p.getColor()));
        }

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

            // Re-adding onto an existing token merges recipients: "everyone" on either side wins,
            // otherwise union the colors.
            Set<String> colors = new LinkedHashSet<>();
            boolean existingIsEveryone =
                    tile.hasFowVisionToken() && tile.getFowVisionGrant().isEmpty();
            if (!newColors.isEmpty() && !existingIsEveryone) {
                if (tile.hasFowVisionToken()) colors.addAll(tile.getFowVisionGrant());
                colors.addAll(newColors);
            }

            tile.addToken(Constants.TOKEN_FOWVISION_PNG, Constants.SPACE);
            tile.setFowVisionGrant(colors);
            sb.append("Placed a fog-vision token on ")
                    .append(position)
                    .append(" - revealed to ")
                    .append(colors.isEmpty() ? "everyone" : String.join(", ", colors))
                    .append('\n');
        }
        if (!sb.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        }
    }
}
