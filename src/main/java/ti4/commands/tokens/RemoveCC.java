package ti4.commands.tokens;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class RemoveCC extends AddRemoveToken {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, List<String> colors, Tile tile, Game game) {
        for (String color : colors) {
            String ccID = Mapper.getCCID(color);
            String ccPath = tile.getCCPath(ccID);
            if (ccPath == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
            }
            if (game.isFowMode()) {
                String colorMention = Emojis.getColorEmojiWithName(color);
                FoWHelper.pingSystem(game, event, tile.getPosition(), colorMention + " has removed a token in the system");
            }

            tile.removeCC(ccID);
            Helper.isCCCountCorrect(event, game, color);
        }
    }

    public static void removeCC(GenericInteractionCreateEvent event, String color, Tile tile, Game game) {

        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        if (game.isFowMode()) {
            String colorMention = Emojis.getColorEmojiWithName(color);
            FoWHelper.pingSystem(game, event, tile.getPosition(), colorMention + " has removed a token in the system");
        }
        tile.removeCC(ccID);

    }

    @Override
    protected String getActionDescription() {
        return "Remove CCs from tile/system";
    }

    @Override
    public String getActionID() {
        return Constants.REMOVE_CC;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true)));
    }
}
