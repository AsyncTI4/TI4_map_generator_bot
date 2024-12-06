package ti4.service.statistics.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;

@UtilityClass
class PingCounterStatisticsService {

    static void listPingCounterList(SlashCommandInteractionEvent event) {
        Game reference = GameManager.getGame("finreference");
        if (reference == null) {
            return;
        }
        Map<String, Integer> pings = new HashMap<>();
        for (String pingsFor : reference.getMessagesThatICheckedForAllReacts().keySet()) {
            if (pingsFor.contains("pingsFor")) {
                String userID = pingsFor.replace("pingsFor", "");
                User user = AsyncTI4DiscordBot.jda.getUserById(Long.parseLong(userID));
                if (user == null) {
                    continue;
                }
                pings.put(userID, Integer.parseInt(reference.getMessagesThatICheckedForAllReacts().get(pingsFor)));
            }
        }

        Map<String, Integer> topThousand = pings.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3000)
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        int index = 1;
        StringBuilder sb = new StringBuilder("List of times the player has hit the autoping threshold(aka the bots most wanted list)\n");
        for (String ket : topThousand.keySet()) {
            User user = AsyncTI4DiscordBot.jda.getUserById(Long.parseLong(ket));
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(user.getEffectiveName()).append(": ");
            sb.append(topThousand.get(ket)).append(" pings");
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Ping Counts", sb.toString());
    }
}
