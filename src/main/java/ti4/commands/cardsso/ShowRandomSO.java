package ti4.commands.cardsso;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowRandomSO extends SOCardsSubcommandData {

    public ShowRandomSO() {
        super("show_random", "Show a Secret Objective to a player");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        List<String> secrets = new ArrayList<>(player.getSecrets().keySet());
        if (secrets.isEmpty()) {
            sendMessage("No secrets to reveal");
            return;
        }
        Collections.shuffle(secrets);
        String soID = secrets.get(0);

        String sb = "Game: " + activeGame.getName() + "\n" +
            "Player: " + player.getUserName() + "\n" +
            "Showed Secret Objectives:" + "\n" +
            SOInfo.getSecretObjectiveRepresentation(soID) + "\n";

        player.setSecret(soID);

        Player player_ = Helper.getPlayer(activeGame, null, event);
        if (player_ == null) {
            sendMessage("Player not found");
            return;
        }
        
        sendMessage("SO shown to player");
        SOInfo.sendSecretObjectiveInfo(activeGame, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, activeGame, sb);
    }
}
