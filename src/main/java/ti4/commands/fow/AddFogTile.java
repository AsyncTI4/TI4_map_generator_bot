package ti4.commands.fow;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ResourceHelper;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class AddFogTile extends GameStateSubcommand {

    public AddFogTile() {
        super(Constants.ADD_FOG_TILE, "Add a Fog of War tile to the map.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map", true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name", true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LABEL, "How you want the system to be labelled").setMaxLength(30));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to add to").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> positions = Helper.getListFromCSV(event.getOption(Constants.POSITION).getAsString());

        List<Player> targetPlayers = CommandHelper.getTargetPlayersFromOption(getGame(), event);
        if (targetPlayers.isEmpty()) {
            targetPlayers.add(getPlayer());
        }

        String planetTileName = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase());
        String tileName = Mapper.getTileID(planetTileName);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return;
        }

        OptionMapping labelMapping = event.getOption(Constants.LABEL);
        String label = labelMapping == null ? "" : labelMapping.getAsString();
        StringBuffer sb = new StringBuffer();
        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Tile position '" + position + "' is invalid");
                continue;
            }

            StringBuffer sb2 = new StringBuffer();
            for (Player target : targetPlayers) {
                target.addFogTile(planetTileName, position, label);
                sb2.append(" ").append(target.getRepresentation());
            }
            sb.append("Added fog tile ").append(position).append(" (").append(planetTileName).append(") to").append(sb2).append("\n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
