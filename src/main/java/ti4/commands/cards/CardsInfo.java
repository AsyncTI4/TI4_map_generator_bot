package ti4.commands.cards;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class CardsInfo extends CardsSubcommandData {
    public CardsInfo() {
        super(Constants.INFO, "Resent all my cards in Private Message");
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
        LinkedHashMap<String, Integer> secretObjective = activeMap.getSecretObjective(player.getUserID());
        LinkedHashMap<String, Integer> scoredSecretObjective = activeMap.getScoredSecretObjective(player.getUserID());
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------\n");
        sb.append("**Game: **").append(activeMap.getName()).append("\n");
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
                    sb.append(index).append(". (").append(pn.getValue()).append(") - ").append(Mapper.getPromissoryNote(pn.getKey()));
                    sb.append("\n");
                    index++;
                }
            }
            sb.append("\n");
            sb.append("\n").append("**PLAY AREA Promissory Notes:**").append("\n");
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    sb.append(index).append(". (").append(pn.getValue()).append(") - ").append(Mapper.getPromissoryNote(pn.getKey()));
                    sb.append("\n");
                    index++;
                }
            }
        }
        sb.append("--------------------\n");
        User userById = event.getJDA().getUserById(player.getUserID());
        if (userById != null) {
        MessageHelper.sentToMessageToUser(event, sb.toString(), userById);
        } else {
            MessageHelper.sentToMessageToUser(event, "Player: " + player.getUserName() + " not found");
        }
    }

    private static void checkAndAddPNs(Map activeMap, Player player) {
        String playerColor = player.getColor();
        String playerFaction = player.getFaction();
        if (Mapper.isColorValid(playerColor) && Mapper.isFaction(playerFaction)){
            List<String> promissoryNotes = new ArrayList<>(Mapper.getPromissoryNotes(playerColor, playerFaction));
            for (Player player_ : activeMap.getPlayers().values()) {
                    promissoryNotes.removeAll(player_.getPromissoryNotes().keySet());
                    promissoryNotes.removeAll(player_.getPromissoryNotesInPlayArea());
                }
            promissoryNotes.removeAll(player.getPromissoryNotes().keySet());
            promissoryNotes.removeAll(player.getPromissoryNotesInPlayArea());
            if (!promissoryNotes.isEmpty()){
                for (String promissoryNote : promissoryNotes) {
                    player.setPromissoryNote(promissoryNote);
                }
            }
        }
    }
}
