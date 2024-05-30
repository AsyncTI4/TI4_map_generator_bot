package ti4.modals;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import lombok.NonNull;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.MessageListener;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class ModalListener extends ListenerAdapter {

    @Override
    public void onModalInteraction(@NonNull ModalInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to handle button presses.").setEphemeral(true).queue();
            return;
        }
        event.deferEdit().queue();
        long startTime = new Date().getTime();
        try {
            resolveModalInteractionEvent(event);
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with button interaction", e);
        }
        long endTime = new Date().getTime();
        if (endTime - startTime > 3000) {
            BotLogger.log(event, "This button command took longer than 3000 ms (" + (endTime - startTime) + ")");
        }
    }

    private static void resolveModalInteractionEvent(@NonNull ModalInteractionEvent event) {
        String userID = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), userID, "modal", "no sub command");
        String modalID = event.getModalId();

        String messageID = event.getMessage() == null ? "" : event.getMessage().getId();

        String gameName = event.getChannel().getName();
        gameName = gameName.replace(Constants.CARDS_INFO_THREAD_PREFIX, "");
        gameName = gameName.replace(Constants.BAG_INFO_THREAD_PREFIX, "");
        gameName = StringUtils.substringBefore(gameName, "-");
        Game game = GameManager.getInstance().getGame(gameName);
        Player player = null;
        MessageChannel privateChannel = event.getChannel();
        MessageChannel mainGameChannel = event.getChannel();
        if (game != null) {
            player = game.getPlayer(userID);
            player = Helper.getGamePlayer(game, player, event.getMember(), userID);
            if (player == null && !"showGameAgain".equalsIgnoreCase(modalID)) {
                event.getChannel().sendMessage("You're not a player of the game").queue();
                return;
            }
            modalID = modalID.replace("delete_buttons_", "resolveAgendaVote_");
            game.increaseButtonPressCount();

            if (game.isFoWMode()) {
                if (player != null && player.getPrivateChannel() == null) {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Private channels are not set up for this game. Messages will be suppressed.");
                    privateChannel = null;
                } else if (player != null) {
                    privateChannel = player.getPrivateChannel();
                }
            }
        }

        if (game != null && game.getMainGameChannel() != null) {
            mainGameChannel = game.getMainGameChannel();
        }

        MessageChannel actionsChannel = null;
        for (TextChannel textChannel_ : AsyncTI4DiscordBot.jda.getTextChannels()) {
            if (textChannel_.getName().equals(gameName + Constants.ACTIONS_CHANNEL_SUFFIX)) {
                actionsChannel = textChannel_;
                break;
            }
        }

        if (modalID.startsWith("FFCC_")) {
            modalID = modalID.replace("FFCC_", "");
            String factionWhoPressedButton = player == null ? "nullPlayer" : player.getFaction();
            if (player != null && !modalID.startsWith(factionWhoPressedButton + "_")) {
                String message = "To " + player.getFactionEmoji() + ": these buttons are for someone else";
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
                return;
            }
            modalID = modalID.replaceFirst(factionWhoPressedButton + "_", "");
        }
        String finsFactionCheckerPrefix = player == null ? "FFCC_nullPlayer_" : player.getFinsFactionCheckerPrefix();
        String trueIdentity = null;
        String fowIdentity = null;
        String ident = null;
        if (player != null) {
            trueIdentity = player.getRepresentation(true, true);
            fowIdentity = player.getRepresentation(false, true);
            ident = player.getFactionEmoji();
        }

        if (modalID.startsWith("jmfA_")) {
            game.initializeMiltySettings().parseModalInput(event);
        }
    }

}
