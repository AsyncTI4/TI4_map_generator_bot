package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;

@UtilityClass
public class ExtensionRefitLLButtonHandler {
    private static final String RESOLVE_EXTENSION_REFIT = "resolveExtensionRefit";
    private static final String SELECT_SOURCE = "selectExtensionRefitSource_";
    private static final String SELECT_ATTACHMENT = "selectExtensionRefitAttachment_";
    private static final String SELECT_DESTINATION = "selectExtensionRefitDestination_";
    private static final String STATE = "extensionRefit_";

    @ButtonHandler(RESOLVE_EXTENSION_REFIT)
    public static void resolveExtensionRefit(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(STATE + player.getFaction());

        List<Button> buttons = getSourceButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a planet from which to move an attachment with _Extension Refit_.";

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no controlled planets with attachments for _Extension Refit_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String prefix = player.factionButtonChecker() + SELECT_SOURCE;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SOURCE)
    public static void selectExtensionRefitSource(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSourceButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a planet from which to move an attachment with _Extension Refit_.";
        String prefix = player.factionButtonChecker() + SELECT_SOURCE;

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String sourcePlanetName = buttonID.substring(SELECT_SOURCE.length());
        Planet sourcePlanet = game.getPlanetsInfo().get(sourcePlanetName);

        if (sourcePlanet == null
                || !player.getPlanetsAllianceMode().contains(sourcePlanetName)
                || sourcePlanet.getAttachments().isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(STATE + player.getFaction(), sourcePlanetName);
        showAttachmentButtons(event, game, player, sourcePlanet);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_ATTACHMENT)
    public static void selectExtensionRefitAttachment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Planet sourcePlanet = getSourcePlanet(game, player);
        if (sourcePlanet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getAttachmentButtons(game, player, sourcePlanet);
        String message = player.getRepresentationNoPing()
                + ", choose the attachment to move from "
                + Helper.getPlanetRepresentation(sourcePlanet.getName(), game)
                + " with _Extension Refit_.";
        String prefix = player.factionButtonChecker() + SELECT_ATTACHMENT;

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        int attachmentIndex = NumberUtils.toInt(buttonID.substring(SELECT_ATTACHMENT.length()), -1);
        List<String> attachments = sourcePlanet.getAttachments();

        if (attachmentIndex < 0 || attachmentIndex >= attachments.size()) {
            game.removeStoredValue(STATE + player.getFaction());
            ButtonHelper.deleteMessage(event);
            return;
        }

        String attachment = attachments.get(attachmentIndex);
        game.setStoredValue(STATE + player.getFaction(), sourcePlanet.getName() + "|" + attachment);

        showDestinationButtons(event, game, player, sourcePlanet.getName(), attachment);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_DESTINATION)
    public static void selectExtensionRefitDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 2);
        String destinationPlanetName = buttonID.substring(SELECT_DESTINATION.length());

        Planet sourcePlanet = state.length == 2 ? game.getPlanetsInfo().get(state[0]) : null;
        Planet destinationPlanet = game.getPlanetsInfo().get(destinationPlanetName);
        String attachment = state.length == 2 ? state[1] : "";

        if (sourcePlanet == null
                || destinationPlanet == null
                || attachment.isBlank()
                || !player.getPlanetsAllianceMode().contains(sourcePlanet.getName())
                || !player.getPlanetsAllianceMode().contains(destinationPlanetName)
                || !sourcePlanet.getAttachments().contains(attachment)
                || sourcePlanet.getName().equals(destinationPlanetName)
                || destinationPlanet.isHomePlanet(game)
                || destinationPlanet.isLegendary()) {
            game.removeStoredValue(STATE + player.getFaction());
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getDestinationButtons(game, player, sourcePlanet.getName());
        String message = player.getRepresentationNoPing()
                + ", choose a non-home, non-legendary planet to receive "
                + getAttachmentName(attachment)
                + " with _Extension Refit_.";
        String prefix = player.factionButtonChecker() + SELECT_DESTINATION;

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        sourcePlanet.removeToken(attachment);
        destinationPlanet.addToken(attachment);
        game.removeStoredValue(STATE + player.getFaction());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " moved "
                        + getAttachmentName(attachment)
                        + " from "
                        + Helper.getPlanetRepresentation(sourcePlanet.getName(), game)
                        + " to "
                        + Helper.getPlanetRepresentation(destinationPlanetName, game)
                        + " with _Extension Refit_.");

        ButtonHelper.deleteMessage(event);
    }

    public static void clearExtensionRefit(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(STATE + player.getFaction());
        }
    }

    private static void showAttachmentButtons(
            ButtonInteractionEvent event, Game game, Player player, Planet sourcePlanet) {
        List<Button> buttons = getAttachmentButtons(game, player, sourcePlanet);
        String message = player.getRepresentationNoPing()
                + ", choose the attachment to move from "
                + Helper.getPlanetRepresentation(sourcePlanet.getName(), game)
                + " with _Extension Refit_.";
        String prefix = player.factionButtonChecker() + SELECT_ATTACHMENT;

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    private static void showDestinationButtons(
            ButtonInteractionEvent event, Game game, Player player, String sourcePlanetName, String attachment) {
        List<Button> buttons = getDestinationButtons(game, player, sourcePlanetName);

        if (buttons.isEmpty()) {
            game.removeStoredValue(STATE + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no controlled non-home, non-legendary planet to receive "
                            + getAttachmentName(attachment)
                            + " with _Extension Refit_.");
            return;
        }

        String message = player.getRepresentationNoPing()
                + ", choose a non-home, non-legendary planet to receive "
                + getAttachmentName(attachment)
                + " with _Extension Refit_.";
        String prefix = player.factionButtonChecker() + SELECT_DESTINATION;

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    private static List<Button> getSourceButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();

        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null || planet.getAttachments().isEmpty()) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_SOURCE + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }

        return buttons;
    }

    private static List<Button> getAttachmentButtons(Game game, Player player, Planet sourcePlanet) {
        List<Button> buttons = new ArrayList<>();
        List<String> attachments = sourcePlanet.getAttachments();

        for (int index = 0; index < attachments.size(); index++) {
            String attachment = attachments.get(index);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_ATTACHMENT + index,
                    "Move " + getAttachmentName(attachment)));
        }

        return buttons;
    }

    private static List<Button> getDestinationButtons(Game game, Player player, String sourcePlanetName) {
        List<Button> buttons = new ArrayList<>();

        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null
                    || planetName.equals(sourcePlanetName)
                    || planet.isHomePlanet(game)
                    || planet.isLegendary()) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_DESTINATION + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }

        return buttons;
    }

    private static Planet getSourcePlanet(Game game, Player player) {
        String sourcePlanetName = game.getStoredValue(STATE + player.getFaction());
        Planet sourcePlanet = game.getPlanetsInfo().get(sourcePlanetName);

        if (sourcePlanet == null
                || !player.getPlanetsAllianceMode().contains(sourcePlanetName)
                || sourcePlanet.getAttachments().isEmpty()) {
            return null;
        }

        return sourcePlanet;
    }

    private static String getAttachmentName(String attachment) {
        AttachmentModel attachmentModel = Mapper.getAttachmentInfo(attachment);
        return attachmentModel == null ? attachment : "_" + attachmentModel.getName() + "_";
    }
}
