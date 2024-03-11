package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ListDiceLuck extends StatusSubcommandData {
    public ListDiceLuck() {
        super(Constants.DICE_LUCK, "List dice luck for this game");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        if (FoWHelper.isPrivateGame(event)) {
            MessageHelper.replyToMessage(event, "This command is not available in fog of war private channels.");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("**__Average dice luck in ").append(activeGame.getName());
        if (!activeGame.getCustomName().isEmpty()) {
            message.append(" - ").append(activeGame.getCustomName());
        }
        message.append("__**");

        for (Player player : activeGame.getPlayers().values()) {
            if (!player.isRealPlayer()) continue;
            String turnString = playerAverageDiceLuck(player);
            message.append("\n").append(turnString);
        }

        MessageHelper.replyToMessage(event, message.toString());
    }

    private String playerAverageDiceLuck(Player player) {
        double expectedHits = player.getExpectedHitsTimes10()/10.0;
        int actualHits = player.getActualHits();
        if (expectedHits == 0) {
            return "> " + player.getUserName() + " has not rolled dice yet.";
        }

        double total = actualHits / expectedHits;
        

      return "> " + player.getUserName() + ": `" +
          String.format("%.2f", total) +
          "` (" + actualHits + "/"+String.format("%.1f", expectedHits) +" actual/expected)";
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        //We reply in execute command
    }
}
