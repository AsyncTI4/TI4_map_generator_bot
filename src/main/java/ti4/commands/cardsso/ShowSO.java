package ti4.commands.cardsso;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowSO extends SOCardsSubcommandData {
    public ShowSO() {
        super(Constants.SHOW_SO, "Show a Secret Objective to a player");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
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
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            sendMessage("Please select what Secret Objective to show");
            return;
        }

        int soIndex = option.getAsInt();
        String soID = null;
        for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(soIndex)) {
                soID = so.getKey();
            }
        }

        if (soID == null) {
            sendMessage("No such Secret Objective ID found, please retry");
            return;
        }

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
