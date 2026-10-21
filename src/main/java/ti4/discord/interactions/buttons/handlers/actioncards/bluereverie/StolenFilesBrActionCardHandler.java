package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
public class StolenFilesBrActionCardHandler {
    private static final String RESOLVE = "resolveStolenFilesBr";
    private static final String KEEP = "stolenFilesKeep_";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Stolen Files"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolve(ButtonInteractionEvent event, Game game, Player player) {
        String first = game.drawSecretObjective(player.getUserID());
        String second = game.drawSecretObjective(player.getUserID());
        List<String> objectives = List.of(first == null ? "" : first, second == null ? "" : second).stream()
                .filter(id -> !id.isEmpty())
                .toList();
        if (objectives.size() < 2) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " could not draw 2 secret objectives for _Stolen Files_.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String objective : objectives) {
            SecretObjectiveModel model = Mapper.getSecretObjective(objective);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + KEEP + objectives.getFirst() + "|" + objectives.getLast() + "|"
                            + objective,
                    "Keep " + (model == null ? objective : model.getName())));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", choose which secret objective to keep from _Stolen Files_.",
                buttons);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
    }

    @ButtonHandler(KEEP)
    public static void keep(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(KEEP.length()).split("\\|", 3);
        if (parts.length != 3 || (!parts[2].equals(parts[0]) && !parts[2].equals(parts[1]))) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        String returnId = parts[2].equals(parts[0]) ? parts[1] : parts[0];
        Integer identifier = player.getSecretsUnscored().get(returnId);
        if (identifier == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        player.removeSecret(identifier);
        game.getSecretObjectives().add(returnId);
        Collections.shuffle(game.getSecretObjectives());
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " returned 1 secret objective to the deck and shuffled it using _Stolen Files_.");
        ButtonHelper.deleteMessage(event);
    }
}
