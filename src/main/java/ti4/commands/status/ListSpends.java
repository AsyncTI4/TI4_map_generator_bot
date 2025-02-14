package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ListSpends extends GameStateSubcommand {

    public ListSpends() {
        super(Constants.SPENDS, "List value of plastic and tokens gained by players this game", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
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
        return "> " + player.getUserName() + ": " + player.getTotalExpenses()
                + " total combined influence and resources spend collectively on units built and command tokens gained.";
    }
}
