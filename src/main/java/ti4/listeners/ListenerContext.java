package ti4.listeners;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.MessageListener;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public abstract class ListenerContext {
    public String componentID;
    public boolean factionChecked = false;
    public Game game;
    public Player player;
    public MessageChannel privateChannel, mainGameChannel, actionsChannel;
    public GenericInteractionCreateEvent event;

    public abstract GenericInteractionCreateEvent getEvent();

    public abstract String getContextType();

    public String getSubCommand() {
        return "no sub command";
    }

    public boolean isValid() {
        return event != null;
    }

    public ListenerContext(GenericInteractionCreateEvent event, String compID) {
        this.event = event;
        this.componentID = compID;

        String userID = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), userID, getContextType(), getSubCommand());

        // Find game
        String gameName = event.getChannel().getName();
        gameName = gameName.replace(Constants.CARDS_INFO_THREAD_PREFIX, "");
        gameName = gameName.replace(Constants.BAG_INFO_THREAD_PREFIX, "");
        gameName = StringUtils.substringBefore(gameName, "-");
        game = GameManager.getInstance().getGame(gameName);

        player = null;
        privateChannel = event.getMessageChannel();
        mainGameChannel = event.getMessageChannel();

        if (game != null) {
            player = game.getPlayer(userID);
            player = Helper.getGamePlayer(game, player, event.getMember(), userID);

            if (player == null && !"showGameAgain".equalsIgnoreCase(componentID)) {
                event.getMessageChannel().sendMessage("You're not a player of the game").queue();
                event = null;
                return;
            }

            if (getContextType().equals("button")) {
                componentID = componentID.replace("delete_buttons_", "resolveAgendaVote_");
                game.increaseButtonPressCount();
            }

            if (game.isFoWMode()) {
                if (player != null && player.getPrivateChannel() == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Private channels are not set up for this game. Messages will be suppressed.");
                    privateChannel = null;
                } else if (player != null) {
                    privateChannel = player.getPrivateChannel();
                }
            }
        }

        if (game != null && game.getMainGameChannel() != null) {
            mainGameChannel = game.getMainGameChannel();
        }

        if (componentID.contains("dummyPlayerSpoof")) {
            String identity = StringUtils.substringBefore(componentID, "_").replace("dummyPlayerSpoof", "");
            player = game.getPlayerFromColorOrFaction(identity);
            componentID = componentID.replace("dummyPlayerSpoof" + identity + "_", "");
        }

        if (!checkFinsFactionChecker(this)) {
            event = null;
            return;
        }

        actionsChannel = null;
        for (TextChannel textChannel_ : AsyncTI4DiscordBot.jda.getTextChannels()) {
            if (textChannel_.getName().equals(gameName + Constants.ACTIONS_CHANNEL_SUFFIX)) {
                actionsChannel = textChannel_;
                break;
            }
        }

        // newstuff
        if (componentID.startsWith("anonDeclare_")) {
            String declaration = componentID.split("_")[1];
            String old = game.getStoredValue(player.getUserID() + "anonDeclare");
            if (old.isEmpty()) {
                if (declaration.toLowerCase().contains("strong")) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Someone has said that they have \"" + declaration + "\"");
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Someone has said that they prefer a \"" + declaration + "\" environment.");
                }
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Someone has changed their preference from \"" + old + "\" to  \"" + declaration + "\" ");
            }
            game.setStoredValue(player.getUserID() + "anonDeclare", declaration);
            GameSaveLoadManager.saveMap(game, event);
            event = null;
            return;
        }

        if (player != null && game != null && game.getActivePlayerID() != null && player.getUserID().equalsIgnoreCase(game.getActivePlayerID())) {
            game.setLastActivePlayerPing(new Date());
        }
    }

    public static boolean checkFinsFactionChecker(ListenerContext context) {
        String componentID = context.componentID;
        Player player = context.player;
        GenericInteractionCreateEvent event = context.getEvent();
        if (!context.factionChecked && componentID != null && componentID.startsWith("FFCC_")) {
            componentID = componentID.replace("FFCC_", "");
            String factionWhoPressedButton = player == null ? "nullPlayer" : player.getFaction();
            if (player != null && !componentID.startsWith(factionWhoPressedButton + "_")) {
                String message = "To " + player.getFactionEmoji() + ": these buttons are for someone else";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                return false;
            }
            context.componentID = componentID.replaceFirst(factionWhoPressedButton + "_", "");
            context.factionChecked = true;
        }
        return true;
    }
}
