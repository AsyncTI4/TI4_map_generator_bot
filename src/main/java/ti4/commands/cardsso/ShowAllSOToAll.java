package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

import java.util.*;

public class ShowAllSOToAll extends SOCardsSubcommandData {
    public ShowAllSOToAll() {
        super(Constants.SHOW_ALL_SO_TO_ALL, "Show all Secret Objectives to all players");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }


            StringBuilder sb = new StringBuilder();

        sb.append("Player: ").append(player.getUserName()).append("\n");

        List<String> secretObjectives = new ArrayList<>(activeMap.getSecretObjective(player.getUserID()).keySet());
        Collections.shuffle(secretObjectives);
        LinkedHashMap<String, Integer> scoredSecretObjective = new LinkedHashMap<>(activeMap.getScoredSecretObjective(player.getUserID()));
        for (String id : activeMap.getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }

        sb.append(Helper.getPlayerRepresentation(player, activeMap));
        sb.append("\n");
        sb.append("**Secret Objectives:**").append("\n");
        int index = 1;
        if (secretObjectives != null) {
            for (String so : secretObjectives) {
                sb.append(index).append(" - ").append(SOInfo.getSecretObjectiveRepresentation(so)).append("\n");
                player.setSecret(so);
                index++;
            }
        }
        sb.append("\n").append("**Scored Secret Objectives:**").append("\n");
        for (java.util.Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
            sb.append(index).append(". (").append(so.getValue()).append(") - ").append(SOInfo.getSecretObjectiveRepresentation(so.getKey())).append("\n");
            index++;
        }
        sendMessage(sb.toString());
    }
}
