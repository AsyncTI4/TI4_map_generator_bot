package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperStats;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
class BountyAcd2ButtonHandler {

    @ButtonHandler("resolveBounty")
    public static void resolveBounty(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperStats.gainTGs(event, game, player, 3, false);

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            String id = player.factionButtonChecker() + "bountyStep2_" + p2.getFaction();
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(id, p2.getColor()));
            } else {
                buttons.add(Buttons.gray(id, p2.getColor()).withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Decline Capture"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the player whose unit you destroyed to capture it with _Bounty_.",
                buttons);
    }

    @ButtonHandler("bountyStep2_")
    public static void bountyStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.replace("bountyStep2_", ""));
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Bounty_ for that player.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        // One button per distinct base unit type with a cost of 3 or more.
        Map<String, UnitModel> capturable = new LinkedHashMap<>();
        for (String unitId : p2.getUnitsOwned()) {
            UnitModel model = p2.getUnitByID(unitId);
            if (model != null && model.getCost() >= 3) {
                capturable.putIfAbsent(model.getBaseType(), model);
            }
        }

        ButtonHelper.deleteMessage(event);
        if (capturable.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    p2.getRepresentationNoPing() + " has no units with a cost of 3 or more for _Bounty_.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitModel> entry : capturable.entrySet()) {
            UnitModel model = entry.getValue();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "bountyCapture_" + p2.getFaction() + "_" + entry.getKey(),
                    "Capture " + model.getBaseType(),
                    model.getUnitEmoji()));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline Capture"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the unit you destroyed to capture with _Bounty_.",
                buttons);
    }

    @ButtonHandler("bountyCapture_")
    public static void bountyCapture(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("bountyCapture_", "");
        int separator = payload.indexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Bounty_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player p2 = game.getPlayerFromColorOrFaction(payload.substring(0, separator));
        String unit = payload.substring(separator + 1);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Bounty_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " captured a " + unit + " owned by " + p2.getRepresentationNoPing()
                        + " with _Bounty_.");
        ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, player, 1, unit, event);
    }
}
