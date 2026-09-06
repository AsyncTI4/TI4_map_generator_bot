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

@UtilityClass
class ArbitrationAcd2ButtonHandler {

    static final String UNOWNED_PLANETS_KEY = "unownedPlanets";
    private static final String NEUTRAL_FACTION = "neutral";
    private static final String COULD_NOT_RESOLVE = "Could not resolve _Arbitration_.";

    @ButtonHandler("resolveArbitration")
    public static void resolveArbitration(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = getInfantryPlayerButtons(game, player);
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
        ButtonHelper.deleteMessage(event);
        String payload = buttonID.replace("arbitrationInfantry_", "");
        Player infantryPlayer = getPlayerFromFactionPrefix(game, payload);
        if (!isEligibleInfantryPlayer(player, infantryPlayer)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), COULD_NOT_RESOLVE);
            return;
        }

        List<Button> buttons = getPlanetOwnerButtons(game, player, infantryPlayer);
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
        ButtonHelper.deleteMessage(event);
        String payload = buttonID.replace("arbitrationOwner_", "");
        Player infantryPlayer = getPlayerFromFactionPrefix(game, payload);
        String ownerKey = getSuffixAfterFactionPrefix(payload);
        if (!isEligibleInfantryPlayer(player, infantryPlayer) || ownerKey == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), COULD_NOT_RESOLVE);
            return;
        }

        List<Button> buttons = getPlanetButtons(game, player, infantryPlayer, ownerKey);
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
        ButtonHelper.deleteMessage(event);
        String payload = buttonID.replace("arbitrationPlace_", "");
        Player infantryPlayer = getPlayerFromFactionPrefix(game, payload);
        String planetName = getSuffixAfterFactionPrefix(payload);
        Planet planet = planetName == null ? null : game.getUnitHolderFromPlanet(planetName);
        Tile tile = planetName == null ? null : game.getTileFromPlanet(planetName);
        if (!isEligibleInfantryPlayer(player, infantryPlayer)
                || planet == null
                || tile == null
                || !isCoexistenceTarget(game, planet, infantryPlayer)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), COULD_NOT_RESOLVE);
            return;
        }

        placeInfantryIntoCoexistence(game, event, tile, infantryPlayer, planetName);

        String message = player.getRepresentation() + " used _Arbitration_ to place 1 "
                + infantryPlayer.getRepresentationNoPing() + " infantry into coexistence on "
                + Helper.getPlanetRepresentation(planetName, game) + ".";
        announceToPlayerAndAffected(player, message, infantryPlayer, game.getPlanetOwner(planetName));
    }

    private static void placeInfantryIntoCoexistence(
            Game game, ButtonInteractionEvent event, Tile tile, Player infantryPlayer, String planetName) {
        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, infantryPlayer.getColor(), "inf " + planetName);
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);
    }

    private static void announceToPlayerAndAffected(Player player, String message, Player... affectedPlayers) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        for (Player affected : affectedPlayers) {
            if (affected != null
                    && affected != player
                    && !Objects.equals(player.getCorrectChannel(), affected.getCorrectChannel())) {
                MessageHelper.sendMessageToChannel(affected.getCorrectChannel(), message);
            }
        }
    }

    static List<Button> getInfantryPlayerButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player infantryPlayer : game.getRealPlayers()) {
            if (!isEligibleInfantryPlayer(player, infantryPlayer)
                    || getPlanetOwnerButtons(game, player, infantryPlayer).isEmpty()) {
                continue;
            }
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + "arbitrationInfantry_" + infantryPlayer.getFaction(),
                    "gray",
                    infantryPlayer));
        }
        return buttons;
    }

    static List<Button> getPlanetOwnerButtons(Game game, Player player, Player infantryPlayer) {
        List<Button> buttons = new ArrayList<>();
        for (Player owner : game.getRealPlayers()) {
            if (owner == infantryPlayer
                    || getPlanetButtons(game, player, infantryPlayer, owner.getFaction())
                            .isEmpty()) {
                continue;
            }
            buttons.add(FoWHelper.fogSafeTargetButton(
                    getOwnerButtonId(player, infantryPlayer, owner.getFaction()), "gray", owner));
        }

        if (!getPlanetButtons(game, player, infantryPlayer, UNOWNED_PLANETS_KEY).isEmpty()) {
            buttons.add(Buttons.gray(getOwnerButtonId(player, infantryPlayer, UNOWNED_PLANETS_KEY), "Unowned Planets"));
        }
        return buttons;
    }

    static List<Button> getPlanetButtons(Game game, Player player, Player infantryPlayer, String ownerKey) {
        List<String> planetNames = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (matchesOwnerCategory(game, planet, ownerKey) && isCoexistenceTarget(game, planet, infantryPlayer)) {
                    planetNames.add(planet.getName());
                }
            }
        }

        Collections.sort(planetNames);
        return planetNames.stream()
                .map(planetName -> Buttons.green(
                        player.factionButtonChecker() + "arbitrationPlace_" + infantryPlayer.getFaction() + "_"
                                + planetName,
                        Helper.getPlanetRepresentation(planetName, game)))
                .toList();
    }

    private static String getOwnerButtonId(Player player, Player infantryPlayer, String ownerKey) {
        return player.factionButtonChecker() + "arbitrationOwner_" + infantryPlayer.getFaction() + "_" + ownerKey;
    }

    private static boolean matchesOwnerCategory(Game game, Planet planet, String ownerKey) {
        Player controller = game.getPlanetOwner(planet.getName());
        if (UNOWNED_PLANETS_KEY.equals(ownerKey)) {
            return controller == null;
        }
        return controller != null && ownerKey.equals(controller.getFaction());
    }

    static boolean isCoexistenceTarget(Game game, Planet planet, Player infantryPlayer) {
        return !planet.isHomePlanet(game)
                && game.getPlanetOwner(planet.getName()) != infantryPlayer
                && hasCoexistencePartner(game, planet, infantryPlayer);
    }

    static boolean hasCoexistencePartner(Game game, Planet planet, Player infantryPlayer) {
        return neutralHasGroundForces(game, planet)
                || game.getRealPlayers().stream()
                        .anyMatch(other -> other != infantryPlayer && planet.hasGroundForces(other));
    }

    private static boolean neutralHasGroundForces(Game game, Planet planet) {
        Player neutral = game.getPlayerFromColorOrFaction(NEUTRAL_FACTION);
        return neutral != null && planet.hasGroundForces(neutral);
    }

    private static boolean isEligibleInfantryPlayer(Player player, Player infantryPlayer) {
        return infantryPlayer != null && infantryPlayer != player;
    }

    private static Player getPlayerFromFactionPrefix(Game game, String payload) {
        return game.getPlayerFromColorOrFaction(payload.split("_", 2)[0]);
    }

    private static String getSuffixAfterFactionPrefix(String payload) {
        String[] parts = payload.split("_", 2);
        return parts.length < 2 ? null : parts[1];
    }
}
