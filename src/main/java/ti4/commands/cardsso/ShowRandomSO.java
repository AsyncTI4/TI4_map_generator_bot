package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowRandomSO extends SOCardsSubcommandData {

    public ShowRandomSO() {
        super("show_random", "Show a Secret Objective to a player");
        addOptions(new OptionData(OptionType.STRING, Constants.OTHER_FACTION_OR_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        List<String> secrets = new ArrayList<>(player.getSecrets().keySet());
        if (secrets.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No secrets to reveal");
            return;
        }
        Collections.shuffle(secrets);
        String soID = secrets.getFirst();

        String sb = "Game: " + game.getName() + "\n" +
            "Player: " + player.getUserName() + "\n" +
            "Showed Secret Objectives:" + "\n" +
            SOInfo.getSecretObjectiveRepresentation(soID) + "\n";

        player.setSecret(soID);

        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player not found");
            return;
        }

        MessageHelper.sendMessageToEventChannel(event, "SO shown to player");
        SOInfo.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(otherPlayer, game, sb);
    }
}
