package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

/**
 * _Arbitration_: "Place 1 infantry from another player's reinforcements into coexistence on a
 * non-home planet."
 *
 * <p>Three picks, in this order: whose reinforcements supply the infantry, whose planets to choose
 * among, then the planet itself. The infantry player comes first because they are the one thing the
 * card actually constrains — every step after that filters against them, and the owner step drops
 * them from its own list, since placing their infantry onto their own planet isn't coexistence.
 */
@UtilityClass
class ArbitrationAcd2ButtonHandler {

    /** Owner-step bucket for planets no player controls, kept apart from the per-faction buckets. */
    private static final String UNOWNED = "unownedPlanets";

    @ButtonHandler("resolveArbitration")
    public static void resolveArbitration(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getArbitrationInfantryButtons(game, player);
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", there is nowhere to place an infantry into coexistence for _Arbitration_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose whose reinforcements the _Arbitration_ infantry comes from.",
                buttons);
    }

    @ButtonHandler("arbitrationInfantry_")
    public static void resolveArbitrationInfantry(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player infantryPlayer = game.getPlayerFromColorOrFaction(buttonID.replace("arbitrationInfantry_", ""));
        ButtonHelper.deleteMessage(event);
        if (infantryPlayer == null || infantryPlayer == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Arbitration_.");
            return;
        }

        List<Button> buttons = getArbitrationOwnerButtons(game, player, infantryPlayer);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", there is nowhere to place "
                            + infantryPlayer.getRepresentationNoPing() + "'s infantry for _Arbitration_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose whose planets to place "
                        + infantryPlayer.getRepresentationNoPing() + "'s infantry among for _Arbitration_.",
                buttons);
    }

    @ButtonHandler("arbitrationOwner_")
    public static void resolveArbitrationOwner(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        // "<infantryFaction>_<ownerKey>" - neither half contains an underscore, so one split is enough.
        String[] parts = buttonID.replace("arbitrationOwner_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player infantryPlayer = game.getPlayerFromColorOrFaction(parts[0]);
        if (infantryPlayer == null || infantryPlayer == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Arbitration_.");
            return;
        }

        List<Button> buttons = getArbitrationPlanetButtons(game, player, infantryPlayer, parts[1]);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible planets for _Arbitration_ in that category.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose the planet " + infantryPlayer.getRepresentationNoPing()
                        + " will place 1 infantry into coexistence on for _Arbitration_.",
                buttons);
    }

    @ButtonHandler("arbitrationPlace_")
    public static void resolveArbitrationPlace(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        // "<infantryFaction>_<planet>" - faction ids carry no underscore, planet names can, so split once.
        String[] parts = buttonID.replace("arbitrationPlace_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        String planetName = parts[1];
        Player infantryPlayer = game.getPlayerFromColorOrFaction(parts[0]);
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Player controller = game.getPlanetOwner(planetName);
        // The builders below already apply every one of these, but a blind-typed id never went through them.
        if (infantryPlayer == null
                || infantryPlayer == player
                || tile == null
                || planet == null
                || planet.isHomePlanet(game)
                || controller == infantryPlayer
                || !hasCoexistencePartner(game, planet, infantryPlayer)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Arbitration_.");
            return;
        }

        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, infantryPlayer.getColor(), "inf " + planetName);
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);

        String message = player.getRepresentation() + " used _Arbitration_ to place 1 "
                + infantryPlayer.getRepresentationNoPing() + " infantry into coexistence on "
                + Helper.getPlanetRepresentation(planetName, game) + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        // The infantry player lost a unit out of reinforcements and the controller now shares a planet, so
        // both need telling - unless they already read the channel this went to. controller can never be
        // the infantry player here, so these two sends can't land on one another.
        List<Player> affected = new ArrayList<>();
        affected.add(infantryPlayer);
        if (controller != null) {
            affected.add(controller);
        }
        for (Player other : affected) {
            if (other != player && !Objects.equals(player.getCorrectChannel(), other.getCorrectChannel())) {
                MessageHelper.sendMessageToChannel(other.getCorrectChannel(), message);
            }
        }
    }

    /** Step 1: every other player whose infantry has somewhere legal to go. */
    private static List<Button> getArbitrationInfantryButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player infantryPlayer : game.getRealPlayers()) {
            if (infantryPlayer == player
                    || getArbitrationOwnerButtons(game, player, infantryPlayer).isEmpty()) {
                continue;
            }
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + "arbitrationInfantry_" + infantryPlayer.getFaction(),
                    "gray",
                    infantryPlayer));
        }
        return buttons;
    }

    /**
     * Step 2: whose holdings to browse. The card never says whose planet it has to be, so the player
     * playing it is included - they may well want to invite somebody onto a planet of their own. Only
     * the infantry player is left out, since nobody coexists with themselves.
     */
    private static List<Button> getArbitrationOwnerButtons(Game game, Player player, Player infantryPlayer) {
        List<Button> buttons = new ArrayList<>();
        for (Player owner : game.getRealPlayers()) {
            if (owner == infantryPlayer
                    || getArbitrationPlanetButtons(game, player, infantryPlayer, owner.getFaction())
                            .isEmpty()) {
                continue;
            }
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + "arbitrationOwner_" + infantryPlayer.getFaction() + "_"
                            + owner.getFaction(),
                    "gray",
                    owner));
        }

        if (!getArbitrationPlanetButtons(game, player, infantryPlayer, UNOWNED).isEmpty()) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "arbitrationOwner_" + infantryPlayer.getFaction() + "_" + UNOWNED,
                    "Unowned Planets"));
        }
        return buttons;
    }

    /** Step 3: the non-home planets in that bucket the infantry player could coexist on. */
    private static List<Button> getArbitrationPlanetButtons(
            Game game, Player player, Player infantryPlayer, String ownerKey) {
        List<String> planets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (isArbitrationPlanetInCategory(game, planet, infantryPlayer, ownerKey)) {
                    planets.add(planet.getName());
                }
            }
        }

        Collections.sort(planets);
        return planets.stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "arbitrationPlace_" + infantryPlayer.getFaction() + "_"
                                + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
    }

    private static boolean isArbitrationPlanetInCategory(
            Game game, Planet planet, Player infantryPlayer, String ownerKey) {
        if (planet.isHomePlanet(game)) {
            return false;
        }
        Player controller = game.getPlanetOwner(planet.getName());
        if (UNOWNED.equals(ownerKey)) {
            if (controller != null) {
                return false;
            }
        } else if (controller == null || controller == infantryPlayer || !ownerKey.equals(controller.getFaction())) {
            return false;
        }
        return hasCoexistencePartner(game, planet, infantryPlayer);
    }

    /**
     * Coexistence needs somebody already standing on the planet to coexist with, and it can't be the
     * infantry player themselves - placing their infantry next to their own is just reinforcing.
     */
    private static boolean hasCoexistencePartner(Game game, Planet planet, Player infantryPlayer) {
        // Deliberately not game.getNeutral(), which creates the neutral player as a side effect. If there
        // isn't one yet then there are no neutral units on the board to coexist with anyway.
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        if (neutral != null && planet.hasGroundForces(neutral)) {
            return true;
        }
        return game.getRealPlayers().stream()
                .anyMatch(other -> other != infantryPlayer && planet.hasGroundForces(other));
    }
}
