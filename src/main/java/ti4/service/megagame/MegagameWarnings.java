package ti4.service.megagame;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MegagameWarnings {

    public static List<String> of(Game game) {
        List<String> warnings = new ArrayList<>();
        List<String> groups = MegagameGroupService.groups(game);
        if (groups.isEmpty()) {
            return warnings;
        }
        for (String group : groups) {
            List<String> gone = MegagameGroupService.memberFactions(game, group).stream()
                    .filter(faction -> !isRealPlayer(game, faction))
                    .toList();
            if (!gone.isEmpty()) {
                warnings.add(MegagameGroupService.label(group) + " still lists " + String.join(", ", gone)
                        + ", who is no longer a real player. Rebalance with `/megagame move`.");
            }
            Player speaker = MegagameGroupService.groupSpeaker(game, group);
            List<Player> members = MegagameGroupService.members(game, group);
            if (!members.isEmpty() && (speaker == null || !members.contains(speaker))) {
                warnings.add(MegagameGroupService.label(group)
                        + " has no speaker among its members. Use `/megagame set_speaker`.");
            }
            Player picker = MegagameGroupService.currentPicker(game, group);
            if ("strategy".equalsIgnoreCase(game.getPhaseOfGame()) && picker != null && !picker.isRealPlayer()) {
                Player next = MegagameStrategyPhaseService.nextPicker(game, group);
                warnings.add(MegagameGroupService.label(group) + " is waiting on " + picker.getFaction()
                        + ", who can no longer pick."
                        + (next == null ? "" : " Pick for " + next.getFaction() + " with `/player sc_pick`."));
            }
        }
        String sizes = groups.stream()
                .map(group ->
                        group + " " + MegagameGroupService.members(game, group).size())
                .collect(Collectors.joining(", "));
        int largest = groups.stream()
                .mapToInt(group -> MegagameGroupService.members(game, group).size())
                .max()
                .orElse(0);
        int smallest = groups.stream()
                .mapToInt(group -> MegagameGroupService.members(game, group).size())
                .min()
                .orElse(0);
        if (largest - smallest > 1) {
            warnings.add("Group sizes are uneven (" + sizes + "). Rebalance with `/megagame move`.");
        }
        Player gameSpeaker = game.getSpeaker();
        if (gameSpeaker != null && !groups.getLast().equals(MegagameGroupService.groupOf(game, gameSpeaker))) {
            warnings.add("The game speaker is not in the last group. Use `/megagame set_speaker`.");
        }
        return warnings;
    }

    public static String asText(Game game) {
        return of(game).stream().map(warning -> "⚠️ " + warning).collect(Collectors.joining("\n"));
    }

    private static boolean isRealPlayer(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player != null && player.isRealPlayer();
    }
}
