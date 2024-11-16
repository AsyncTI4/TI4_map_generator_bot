package ti4.commands.special;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.CommandHelper;
import ti4.commands2.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class MoveCreussWormhole extends SpecialSubcommandData {

    public MoveCreussWormhole() {
        super(Constants.MOVE_CREUSS_WORMHOLE, "Adds or moves a Creuss wormhole token to the target system.");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Target System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CREUSS_TOKEN_NAME, "Token Name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToEventChannel(event, "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(StringUtils.substringBefore(tileOption.getAsString().toLowerCase(), " "));
        Tile tile = AddRemoveUnits.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        String tokenName = event.getOption(Constants.CREUSS_TOKEN_NAME, null, OptionMapping::getAsString);
        tokenName = AliasHandler.resolveToken(tokenName);
        if (!isValidCreussWormhole(tokenName)) {
            MessageHelper.sendMessageToEventChannel(event, "Token Name: " + tokenName + " is not a valid Creuss Wormhole Token.");
            return;
        }

        StringBuilder sb = new StringBuilder(player.getRepresentation());
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        sb.append(" moved ").append(Emojis.getEmojiFromDiscord(tokenName)).append(" to ").append(tile.getRepresentation());
        for (Tile tile_ : game.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                sb.append(" (from ").append(tile_.getRepresentation()).append(")");
                break;
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
        CommanderUnlockCheck.checkPlayer(player, "ghost");
    }

    private boolean isValidCreussWormhole(String tokenName) {
        if (tokenName == null) return false;
        List<String> validNames = List.of("creussalpha", "creussbeta", "creussgamma");
        return validNames.contains(tokenName);
    }

}
