package ti4.commands.uncategorized;

import java.util.List;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class CardsInfo implements Command {

    @Override
    public String getActionID() {
        return Constants.CARDS_INFO;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    public static boolean acceptEvent(SlashCommandInteractionEvent event, String actionID) {
        if (event.getName().equals(actionID)) {
            String userID = event.getUser().getId();
            GameManager gameManager = GameManager.getInstance();
            if (!gameManager.isUserWithActiveGame(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Member member = event.getMember();
            if (member != null) {
                List<Role> roles = member.getRoles();
                for (Role role : AsyncTI4DiscordBot.adminRoles) {
                    if (roles.contains(role)) {
                        return true;
                    }
                }
            }
            Game userActiveGame = gameManager.getUserActiveGame(userID);
            if (userActiveGame.isCommunityMode()) {
                Player player = Helper.getGamePlayer(userActiveGame, null, event, userID);
                if (player == null || !userActiveGame.getPlayerIDs().contains(player.getUserID()) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                    MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                    return false;
                }
            } else if (!userActiveGame.getPlayerIDs().contains(userID) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
            if (!event.getChannel().getName().startsWith(userActiveGame.getName() + "-")) {
                MessageHelper.replyToMessage(event, "Commands can be executed only in game specific channels");
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        GameManager gameManager = GameManager.getInstance();
        Game activeGame;
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        } else {
            activeGame = gameManager.getUserActiveGame(userID);
            String color = Helper.getColor(activeGame, event);
            if (!Mapper.isValidColor(color)) {
                MessageHelper.replyToMessage(event, "Color/Faction not valid");
                return;
            }
        }

        Player player = activeGame.getPlayer(userID);
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        activeGame.checkPromissoryNotes();
        PNInfo.checkAndAddPNs(activeGame, player);
        sendCardsInfo(activeGame, player, event);
    }

    public static void sendCardsInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        if (player == null) return;
        String headerText = player.getRepresentation(true, true) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendCardsInfo(activeGame, player);
    }

    public static void sendCardsInfo(Game activeGame, Player player) {
        SOInfo.sendSecretObjectiveInfo(activeGame, player);
        ACInfo.sendActionCardInfo(activeGame, player);
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
    }

    protected String getActionDescription() {
        return "Send to your Cards Info thread: Scored & Unscored SOs, ACs, and PNs in both hand and Play Area";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to show full promissory text").setRequired(false))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO, "Set TRUE to get card info as direct message also").setRequired(false)));
    }

}
