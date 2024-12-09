package ti4.service.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class SetOrderService {

    public static void setPlayerOrder(GenericInteractionCreateEvent event, Game game, List<User> users) {
        Map<String, Player> newPlayerOrder = new LinkedHashMap<>();
        Map<String, Player> players = new LinkedHashMap<>(game.getPlayers());
        Map<String, Player> playersBackup = new LinkedHashMap<>(game.getPlayers());
        try {
            for (User user : users) {
                setPlayerOrder(newPlayerOrder, players, user);
            }
            if (!players.isEmpty()) {
                newPlayerOrder.putAll(players);
            }
            game.setPlayers(newPlayerOrder);
        } catch (Exception e) {
            game.setPlayers(playersBackup);
        }
        StringBuilder sb = new StringBuilder("Player order set:");
        for (Player player : game.getPlayers().values()) {
            sb.append("\n> ").append(player.getRepresentationNoPing());
            if (player.isSpeaker()) {
                sb.append(Emojis.SpeakerToken);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    public static void setPlayerOrder(Map<String, Player> newPlayerOrder, Map<String, Player> players, User user) {
        if (user == null) {
            return;
        }
        String id = user.getId();
        Player player = players.get(id);
        if (player != null) {
            newPlayerOrder.put(id, player);
            players.remove(id);
        }
    }

    public void setPlayerOrder(Map<String, Player> newPlayerOrder, Map<String, Player> players, Player player) {
        if (player != null) {
            newPlayerOrder.put(player.getUserID(), player);
            players.remove(player.getUserID());
        }
    }
}
