package ti4.commands.cards;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

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

        LinkedHashMap<String, Integer> secretObjective = activeMap.getSecretObjective(player.getUserID());
        LinkedHashMap<String, Integer> scoredSecretObjective = activeMap.getSecretObjective(player.getUserID());
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Secret Objectives:").append("\n");
        int index = 1;
        if (secretObjective != null) {
            for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                sb.append(index).append(". (").append(so.getValue()).append(") - ").append(Mapper.getSecretObjective(so.getKey())).append("\n");
                index++;
            }
        }
        sb.append("\n").append("\n").append("Scored Secret Objectives:").append("\n");
        if (scoredSecretObjective != null) {
            for (java.util.Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
                sb.append(index).append(". (").append(so.getValue()).append(") - ").append(Mapper.getSecretObjective(so.getKey())).append("\n");
                index++;
            }
        }
        sb.append("\n").append("\n");
        MessageHelper.sentToMessageToUser(event, sb.toString());
    }

}
