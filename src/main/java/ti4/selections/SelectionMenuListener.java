package ti4.selections;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
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

public class SelectionMenuListener extends ListenerAdapter {
    @Data
    public class SelectionMenuContext {
        String menuID, messageID;
        List<String> values;

        Game game;
        Player player;
        boolean factionChecked = false;

        MessageChannel privateChannel, mainGameChannel, actionsChannel;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to receive selections.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        try {
            SelectionMenuProvider.resolveSelectionMenu(event, getContext(event));
        } catch (Exception e) {
            String message = "Selection Menu issue in event: " + event.getComponentId() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.log(message, e);
        }
    }

    public SelectionMenuContext getContext(StringSelectInteractionEvent event) {
        SelectionMenuContext context = new SelectionMenuContext();
        String userID = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), userID, "button", "no sub command");

        String componentID = event.getComponentId();
        List<String> values = event.getValues();
        String messageID = event.getMessageId();

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

        if (componentID.startsWith("FFCC_")) {
            componentID = componentID.replace("FFCC_", "");
            String factionWhoPressedButton = player == null ? "nullPlayer" : player.getFaction();
            if (player != null && !componentID.startsWith(factionWhoPressedButton + "_")) {
                String message = "To " + player.getFactionEmoji() + ": these buttons are for someone else";
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
                return null;
            }
            componentID = componentID.replaceFirst(factionWhoPressedButton + "_", "");
            context.setFactionChecked(true);
        }

        context.setGame(game);
        context.setMenuID(componentID);
        context.setMessageID(messageID);

        context.setValues(new ArrayList<>(values));
        context.setPlayer(player);

        context.setPrivateChannel(privateChannel);
        context.setMainGameChannel(mainGameChannel);
        context.setActionsChannel(actionsChannel);
        return context;
    }
}
