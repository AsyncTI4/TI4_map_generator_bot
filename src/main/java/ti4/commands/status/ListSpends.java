package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ListSpends extends StatusSubcommandData {
    public ListSpends() {
        super(Constants.SPENDS, "List value of plastic and CCs gained by players this game");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        if (FoWHelper.isPrivateGame(event)) {
            MessageHelper.replyToMessage(event, "This command is not available in fog of war private channels.");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("**__Total Spends in ").append(game.getName());
        if (!game.getCustomName().isEmpty()) {
            message.append(" - ").append(game.getCustomName());
        }
        message.append("__**");

        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) continue;
            String turnString = playerSpends(player);
            message.append("\n").append(turnString);
        }

        MessageHelper.replyToMessage(event, message.toString());
    }

    private String playerSpends(Player player) {
        return "> " + player.getUserName() + ": " + player.getTotalExpenses() + " total i/r value of plastic built and CCs gained";
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        //We reply in execute command
    }
}
