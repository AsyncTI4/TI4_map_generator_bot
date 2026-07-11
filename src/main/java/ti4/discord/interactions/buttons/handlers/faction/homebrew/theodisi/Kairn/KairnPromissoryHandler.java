package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kairn;

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
public class KairnPromissoryHandler {
    private static final String PN_ID = "thpnkairn";
    private static final String ATTACHMENT_TOKEN = "attachment_kairnoutpost.png";
    private static final String KAIRN_PN_ATTACH = "kairnPnAttach_";

    public static void offerArchaeologicalOutpostButtons(
            GenericInteractionCreateEvent event, Player player, Game game) {
        if (game == null
                || player == null
                || player.ownsPromissoryNote(PN_ID)
                || !player.getPromissoryNotesInPlayArea().contains(PN_ID)) {
            return;
        }

        List<String> legalPlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getPlanet(planetName);
            if (planet == null
                    || planet.isHomePlanet()
                    || planet.getAttachments().contains(ATTACHMENT_TOKEN)) {
                continue;
            }
            legalPlanets.add(planetName);
        }

        if (legalPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + ", there are no legal planets to attach _Archaeological Outpost_ to.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : legalPlanets) {
            Tile tile = game.getTileContainingPlanet(planetName);
            if (tile == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + KAIRN_PN_ATTACH + tile.getPosition() + "|" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", please choose a non-home planet to attach _Archaeological Outpost_ to.",
                buttons);
    }

    @ButtonHandler(KAIRN_PN_ATTACH)
    public static void resolveKairnPnAttach(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            return;
        }

        if (player.ownsPromissoryNote(PN_ID)
                || !player.getPromissoryNotesInPlayArea().contains(PN_ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(KAIRN_PN_ATTACH.length());
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

        Planet planet = tile.getPlanet(planetName);
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!player.containsPlanet(planetName)
                || planet.isHomePlanet()
                || planet.getAttachments().contains(ATTACHMENT_TOKEN)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        planet.addToken(ATTACHMENT_TOKEN);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " attached _Archaeological Outpost_ to "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
    }

    public static void offerArchaeologicalOutpostExplore(Player player, Game game, Tile tile) {
        if (player == null || game == null || tile == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (!player.canUsePlanet(planet.getName())
                    || !planet.getAttachments().contains(ATTACHMENT_TOKEN)) {
                continue;
            }

            List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
            if (planetButtons != null) {
                buttons.addAll(planetButtons);
            }
        }

        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.toString()
                            + ", you activated a system containing your _Archaeological Outpost_. Explore that planet.",
                    buttons);
        }
    }

    public static boolean planetHasArchaeologicalOutpost(Planet planet) {
        return planet != null && planet.getAttachments().contains(ATTACHMENT_TOKEN);
    }
}
