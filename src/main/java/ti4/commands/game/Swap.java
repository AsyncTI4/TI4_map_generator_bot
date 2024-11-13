package ti4.commands.game;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Swap extends GameStateSubcommand {

    public Swap() {
        super(Constants.SWAP, "Swap factions with a player", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Swap with player in Faction/Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.OTHER_PLAYER, "Replacement player @playerName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User callerUser = event.getUser();
        Game game = getGame();
        Collection<Player> players = game.getPlayers().values();
        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.adminRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }
        if (players.stream().noneMatch(player -> player.getUserID().equals(callerUser.getId())) && !isAdmin) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only game players can swap with a player.");
            return;
        }
        String message = "";

        Player removedPlayer = Helper.getOtherPlayerFromEvent(game, event);
        if (removedPlayer == null) {
            MessageHelper.replyToMessage(event, "Could not find player for faction/color to replace");
            return;
        }
        Player swapperPlayer = getPlayer();
        if (swapperPlayer.getFaction() == null) {
            MessageHelper.replyToMessage(event, "Could not find faction/player to swap");
            return;
        }
        User addedUser = event.getOption(Constants.OTHER_PLAYER).getAsUser();
        secondHalfOfSwap(game, swapperPlayer, removedPlayer, addedUser, event);
        GameSaveLoadManager.saveGame(game, event);
        GameSaveLoadManager.reload(game.getName());
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    public static void secondHalfOfSwap(Game game, Player swapperPlayer, Player removedPlayer, User addedUser, GenericInteractionCreateEvent event) {
        StringBuilder message = new StringBuilder("Users have swapped factions:\n");
        message.append("> **Before:** ").append(swapperPlayer.getRepresentation()).append(" & ").append(removedPlayer.getRepresentation()).append("\n");
        Collection<Player> players = game.getPlayers().values();
        if (players.stream().anyMatch(player -> player.getUserID().equals(removedPlayer.getUserID()))) {
            Player player = game.getPlayer(removedPlayer.getUserID());
            Map<String, List<String>> scoredPublicObjectives = game.getScoredPublicObjectives();
            for (Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
                List<String> value = poEntry.getValue();
                boolean removed = value.remove(removedPlayer.getUserID());
                boolean removed2 = value.remove(swapperPlayer.getUserID());
                if (removed) {
                    value.add(addedUser.getId());
                }
                if (removed2) {
                    value.add(removedPlayer.getUserID());
                }
            }
            if (player.isDummy()) {
                player.setDummy(false);
                swapperPlayer.setDummy(true);
            }
            // LinkedHashSet<Integer> holder = new LinkedHashSet<>(player.getSCs());
            // player.setSCs(new LinkedHashSet<>(swapperPlayer.getSCs()));
            // swapperPlayer.setSCs(holder);
            swapperPlayer.setUserName(removedPlayer.getUserName());
            swapperPlayer.setUserID(removedPlayer.getUserID());
            player.setUserName(addedUser.getName());
            player.setUserID(addedUser.getId());

            if (game.getActivePlayerID() != null && game.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
                // if (!game.isFoWMode()) {
                //     try {
                //         if (game.getLatestTransactionMsg() != null && !"".equals(game.getLatestTransactionMsg())) {
                //             game.getMainGameChannel().deleteMessageById(game.getLatestTransactionMsg()).queue();
                //             game.setLatestTransactionMsg("");
                //         }
                //     } catch (Exception e) {
                //         //  Block of code to handle errors
                //     }
                // }
                // String text = "# " + player.getRepresentationUnfogged() + " UP NEXT";
                // String buttonText = "Use buttons to do your turn. ";
                // List<Button> buttons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                // MessageHelper.sendMessageToChannel(game.getMainGameChannel(), text);
                // MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), buttonText, buttons);
            }
        } else {
            MessageHelper.replyToMessage(event, "Specify player that is in game to be swapped");
            return;
        }
        GameSaveLoadManager.saveGame(game, event);
        GameSaveLoadManager.reload(game.getName());
        // SOInfo.sendSecretObjectiveInfo(activeMap, swapperPlayer);
        // SOInfo.sendSecretObjectiveInfo(activeMap, removedPlayer);
        message.append("> **After:** ").append(swapperPlayer.getRepresentation()).append(" & ").append(removedPlayer.getRepresentation()).append("\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
    }
}
