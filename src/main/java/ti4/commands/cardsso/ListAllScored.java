package ti4.commands.cardsso;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ListAllScored extends SOCardsSubcommandData{
    public ListAllScored() {
        super(Constants.SO_LIST_SCORED, "Displays all scored secret objectives");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**Scored Secret Objectives**__\n");

        Game game = getActiveGame();
        List<Player> players = game.getPlayers().values().stream().toList();
        for (Player player : players) {
            for (var objective : player.getSecretsScored().keySet()) {
                sb.append(player.getFactionEmoji()).append(SOInfo.getSecretObjectiveRepresentation(objective));
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
