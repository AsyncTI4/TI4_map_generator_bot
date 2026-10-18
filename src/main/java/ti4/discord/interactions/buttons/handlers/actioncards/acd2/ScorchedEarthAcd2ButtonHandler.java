package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.planet.PlanetRemove;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
class ScorchedEarthAcd2ButtonHandler {

    private static final int MAX_UNITS_DESTROYED = 2;

    @ButtonHandler("resolveScorchedEarth")
    public static void resolveScorchedEarth(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        if (activeTile != null) {
            for (Planet planet : activeTile.getPlanetUnitHolders()) {
                if (!planet.isHomePlanet(game) && hasOtherPlayersUnits(player, planet)) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "scorchedEarthPlanet_" + planet.getName(),
                            Helper.getPlanetRepresentation(planet.getName(), game)));
                }
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", there is no non-home planet in the active system with"
                            + " another player's units on it for _Scorched Earth_. Please resolve it manually.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the planet to target with _Scorched Earth_.",
                buttons);
    }

    @ButtonHandler("scorchedEarthPlanet_")
    public static void resolveScorchedEarthPlanet(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.replace("scorchedEarthPlanet_", "");
        ButtonHelper.deleteMessage(event);
        sendDestroyButtons(player, game, planetName, 0);
    }

    @ButtonHandler("scorchedEarthDestroy_")
    public static void resolveScorchedEarthDestroy(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("scorchedEarthDestroy_", "").split("_", 4);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 4) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Scorched Earth_.");
            return;
        }
        int destroyedSoFar = Integer.parseInt(parts[0]);
        UnitKey unitKey = Units.getUnitKey(parts[2], parts[1]);
        String planetName = parts[3];
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (unitKey == null || planet == null || tile == null || planet.getUnitCount(unitKey) < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find that unit to destroy for _Scorched Earth_.");
            sendDestroyButtons(player, game, planetName, destroyedSoFar);
            return;
        }

        DestroyUnitService.destroyUnit(event, tile, game, unitKey, 1, planet, true);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " destroyed 1 " + describeUnit(game, unitKey) + " on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with _Scorched Earth_.");

        int destroyed = destroyedSoFar + 1;
        if (destroyed >= MAX_UNITS_DESTROYED || !hasOtherPlayersUnits(player, planet)) {
            finishScorchedEarth(player, game, planetName);
            return;
        }
        sendDestroyButtons(player, game, planetName, destroyed);
    }

    @ButtonHandler("scorchedEarthDone_")
    public static void resolveScorchedEarthDone(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);
        finishScorchedEarth(player, game, buttonID.replace("scorchedEarthDone_", ""));
    }

    private static void sendDestroyButtons(Player player, Game game, String planetName, int destroyedSoFar) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Scorched Earth_.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : planet.getUnitKeys()) {
            if (player.unitBelongsToPlayer(unitKey)) {
                continue;
            }
            String id = player.factionButtonChecker() + "scorchedEarthDestroy_" + destroyedSoFar + "_"
                    + unitKey.colorID() + "_" + unitKey.asyncID() + "_" + planetName;
            buttons.add(Buttons.red(id, "Destroy " + describeUnit(game, unitKey), unitKey.unitEmoji()));
        }
        if (buttons.isEmpty()) {
            finishScorchedEarth(player, game, planetName);
            return;
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + "scorchedEarthDone_" + planetName, "Done"));
        int remaining = MAX_UNITS_DESTROYED - destroyedSoFar;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a unit on "
                        + Helper.getPlanetRepresentation(planetName, game) + " to destroy with _Scorched Earth_ ("
                        + remaining + " of " + MAX_UNITS_DESTROYED + " remaining).",
                buttons);
    }

    private static void finishScorchedEarth(Player player, Game game, String planetName) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null || planet.hasUnits()) {
            return;
        }
        for (Player owner : game.getPlayers().values()) {
            if (owner.hasPlanet(planetName)) {
                owner.removePlanet(planetName);
                PlanetRemove.removePlayerControlToken(owner, planet);
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                Helper.getPlanetRepresentation(planetName, game) + " contains no units, so its planet card has been"
                        + " returned to the planet card deck by _Scorched Earth_.");
    }

    private static boolean hasOtherPlayersUnits(Player player, Planet planet) {
        return planet.getUnitKeys().stream().anyMatch(unitKey -> !player.unitBelongsToPlayer(unitKey));
    }

    private static String describeUnit(Game game, UnitKey unitKey) {
        Player owner = game.getPlayerFromColorOrFaction(unitKey.getColor());
        String ownerName = owner == null ? unitKey.getColor() : owner.getFactionNameOrColor();
        return ownerName + " " + unitKey.humanReadableName();
    }
}
