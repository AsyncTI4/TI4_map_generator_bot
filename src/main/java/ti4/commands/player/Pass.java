package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class Pass extends PlayerSubcommandData {
    public Pass() {
        super(Constants.PASS, "Pass");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("You're not a player of this game");
            return;
        }

        if (!activeGame.getPlayedSCs().containsAll(player.getSCs())) {
            sendMessage("You have not played your strategy cards, you cannot pass.");
            return;
        }
        player.setPassed(true);
        String text = Helper.getPlayerRepresentation(player, activeGame) + " PASSED";
        sendMessage(text);
        Turn.pingNextPlayer(event, activeGame, player);
    }
}
