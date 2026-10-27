package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.unit.monuments.TwilightsFallMonumentsButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class TnelisUnitHandler {
    private static final String DEPLOY = "tnelisBrDeploy_";
    private static final String DEPLOY_ON = "tnelisBrDeployOn_";
    private static final String DEPLOY_USED = "tnelisBrDeployUsed_";
    private static final String DEPLOY_COEXISTENCE = "tnelisBrDeployCoexistence_";
    private static final String DESTROY_STEALTH_CORPS_GROUND_FORCE = "destroyStealthCorpsGroundForce_";

    public static void offerStealthCorps(Game game, Player player, UnitHolder planet) {
        if (!player.hasAbility("stealth_insertion_br")) {
            return;
        }

        Tile tile = game.getTileFromPlanet(planet.getName());
        List<Button> buttons = getStealthCorpsGroundForceButtons(game, player, tile, planet);
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose up to 1 ground force to destroy with _Stealth Corps_.",
                buttons);
    }

    @ButtonHandler(DESTROY_STEALTH_CORPS_GROUND_FORCE)
    public static void destroyStealthCorpsGroundForce(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(DESTROY_STEALTH_CORPS_GROUND_FORCE.length()).split("\\|", 4);
        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder planet = tile == null || payload.length != 4 ? null : tile.getUnitHolderFromPlanet(payload[1]);
        Player target = payload.length == 4 ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        Units.UnitKey unit =
                payload.length == 4 ? Units.getUnitKey(payload[3], target == null ? null : target.getColor()) : null;
        UnitModel unitModel = target == null || unit == null ? null : target.getUnitFromUnitKey(unit);
        if (!player.hasAbility("stealth_insertion_br")
                || tile == null
                || planet == null
                || target == null
                || unitModel == null
                || !unitModel.getIsGroundForce()
                || planet.getUnitCount(unit) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyUnit(event, tile, game, unit, 1, planet, false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " destroyed 1 " + unit.humanReadableName() + " on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " with _Stealth Corps_.");
        if (target != player) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    "One of your " + unit.humanReadableName() + " on "
                            + Helper.getPlanetRepresentation(planet.getName(), game)
                            + " was destroyed by _Stealth Corps_.");
        }
        ButtonHelper.deleteMessage(event);
    }

    public static boolean canDeploy(Game game, Player player, Tile tile) {
        return tile != null
                && tile.getPosition().equals(game.getActiveSystem())
                && !tile.isHomeSystem(game)
                && player.hasUnit("tnelis_mech_br")
                && tile.getSpaceUnitHolder().getUnitCount(UnitType.Destroyer, player) > 0
                && ButtonHelperFactionSpecific.vortexButtonAvailable(
                        game, Units.getUnitKey(UnitType.Mech, player.getColor()))
                && !tile.getPosition().equals(game.getStoredValue(deployUsedKey(player)))
                && hasEligiblePlanet(game, player, tile);
    }

    @ButtonHandler(DEPLOY)
    public static void offerDeploy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(DEPLOY.length()));
        if (!canDeploy(game, player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = tile.getPlanetUnitHolders().stream()
                .filter(planet -> canDeployOnPlanet(game, player, tile, planet))
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + DEPLOY_ON + tile.getPosition() + "|" + planet.getName(),
                        "Deploy on " + Helper.getPlanetRepresentation(planet.getName(), game),
                        FactionEmojis.tnelis))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a planet to deploy Daedalon into coexistence.",
                buttons);

        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(DEPLOY_ON)
    public static void deploy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DEPLOY_ON.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null || payload.length != 2 ? null : tile.getUnitHolderFromPlanet(payload[1]);
        if (!canDeploy(game, player, tile) || planet == null || !canDeployOnPlanet(game, player, tile, planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(deployUsedKey(player), tile.getPosition());
        tile.addUnit(planet.getName(), Units.getUnitKey(UnitType.Mech, player.getColor()), 1);
        game.setStoredValue(deployCoexistenceKey(player), tile.getPosition() + "|" + planet.getName());
        ButtonHelperAbilities.resolveCoexistence(event, game, player, planet.getName());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " deployed 1 Daedalon into coexistence on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasEligiblePlanet(Game game, Player player, Tile tile) {
        return tile.getPlanetUnitHolders().stream().anyMatch(planet -> canDeployOnPlanet(game, player, tile, planet));
    }

    private static List<Button> getStealthCorpsGroundForceButtons(
            Game game, Player player, Tile tile, UnitHolder planet) {
        List<Button> buttons = new java.util.ArrayList<>();
        if (tile == null) {
            return buttons;
        }
        for (Player target : game.getRealPlayersNDummies()) {
            for (Units.UnitKey unit : planet.getUnitKeysForPlayer(target)) {
                UnitModel unitModel = target.getUnitFromUnitKey(unit);
                if (unitModel == null || !unitModel.getIsGroundForce() || planet.getUnitCount(unit) < 1) {
                    continue;
                }
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + DESTROY_STEALTH_CORPS_GROUND_FORCE + tile.getPosition() + "|"
                                + planet.getName() + "|" + target.getFaction() + "|" + unit.asyncID(),
                        "Destroy " + unit.humanReadableName() + " on "
                                + Helper.getPlanetRepresentation(planet.getName(), game),
                        unit.unitEmoji()));
            }
        }
        return buttons;
    }

    private static boolean canDeployOnPlanet(Game game, Player player, Tile tile, Planet planet) {
        return tile.getPosition().equals(game.getActiveSystem())
                && !TwilightsFallMonumentsButtonHandler.preventsCoexistence(game, tile)
                && ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet).stream()
                        .anyMatch(other -> other != player && !player.isPlayerMemberOfAlliance(other));
    }

    private static String deployUsedKey(Player player) {
        return DEPLOY_USED + player.getFaction();
    }

    public static boolean consumeDeployCoexistence(Game game, Player player, Tile tile, UnitHolder planet) {
        String key = deployCoexistenceKey(player);
        if (!game.getStoredValue(key).equals(tile.getPosition() + "|" + planet.getName())) {
            return false;
        }
        game.removeStoredValue(key);
        return true;
    }

    public static void clearDeployState(Game game, Player player) {
        game.removeStoredValue(deployUsedKey(player));
        game.removeStoredValue(deployCoexistenceKey(player));
    }

    private static String deployCoexistenceKey(Player player) {
        return DEPLOY_COEXISTENCE + player.getFaction();
    }
}
