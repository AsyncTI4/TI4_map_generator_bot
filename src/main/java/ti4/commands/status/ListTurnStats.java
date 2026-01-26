package ti4.commands.status;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ListTurnStats extends GameStateSubcommand {

    public ListTurnStats() {
        super(Constants.TURN_STATS, "List average amount of time players take on their turns", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (FoWHelper.isPrivateGame(event)) {
            MessageHelper.replyToMessage(event, "This command is not available in fog of war private channels.");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("**__Average turn length in ").append(game.getName());
        if (!game.getCustomName().isEmpty()) {
            message.append(" - ").append(game.getCustomName());
        }
        message.append("__**");

        List<Player> players = game.getRealPlayers().stream()
                .filter(p -> !p.isNeutral())
                .filter(p -> p.getNumberOfTurns() > 0)
                .toList();
        Comparator<Player> byTurnTime = Comparator.comparing(ListTurnStats::getAvgTurnTime);
        Optional<Player> min = players.stream().sorted(byTurnTime).findFirst();
        Optional<Player> max = players.stream().sorted(byTurnTime.reversed()).findFirst();
        Long maxTime = max.map(ListTurnStats::getAvgTurnTime).orElse(null);

        for (Player player : players) {
            String turnString = playerAverageTurnLength(player);
            message.append("\n").append(turnString);
            if (min.map(player::is).orElse(false)) message.append(" 🐇");
            else if (max.map(player::is).orElse(false)) message.append(" 🐢");
            else if (maxTime != null && maxTime < 30 * 60000) message.append(" 🐢"); // 30 minutes
        }
        if (players.isEmpty()) {
            message.append("\n> Nobody has taken a turn yet :)");
        }

        MessageHelper.replyToMessage(event, message.toString());
    }

    private static long getAvgTurnTime(Player player) {
        long totalMillis = player.getTotalTurnTime();
        int numTurns = player.getNumberOfTurns();
        if (numTurns == 0 || totalMillis == 0) {
            return -1;
        }
        return totalMillis / numTurns;
    }

    private static String playerAverageTurnLength(Player player) {
        int numTurns = player.getNumberOfTurns();
        long total = getAvgTurnTime(player);
        if (total == -1) {
            return "> " + player.getUserName() + " has not taken a turn yet.";
        }
        long millis = total % 1000;

        total /= 1000; // total seconds (truncates)
        long seconds = total % 60;

        total /= 60; // total minutes (truncates)
        long minutes = total % 60;
        long hours = total / 60; // total hours (truncates)

        String emoji = player.getGame().isFowMode() ? "" : (player.fogSafeEmoji() + " ");
        return "> " + emoji + player.getUserName() + ": `"
                + String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
                + "` (" + numTurns + " turns)";
    }
}
