package ti4.service.objectives;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
public class DiscardSecretService {

    public static void discardSO(Player player, int SOID, Game game) {
        String soIDString = "";
        for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(SOID)) {
                soIDString = so.getKey();
            }
        }
        boolean removed = game.discardSecretObjective(player.getUserID(), SOID);
        if (!removed) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "No such Secret Objective ID found, please retry");
            return;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "SO Discarded");

        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        if (!soIDString.isEmpty()) {
            String msg = "You discarded the SO " + Mapper.getSecretObjective(soIDString).getName() + ". If this was an accident, you can get it back with the below button. This will tell everyone that you made a mistake discarding and are picking back up the secret.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray("drawSpecificSO_" + soIDString, "Retrieve " + Mapper.getSecretObjective(soIDString).getName()));
            buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
        }

        handleSecretObjectiveDrawOrder(game, player);
    }

    private static void handleSecretObjectiveDrawOrder(Game game, Player player) {
        String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        if (game.getStoredValue(key2)
            .contains(player.getFaction() + "*")) {
            game.setStoredValue(key2,
                game.getStoredValue(key2)
                    .replace(player.getFaction() + "*", ""));
        }
        if (!game.getStoredValue(key)
            .contains(player.getFaction() + "*")) {
            game.setStoredValue(key,
                game.getStoredValue(key)
                    + player.getFaction() + "*");
        }
        if (game.getStoredValue(key3)
            .contains(player.getFaction() + "*")) {
            game.setStoredValue(key3,
                game.getStoredValue(key3)
                    .replace(player.getFaction() + "*", ""));
            Helper.resolveQueue(game);
        }
    }
}
