package ti4.discord.interactions.buttons.handlers.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class NiflheimLegendaryButtonHandler {

    private static final String SELECT_TARGET = "niflheimSelectTarget_";
    private static final String DISCARD = "niflheimDiscard_";

    public static void offerHvergelmirsHaze(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!player.hasPlanet("niflheim")
                || player.getExhaustedPlanetsAbilities().contains("niflheim")) return;
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayersExcludingThis(player)) {
            if (target.getActionCards().isEmpty()) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_TARGET + target.getFaction(),
                    "Choose " + target.getFactionNameOrColor(),
                    target.getFactionEmoji()));
        }
        if (buttons.isEmpty()) return;
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you passed and may exhaust _Hvergelmir's Haze_ to choose a player to discard 1 action card. You and that player then draw 1 action card.",
                buttons);
    }

    @ButtonHandler(SELECT_TARGET)
    public static void selectTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_TARGET.length()));
        if (target == null
                || !player.isPassed()
                || !player.hasPlanet("niflheim")
                || player.getExhaustedPlanetsAbilities().contains("niflheim")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.exhaustPlanetAbility("niflheim");
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> actionCard : target.getActionCards().entrySet()) {
            buttons.add(Buttons.blue(
                    target.factionButtonChecker() + DISCARD + player.getFaction() + "_" + actionCard.getValue(),
                    "(" + actionCard.getValue() + ") "
                            + Mapper.getActionCard(actionCard.getKey()).getName(),
                    CardEmojis.getACEmoji(target)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", discard 1 action card due to "
                        + player.getRepresentationNoPing() + "'s _Hvergelmir's Haze_.",
                buttons);
    }

    @ButtonHandler(DISCARD)
    public static void discardActionCard(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(DISCARD.length()).split("_", 2);
        if (payload.length != 2) return;
        Player owner = game.getPlayerFromColorOrFaction(payload[0]);
        int actionCardIndex;
        try {
            actionCardIndex = Integer.parseInt(payload[1]);
        } catch (NumberFormatException e) {
            return;
        }
        String actionCardId = target.getActionCards().entrySet().stream()
                .filter(entry -> entry.getValue().equals(actionCardIndex))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (owner == null || actionCardId == null || !game.discardActionCard(target.getUserID(), actionCardIndex)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                target.getRepresentationNoPing() + " discarded _"
                        + Mapper.getActionCard(actionCardId).getName() + "_ due to _Hvergelmir's Haze_.");
        ActionCardHelper.sendActionCardInfo(game, target);
        drawForBoth(game, owner, target);
        ButtonHelper.deleteMessage(event);
    }

    private static void drawForBoth(Game game, Player owner, Player target) {
        ActionCardHelper.drawActionCards(owner, 1);
        ActionCardHelper.drawActionCards(target, 1);
    }
}
