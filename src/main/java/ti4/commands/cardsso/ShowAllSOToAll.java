package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.cards.CardsInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class ShowAllSOToAll extends SOCardsSubcommandData {
    public ShowAllSOToAll() {
        super(Constants.SHOW_ALL_SO_TO_ALL, "Show Secret Objective to player");
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


            StringBuilder sb = new StringBuilder();

        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");

        LinkedHashMap<String, Integer> secretObjective = activeMap.getSecretObjective(player.getUserID());
        LinkedHashMap<String, Integer> scoredSecretObjective = new LinkedHashMap<>(activeMap.getScoredSecretObjective(player.getUserID()));
        for (String id : activeMap.getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }

        String color = player.getColor();
        sb.append(Helper.getFactionIconFromDiscord(player.getFaction()));
        sb.append(" (").append(player.getFaction()).append(")");
        if (color != null) {
            sb.append(" (").append(color).append(")");
        }
        sb.append("\n");
        sb.append("**Secret Objectives:**").append("\n");
        int index = 1;
        if (secretObjective != null) {
            for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                sb.append(index).append(" - ").append(Mapper.getSecretObjective(so.getKey())).append("\n");
                player.setSecret(so.getKey());
                index++;
            }
        }
        sb.append("\n").append("**Scored Secret Objectives:**").append("\n");
        for (java.util.Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
            sb.append(index).append(". (").append(so.getValue()).append(") - ").append(Mapper.getSecretObjective(so.getKey())).append("\n");
            index++;
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        CardsInfo.sentUserCardInfo(event, activeMap, player);
    }
}
