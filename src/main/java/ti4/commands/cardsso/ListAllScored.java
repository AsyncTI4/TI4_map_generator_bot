package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListAllScored extends SOCardsSubcommandData{
    public ListAllScored() {
        super(Constants.SO_LIST_SCORED, "Displays all scored secret objectives");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var stringBuilder = new StringBuilder();
        stringBuilder.append("__**Scored Secret Objectives**__\n");

        var game = getActiveGame();
        var players = game.getPlayers().entrySet();
        for (var player : players) {
            var objectiveIDs = getActiveGame().getScoredSecretObjective(player.getKey());
            for (var objective : objectiveIDs.keySet()) {
                stringBuilder.append(player.getValue().getFactionEmoji()).append(SOInfo.getSecretObjectiveRepresentation(objective));
            }
        }

        MessageHelper.sendMessageToChannel(
            event.getMessageChannel(),
            stringBuilder.toString()
        );
    }
}
