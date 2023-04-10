package ti4.commands.cards;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.*;

public class CardsInfo extends CardsSubcommandData {

    public static final String CARDS_INFO = Constants.CARDS_INFO_THREAD_PREFIX;

    public CardsInfo() {
        super(Constants.INFO, "Send all your cards to your Cards Info thread");
        addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to show full promissory text").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO, "Set TRUE to get card info as direct message also").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        String headerText = Helper.getPlayerRepresentation(event, player) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        sentUserCardInfo(event, activeMap, player);
    }

    public static void sentUserCardInfo(SlashCommandInteractionEvent event, Map activeMap, Player player) {
        OptionMapping longPNOption = event != null ? event.getOption(Constants.LONG_PN_DISPLAY) : null;
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = longPNOption.getAsString().equalsIgnoreCase("y") || longPNOption.getAsString().equalsIgnoreCase("yes");
        }

        sentUserCardInfo(event, activeMap, player, longPNDisplay);
    }

    public static void sentUserCardInfo(GenericInteractionCreateEvent event, Map activeMap, Player player, boolean longPNDisplay) {
        checkAndAddPNs(activeMap, player);

        String headerText;
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------\n");
        sb.append("**Game: **`").append(activeMap.getName()).append("`\n");
        if (event != null) {
            sb.append(Helper.getPlayerRepresentation(event, player, true));
        }
        
        headerText = sb.toString();      

        User userById = event != null ? event.getJDA().getUserById(player.getUserID()) : null;
        if (userById != null) {
            try {
                MessageChannel channel = event.getMessageChannel();
                if (activeMap.isFoWMode()) {
                    if (player.getPrivateChannel() == null) {
                        MessageHelper.sendMessageToChannel(channel, "Private channels are not set up for this game. Messages will be suppressed.");
                        return;
                    }
                }

                String threadName = CARDS_INFO + activeMap.getName() + "-" + player.getUserName().replaceAll("/", "");
                String playerPing = Helper.getPlayerPing(player);
                
                ThreadChannel threadChannel = Helper.getPlayerCardsInfoThread(activeMap, player);
                if (threadChannel == null) {
                    MessageHelper.sendMessageToChannel(channel, "Could not generate card info for " + playerPing);
                    return;
                } else {
                    sendCardInfoToChannel(threadChannel, threadName + " " + playerPing, headerText, activeMap, player, longPNDisplay);
                }
            } catch (Exception e) {
                BotLogger.log("Could not create Private Thread");
            }
        } else {
            MessageHelper.sendMessageToUser("Player: " + player.getUserName() + " not found", event);
        }
    }

    private static void sendCardInfoToChannel(MessageChannel privateChannel, String ping, String header, Map activeMap, Player player, boolean longPNDisplay) {
        String text = ping == null ? null : ping + "\n";
        MessageHelper.sendMessageToChannel(privateChannel, header);
        MessageHelper.sendMessageToChannel(privateChannel, text);
        SOInfo.sendSecretObjectiveInfo(activeMap, player);
        ACInfo.sendActionCardInfo(activeMap, player);
        PNInfo.sendPromissoryNoteInfo(activeMap, player, longPNDisplay);
    }

    private static void checkAndAddPNs(Map activeMap, Player player) {
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (Mapper.isColorValid(playerColor) && Mapper.isFaction(playerFaction)) {
            List<String> promissoryNotes = new ArrayList<>(Mapper.getPromissoryNotes(playerColor, playerFaction));
            for (Player player_ : activeMap.getPlayers().values()) {
                promissoryNotes.removeAll(player_.getPromissoryNotes().keySet());
                promissoryNotes.removeAll(player_.getPromissoryNotesInPlayArea());
            }
            promissoryNotes.removeAll(player.getPromissoryNotes().keySet());
            promissoryNotes.removeAll(player.getPromissoryNotesInPlayArea());
            promissoryNotes.removeAll(activeMap.getPurgedPN());
            if (!promissoryNotes.isEmpty()) {
                for (String promissoryNote : promissoryNotes) {
                    player.setPromissoryNote(promissoryNote);
                }
            }
        }
    }
}
