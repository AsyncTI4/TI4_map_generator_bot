package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Myrr;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

@UtilityClass
public class MyrrPromissoryHandler {
    private static final String FACTORY_LEASE_ATTACH = "myrrFactoryLeaseAttach_";

    public static void offerFactoryLeaseButtons(
            GenericInteractionCreateEvent event, Player player, Game game, String promissoryNoteId) {
        if (game == null || player == null) {
            return;
        }

        PromissoryNoteModel pnModel = Mapper.getPromissoryNote(promissoryNoteId);
        if (pnModel == null || pnModel.getAttachment().isEmpty()) {
            return;
        }

        String attachmentId = pnModel.getAttachment().get();
        if (!player.getPromissoryNotesInPlayArea().contains(promissoryNoteId)
                || attachmentAlreadyPlaced(game, attachmentId)) {
            return;
        }

        List<Button> buttons = getFactoryLeaseButtons(player, game, promissoryNoteId, attachmentId);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + ", there are no legal planets to attach _" + pnModel.getName() + "_ to.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", please choose a planet to attach _" + pnModel.getName() + "_ to.",
                buttons);
    }

    @ButtonHandler(FACTORY_LEASE_ATTACH)
    public static void resolveFactoryLeaseAttach(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            return;
        }

        String payload = buttonID.substring(FACTORY_LEASE_ATTACH.length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String promissoryNoteId = parts[0];
        String tilePosition = parts[1];
        String planetName = parts[2];

        PromissoryNoteModel pnModel = Mapper.getPromissoryNote(promissoryNoteId);
        if (pnModel == null || pnModel.getAttachment().isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String attachmentId = pnModel.getAttachment().get();
        if (!player.getPromissoryNotesInPlayArea().contains(promissoryNoteId)
                || attachmentAlreadyPlaced(game, attachmentId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(tilePosition);
        Planet planet = game.getPlanet(planetName);
        if (tile == null || planet == null || !isLegalFactoryLeaseTarget(player, game, planetName, attachmentId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        planet.addToken("attachment_" + attachmentId + ".png");
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " attached _" + pnModel.getName() + "_ to "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
    }

    private static List<Button> getFactoryLeaseButtons(
            Player player, Game game, String promissoryNoteId, String attachmentId) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getUniquePlanets()) {
            if (!isLegalFactoryLeaseTarget(player, game, planetName, attachmentId)) {
                continue;
            }

            Tile tile = game.getTileContainingPlanet(planetName);
            if (tile == null) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + FACTORY_LEASE_ATTACH + promissoryNoteId + "|" + tile.getPosition()
                            + "|" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        return buttons;
    }

    private static boolean isLegalFactoryLeaseTarget(Player player, Game game, String planetName, String attachmentId) {
        if (!player.containsPlanet(planetName)) {
            return false;
        }

        Planet planet = game.getPlanet(planetName);
        if (planet == null || planet.getAttachments().contains("attachment_" + attachmentId + ".png")) {
            return false;
        }

        return switch (attachmentId) {
            case "factoryleasei" -> "industrial".equalsIgnoreCase(planet.getOriginalPlanetType());
            case "factoryleaseh" -> "hazardous".equalsIgnoreCase(planet.getOriginalPlanetType());
            case "factoryleasec" -> "cultural".equalsIgnoreCase(planet.getOriginalPlanetType());
            default -> false;
        };
    }

    private static boolean attachmentAlreadyPlaced(Game game, String attachmentId) {
        String tokenName = "attachment_" + attachmentId + ".png";
        return game.getPlanetsInfo().values().stream()
                .anyMatch(planet -> planet.getAttachments().contains(tokenName));
    }
}
