package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
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

        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) continue;
            String turnString = playerAverageTurnLength(player);
            message.append("\n").append(turnString);
        }

        MessageHelper.replyToMessage(event, message.toString());
    }

    private String playerAverageTurnLength(Player player) {
        long totalMillis = player.getTotalTurnTime();
        int numTurns = player.getNumberTurns();
        if (numTurns == 0 || totalMillis == 0) {
            return "> " + player.getUserName() + " has not taken a turn yet.";
        }

        long total = totalMillis / numTurns;
        long millis = total % 1000;

        total = total / 1000; //total seconds (truncates)
        long seconds = total % 60;

        total = total / 60; //total minutes (truncates)
        long minutes = total % 60;
        long hours = total / 60; //total hours (truncates)

        return "> " + player.getUserName() + ": `" +
            String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis) +
            "` (" + numTurns + " turns)";
    }
}
