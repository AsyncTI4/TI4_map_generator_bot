package ti4.commands.fow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.PositionMapper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class RemoveFogTile extends GameStateSubcommand {

    public RemoveFogTile() {
        super(Constants.REMOVE_FOG_TILE, "Remove Fog of War tiles from the map.", true, true);
        addOptions(new OptionData(
                        OptionType.STRING, Constants.POSITION, "Tile positions on map or ALL to remove all fog tiles")
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to remove from")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String positionMapping = event.getOption(Constants.POSITION, "", OptionMapping::getAsString);

        List<Player> targetPlayers = CommandHelper.getTargetPlayersFromOption(getGame(), event);
        if (targetPlayers.isEmpty()) {
            targetPlayers.add(getPlayer());
        }

        Set<String> positions = new HashSet<>(Helper.getListFromCSV(positionMapping));
        StringBuilder sb = new StringBuilder();
        for (Player targetPlayer : targetPlayers) {
            StringBuilder sb2 = new StringBuilder();
            Set<String> positionsToRemove = Constants.ALL.equals(positionMapping)
                    ? new HashSet<>(targetPlayer.getFogTiles().keySet())
                    : positions;
            for (String position : positionsToRemove) {
                if (!PositionMapper.isTilePositionValid(position)) {
                    MessageHelper.replyToMessage(event, "Tile position '" + position + "' is invalid");
                    continue;
                }

                // remove the custom tile from the player
                targetPlayer.removeFogTile(position);
                sb2.append(" ").append(position);
            }
            sb.append(targetPlayer.getRepresentation())
                    .append(" removed fog tiles:")
                    .append(sb2)
                    .append("\n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
