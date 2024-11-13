package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

public class ChangeUnitDecal extends PlayerSubcommandData {
    public ChangeUnitDecal() {
        super(Constants.CHANGE_UNIT_DECAL, "Player Change Unit Decals");
        addOptions(new OptionData(OptionType.STRING, Constants.DECAL_SET, "Decals for units. Enter 'none' to remove current decals.").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        String newDecalSet = event.getOption(Constants.DECAL_SET).getAsString().toLowerCase();
        if ("none".equals(newDecalSet)) {
            MessageHelper.sendMessageToEventChannel(event, "Decal Set removed: " + player.getDecalSet());
            player.setDecalSet(null);
            return;
        }
        if (!Mapper.isValidDecalSet(newDecalSet)) {
            MessageHelper.sendMessageToEventChannel(event, "Decal Set not valid: " + newDecalSet);
            player.setDecalSet(null);
            return;
        }
        if (!userMayUseDecal(player.getUserID(), newDecalSet)) {
            MessageHelper.sendMessageToEventChannel(event, "This decal set may only be used by specific players.");
            return;
        }

        player.setDecalSet(newDecalSet);
        MessageHelper.sendMessageToEventChannel(event, player.getFactionEmojiOrColor() + " changed their decal set to " + newDecalSet);
    }

    public static boolean userMayUseDecal(String userID, String decalID) {
        return switch (decalID) {
            case "cb_10" -> userID.equals(Constants.jazzId); // jazz -> jazz
            case "cb_11" -> Constants.tourneyWinners.contains(userID); // tournament winner decal
            case "cb_52" -> userID.equals(Constants.sigmaId); // sigma -> void
            case "cb_12", "cb_34", "cb_35", "cb_36" -> false; // disable tech icons to prevent confusion
            case "cb_37", "cb_38", "cb_39", "cb_40" -> false; // disable trait icons to prevent confusion
            case "cb_42" -> false; // disable eye icon for use elsewhere
            case "cb_54" -> false; // disable Australia icon
            default -> true;
        };
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = CommandHelper.getGameName(event);
        GameSaveLoadManager.saveGame(game, event);
        ShowGame.simpleShowGame(game, event);
    }
}
