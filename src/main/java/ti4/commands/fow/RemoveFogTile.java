package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
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
        String[] positions = positionMapping.replace(" ", "").split(",");
        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Tile position is not allowed");
                return;
            }

            //remove the custom tile from the player
            targetPlayer.removeFogTile(position);
        }
    }
}
