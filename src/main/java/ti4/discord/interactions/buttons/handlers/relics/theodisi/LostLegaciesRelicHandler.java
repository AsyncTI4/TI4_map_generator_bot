package ti4.discord.interactions.buttons.handlers.relics.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class LostLegaciesRelicHandler {
    private static final String USE_EBOON = "useEconomicBoon";
    private static final String USE_NBOON = "useNaturesBoon_";
    private static final String CHOOSE_NBOON_PLANET = "chooseNaturesBoonPlanet_";

    public static Button getEconomicBoonCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_EBOON, "Ready Planet with Economic Boon", CardEmojis.RelicCard);
    }

    public static Button getNaturesBoonSpendButton(Player player, String whatIsItFor) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_NBOON + whatIsItFor, "Use Nature's Boon", CardEmojis.RelicCard);
    }

    @ButtonHandler(USE_EBOON)
    public static void resolveEconomicBoon(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasRelicReady("economicboon")) {
            return;
        }

        List<Button> buttons = Helper.getPlanetRefreshButtons(player, game);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        player.addExhaustedRelic("economicboon");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the exhausted planet you wish to ready with _Economic Boon_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_NBOON)
    public static void offerNaturesBoonPlanets(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("naturesboon")) {
            return;
        }

        String whatIsItFor = buttonID.substring(USE_NBOON.length());
        String paymentMessageId = event.getMessageId();
        List<Button> buttons = getNaturesBoonPlanetButtons(player, game, whatIsItFor, paymentMessageId);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", spend a planet before using _Nature's Boon_.");
            return;
        }

        String message =
                player.getRepresentation() + ", choose a planet already spent for this payment with _Nature's Boon_.";
        String buttonPrefix =
                player.factionButtonChecker() + CHOOSE_NBOON_PLANET + whatIsItFor + "|" + paymentMessageId + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(CHOOSE_NBOON_PLANET)
    public static void resolveNaturesBoon(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("naturesboon")) {
            return;
        }

        String payload = buttonID.substring(CHOOSE_NBOON_PLANET.length());
        String[] data = payload.split("\\|", 3);
        if (data.length != 3) {
            return;
        }
        String whatIsItFor = data[0];
        String paymentMessageId = data[1];
        String planetName = data[2];
        List<Button> buttons = getNaturesBoonPlanetButtons(player, game, whatIsItFor, paymentMessageId);
        String message =
                player.getRepresentation() + ", choose a planet already spent for this payment with _Nature's Boon_.";
        String buttonPrefix =
                player.factionButtonChecker() + CHOOSE_NBOON_PLANET + whatIsItFor + "|" + paymentMessageId + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        if (!player.getSpentThingsThisWindow().contains(planetName)
                || !player.getExhaustedPlanets().contains(planetName)
                || !game.getPlanetsInfo().containsKey(planetName)) {
            return;
        }

        player.addExhaustedRelic("naturesboon");
        player.addSpentThing("naturesboon_" + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted _Nature's Boon_ for "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        event.getChannel()
                .retrieveMessageById(paymentMessageId)
                .queue(
                        paymentMessage -> {
                            List<Button> paymentButtons = new ArrayList<>();
                            for (ActionRow row :
                                    paymentMessage.getComponentTree().findAll(ActionRow.class)) {
                                for (ActionRowChildComponentUnion component : row.getComponents()) {
                                    if (component instanceof Button button
                                            && (button.getCustomId() == null
                                                    || !button.getCustomId().contains(USE_NBOON))) {
                                        paymentButtons.add(button);
                                    }
                                }
                            }
                            paymentMessage
                                    .editMessage(Helper.buildSpentThingsMessage(player, game, whatIsItFor))
                                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(paymentButtons))
                                    .queue(Consumers.nop(), BotLogger::catchRestError);
                        },
                        BotLogger::catchRestError);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getNaturesBoonPlanetButtons(
            Player player, Game game, String whatIsItFor, String paymentMessageId) {
        List<Button> buttons = new ArrayList<>();
        for (String spentThing : player.getSpentThingsThisWindow()) {
            if (!player.getExhaustedPlanets().contains(spentThing)
                    || !game.getPlanetsInfo().containsKey(spentThing)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker()
                            + CHOOSE_NBOON_PLANET
                            + whatIsItFor
                            + "|"
                            + paymentMessageId
                            + "|"
                            + spentThing,
                    Helper.getPlanetRepresentation(spentThing, game)));
        }
        return buttons;
    }
}
