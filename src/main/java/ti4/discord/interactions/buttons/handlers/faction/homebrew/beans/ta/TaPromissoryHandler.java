package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

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
import ti4.message.MessageHelper;

@UtilityClass
public class TaPromissoryHandler {
    private static final String PN_ID = "bepnta";
    private static final String ASE_ATTACHMENT_TOKEN = "attachment_ase.png";
    private static final String TA_PN_ATTACH = "taPnAseAttach_";

    public static boolean hasLegalAdvancedStructuralEngineeringTargets(Player player, Game game) {
        if (game == null || player == null || player.ownsPromissoryNote(PN_ID)) {
            return false;
        }

        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || planet.isHomePlanet()
                    || planet.getAttachments().contains(ASE_ATTACHMENT_TOKEN)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static void offerAdvancedStructuralEngineeringButtons(
            GenericInteractionCreateEvent event, Player player, Game game) {
        if (game == null
                || player == null
                || player.ownsPromissoryNote(PN_ID)
                || !player.getPromissoryNotesInPlayArea().contains(PN_ID)) {
            return;
        }

        List<String> legalPlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || planet.isHomePlanet()
                    || planet.getAttachments().contains(ASE_ATTACHMENT_TOKEN)) {
                continue;
            }

            legalPlanets.add(planetName);
        }

        if (legalPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", there are no legal planets to play _Advanced Structural Engineering_ on.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : legalPlanets) {
            Tile tilePos = game.getTileFromPlanet(planetName);
            if (tilePos == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + TA_PN_ATTACH + tilePos.getPosition() + "|" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please select a non-home planet to attach _Advanced Structural Engineering_ to.",
                buttons);
    }

    @ButtonHandler(TA_PN_ATTACH)
    public static void resolveTaPnAttach(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            return;
        }

        if (player.ownsPromissoryNote(PN_ID)
                || !player.getPromissoryNotesInPlayArea().contains(PN_ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(TA_PN_ATTACH.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePosition = parts[0];
        String planetName = parts[1];

        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!player.getPlanets().contains(planetName)
                || planet.isHomePlanet()
                || planet.getAttachments().contains(ASE_ATTACHMENT_TOKEN)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        planet.addToken(ASE_ATTACHMENT_TOKEN);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " attached _Advanced Structural Engineering_ to "
                        + Helper.getPlanetRepresentation(planetName, game));
    }

    public static boolean planetHasAdvancedStructuralEngineering(Planet planet) {
        return planet != null && planet.getAttachments().contains(ASE_ATTACHMENT_TOKEN);
    }
}
