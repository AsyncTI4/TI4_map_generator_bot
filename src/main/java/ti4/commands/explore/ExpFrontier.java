package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.SendTG;
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

        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        if (space.getTokenList().contains(frontierFilename)) {
            space.removeToken(frontierFilename);
            String cardID = activeMap.drawExplore(Constants.FRONTIER);
            boolean isEnigmatic = false;
            Player player = activeMap.getPlayer(getUser().getId());
            player = Helper.getPlayer(activeMap, player, event);
            if ("ed1".equals(cardID) || "ed2".equals(cardID)){
                if (player != null){
                    player.addRelic(Constants.ENIGMATIC_DEVICE);
                    isEnigmatic = true;
                }
            }
            StringBuilder messageText = new StringBuilder(Emojis.Frontier);
            messageText.append("Frontier *(tile "+ tile.getPosition() + ")* explored by " + SendTG.getPlayerRepresentation(event, player)).append(":\n");
            messageText.append(displayExplore(cardID));
            resolveExplore(event, cardID, tile, null, messageText.toString(), isEnigmatic);
        } else {
            MessageHelper.replyToMessage(event, "No frontier token in given system.");
        }
    }
}