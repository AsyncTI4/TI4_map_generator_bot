package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class AshenLeadersHandler {

    private static final String AGENT_ID = "ashenagent";
    private static final String AGENT_SELECT_TARGET = "ashenAgentSelectTarget";
    private static final String AGENT_USE_ON_TARGET_PREFIX = "ashenAgentUseOn_";
    private static final String AGENT_CHOOSE_UNIT_PREFIX = "ashenAgentChooseUnit_";
    private static final String AGENT_PLACE_DESTINATION_PREFIX = "ashenAgentPlace_";
    private static final String COMMANDER_ID = "ashencommander";
    private static final String COMMANDER_PLACE_PREFIX = "ashenCommanderPlace_";
    private static final String HERO_SYSTEM_PREFIX = "ashenHeroSystem_";
    private static final String HERO_BOMBARDMENT_ASSIGN_PREFIX = "ashenHeroBombardmentAssign_";
    private static final String HERO_PLAIN_BOMBARDMENT_PREFIX = "ashenHeroPlainBombardment_";

    public static Button getAshTenderCardsInfoButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + AGENT_SELECT_TARGET, "Use Orrun", FactionEmojis.ashen);
    }

    public static void postHeroButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        if (event == null || game == null || player == null) {
            return;
        }

        List<Tile> eligibleTiles = getEligibleHeroTiles(game, player);
        if (eligibleTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No eligible systems are available for **Asera** right now.");
            return;
        }

        List<Button> buttons = eligibleTiles.stream()
                .map(tile -> Buttons.red(
                        player.factionButtonChecker() + HERO_SYSTEM_PREFIX + tile.getPosition(),
                        "Use Hell From Above in " + tile.getRepresentationForButtons(game, player),
                        FactionEmojis.ashen))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose an eligible system for **Ashfall Effigy - Hell From Above**.",
                buttons);
    }

    @ButtonHandler(HERO_SYSTEM_PREFIX)
    public static void resolveAshenHeroSystem(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String position = buttonID.substring(HERO_SYSTEM_PREFIX.length());
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile sourceTile = game.getTileByPosition(position);
        if (sourceTile == null
                || getEligibleHeroTiles(game, player).stream()
                        .noneMatch(tile -> tile.getPosition().equals(position))) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That system is no longer eligible for **Asera**.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> targetPlanets = getHeroTargetPlanets(game, player, sourceTile);
        if (targetPlanets.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are no longer any valid planets to bombard from that system.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " is resolving **Ashfall Effigy - Hell From Above** from "
                        + sourceTile.getRepresentationForButtons(game, player)
                        + ".");
        ButtonHelper.deleteMessage(event);

        for (String planet : targetPlanets) {
            game.setStoredValue(getHeroBombardmentAssignKey(player), planet);
            game.setStoredValue(getHeroPlainBombardmentKey(player), planet);
            game.setStoredValue("bombardmentTarget" + player.getFaction(), planet);
            game.setStoredValue(
                    "assignedBombardment" + player.getFaction(),
                    buildHeroBombardmentAssignment(player, game, sourceTile, planet));
            CombatRollService.secondHalfOfCombatRoll(
                    player, game, event, sourceTile, "space", CombatRollType.bombardment, false);
        }

        game.removeStoredValue("assignedBombardment" + player.getFaction());
        game.removeStoredValue("bombardmentTarget" + player.getFaction());
        game.removeStoredValue(getHeroBombardmentAssignKey(player));
        game.removeStoredValue(getHeroPlainBombardmentKey(player));
    }

    public static boolean offerHeroBombardmentAssignButtons(
            GenericInteractionCreateEvent event, Game game, Player player, int hits, String bombardPlanet) {
        if (event == null
                || game == null
                || player == null
                || hits < 1
                || bombardPlanet == null
                || bombardPlanet.isEmpty()
                || !bombardPlanet.equals(game.getStoredValue(getHeroBombardmentAssignKey(player)))) {
            return false;
        }

        Planet targetPlanet = game.getUnitHolderFromPlanet(bombardPlanet);
        Tile targetTile = game.getTileFromPlanet(bombardPlanet);
        if (targetPlanet == null || targetTile == null) {
            return false;
        }

        for (Player p2 : game.getRealPlayersNNeutral()) {
            if (p2 == player || !FoWHelper.playerHasUnitsOnPlanet(p2, targetPlanet)) {
                continue;
            }
            if (p2.isRealPlayer()) {
                List<Button> buttons = getHeroPlanetAssignButtons(p2, game, targetTile, bombardPlanet);
                if (!buttons.isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            game.isFowMode() ? p2.getCorrectChannel() : event.getMessageChannel(),
                            p2.getRepresentation() + ", please assign the BOMBARDMENT hit"
                                    + (hits == 1 ? "" : "s")
                                    + " on "
                                    + targetPlanet.getRepresentation(game)
                                    + ".",
                            buttons);
                }
            } else {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(
                        p2.dummyPlayerSpoof() + "autoAssignGroundHits_" + targetPlanet.getName() + "_" + hits,
                        "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
                MessageHelper.sendMessageToChannelWithButtons(
                        game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                        player.getRepresentation() + ", please assign the BOMBARDMENT hit"
                                + (hits == 1 ? "" : "s")
                                + " for the dummy player on "
                                + targetPlanet.getRepresentation(game)
                                + ".",
                        buttons);
            }
        }
        return true;
    }

    public static boolean isPlainHeroBombardment(Game game, Player player, String bombardPlanet) {
        return game != null
                && player != null
                && bombardPlanet != null
                && !bombardPlanet.isEmpty()
                && bombardPlanet.equals(game.getStoredValue(getHeroPlainBombardmentKey(player)));
    }

    public static void offerCommanderPlacementButtons(
            GenericInteractionCreateEvent event, Game game, Player player, TechnologyModel techModel) {
        if (game == null
                || player == null
                || techModel == null
                || !techModel.isUnitUpgrade()
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER_ID)) {
            return;
        }

        UnitModel unitModel = getCommanderPlacementUnit(player, techModel);
        if (unitModel == null) {
            return;
        }

        List<Tile> eligibleTiles = getEligibleCommanderPlacementTiles(game, player);
        if (eligibleTiles.isEmpty()) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : eligibleTiles) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker()
                            + COMMANDER_PLACE_PREFIX
                            + unitModel.getAsyncId()
                            + "~"
                            + tile.getPosition(),
                    "Place " + unitModel.getName() + " in " + tile.getRepresentationForButtons(game, player),
                    unitModel.getUnitEmoji()));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageChannel channel = event == null ? player.getCorrectChannel() : event.getMessageChannel();
        if (channel == null) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentationUnfogged() + ", you may use **Karos** to place 1 " + unitModel.getName()
                        + " from your reinforcements into an eligible system.",
                buttons);
    }

    public static void offerCommanderBombardmentButtons(
            GenericInteractionCreateEvent event, Game game, Player player, int hits) {
        if (event == null
                || game == null
                || player == null
                || hits < 1
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER_ID)) {
            return;
        }

        String message = player.getRepresentationUnfogged() + ", **Karos**: for each of these "
                + StringHelper.pluralize(hits, "hit")
                + ", you may either gain 1 commodity or convert 1 of your commodities to a trade good."
                + "\n-# You have (" + player.getCommoditiesRepresentation() + ") commodities.";
        List<Button> buttons = ButtonHelperFactionSpecific.gainOrConvertCommButtons(player, false);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler(AGENT_SELECT_TARGET)
    public static void offerAshTenderTargetButtons(ButtonInteractionEvent event, Game game, Player ashenPlayer) {
        if (!ashenPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Orrun** is no longer available.");
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> !getEligibleUnitModels(target).isEmpty())
                .filter(target -> !getEligibleDestinationTiles(game, target).isEmpty())
                .map(target -> Buttons.gray(
                        ashenPlayer.factionButtonChecker()
                                + AGENT_USE_ON_TARGET_PREFIX
                                + ashenPlayer.getFaction()
                                + "~"
                                + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "No player currently has both an eligible **Orrun** unit choice and an eligible destination system.");
            return;
        }

        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event, ashenPlayer.getRepresentationUnfogged() + ", choose the player who may use **Orrun**.", buttons);
    }

    @ButtonHandler(AGENT_USE_ON_TARGET_PREFIX)
    public static void useAshTenderOnTarget(
            ButtonInteractionEvent event, Game game, Player ashenPlayer, String buttonID) {
        if (!ashenPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Orrun** is no longer available.");
            return;
        }

        String payload = buttonID.substring(AGENT_USE_ON_TARGET_PREFIX.length());
        String[] parts = payload.split("~", 2);
        if (parts.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that **Orrun** choice.");
            return;
        }

        String targetFaction = parts[1];
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }

        List<UnitModel> eligibleUnitModels = getEligibleUnitModels(target);
        List<Tile> eligibleTiles = getEligibleDestinationTiles(game, target);
        if (eligibleUnitModels.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    target.getRepresentation(false, false)
                            + " has no eligible non-fighter, non-infantry ship units for **Orrun**.");
            return;
        }
        if (eligibleTiles.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    target.getRepresentation(false, false)
                            + " no longer has any eligible destination systems for **Orrun**.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitModel unitModel : eligibleUnitModels) {
            buttons.add(Buttons.gray(
                    target.factionButtonChecker()
                            + AGENT_CHOOSE_UNIT_PREFIX
                            + ashenPlayer.getFaction()
                            + "~"
                            + unitModel.getAsyncId(),
                    unitModel.getName(),
                    unitModel.getUnitEmoji()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", choose which eligible unit **Orrun** should place for you.",
                buttons);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Sent **Orrun** unit-choice buttons to " + target.getRepresentationUnfoggedNoPing() + ".");
    }

    @ButtonHandler(AGENT_CHOOSE_UNIT_PREFIX)
    public static void chooseAshTenderUnit(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String payload = buttonID.substring(AGENT_CHOOSE_UNIT_PREFIX.length());
        String[] parts = payload.split("~", 2);
        if (parts.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that **Orrun** unit choice.");
            return;
        }

        Player ashenPlayer = game.getPlayerFromColorOrFaction(parts[0]);
        if (ashenPlayer == null || !ashenPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Orrun** is no longer available.");
            return;
        }

        UnitModel selectedUnit = target.getUnitFromAsyncID(parts[1]);
        if (!isEligibleUnitModel(selectedUnit)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit is not eligible for **Orrun**.");
            return;
        }

        List<Tile> eligibleTiles = getEligibleDestinationTiles(game, target);
        if (eligibleTiles.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You no longer have any eligible destination systems for **Orrun**.");
            return;
        }

        List<Button> buttons = eligibleTiles.stream()
                .map(tile -> Buttons.gray(
                        target.factionButtonChecker()
                                + AGENT_PLACE_DESTINATION_PREFIX
                                + ashenPlayer.getFaction()
                                + "~"
                                + selectedUnit.getAsyncId()
                                + "~"
                                + tile.getPosition(),
                        "Place " + selectedUnit.getName() + " in " + tile.getRepresentationForButtons(game, target)))
                .toList();

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose which eligible system **Orrun** should place your "
                        + selectedUnit.getName()
                        + " in.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(AGENT_PLACE_DESTINATION_PREFIX)
    public static void placeAshTenderDestroyedShip(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String payload = buttonID.substring(AGENT_PLACE_DESTINATION_PREFIX.length());
        String[] parts = payload.split("~", 3);
        if (parts.length != 3) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that **Orrun** choice.");
            return;
        }

        Player ashenPlayer = game.getPlayerFromColorOrFaction(parts[0]);
        if (ashenPlayer == null || !ashenPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Orrun** is no longer available.");
            return;
        }

        UnitModel selectedUnit = target.getUnitFromAsyncID(parts[1]);
        if (!isEligibleUnitModel(selectedUnit)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit is not eligible for **Orrun**.");
            return;
        }

        Tile destination = game.getTileByPosition(parts[2]);
        if (destination == null
                || getEligibleDestinationTiles(game, target).stream()
                        .noneMatch(tile -> tile.getPosition().equals(destination.getPosition()))) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That destination is no longer eligible for **Orrun**.");
            return;
        }

        Leader agent = ashenPlayer.getLeader(AGENT_ID).orElse(null);
        if (agent == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find **Orrun**.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, ashenPlayer, agent);
        AddUnitService.addUnits(event, destination, game, target.getColor(), "1 " + selectedUnit.getAsyncId());
        ButtonHelper.deleteMessage(event);

        String message = ashenPlayer.getRepresentation()
                + " exhausted **Orrun**, the Ashen agent, to place "
                + target.getRepresentation()
                + "'s "
                + selectedUnit.getName()
                + " in "
                + destination.getRepresentationForButtons(game, target)
                + ".";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    ashenPlayer.getRepresentationUnfogged()
                            + " used **Orrun** on you and placed your "
                            + selectedUnit.getName()
                            + " in "
                            + destination.getRepresentationForButtons(game, target)
                            + ".");
        }
    }

    @ButtonHandler(COMMANDER_PLACE_PREFIX)
    public static void placeCommanderUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(COMMANDER_PLACE_PREFIX.length());
        String[] parts = payload.split("~", 2);
        if (parts.length != 2
                || game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER_ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitModel unitModel = player.getUnitFromAsyncID(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);
        if (unitModel == null
                || tile == null
                || getEligibleCommanderPlacementTiles(game, player).stream()
                        .noneMatch(t -> t.getPosition().equals(tile.getPosition()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnitsToDefaultLocations(
                event, tile, game, player.getColor(), "1 " + getCommanderPlacementUnitAlias(unitModel));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentation() + " used **Karos** to place 1 " + unitModel.getName() + " in "
                        + tile.getRepresentationForButtons(game, player) + ".");
    }

    private static List<Tile> getEligibleDestinationTiles(Game game, Player target) {
        List<Tile> tiles = new ArrayList<>();
        String activeSystem = game.getActiveSystem();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().equalsIgnoreCase(activeSystem)) {
                continue;
            }
            if (FoWHelper.playerHasActualShipsInSystem(target, tile) && CommandCounterHelper.hasCC(target, tile)) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    private static List<UnitModel> getEligibleUnitModels(Player target) {
        return target.getUnitModels().stream()
                .filter(AshenLeadersHandler::isEligibleUnitModel)
                .sorted(Comparator.comparing(UnitModel::getName))
                .toList();
    }

    private static List<Tile> getEligibleHeroTiles(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(player, tile))
                .filter(tile -> !CombatRollService.getUnitsInBombardment(tile, player, null)
                        .isEmpty())
                .filter(tile -> !getHeroTargetPlanets(game, player, tile).isEmpty())
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList();
    }

    private static List<String> getHeroTargetPlanets(Game game, Player player, Tile sourceTile) {
        List<String> planets = new ArrayList<>();
        for (String tilePos : FoWHelper.getAdjacentTiles(game, sourceTile.getPosition(), player, false, true)) {
            Tile tile = game.getTileByPosition(tilePos);
            if (tile == null) {
                continue;
            }
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (isPlanetControlledByAnotherPlayer(game, player, planet.getName())) {
                    planets.add(planet.getName());
                }
            }
        }
        planets.sort(String::compareToIgnoreCase);
        return planets;
    }

    private static boolean isPlanetControlledByAnotherPlayer(Game game, Player player, String planetName) {
        return game.getRealPlayersNNeutral().stream()
                .filter(otherPlayer -> otherPlayer != player)
                .anyMatch(otherPlayer -> otherPlayer.getPlanets().contains(planetName));
    }

    private static String buildHeroBombardmentAssignment(
            Player player, Game game, Tile sourceTile, String targetPlanet) {
        StringBuilder assignment = new StringBuilder();
        Map<UnitModel, Integer> bombardUnits = CombatRollService.getUnitsInBombardment(sourceTile, player, null);
        for (Map.Entry<UnitModel, Integer> entry : bombardUnits.entrySet()) {
            for (int x = 0; x < entry.getValue(); x++) {
                assignment
                        .append(entry.getKey().getAsyncId())
                        .append("_")
                        .append(x)
                        .append("_")
                        .append(targetPlanet)
                        .append(";");
            }
        }
        return assignment.toString();
    }

    private static List<Button> getHeroPlanetAssignButtons(Player player, Game game, Tile tile, String planetName) {
        return ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, "bombardment").stream()
                .filter(button -> {
                    String id = button.getCustomId();
                    return id != null && ("deleteButtons".equals(id) || id.contains("_" + planetName + "_"));
                })
                .toList();
    }

    private static String getHeroBombardmentAssignKey(Player player) {
        return HERO_BOMBARDMENT_ASSIGN_PREFIX + player.getFaction();
    }

    private static String getHeroPlainBombardmentKey(Player player) {
        return HERO_PLAIN_BOMBARDMENT_PREFIX + player.getFaction();
    }

    private static List<Tile> getEligibleCommanderPlacementTiles(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> player.getPlanets().contains(planet.getName())))
                .filter(tile -> !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game))
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList();
    }

    private static UnitModel getCommanderPlacementUnit(Player player, TechnologyModel techModel) {
        UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(techModel.getAlias());
        if (upgradedUnit != null) {
            return upgradedUnit;
        }

        String baseUpgrade = techModel.getBaseUpgrade().orElse("");
        return switch (baseUpgrade) {
            case "cv2" -> player.getUnitByBaseType("carrier");
            case "cr2" -> player.getUnitByBaseType("cruiser");
            case "dd2" -> player.getUnitByBaseType("destroyer");
            case "dn2" -> player.getUnitByBaseType("dreadnought");
            case "ff2" -> player.getUnitByBaseType("fighter");
            case "inf2" -> player.getUnitByBaseType("infantry");
            case "pds2" -> player.getUnitByBaseType("pds");
            case "sd2" -> player.getUnitByBaseType("spacedock");
            case "ws" -> player.getUnitByBaseType("warsun");
            default -> null;
        };
    }

    private static String getCommanderPlacementUnitAlias(UnitModel unitModel) {
        return switch (unitModel.getUnitType()) {
            case Carrier -> "cv";
            case Cruiser -> "ca";
            case Destroyer -> "dd";
            case Dreadnought -> "dn";
            case Fighter -> "ff";
            case Infantry -> "gf";
            case Pds -> "pds";
            case Spacedock -> "sd";
            case Warsun -> "ws";
            default -> unitModel.getAsyncId();
        };
    }

    private static boolean isEligibleUnitModel(UnitModel unitModel) {
        return unitModel != null
                && unitModel.getIsShip()
                && unitModel.getUnitType() != UnitType.Fighter
                && unitModel.getUnitType() != UnitType.Infantry;
    }
}
