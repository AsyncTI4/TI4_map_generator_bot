package ti4.commands.special;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;

public class MoveCreussWormhole extends SpecialSubcommandData {

    public MoveCreussWormhole() {
        super(Constants.MOVE_CREUSS_WORMHOLE, "Adds or moves a creuss wormhole token to the target system.");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Target System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CREUSS_TOKEN_NAME, "Token Name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null){
            sendMessage("Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(StringUtils.substringBefore(tileOption.getAsString().toLowerCase(), " "));
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeGame);
        if (tile == null) {
            sendMessage("Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        String tokenName = event.getOption(Constants.CREUSS_TOKEN_NAME, null, OptionMapping::getAsString);
        tokenName = AliasHandler.resolveToken(tokenName);
        if (!isValidCreussWormhole(tokenName)) {
            sendMessage("Token Name: " + tokenName + " is not a valid Creuss Wormhole Token.");
            return;
        }

        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame));
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        sb.append(" moved ").append(Helper.getEmojiFromDiscord(tokenName)).append(" to ").append(tile.getRepresentation());
        for (Tile tile_ : activeGame.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                sb.append(" (from ").append(tile_.getRepresentation()).append(")");
                break;
            }
        }
        sendMessage(sb.toString());
        if(player.getLeaderIDs().contains("ghostcommander") && !player.hasLeaderUnlocked("ghostcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeGame, "ghost", event);
        }
    }

    private boolean isValidCreussWormhole(String tokenName) {
        if (tokenName == null) return false;
        List<String> validNames = List.of("creussalpha", "creussbeta", "creussgamma");
        return validNames.contains(tokenName);
    }

}
