package ti4.commands.fow;

import java.util.ArrayList;
import java.util.List;

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
        super(Constants.REMOVE_FOG_TILE, "Remove Fog of War tiles from the map.", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile positions on map").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to remove from").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String positionMapping = event.getOption(Constants.POSITION, null, OptionMapping::getAsString);
        if (positionMapping == null) {
            MessageHelper.replyToMessage(event, "Specify position");
            return;
        }
        Player targetPlayer = CommandHelper.getOtherPlayerFromEvent(getGame(), event);
        if (targetPlayer == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }

        List<String> positions = Helper.getListFromCSV(positionMapping);
        if ("ALL".equals(positionMapping)) {
            positions = new ArrayList<>(targetPlayer.getFogTiles().keySet());
        }

        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Tile position '" + position + "' is invalid");
            }

            //remove the custom tile from the player
            targetPlayer.removeFogTile(position);
        }
    }
}
