package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.combat.CancelGroundHitsButtonId;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class InnovativeOfficerBrActionCardHandler {
    private static final String RESOLVE = "resolveInnovativeOfficerBr";
    private static final String SELECT_SHIP = "innovativeOfficerShip_";
    private static final String SELECT_PLANET = "innovativeOfficerPlanet_";
    private static final String SELECT_TARGET = "innovativeOfficerTarget_";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Innovative Officer"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolveInnovativeOfficer(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeSystem = getActiveSystem(game);
        List<Button> buttons = getShipButtons(player, activeSystem);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no non-fighter ship in the active system for _Innovative Officer_.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose a non-fighter ship to remove for _Innovative Officer_:",
                buttons);
    }

    @ButtonHandler(SELECT_SHIP)
    public static void selectShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String shipId = buttonID.substring(SELECT_SHIP.length());
        Tile activeSystem = getActiveSystem(game);
        if (getEligibleShip(player, activeSystem, shipId) == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = getPlanetButtons(game, player, activeSystem, shipId);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no planet with another player's units in the active system for _Innovative Officer_.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose the planet where _Innovative Officer_ will produce hits:",
                buttons);
    }

    @ButtonHandler(SELECT_PLANET)
    public static void selectPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_PLANET.length()).split("\\|", 2);
        Tile activeSystem = getActiveSystem(game);
        Planet planet =
                payload.length == 2 && activeSystem != null ? activeSystem.getUnitHolderFromPlanet(payload[1]) : null;
        if (payload.length != 2 || getEligibleShip(player, activeSystem, payload[0]) == null || planet == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Player> targets = getTargetPlayers(game, player, planet);
        if (targets.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        if (targets.size() == 1) {
            resolveHits(event, game, player, activeSystem, planet, targets.getFirst(), payload[0]);
            return;
        }
        List<Button> buttons = targets.stream()
                .map(target -> FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker()
                                + SELECT_TARGET
                                + payload[0]
                                + "|"
                                + planet.getName()
                                + "|"
                                + target.getFaction(),
                        "gray",
                        target,
                        player))
                .toList();
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose whose units on "
                        + planet.getRepresentation(game)
                        + " will be affected by _Innovative Officer_:",
                buttons);
    }

    @ButtonHandler(SELECT_TARGET)
    public static void selectTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_TARGET.length()).split("\\|", 3);
        Tile activeSystem = getActiveSystem(game);
        Planet planet =
                payload.length == 3 && activeSystem != null ? activeSystem.getUnitHolderFromPlanet(payload[1]) : null;
        Player target = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        if (payload.length != 3
                || getEligibleShip(player, activeSystem, payload[0]) == null
                || planet == null
                || target == null
                || !getTargetPlayers(game, player, planet).contains(target)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        resolveHits(event, game, player, activeSystem, planet, target, payload[0]);
    }

    private static void resolveHits(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            Tile activeSystem,
            Planet planet,
            Player target,
            String shipId) {
        UnitKey shipKey = getEligibleShip(player, activeSystem, shipId);
        UnitModel ship = shipKey == null
                ? null
                : player.getPriorityUnitByAsyncID(shipKey.asyncID(), activeSystem.getSpaceUnitHolder());
        if (shipKey == null || ship == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        int hits = Math.round(ship.getCost()) + 1;
        RemoveUnitService.removeUnit(
                event, activeSystem, game, player, activeSystem.getSpaceUnitHolder(), shipKey.unitType(), 1);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " removed 1 " + ship.getName() + " and produced " + hits + " hit"
                        + (hits == 1 ? "" : "s") + " against "
                        + FoWHelper.identityOrColorIfCanSeeStats(game, target, player, target.getRepresentationNoPing())
                        + "'s units on "
                        + planet.getRepresentation(game) + " with _Innovative Officer_.");
        offerOptionalAssignedHit(event, game, player, activeSystem, planet, target);
        offerRemainingHitAssignment(event, game, player, activeSystem, planet, target, hits - 1);
    }

    private static void offerOptionalAssignedHit(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, Planet planet, Player target) {
        List<Button> buttons = getAssignedHitButtons(game, player, tile, planet, target);
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS_GRAY);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may assign 1 hit from _Innovative Officer_:",
                buttons);
    }

    private static void offerRemainingHitAssignment(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, Planet planet, Player target, int hits) {
        if (hits < 1) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        String targetButtonPrefix = target.isNeutral() ? target.dummyPlayerSpoof() : target.factionButtonChecker();
        buttons.add(Buttons.green(
                targetButtonPrefix + AutoAssignGroundHitsButtonIds.format(planet.getName(), hits),
                "Auto-Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_groundcombat",
                "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
        buttons.add(
                Buttons.gray(CancelGroundHitsButtonId.of(tile.getPosition(), hits, planet.getName()), "Cancel a Hit"));
        MessageChannel channel =
                game.isFowMode() && !target.isNeutral() ? target.getCorrectChannel() : event.getMessageChannel();
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                target.getRepresentationNoPing() + ", assign the remaining " + hits + " hit" + (hits == 1 ? "" : "s")
                        + " from _Innovative Officer_ on " + planet.getRepresentation(game) + ".",
                buttons);
    }

    private static List<Button> getShipButtons(Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        if (tile == null) {
            return buttons;
        }
        for (UnitKey key : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
            UnitModel ship = player.getPriorityUnitByAsyncID(key.asyncID(), tile.getSpaceUnitHolder());
            if (ship != null && ship.getIsShip() && key.unitType() != UnitType.Fighter) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SELECT_SHIP + key.asyncID(),
                        "Remove " + ship.getName(),
                        key.unitEmoji()));
            }
        }
        return buttons;
    }

    private static List<Button> getPlanetButtons(Game game, Player player, Tile tile, String shipId) {
        if (tile == null) {
            return List.of();
        }
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> !getTargetPlayers(game, player, planet).isEmpty())
                .map(planet -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_PLANET + shipId + "|" + planet.getName(),
                        planet.getRepresentation(game)))
                .toList();
    }

    private static List<Player> getTargetPlayers(Game game, Player player, Planet planet) {
        return planet.getUnitKeys().stream()
                .map(UnitKey::colorID)
                .map(game::getPlayerFromColorOrFaction)
                .filter(target -> target != null && target != player)
                .distinct()
                .toList();
    }

    private static List<Button> getAssignedHitButtons(
            Game game, Player player, Tile tile, Planet planet, Player target) {
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : planet.getUnitKeysForPlayer(target)) {
            for (UnitState state : UnitState.defaultRemoveOrder()) {
                if (planet.getUnitCountForState(unitKey, state) < 1) {
                    continue;
                }
                String stateId = unitKey.unitName()
                        + (state.isDamaged() ? "damaged" : "")
                        + (state.isGalvanized() ? "galvanized" : "");
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + "hitOpponentGround_" + planet.getName() + "_" + stateId + "_"
                                + unitKey.getColor() + "_innovativeofficer",
                        "Assign Hit to "
                                + state.humanDescr()
                                + " "
                                + FoWHelper.identityOrColorIfCanSeeStats(game, target, player, target.getColor())
                                + " "
                                + unitKey.humanReadableName(),
                        unitKey.unitEmoji()));
            }
        }
        return buttons;
    }

    private static Tile getActiveSystem(Game game) {
        String activeSystem = game.getActiveSystem();
        return activeSystem == null || activeSystem.isEmpty() ? null : game.getTileByPosition(activeSystem);
    }

    private static UnitKey getEligibleShip(Player player, Tile tile, String shipId) {
        if (tile == null) {
            return null;
        }
        return tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .filter(key -> key.asyncID().equals(shipId))
                .filter(key -> {
                    UnitModel ship = player.getPriorityUnitByAsyncID(key.asyncID(), tile.getSpaceUnitHolder());
                    return ship != null && ship.getIsShip() && key.unitType() != UnitType.Fighter;
                })
                .findFirst()
                .orElse(null);
    }
}
