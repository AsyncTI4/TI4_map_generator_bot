package ti4.discord.interactions.buttons.handlers.explore.theodisi;

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
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.ExploreHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.MoveUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class LostLegciesExploreHandler {
    // Polymorphism
    private static final String USE_POLY = "usePolymorphism_";
    private static final String SELECT_POLY_SHIP = "selectPolymorphismShip_";
    private static final String PLACE_POLY_SHIP = "placePolymorphismShip_";
    // Spatial Displacement
    private static final String USE_SPATIAL = "useSpatialDisplacement_";
    private static final String MOVE_SHIP = "moveSpatialDisplacementShip_";
    private static final String DISPLACE = "finalizeSpatialDisplacementShipMovement_";
    // Battleworld
    private static final String BATTLEWORLD_ATTACHMENT = "attachment_battleworld.png";
    private static final String CLAIM_BATTLEWORLD_CC = "claimBattleworldCC_";
    // Immediate Assembly
    private static final String RESOLVE_IMMEDIATE_ASSEMBLY_MECH = "resolveImmediateAssemblyMech_";
    private static final String RESOLVE_IMMEDIATE_ASSEMBLY_INF = "resolveImmediateAssemblyInf_";
    public static final String IMMEDIATE_ASSEMBLY_PRODUCTION = "immediateAssemblyProduction_";
    // Objective Deliberations
    private static final String GAINTG_OD = "gainTgObjDeliberation";
    private static final String SPENDTG_SO = "spendTgForSecretObjDelib";
    // Exploration Enclave
    private static final String SPENDINF_EE = "spendInfToReadyPlanetsEE";

    // Exploration Enclave
    public static List<Button> getExplorationEnclaveButtons(
            GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + SPENDINF_EE, "Spend Influence to Ready Planets"));
        buttons.add(Buttons.red(player.factionButtonChecker() + "decline_explore", "Decline Exploration"));

        return buttons;
    }

    @ButtonHandler(SPENDINF_EE)
    public static void resolveExplorationEncalveSpend(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.addAll(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        buttons.add(Buttons.red(
                player.factionButtonChecker() + "doneSpendingExplorationEnclave", "Done Spending Influence"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you may spend up to 2 influence to ready that many non-home planets.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doneSpendingExplorationEnclave")
    public static void resolveExplorationEnclave(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        int influenceSpent = 0;
        for (String spentThing : player.getSpentThingsThisWindow()) {
            Planet planet = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(spentThing));
            if (planet != null) {
                influenceSpent += planet.getInfluence();
            }
        }
        influenceSpent += player.getSpentTgsThisWindow();
        player.resetSpentThings();

        List<Button> buttons = new ArrayList<>();

        for (String planetName : player.getExhaustedPlanets()) {
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null || tile.isHomeSystem(game)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "refresh_" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", you may ready " + Math.min(influenceSpent, 2) + " non-home planet"
                        + (influenceSpent > 1 ? "s." : "."),
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    // Objective Deliberations
    public static List<Button> offerObjectiveDeliberationButtons(
            GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return List.of();
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + GAINTG_OD, "Gain 2 Trade Goods", MiscEmojis.tg));
        buttons.add(Buttons.red(
                player.factionButtonChecker() + SPENDTG_SO,
                "Spend 2 Trade Goods For 1 Secret Objective",
                CardEmojis.SecretObjective));

        return buttons;
    }

    @ButtonHandler(GAINTG_OD)
    public static void resolveObjectiveDeliberationTgGain(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        ButtonHelperStats.gainTGs(event, game, player, 2, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SPENDTG_SO)
    public static void resolveSpendTgForSoObjectiveDeliberation(
            ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        if (player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You do not have enough trade goods to spend.");
            return;
        }

        int oldTg = player.getTg();

        player.setTg(oldTg - 2);
        game.drawSecretObjective(player.getUserID());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " spent 2 trade goods to draw a secret objective.\n-# You now have " + player.getTg() + " "
                        + (player.getTg() > 1 ? "trade goods." : "trade good."));
        ButtonHelper.deleteMessage(event);
    }

    // Battleworld
    public static void resolveBattleworldEndOfTurn(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        for (String planetName : player.getPlanets()) {
            UnitHolder planet = game.getUnitHolderFromPlanet(planetName);
            Tile tile = game.getTileFromPlanet(planetName);
            if (planet == null || tile == null || !planet.getTokenList().contains(BATTLEWORLD_ATTACHMENT)) {
                continue;
            }
            Player neutral = game.getNeutral();
            if (FoWHelper.playerHasUnitsOnPlanet(neutral, planet)) {
                continue;
            }

            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
            AddUnitService.addUnits(
                    event, tile, game, neutral.getColor(), "1 mech " + planetName + ", 2 infantry " + planetName);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " placed 1 infantry, 1 neutral mech, and 2 neutral infantry on "
                            + Helper.getPlanetRepresentation(planetName, game) + " due to _Battleworld_.");
        }
    }

    public static void offerBattleworldCombatReward(
            Game game, Player playerOne, Player playerTwo, Tile tile, UnitHolder planet) {
        if (game == null || playerOne == null || playerTwo == null || tile == null || planet == null) {
            return;
        }

        if (!planet.getTokenList().contains(BATTLEWORLD_ATTACHMENT)) {
            return;
        }

        Player neutral = game.getNeutral();
        Player player = playerOne == neutral ? playerTwo : playerTwo == neutral ? playerOne : null;
        if (player == null || !player.isRealPlayer() || !FoWHelper.playerHasUnitsOnPlanet(neutral, planet)) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", after you win this ground combat against the neutral units on "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + ", press this button to gain 1 command token due to _Battleworld_.",
                Buttons.gray(
                        player.factionButtonChecker() + CLAIM_BATTLEWORLD_CC + planet.getName(),
                        "Claim Battleworld Command Token"));
    }

    @ButtonHandler(CLAIM_BATTLEWORLD_CC)
    public static void claimBattleworldCommandToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose where to gain 1 command token from _Battleworld_.",
                ButtonHelper.getGainCCButtons(player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE_IMMEDIATE_ASSEMBLY_MECH)
    public static void resolveImmediateAssemblyMech(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(RESOLVE_IMMEDIATE_ASSEMBLY_MECH.length()).split("_(?=[^_]+$)");
        String planetName = payload[0];
        String result = payload.length > 1 ? payload[1] : "";
        Tile tile = game == null ? null : game.getTileFromPlanet(planetName);
        if (player == null
                || tile == null
                || !("production".equals(result) || "mech".equals(result))
                || !ExploreHelper.checkForMech(planetName, game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if ("production".equals(result)) {
            game.setStoredValue(IMMEDIATE_ASSEMBLY_PRODUCTION + player.getFaction(), planetName);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentationNoPing() + " is using a mech to resolve _Immediate Assembly_. "
                            + Helper.getPlanetRepresentation(planetName, game)
                            + " has **PRODUCTION 3** until the end of their turn.");
        } else if ("mech".equals(result)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planetName);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentationNoPing() + " is using a mech to resolve _Immediate Assembly_. "
                            + "Placed 1 mech on " + Helper.getPlanetRepresentation(planetName, game) + ".");
        }
        ButtonHelper.deleteMessage(event);
    }

    // Immediate Assembly
    @ButtonHandler(RESOLVE_IMMEDIATE_ASSEMBLY_INF)
    public static void resolveImmediateAssemblyInf(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(RESOLVE_IMMEDIATE_ASSEMBLY_INF.length()).split("_(?=[^_]+$)");
        String planetName = payload[0];
        String result = payload.length > 1 ? payload[1] : "";
        Tile tile = game == null ? null : game.getTileFromPlanet(planetName);
        UnitHolder planet = game == null ? null : game.getUnitHolderFromPlanet(planetName);
        if (player == null
                || tile == null
                || planet == null
                || !("production".equals(result) || "mech".equals(result))
                || !ExploreHelper.checkForInf(planetName, game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Infantry, 1, null);
        ButtonHelper.resolveInfantryRemoval(player, 1, tile);
        if ("production".equals(result)) {
            game.setStoredValue(IMMEDIATE_ASSEMBLY_PRODUCTION + player.getFaction(), planetName);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentationNoPing() + " removed 1 infantry to resolve _Immediate Assembly_. "
                            + Helper.getPlanetRepresentation(planetName, game)
                            + " has **PRODUCTION 3** until the end of their turn.");
        } else if ("mech".equals(result)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planetName);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentationNoPing() + " removed 1 infantry to resolve _Immediate Assembly_. "
                            + "Placed 1 mech on " + Helper.getPlanetRepresentation(planetName, game) + ".");
        }
        ButtonHelper.deleteMessage(event);
    }

    // Polymorphism
    public static void offerPolymorphism(ButtonInteractionEvent event, Game game, Player player, String planetName) {
        if (game == null || player == null || planetName == null) {
            return;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null
                || tile.getSpaceUnitHolder().getUnitsByStateForPlayer(player).keySet().stream()
                        .noneMatch(unitKey -> {
                            UnitModel unit = player.getUnitFromUnitKey(unitKey);
                            return unit != null && unit.getIsShip() && unitKey.unitType() != UnitType.Fighter;
                        })) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + USE_POLY + planetName, "Use Polymorphism"),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may use _Polymorphism_ to replace 1 non-fighter ship in this system with a ship that costs 1 more.",
                buttons);
    }

    @ButtonHandler(USE_POLY)
    public static void selectPolymorphismShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(USE_POLY.length());
        Tile tile = game == null ? null : game.getTileFromPlanet(planetName);
        UnitHolder planet = game == null ? null : ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        if (player == null
                || tile == null
                || planet == null
                || space == null
                || !player.getPlanets().contains(planetName)
                || !planet.getTokenList().contains("attachment_polymorphism.png")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : space.getUnitsByStateForPlayer(player).keySet()) {
            UnitModel unit = player.getUnitFromUnitKey(unitKey);
            if (unit == null || !unit.getIsShip() || unitKey.unitType() == UnitType.Fighter) {
                continue;
            }
            for (UnitState state : space.getNonZeroUnitStates(unitKey)) {
                String stateText = state == UnitState.none ? "" : state.humanDescr() + " ";
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SELECT_POLY_SHIP + planetName + "|" + unitKey.asyncID() + "|"
                                + state,
                        "Remove 1 " + stateText + unit.getName(),
                        unitKey.unitEmoji()));
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no eligible non-fighter ships in this system.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the ship to replace using _Polymorphism_.",
                buttons);
    }

    @ButtonHandler(SELECT_POLY_SHIP)
    public static void removePolymorphismShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_POLY_SHIP.length()).split("\\|", 3);
        String planetName = payload.length == 3 ? payload[0] : null;
        Tile tile = planetName == null || game == null ? null : game.getTileFromPlanet(planetName);
        UnitHolder planet =
                planetName == null || game == null ? null : ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        UnitKey unitKey =
                payload.length == 3 && player != null ? Mapper.getUnitKey(payload[1], player.getColor()) : null;
        UnitState state = payload.length == 3 ? Units.findUnitState(payload[2]) : null;
        UnitModel removedShip = unitKey == null || player == null ? null : player.getUnitFromUnitKey(unitKey);
        if (player == null
                || tile == null
                || planet == null
                || space == null
                || unitKey == null
                || state == null
                || removedShip == null
                || !player.getPlanets().contains(planetName)
                || !planet.getTokenList().contains("attachment_polymorphism.png")
                || !removedShip.getIsShip()
                || unitKey.unitType() == UnitType.Fighter
                || space.getUnitCountForState(unitKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        float replacementCost = removedShip.getCost() + 1;
        List<Button> replacementButtons = player.getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .filter(unit -> !"fighter".equals(unit.getBaseType()))
                .filter(unit -> Float.compare(unit.getCost(), replacementCost) == 0)
                .sorted(java.util.Comparator.comparing(UnitModel::getName))
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + PLACE_POLY_SHIP + planetName + "|" + unit.getAsyncId() + "|"
                                + replacementCost,
                        "Place 1 " + unit.getName(),
                        unit.getUnitEmoji()))
                .toList();
        if (replacementButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no non-fighter ship that costs " + (int) replacementCost
                            + " to place.");
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, space, unitKey.unitType(), 1, state);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the ship to place using _Polymorphism_.",
                replacementButtons);
    }

    @ButtonHandler(PLACE_POLY_SHIP)
    public static void placePolymorphismShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PLACE_POLY_SHIP.length()).split("\\|", 3);
        String planetName = payload.length == 3 ? payload[0] : null;
        Tile tile = planetName == null || game == null ? null : game.getTileFromPlanet(planetName);
        UnitHolder planet =
                planetName == null || game == null ? null : ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        UnitModel unit = payload.length == 3 && player != null ? player.getUnitFromAsyncID(payload[1]) : null;
        float replacementCost;
        try {
            replacementCost = payload.length == 3 ? Float.parseFloat(payload[2]) : -1;
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (player == null
                || tile == null
                || planet == null
                || unit == null
                || !player.getPlanets().contains(planetName)
                || !planet.getTokenList().contains("attachment_polymorphism.png")
                || !unit.getIsShip()
                || "fighter".equals(unit.getBaseType())
                || Float.compare(unit.getCost(), replacementCost) != 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit.getAsyncId());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed 1 " + unit.getNameRepresentation() + " using _Polymorphism_.");
        ButtonHelper.deleteMessage(event);
    }

    // Spatial Discplacement
    public static void resolveSpatialDisplacement(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "gainComms_1", "Gain 1 Commodity", MiscEmojis.comm));
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_SPATIAL + tile.getPosition(),
                "Use Spatial Displacement",
                UnitEmojis.destroyer));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may gain 1 commodity or spend 1 commodity or trade good to move 1 ship to an adjacent system that contains no other player's ships using _Spatial Displacement_.",
                buttons);
    }

    @ButtonHandler(USE_SPATIAL)
    public static void spatialDisplacementStep1(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String commOrTg;
        if (player.getCommodities() > 0) {
            player.setCommodities(player.getCommodities() - 1);
            commOrTg = "commodity";
            if (player.getPromissoryNotesInPlayArea().contains("dark_pact")) {
                commOrTg += " (though you may wish to manually spend a trade good instead because of _Dark Pact_)";
            }
        } else if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
            commOrTg = "trade good";
        } else {
            ReactionService.addReaction(
                    event,
                    game,
                    player,
                    "Didn't have any commodities or trade goods to spend, so no ship can be moved.");
            return;
        }

        ReactionService.addReaction(
                event,
                game,
                player,
                player.getFactionEmoji() + " spent 1 " + commOrTg + " to spatially displace 1 ship.");

        String tilePos = buttonID.replace(USE_SPATIAL, "");

        Tile activeTile = game.getTileByPosition(tilePos);
        List<Button> buttons = new ArrayList<>();

        if (activeTile != null) {
            for (UnitKey unitKey : activeTile
                    .getSpaceUnitHolder()
                    .getUnitsByStateForPlayer(player)
                    .keySet()) {
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.getIsShip()) {
                    continue;
                }

                buttons.add(Buttons.green(
                        player.factionButtonChecker() + MOVE_SHIP + unitKey.asyncID() + "|" + activeTile.getPosition(),
                        "Move 1 " + unitModel.getName(),
                        unitKey.unitEmoji()));
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no ships in the active system to move.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the ship you wish to move using _Spatial Displacement_.",
                buttons);
    }

    @ButtonHandler(MOVE_SHIP)
    public static void spatialDisplacementStep2(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.substring(MOVE_SHIP.length()).split("\\|", 2);
        if (payload.length != 2) {
            return;
        }

        String asyncId = payload[0];
        String tilePos = payload[1];
        Tile tile = game.getTileByPosition(tilePos);
        if (asyncId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that ship.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that system.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> destinations = new ArrayList<>();
        for (String adjacent : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false)) {
            Tile adjacentTile = game.getTileByPosition(adjacent);
            if (adjacentTile == null || FoWHelper.otherPlayersHaveShipsInSystem(player, adjacentTile, game)) {
                continue;
            }

            destinations.add(Buttons.green(
                    player.factionButtonChecker() + DISPLACE + tile.getPosition() + "|" + asyncId + "|"
                            + adjacentTile.getPosition(),
                    adjacentTile.getRepresentationForButtons(game, player)));
        }
        if (destinations.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no eligible adjacent systems.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the system to which you wish to move the ship.",
                destinations);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DISPLACE)
    public static void resolveSpatialDisplacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String payload = buttonID.replace(DISPLACE, "");
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve that movement selection.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String origPos = parts[0];
        String asyncId = parts[1];
        String destPos = parts[2];

        Tile origTile = game.getTileByPosition(origPos);
        UnitModel unit = player.getUnitFromAsyncID(asyncId);
        UnitKey unitKey = Mapper.getUnitKey(asyncId, player.getColorID());
        Tile destTile = game.getTileByPosition(destPos);
        if (origTile == null || destTile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find original or destination tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (unit == null || unitKey == null || origTile.getSpaceUnitHolder().getUnitCount(unitKey) < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that ship.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!FoWHelper.getAdjacentTiles(game, origPos, player, false).contains(destPos)
                || FoWHelper.otherPlayersHaveShipsInSystem(player, destTile, game)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That system is no longer eligible.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MoveUnitService.moveUnits(event, origTile, game, player.getColor(), "1 " + asyncId, destTile, Constants.SPACE);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " moved 1 " + unit.getNameRepresentation() + " from "
                        + origTile.getRepresentation() + " to " + destTile.getRepresentation()
                        + " via _Spatial Displacement_.");

        ButtonHelper.deleteMessage(event);
    }
}
