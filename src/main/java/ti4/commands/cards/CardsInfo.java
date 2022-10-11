package ti4.commands.cards;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class CardsInfo extends CardsSubcommandData {
    public CardsInfo() {
        super(Constants.INFO, "Resent all my cards in Private Message");
        addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to enable").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        sentUserCardInfo(event, activeMap, player);
    }

    public static void sentUserCardInfo(SlashCommandInteractionEvent event, Map activeMap, Player player) {
        checkAndAddPNs(activeMap, player);
        OptionMapping longPNOption = event.getOption(Constants.LONG_PN_DISPLAY);
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = longPNOption.getAsString().equalsIgnoreCase("y") || longPNOption.getAsString().equalsIgnoreCase("yes");
        }
        LinkedHashMap<String, Integer> secretObjective = activeMap.getSecretObjective(player.getUserID());
        LinkedHashMap<String, Integer> scoredSecretObjective = new LinkedHashMap<>(activeMap.getScoredSecretObjective(player.getUserID()));
        for (String id : activeMap.getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------\n");
        sb.append("**Game: **").append(activeMap.getName()).append("\n");
        String color = player.getColor();
        sb.append(Helper.getFactionIconFromDiscord(player.getFaction()));
        sb.append("(").append(player.getFaction()).append(")");
        if (color != null) {
            sb.append(" (").append(color).append(")");
        }
        sb.append("\n");
        sb.append("**Secret Objectives:**").append("\n");
        int index = 1;
        if (secretObjective != null) {
            for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                sb.append(index).append(". (").append(so.getValue()).append(") - ").append(Mapper.getSecretObjective(so.getKey())).append("\n");
                index++;
            }
        }
        sb.append("\n").append("**Scored Secret Objectives:**").append("\n");
        if (scoredSecretObjective != null) {
            for (java.util.Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
                sb.append(index).append(". (").append(so.getValue()).append(") - ").append(Mapper.getSecretObjective(so.getKey())).append("\n");
                index++;
            }
        }
        sb.append("\n").append("**Action Cards:**").append("\n");
        index = 1;
        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                sb.append(index).append(". (").append(ac.getValue()).append(") - ").append(Mapper.getActionCard(ac.getKey())).append("\n");
                index++;
            }
        }
        sb.append("\n").append("**Promissory Notes:**").append("\n");
        index = 1;
        LinkedHashMap<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                    sb.append(index).append(". (").append(pn.getValue()).append(") - ")
                            .append(Mapper.getPromissoryNote(pn.getKey(), longPNDisplay));
                    sb.append("\n");
                    index++;
                }
            }
            sb.append("\n");
            sb.append("\n").append("**PLAY AREA Promissory Notes:**").append("\n");
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    sb.append(index).append(". (").append(pn.getValue()).append(") - ").append(Mapper.getPromissoryNote(pn.getKey(), longPNDisplay));
                    sb.append("\n");
                    index++;
                }
            }
        }
        sb.append("--------------------\n");
        User userById = event.getJDA().getUserById(player.getUserID());
        if (userById != null) {
            if (activeMap.isCommunityMode() && player.getChannelForCommunity() instanceof MessageChannel){
                MessageHelper.sendMessageToChannel((MessageChannel) player.getChannelForCommunity(), sb.toString());
            } else {
                MessageHelper.sentToMessageToUser(event, sb.toString(), userById);
                try {
                    TextChannel textChannel = event.getTextChannel();
                    ThreadChannelAction threadChannel = textChannel.createThreadChannel("AC Info", true);
                    threadChannel.queue(msg -> {
                        msg.sendMessage("hello i private thread: " + Helper.getPlayerPing(event, player)).queue();
                    });
                }
                catch (Exception e){
                    BotLogger.log("Could not create Private Thread");
                }

            }
        } else {
            MessageHelper.sentToMessageToUser(event, "Player: " + player.getUserName() + " not found");
        }
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
