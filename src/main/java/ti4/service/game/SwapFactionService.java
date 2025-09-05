package ti4.service.game;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class SwapFactionService {

    public static void secondHalfOfSwap(
            Game game,
            Player addedPlayer,
            Player removedPlayer,
            User addedUser,
            GenericInteractionCreateEvent event) {
        Collection<Player> players = game.getPlayers().values();
        if (players.stream().noneMatch(player -> player.getUserID().equals(removedPlayer.getUserID()))) {
            MessageHelper.replyToMessage(event, "Specify player that is in game to be swapped");
            return;
        }
        Player player = game.getPlayer(removedPlayer.getUserID());
        Map<String, List<String>> scoredPublicObjectives = game.getScoredPublicObjectives();
        for (Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
            List<String> value = poEntry.getValue();
            boolean removed = value.remove(removedPlayer.getUserID());
            boolean removed2 = value.remove(addedPlayer.getUserID());
            boolean removed4 = value.remove(addedPlayer.getUserID());
            if (removed) {
                boolean removed3 = value.remove(removedPlayer.getUserID());
                value.add(addedPlayer.getUserID());
                if (removed3) {
                    value.add(addedPlayer.getUserID());
                }
            }
            if (removed2) {
                value.add(removedPlayer.getUserID());

                if (removed4) {
                    value.add(removedPlayer.getUserID());
                }
            }
        }
        String oldActive = game.getActivePlayerID();
        String oldSpeaker = game.getSpeakerUserID();

        if (addedPlayer.getUserID().equalsIgnoreCase(oldActive)) {
            game.setActivePlayerID(removedPlayer.getUserID());
        }
        if (removedPlayer.getUserID().equalsIgnoreCase(oldActive)) {
            game.setActivePlayerID(addedPlayer.getUserID());
        }

        if (addedPlayer.getUserID().equalsIgnoreCase(oldSpeaker)) {
            game.setSpeakerUserID(removedPlayer.getUserID());
        }
        if (removedPlayer.getUserID().equalsIgnoreCase(oldSpeaker)) {
            game.setSpeakerUserID(addedPlayer.getUserID());
        }

        if (player.isDummy()) {
            player.setDummy(false);
            addedPlayer.setDummy(true);
        }

        if (game.isFowMode()) {
            // Fog data is saved by userID so need to swap it too
            game.getTileMap().values().forEach(tile -> tile.swapFogData(player, addedPlayer));
        }

        String before =
                "> **Before:** " + addedPlayer.getRepresentation() + " & " + player.getRepresentation() + "\n";
        addedPlayer.setUserName(removedPlayer.getUserName());
        addedPlayer.setUserID(removedPlayer.getUserID());
        addedPlayer.removeTeamMateID(removedPlayer.getUserID());
        player.setUserName(addedUser.getName());
        player.setUserID(addedUser.getId());
        player.removeTeamMateID(addedUser.getId());
        String after =
                "> **After:** " + addedPlayer.getRepresentation() + " & " + player.getRepresentation() + "\n";

        String message = "Users have swapped factions:\n" + before + after;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }
}
