package ti4.service.game;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.map.Game;
import ti4.map.manage.GameSaveService;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class SwapFactionService {

    public static void secondHalfOfSwap(Game game, Player swapperPlayer, Player removedPlayer, User addedUser, GenericInteractionCreateEvent event) {
        StringBuilder message = new StringBuilder("Users have swapped factions:\n");
        message.append("> **Before:** ").append(swapperPlayer.getRepresentation()).append(" & ").append(removedPlayer.getRepresentation()).append("\n");
        Collection<Player> players = game.getPlayers().values();
        if (players.stream().anyMatch(player -> player.getUserID().equals(removedPlayer.getUserID()))) {
            Player player = game.getPlayer(removedPlayer.getUserID());
            Map<String, List<String>> scoredPublicObjectives = game.getScoredPublicObjectives();
            for (Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
                List<String> value = poEntry.getValue();
                boolean removed = value.remove(removedPlayer.getUserID());
                boolean removed2 = value.remove(swapperPlayer.getUserID());
                if (removed) {
                    value.add(addedUser.getId());
                }
                if (removed2) {
                    value.add(removedPlayer.getUserID());
                }
            }
            if (player.isDummy()) {
                player.setDummy(false);
                swapperPlayer.setDummy(true);
            }
            swapperPlayer.setUserName(removedPlayer.getUserName());
            swapperPlayer.setUserID(removedPlayer.getUserID());
            player.setUserName(addedUser.getName());
            player.setUserID(addedUser.getId());
        } else {
            MessageHelper.replyToMessage(event, "Specify player that is in game to be swapped");
            return;
        }
        GameSaveService.saveGame(game, event);
        GameSaveService.reload(game.getName());
        message.append("> **After:** ").append(swapperPlayer.getRepresentation()).append(" & ").append(removedPlayer.getRepresentation()).append("\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
    }
}
