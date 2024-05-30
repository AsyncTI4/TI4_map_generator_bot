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
        super(Constants.FRONTIER, "Explore a Frontier token on a Tile");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Location of the frontier tile").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileName = event.getOption(Constants.TILE_NAME).getAsString();
        Game game = getActiveGame();
        Tile tile = getTile(event, tileName, game);

        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        expFront(event, tile, game, player);

    }

    public void expFront(GenericInteractionCreateEvent event, Tile tile, Game game, Player player) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        if (space.getTokenList().contains(frontierFilename)) {
            space.removeToken(frontierFilename);
            String cardID = game.drawExplore(Constants.FRONTIER);
            String messageText = Emojis.Frontier + "Frontier *(tile " + tile.getPosition() + ")* explored by " + player.getRepresentation() + ":";
            resolveExplore(event, cardID, tile, null, messageText, player, game);

            if (player.hasTech("dslaner")) {
                player.setAtsCount(player.getAtsCount() + 1);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No frontier token in given system.");
        }
    }

    public void expFrontAlreadyDone(GenericInteractionCreateEvent event, Tile tile, Game game, Player player, String cardID) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        if (space.getTokenList().contains(frontierFilename)) {
            space.removeToken(frontierFilename);
            String messageText = Emojis.Frontier + "Frontier *(tile " + tile.getPosition() + ")* explored by " + player.getRepresentation() + ":";
            resolveExplore(event, cardID, tile, null, messageText, player, game);

            if (player.hasTech("dslaner")) {
                player.setAtsCount(player.getAtsCount() + 1);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No frontier token in given system.");
        }
    }
}
