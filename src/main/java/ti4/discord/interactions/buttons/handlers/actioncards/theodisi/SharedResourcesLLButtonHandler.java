package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class SharedResourcesLLButtonHandler {
    private static final String RESOLVE = "resolveSharedResources";
    private static final String SOURCE = "sharedResourcesSource_";
    private static final String DONOR = "sharedResourcesDonor_";
    private static final String STATE = "sharedResources_";

    @ButtonHandler(RESOLVE)
    public static void resolveSharedResources(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getSourceButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no exhausted controlled planet in the current payment for _Shared Resources_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = player.getRepresentationNoPing() + ", choose the planet you exhausted for _Shared Resources_.";
        MessageHelper.editMessageWithButtons(
                event, message, NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SOURCE, 0));
    }

    @ButtonHandler(SOURCE)
    public static void selectSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSourceButtons(game, player);
        String message = player.getRepresentationNoPing() + ", choose the planet you exhausted for _Shared Resources_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + SOURCE, buttonID)) {
            return;
        }

        String source = buttonID.substring(SOURCE.length());
        if (!getExhaustedSpentPlanets(game, player).contains(source)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible.");
            return;
        }
        List<Button> donorButtons = getDonorButtons(game, player, source);
        if (donorButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You control no other planet to use for _Shared Resources_.");
            return;
        }
        String donorMessage = player.getRepresentationNoPing()
                + ", choose the planet whose resource value " + Helper.getPlanetRepresentation(source, game)
                + " will use for _Shared Resources_.";
        MessageHelper.editMessageWithButtons(
                event,
                donorMessage,
                NewStuffHelper.buttonPagination(donorButtons, player.factionButtonChecker() + DONOR + source + "|", 0));
    }

    @ButtonHandler(DONOR)
    public static void selectDonor(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DONOR.length()).split("\\|", 2);
        if (payload.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That selection is no longer eligible.");
            return;
        }
        String source = payload[0];
        List<Button> buttons = getDonorButtons(game, player, source);
        String message = player.getRepresentationNoPing()
                + ", choose the planet whose resource value " + Helper.getPlanetRepresentation(source, game)
                + " will use for _Shared Resources_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + DONOR + source + "|",
                buttonID)) {
            return;
        }

        String donor = payload[1];
        Planet donorPlanet = game.getPlanetsInfo().get(donor);
        if (!getExhaustedSpentPlanets(game, player).contains(source)
                || source.equals(donor)
                || donorPlanet == null
                || !player.hasPlanet(donor)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible.");
            return;
        }
        game.setStoredValue(STATE + player.getFaction(), source + "|" + donor);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " is treating " + Helper.getPlanetRepresentation(source, game)
                        + " as having " + donorPlanet.getResources() + " resource"
                        + (donorPlanet.getResources() == 1 ? "" : "s") + " from "
                        + Helper.getPlanetRepresentation(donor, game) + " for _Shared Resources_.");
        ButtonHelper.deleteMessage(event);
    }

    public static Planet getResourceDonor(Game game, Player player, String source) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 2);
        if (state.length != 2 || !source.equals(state[0]) || !player.hasPlanet(state[1])) return null;
        return game.getPlanetsInfo().get(state[1]);
    }

    public static void clear(Game game, Player player) {
        game.removeStoredValue(STATE + player.getFaction());
    }

    private static List<Button> getSourceButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : getExhaustedSpentPlanets(game, player)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SOURCE + planet,
                    "Use " + Helper.getPlanetRepresentation(planet, game)));
        }
        return buttons;
    }

    private static List<Button> getDonorButtons(Game game, Player player, String source) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            if (!planet.equals(source) && game.getPlanetsInfo().containsKey(planet)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + DONOR + source + "|" + planet,
                        "Use " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        return buttons;
    }

    private static List<String> getExhaustedSpentPlanets(Game game, Player player) {
        return player.getSpentThingsThisWindow().stream()
                .filter(player::hasPlanet)
                .filter(player.getExhaustedPlanets()::contains)
                .filter(game.getPlanetsInfo()::containsKey)
                .distinct()
                .toList();
    }
}
