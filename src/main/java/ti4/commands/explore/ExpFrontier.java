package ti4.commands.explore;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

public class ExpFrontier extends ExploreSubcommandData {
    public ExpFrontier() {
        super(Constants.FRONTIER, "Explore a frontier tile");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Location of the frontier tile").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileName = event.getOption(Constants.TILE_NAME).getAsString();
        Map activeMap = getActiveMap();
        Tile tile = getTile(event, tileName, activeMap);
        
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        if (space.getTokenList().contains(frontierFilename)) {
            space.removeToken(frontierFilename);
            String cardID = activeMap.drawExplore(Constants.FRONTIER);
            StringBuilder messageText = new StringBuilder(Emojis.Frontier);
            messageText.append("Frontier *(tile "+ tile.getPosition() + ")* explored by " + Helper.getPlayerRepresentation(event, player)).append(":\n");
            messageText.append(displayExplore(cardID));
            resolveExplore(event, cardID, tile, null, messageText.toString(), checkIfEngimaticDevice(player, cardID));
        } else {
            MessageHelper.replyToMessage(event, "No frontier token in given system.");
        }
    }

    public static boolean checkIfEngimaticDevice(@NotNull Player player, String cardID) {
        if (player != null && ("ed1".equals(cardID) || "ed2".equals(cardID))) {
            player.addRelic(Constants.ENIGMATIC_DEVICE);
            return true;
        }
        return false;
    }
}