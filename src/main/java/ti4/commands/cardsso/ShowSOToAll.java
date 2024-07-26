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

public class ShowSOToAll extends SOCardsSubcommandData {
    public ShowSOToAll() {
        super(Constants.SHOW_SO_TO_ALL, "Show a secret objectives to all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID to show").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found.");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Please select which secret objective to show to everyone.");
            return;
        }

        int soIndex = option.getAsInt();
        String soID = null;
        boolean scored = false;
        for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(soIndex)) {
                soID = so.getKey();
                break;
            }
        }
        if (soID == null) {
            for (Map.Entry<String, Integer> so : player.getSecretsScored().entrySet()) {
                if (so.getValue().equals(soIndex)) {
                    soID = so.getKey();
                    scored = true;
                    break;
                }
            }
        }

        if (soID == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such secret objective ID found, please retry.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        if (scored) {
            sb.append("Showed Scored Secret Objectives:").append("\n");
        } else {
            sb.append("Showed Secret Objectives:").append("\n");
        }
        sb.append(SOInfo.getSecretObjectiveRepresentation(soID)).append("\n");
        if (!scored) {
            player.setSecret(soID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
