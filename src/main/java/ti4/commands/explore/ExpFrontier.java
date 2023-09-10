package ti4.commands.explore;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
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
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Location of the frontier tile").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileName = event.getOption(Constants.TILE_NAME).getAsString();
        Game activeGame = getActiveGame();
        Tile tile = getTile(event, tileName, activeGame);

        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        expFront(event, tile, activeGame, player);
        
    }

    public void expFront(GenericInteractionCreateEvent event, Tile tile, Game activeGame, Player player) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        if (space.getTokenList().contains(frontierFilename)) {
            space.removeToken(frontierFilename);
            String cardID = activeGame.drawExplore(Constants.FRONTIER);
          String messageText = Emojis.Frontier + "Frontier *(tile " + tile.getPosition() + ")* explored by " + Helper.getPlayerRepresentation(player, activeGame) + ":\n" +
              displayExplore(cardID);
            resolveExplore(event, cardID, tile, null, messageText, checkIfEngimaticDevice(player, cardID), player, activeGame);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"No frontier token in given system.");
        }
    }
    public void expFrontAlreadyDone(GenericInteractionCreateEvent event, Tile tile, Game activeGame, Player player, String cardID) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        if (space.getTokenList().contains(frontierFilename)) {
            space.removeToken(frontierFilename);
          String messageText = Emojis.Frontier + "Frontier *(tile " + tile.getPosition() + ")* explored by " + Helper.getPlayerRepresentation(player, activeGame) + ":\n" +
              displayExplore(cardID);
            resolveExplore(event, cardID, tile, null, messageText, checkIfEngimaticDevice(player, cardID), player, activeGame);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"No frontier token in given system.");
        }
    }

    public static boolean checkIfEngimaticDevice(@NotNull Player player, String cardID) {
        if ("ed1".equals(cardID) || "ed2".equals(cardID)) {
            player.addRelic(Constants.ENIGMATIC_DEVICE);
            return true;
        }
        return false;
    }
}