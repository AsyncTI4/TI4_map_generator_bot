package ti4.helpers.omega_phase;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PriorityTrackHelper {
    public static void PrintPriorityTrack(Game game) {
        StringBuilder sb = new StringBuilder("**Priority Track**\n");

        var priorityTrack = GetPriorityTrack(game);
        for (var i = 0; i < priorityTrack.size(); i++) {
            int priority = i + 1;
            if (priorityTrack.get(i) != null) {
                var player = priorityTrack.get(i);
                sb.append(String.format("%d. %s\n", priority, player.getRepresentation()));
            } else {
                sb.append(String.format("%d.\n", priority));
            }
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb.toString());
    }

    /*
     * Priority is 1-indexed, so the first player gets priority 1, the second player
     * gets priority 2, etc.
     */
    public static void AssignPlayerToPriority(Game game, Player player, Integer priority) {
        // Ensure player exists in the game
        var players = game.getPlayers().values();
        if (players.stream().noneMatch(p -> Objects.equals(p.getUserID(), player.getUserID()))) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Player not found in the game.");
            return;
        }

        var messageOutput = "";
        if (priority != null) {
            if (priority < -1) {
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(), "Priority must be between 1 and the number of players (or just -1).");
                return;
            }

            if (priority > players.size()) {
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(), "Priority cannot exceed the number of players.");
                return;
            }

            if (priority == 0) {
                priority = -1;
            }

            // If another player already has this priority value, clear it
            if (priority != -1) {
                int immutablePriority = priority;
                var existingIndex = players.stream()
                        .filter(p -> p.hasPriorityPosition() && p.getPriorityPosition() == immutablePriority)
                        .findFirst();
                if (existingIndex.isPresent()) {
                    var existingPlayer = existingIndex.get();
                    existingPlayer.setPriorityPosition(-1); // Clear the existing player's priority
                    messageOutput += existingPlayer.getRepresentation() + " has been removed from position " + priority
                            + " on the priority track.\n";
                }
            }
        } else {
            if (player.hasPriorityPosition()) {
                // If the player already has a priority position, we don't need to assign them again
                messageOutput += player.getRepresentation() + " is already on the priority track at position "
                        + player.getPriorityPosition() + ".\n";
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), messageOutput);
                return;
            }

            var currentPriotityTrack = GetPriorityTrack(game);
            for (var i = 0; i < currentPriotityTrack.size(); i++) {
                if (currentPriotityTrack.get(i) == null) {
                    // Found an empty spot, assign the player here
                    priority = i + 1; // 1-indexed
                    break;
                }
            }
            if (priority == null) {
                // If no empty spot was found, return early with message
                player.setPriorityPosition(-1); // Ensure data model matches inferred state
                messageOutput += player.getRepresentation()
                        + " could not be placed on the priority track because no empty spot was availble.\n";
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), messageOutput);
                return;
            }
        }

        if (priority > 0) {
            // Assign the player's priority
            player.setPriorityPosition(priority);
            messageOutput += player.getRepresentation() + " has been assigned to position " + priority
                    + " on the priority track.";
        } else if (priority < 1 && player.hasPriorityPosition()) {
            // If priority is -1, remove the player from the priority track
            player.setPriorityPosition(-1);
            messageOutput += player.getRepresentation() + " has been removed from the priority track.";
        } else {
            // The player was to be removed, but was already off the track
            messageOutput = player.getRepresentation() + " is not on the priority track.";
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), messageOutput);
    }

    public static void ClearPriorityTrack(Game game) {
        var players = game.getPlayers().values();
        for (var player : players) {
            player.setPriorityPosition(-1);
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "The priority track has been cleared.");
    }

    public static List<Player> GetPriorityTrack(Game game) {
        List<Player> priorityTrack = new ArrayList<>();
        int numPlayers = game.getRealPlayers().size();
        for (int i = 0; i < numPlayers; i++) {
            priorityTrack.add(null);
        }

        for (Player player : game.getRealPlayers()) {
            int position = player.getPriorityPosition();
            if (position > 0 && position <= numPlayers) {
                priorityTrack.set(position - 1, player);
            }
        }

        return priorityTrack;
    }

    public static void CreateDefaultPriorityTrack(Game game) {
        var currentPriorityTrack = GetPriorityTrack(game);
        var players = game.getRealPlayers();
        if (game.getSpeaker() != null) {
            players = Helper.getSpeakerOrderFromThisPlayer(game.getSpeaker(), game);
        }
        for (var i = 0; i < currentPriorityTrack.size(); i++) {
            if (currentPriorityTrack.get(i) == null) {
                var player =
                        players.stream().filter(p -> !p.hasPriorityPosition()).findFirst();
                if (player.isPresent()) {
                    player.get().setPriorityPosition(i + 1);
                } else {
                    break;
                }
            }
        }
    }

    public enum PriorityTrackMode {
        NONE("Priority Track not used"),
        BASIC("Use to select Strategy Cards"),
        AFTER_SPEAKER("Use to select remaining Strategy Cards after the Speaker"),
        THIS_ROUND_ONLY("Use to select Strategy Cards for a single round"),
        FULL("Replace Speaker order fully, and score in Priority order");

        private final String description;

        PriorityTrackMode(String description) {
            this.description = description;
        }

        public String getAutoCompleteName() {
            return this + ": " + description;
        }

        public boolean search(String searchString) {
            return toString().toLowerCase().contains(searchString)
                    || description.toLowerCase().contains(searchString);
        }

        public static boolean isValid(String value) {
            return EnumSet.allOf(PriorityTrackMode.class).stream().anyMatch(r -> value.equals(r.name()));
        }

        public static PriorityTrackMode parse(String value) {
            for (PriorityTrackMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value) || mode.description.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Invalid PriorityTrackMode: " + value);
        }
    }
}
