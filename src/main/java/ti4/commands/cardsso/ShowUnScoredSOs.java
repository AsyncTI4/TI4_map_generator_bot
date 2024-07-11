package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowUnScoredSOs extends SOCardsSubcommandData {
    public ShowUnScoredSOs() {
        super(Constants.SHOW_UNSCORED_SOS, "List any SOs that are not scored yet");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        showUnscored(game, event);
    }

    public static void showUnscored(Game game, GenericInteractionCreateEvent event) {
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This command is disabled for fog mode");
            return;
        }
        List<String> defaultSecrets = Mapper.getDecks().get(game.getSoDeckID()).getNewShuffledDeck();
        List<String> currentSecrets = new ArrayList<>(defaultSecrets);
        for (Player player : game.getPlayers().values()) {
            if (player == null) {
                continue;
            }
            if (player.getSecretsScored() != null) {
                currentSecrets.removeAll(player.getSecretsScored().keySet());
            }
        }
        currentSecrets.removeAll(game.getSoToPoList());
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Unscored Action Phase Secrets: ").append("\n");
        int x = 1;
        for (String id : currentSecrets) {
            if (SOInfo.getSecretObjectiveRepresentation(id).contains("Action Phase")) {
                sb.append(x).append(SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }
        }
        x = 1;
        sb.append("\n").append("Unscored Status Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if (SOInfo.getSecretObjectiveRepresentation(id).contains("Status Phase")) {
                sb.append(x).append(SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }
        }
        x = 1;
        sb.append("\n").append("Unscored Agenda Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if (SOInfo.getSecretObjectiveRepresentation(id).contains("Agenda Phase")) {
                sb.append(x).append(SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
