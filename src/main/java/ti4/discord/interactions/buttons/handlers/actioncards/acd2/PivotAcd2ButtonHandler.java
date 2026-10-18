package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
class PivotAcd2ButtonHandler {

    @ButtonHandler("resolvePivot")
    public static void resolvePivot(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        sendPivotAcButtons(player, 3, 0);
    }

    @ButtonHandler("pivotDiscardAc_")
    public static void resolvePivotDiscardAc(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("pivotDiscardAc_", "");
        int separator = payload.indexOf('_');
        if (separator < 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int remaining;
        int numericalID;
        int discarded;
        try {
            remaining = Integer.parseInt(payload.substring(0, separator));
            String[] discardPayload = payload.substring(separator + 1).split("_", 2);
            numericalID = Integer.parseInt(discardPayload[0]);
            discarded = Integer.parseInt(discardPayload[1]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ActionCardHelper.discardAC(event, game, player, numericalID);
        ButtonHelper.deleteMessage(event);

        if (remaining > 1) {
            sendPivotAcButtons(player, remaining - 1, discarded + 1);
        } else {
            completePivotActionCardStep(player, discarded + 1);
        }
    }

    @ButtonHandler("pivotToSecret")
    public static void resolvePivotToSecret(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);
        try {
            completePivotActionCardStep(player, Integer.parseInt(buttonID.replace("pivotToSecret_", "")));
        } catch (NumberFormatException e) {
            sendPivotSecretStep(player);
        }
    }

    private static void sendPivotAcButtons(Player player, int remaining, int discarded) {
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards == null || actionCards.isEmpty()) {
            completePivotActionCardStep(player, discarded);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
            int numericalID = ac.getValue();
            String acName = Mapper.getActionCard(ac.getKey()).getName();
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + "pivotDiscardAc_" + remaining + "_" + numericalID + "_" + discarded,
                    "(" + numericalID + ") " + acName,
                    CardEmojis.getACEmoji(player)));
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "pivotToSecret_" + discarded, "Continue to Secret Objective step"));
        buttons.add(Buttons.red("deleteButtons", "Done"));
        String remainingText = remaining == 1 ? "1 discard remaining" : remaining + " discards remaining";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", discard up to 3 action cards for _Pivot_, then draw that many"
                        + " replacements (" + remainingText + ").",
                buttons);
    }

    private static void completePivotActionCardStep(Player player, int discarded) {
        if (discarded > 0) {
            ActionCardHelper.drawActionCards(player, discarded);
        }
        sendPivotSecretStep(player);
    }

    private static void sendPivotSecretStep(Player player) {
        List<Button> buttons = new ArrayList<>(SecretObjectiveHelper.getSODiscardButtonsWithSuffix(player, "redraw"));
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " has no secret objectives to discard for _Pivot_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Skip / Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", you may discard 1 secret objective to draw 1 for _Pivot_. A"
                        + " replacement is drawn automatically.",
                buttons);
    }
}
