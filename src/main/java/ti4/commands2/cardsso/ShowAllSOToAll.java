package ti4.commands2.cardsso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class ShowAllSOToAll extends GameStateSubcommand {

    public ShowAllSOToAll() {
        super(Constants.SHOW_ALL_SO_TO_ALL, "Show all Secret Objectives to all players", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();

        StringBuilder sb = new StringBuilder();

        sb.append("Player: ").append(player.getUserName()).append("\n");

        List<String> secretObjectives = new ArrayList<>(player.getSecrets().keySet());
        Collections.shuffle(secretObjectives);
        Map<String, Integer> scoredSecretObjective = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : getGame().getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }

        sb.append(player.getRepresentation());
        sb.append("\n");
        sb.append("**Secret Objectives:**").append("\n");
        int index = 1;
        for (String so : secretObjectives) {
            sb.append(index).append(" - ").append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(so)).append("\n");
            player.setSecret(so);
            index++;
        }
        sb.append("\n").append("**Scored Secret Objectives:**").append("\n");
        for (Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
            sb.append(index).append(". (").append(so.getValue()).append(") - ").append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(so.getKey())).append("\n");
            index++;
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
