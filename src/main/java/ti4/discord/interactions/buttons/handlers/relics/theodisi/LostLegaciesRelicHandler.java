package ti4.discord.interactions.buttons.handlers.relics.theodisi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CaptureUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class LostLegaciesRelicHandler {
    // Economic Boon
    private static final String USE_EBOON = "useEconomicBoon";
    // Nature's Boon
    private static final String USE_NBOON = "useNaturesBoon_";
    private static final String CHOOSE_NBOON_PLANET = "chooseNaturesBoonPlanet_";
    // Diplomatic Boon
    private static final String CHOOSE_DBOON_PLANET = "chooseDiplomaticBoonPlanet_";
    private static final String PLACE_UNIT_DBOON = "placeUnitWithDiplomaticBoon_";
    private static final String FINISH_DBOON = "finishDiplomaticBoonPlacement";
    // Cosmic Boon
    private static final String USE_CBOON = "useCosmicBoon";
    private static final String BOON_TOKENS = "boonTokens_";
    private static final String ECONOMIC_AMOUNT = "economicBoonAmount_";
    private static final String ECONOMIC_SYSTEM = "economicBoonSystem_";
    private static final String ECONOMIC_SHIP = "economicBoonShip_";
    private static final String NATURE_AMOUNT = "naturesBoonAmount_";
    private static final String NATURE_PLANET = "naturesBoonPlanet_";
    private static final String DIPLOMATIC_AMOUNT = "diplomaticBoonAmount_";
    private static final String DIPLOMATIC_PLACE = "diplomaticBoonPlace_";
    private static final String COSMIC_EXPLORE = "cosmicBoonExplore_";
    private static final String NATURE_BONUS = "naturesBoonBonus_";
    // Ancient Radar
    private static final String RADAR_EXPLORE = "exploreAncientRadar_";
    // Horn of the Abyss
    private static final String PLACE_NEUTRAL = "hornOfTheAbyssPlace_";
    private static final String CHOOSE_HORN_SYSTEM = "chooseHornOfTheAbyssSystem_";
    private static final String PLACE_HORN_SHIP = "placeHornOfTheAbyssShip_";
    private static final String DONE_HORN_SHIPS = "donePlacingHornOfTheAbyssShips";
    private static final String HORN_SYSTEM = "hornOfTheAbyssSystem_";
    private static final String HORN_COST = "hornOfTheAbyssCost_";
    private static final String HORN_SHIPS = "hornOfTheAbyssShips_";
    private static final String HORN_REPLACEMENT_BATCH = "hornOfTheAbyssReplacementBatch";
    private static final String HORN_REPLACEMENT_CHOICES = "hornOfTheAbyssReplacementChoices_";
    private static final int HORN_COST_LIMIT = 8;

    // Horn of the Abyss
    public static void offerNeutralReplacement(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        Map<Player, List<RemovedUnit>> destroyedByKiller = new LinkedHashMap<>();
        for (RemovedUnit destroyedUnit : destroyedUnits) {
            if (!Mapper.getColorID(game.getNeutralColor())
                    .equals(destroyedUnit.unitKey().colorID())) {
                continue;
            }
            List<Player> possibleKillers = CaptureUnitService.listProbableKiller(game, destroyedUnit).stream()
                    .filter(killer -> destroyedUnit.uh().getUnitKeys().stream().anyMatch(killer::unitBelongsToPlayer))
                    .distinct()
                    .toList();
            if (possibleKillers.size() == 1 && possibleKillers.getFirst().hasRelic("horn_of_the_abyss")) {
                destroyedByKiller
                        .computeIfAbsent(possibleKillers.getFirst(), ignored -> new ArrayList<>())
                        .add(destroyedUnit);
            }
        }

        for (Map.Entry<Player, List<RemovedUnit>> entry : destroyedByKiller.entrySet()) {
            Player killer = entry.getKey();
            Map<String, RemovedUnit> replacementChoices = getNeutralReplacementChoices(killer, entry.getValue());
            if (replacementChoices.isEmpty()) {
                continue;
            }
            if (!game.getStoredValue(HORN_REPLACEMENT_BATCH).isEmpty()) {
                String key = HORN_REPLACEMENT_CHOICES + killer.getFaction();
                List<String> choices =
                        new ArrayList<>(List.of(game.getStoredValue(key).split(",")));
                choices.removeIf(String::isEmpty);
                replacementChoices.keySet().forEach(choice -> {
                    if (!choices.contains(choice)) {
                        choices.add(choice);
                    }
                });
                game.setStoredValue(key, String.join(",", choices));
                continue;
            }
            sendNeutralReplacementPrompt(event, game, killer, replacementChoices.keySet());
        }
    }

    public static void beginNeutralReplacementBatch(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(HORN_REPLACEMENT_CHOICES + player.getFaction());
        }
        game.setStoredValue(HORN_REPLACEMENT_BATCH, "yes");
    }

    public static void finishNeutralReplacementBatch(GenericInteractionCreateEvent event, Game game) {
        game.removeStoredValue(HORN_REPLACEMENT_BATCH);
        for (Player player : game.getRealPlayers()) {
            String key = HORN_REPLACEMENT_CHOICES + player.getFaction();
            List<String> choices =
                    new ArrayList<>(List.of(game.getStoredValue(key).split(",")));
            choices.removeIf(String::isEmpty);
            game.removeStoredValue(key);
            sendNeutralReplacementPrompt(event, game, player, choices);
        }
    }

    private static Map<String, RemovedUnit> getNeutralReplacementChoices(
            Player killer, List<RemovedUnit> destroyedUnits) {
        Map<String, RemovedUnit> replacementChoices = new LinkedHashMap<>();
        boolean canPlaceWarSun = killer.hasTech("ws")
                || killer.hasTech("absol_ws")
                || killer.getUnitModels().stream()
                        .anyMatch(unit -> unit.getUnitType() == UnitType.Warsun
                                && unit.getFaction().isPresent());
        for (RemovedUnit destroyedUnit : destroyedUnits) {
            if (destroyedUnit.unitKey().unitType() == UnitType.Warsun && !canPlaceWarSun) {
                continue;
            }
            String payload = destroyedUnit.tile().getPosition() + "|"
                    + destroyedUnit.unitKey().unitName();
            replacementChoices.putIfAbsent(payload, destroyedUnit);
        }
        return replacementChoices;
    }

    private static void sendNeutralReplacementPrompt(
            GenericInteractionCreateEvent event, Game game, Player killer, Iterable<String> choices) {
        List<Button> buttons = new ArrayList<>();
        for (String choice : choices) {
            String[] payload = choice.split("\\|", 2);
            if (payload.length != 2 || game.getTileByPosition(payload[0]) == null) {
                continue;
            }
            var unitKey = Units.getUnitKey(payload[1], killer.getColor());
            if (unitKey == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    killer.factionButtonChecker() + PLACE_NEUTRAL + choice, "Place 1 " + unitKey.humanReadableName()));
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                killer.getRepresentationNoPing()
                        + ", choose 1 destroyed neutral unit to replace with _Horn of the Abyss_.\n"
                        + "-# Selecting a unit removes every other choice from this batch.",
                buttons);
    }

    @ButtonHandler(PLACE_NEUTRAL)
    public static void placeNeutralReplacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasRelic("horn_of_the_abyss")) {
            return;
        }

        String[] payload = buttonID.substring(PLACE_NEUTRAL.length()).split("\\|", 2);
        if (payload.length != 2) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        if (tile == null) {
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + payload[1]);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " used _Horn of the Abyss_ and placed 1 "
                        + payload[1]
                        + " in "
                        + tile.getRepresentationForButtons(game, player)
                        + ".");

        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getHornOfTheAbyssSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getTileModel().isHyperlane())
                .filter(tile -> !hasOtherPlayersShips(tile, game, player))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_HORN_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    @ButtonHandler(CHOOSE_HORN_SYSTEM)
    public static void chooseHornOfTheAbyssSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasRelic("horn_of_the_abyss")
                || !player.getExhaustedRelics().contains("horn_of_the_abyss")) {
            return;
        }

        List<Button> systemButtons = getHornOfTheAbyssSystemButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", please choose the system in which to place neutral ships with _Horn of the Abyss_.\n"
                + "-# You may place neutral ships with a combined cost of 4 or less.";
        String buttonPrefix = player.factionButtonChecker() + CHOOSE_HORN_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, message, buttonPrefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(CHOOSE_HORN_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || tile.getTileModel().isHyperlane() || hasOtherPlayersShips(tile, game, player)) {
            return;
        }

        game.setStoredValue(HORN_SYSTEM + player.getFaction(), position);
        game.setStoredValue(HORN_COST + player.getFaction(), "0");
        game.removeStoredValue(HORN_SHIPS + player.getFaction());
        sendHornOfTheAbyssShipButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_HORN_SHIP)
    public static void placeHornOfTheAbyssShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = game.getStoredValue(HORN_SYSTEM + player.getFaction());
        Tile tile = position.isEmpty() ? null : game.getTileByPosition(position);
        UnitModel ship = game.getNeutral().getUnitFromAsyncID(buttonID.substring(PLACE_HORN_SHIP.length()));
        if (!player.getExhaustedRelics().contains("horn_of_the_abyss")
                || tile == null
                || ship == null
                || !ship.getIsShip()
                || hasOtherPlayersShips(tile, game, player)) {
            return;
        }

        int totalCost = getHornCost(game, player);
        int shipCost = getHornShipCost(ship);
        if (shipCost <= 0 || totalCost + shipCost > HORN_COST_LIMIT) {
            return;
        }

        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), "1 " + ship.getAsyncId());
        game.setStoredValue(HORN_COST + player.getFaction(), Integer.toString(totalCost + shipCost));
        String shipList = game.getStoredValue(HORN_SHIPS + player.getFaction());
        game.setStoredValue(
                HORN_SHIPS + player.getFaction(),
                shipList.isEmpty() ? ship.getName() : shipList + ", " + ship.getName());
        sendHornOfTheAbyssShipButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DONE_HORN_SHIPS)
    public static void finishHornOfTheAbyssShips(ButtonInteractionEvent event, Game game, Player player) {
        String position = game.getStoredValue(HORN_SYSTEM + player.getFaction());
        String shipList = game.getStoredValue(HORN_SHIPS + player.getFaction());
        int totalCost = getHornCost(game, player);
        Tile tile = position.isEmpty() ? null : game.getTileByPosition(position);
        game.removeStoredValue(HORN_SYSTEM + player.getFaction());
        game.removeStoredValue(HORN_COST + player.getFaction());
        game.removeStoredValue(HORN_SHIPS + player.getFaction());

        if (!shipList.isEmpty() && tile != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " exhausted _Horn of the Abyss_ and placed " + shipList + " in "
                            + tile.getRepresentationForButtons(game, player) + " (combined cost "
                            + formatHornCost(totalCost) + ").");
        }
        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    private static void sendHornOfTheAbyssShipButtons(ButtonInteractionEvent event, Game game, Player player) {
        int totalCost = getHornCost(game, player);
        List<Button> shipButtons = game.getNeutral().getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .filter(ship -> getHornShipCost(ship) > 0 && totalCost + getHornShipCost(ship) <= HORN_COST_LIMIT)
                .sorted(Comparator.comparing(UnitModel::getCost).thenComparing(UnitModel::getName))
                .map(ship -> Buttons.green(
                        player.factionButtonChecker() + PLACE_HORN_SHIP + ship.getAsyncId(),
                        "Place 1 " + ship.getName() + " (Cost " + formatHornCost(getHornShipCost(ship)) + ")",
                        ship.getUnitEmoji()))
                .toList();
        shipButtons = new ArrayList<>(shipButtons);
        shipButtons.add(Buttons.red(player.factionButtonChecker() + DONE_HORN_SHIPS, "Done Placing Ships"));

        String message =
                player.getRepresentationNoPing() + ", please choose neutral ships to place with _Horn of the Abyss_.\n"
                        + "-# Combined cost: " + formatHornCost(totalCost) + "/4.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, shipButtons);
    }

    private static int getHornShipCost(UnitModel ship) {
        return Math.round(ship.getCost() * 2);
    }

    private static int getHornCost(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(HORN_COST + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void clearHornOfTheAbyssState(Game game) {
        game.removeStoredValue(HORN_REPLACEMENT_BATCH);
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(HORN_SYSTEM + player.getFaction());
            game.removeStoredValue(HORN_COST + player.getFaction());
            game.removeStoredValue(HORN_SHIPS + player.getFaction());
            game.removeStoredValue(HORN_REPLACEMENT_CHOICES + player.getFaction());
        }
    }

    private static String formatHornCost(int halfCost) {
        return halfCost % 2 == 0 ? Integer.toString(halfCost / 2) : halfCost / 2 + ".5";
    }

    private static boolean hasOtherPlayersShips(Tile tile, Game game, Player player) {
        return tile.getSpaceUnitHolder().getUnitKeys().stream().anyMatch(unitKey -> {
            Player owner = game.getPlayerFromColorOrFaction(unitKey.colorID());
            UnitModel model = owner == null ? null : owner.getUnitFromUnitKey(unitKey);
            return owner != null && !owner.isNeutral() && owner != player && model != null && model.getIsShip();
        });
    }

    // Ancient Radar
    public static List<Button> getAncientRadarPlanets(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            Tile tile = game.getTileFromPlanet(planetName);
            if (planet != null
                    && tile != null
                    && !planet.isHomePlanet()
                    && !tile.isMecatol(game)
                    && !planet.isLegendary()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + RADAR_EXPLORE + planetName, planet.getRepresentation(game)));
            }
        }

        return buttons;
    }

    @ButtonHandler(RADAR_EXPLORE)
    public static void resolveAncientRadarExplore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }
        if (!player.hasRelic("ancient_radar") || !player.getExhaustedRelics().contains("ancient_radar")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = player.getRepresentationNoPing()
                + ", please choose a non-home planet for _Ancient Radar_ to explore once as each planet trait.";
        String buttonPrefix = player.factionButtonChecker() + RADAR_EXPLORE;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getAncientRadarPlanets(event, game, player),
                message,
                buttonPrefix,
                buttonID)) {
            return;
        }

        String planetName = buttonID.substring(RADAR_EXPLORE.length());
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!player.getPlanets().contains(planetName)
                || tile == null
                || planet == null
                || planet.isHomePlanet()
                || tile.isMecatol(game)
                || planet.isLegendary()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        for (String trait : List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL)) {
            ExploreService.explorePlanet(event, tile, planetName, trait, player, true, game, 1, false);
        }

        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    // Cosmic Boon
    public static Button getCosmicBoonButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_CBOON, "Exhaust Cosmic Boon");
    }

    @ButtonHandler(USE_CBOON)
    public static void resolveCosmicBoon(ButtonInteractionEvent event, Game game, Player player) {
        resolveCosmicBoonTokens(event, game, player);
    }

    // Diplomatic Boon
    public static List<Button> getDiplomaticBoonPlanets(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return List.of();
        }

        List<Button> planets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            Tile tile = game.getTileFromPlanet(planetName);
            if (planet != null
                    && !planet.isHomePlanet()
                    && tile != null
                    && !tile.isMecatol()
                    && FoWHelper.playerHasShipsInSystem(player, tile)) {
                planets.add(Buttons.green(
                        player.factionButtonChecker() + CHOOSE_DBOON_PLANET + planetName,
                        planet.getRepresentation(game)));
            }
        }

        return planets;
    }

    @ButtonHandler(CHOOSE_DBOON_PLANET)
    public static void chooseDBoonInfantryOrFighter(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetName = buttonID.replace(CHOOSE_DBOON_PLANET, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", place up to " + planet.getInfluence()
                        + " total infantry and fighters using _Diplomatic Boon_ on "
                        + planet.getRepresentation(game) + ".",
                getDiplomaticBoonPlacementButtons(game, player, planetName, planet.getInfluence()));

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_UNIT_DBOON)
    public static void placeUnitUsingDiplomaticBoon(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.replace(PLACE_UNIT_DBOON, "").split("~", 4);
        if (payload.length != 4) {
            return;
        }

        String planetName = payload[0];
        int remaining;
        try {
            remaining = Integer.parseInt(payload[1]);
        } catch (NumberFormatException e) {
            return;
        }
        if (remaining < 1) {
            return;
        }

        String unit = payload[2];
        String destination = payload[3];
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null || (!"infantry".equals(unit) && !"fighter".equals(unit))) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find planet or tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if ("infantry".equals(unit)) {
            Planet destinationPlanet = game.getUnitHolderFromPlanet(destination);
            Tile destinationTile = game.getTileFromPlanet(destination);
            if (destinationPlanet == null
                    || destinationTile == null
                    || !player.getPlanets().contains(destination)
                    || !tile.getPosition().equals(destinationTile.getPosition())) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Could not find an eligible destination planet.");
                ButtonHelper.deleteMessage(event);
                return;
            }
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + destination);
        } else {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fighter");
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed 1 " + unit + " using _Diplomatic Boon_.");

        if (remaining > 1) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + " may place " + (remaining - 1)
                            + " more total infantry and fighters using _Diplomatic Boon_.",
                    getDiplomaticBoonPlacementButtons(game, player, planetName, remaining - 1));
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_DBOON)
    public static void finishDiplomaticBoonPlacement(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getDiplomaticBoonPlacementButtons(
            Game game, Player player, String planetName, int remaining) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            return List.of(Buttons.red(player.factionButtonChecker() + FINISH_DBOON, "Done Placing"));
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + PLACE_UNIT_DBOON + planetName + "~" + remaining + "~fighter~space",
                "Place 1 Fighter (" + remaining + " remaining)",
                UnitEmojis.fighter));
        for (String controlledPlanet : player.getPlanets()) {
            Planet destination = game.getUnitHolderFromPlanet(controlledPlanet);
            Tile destinationTile = game.getTileFromPlanet(controlledPlanet);
            if (destination != null
                    && destinationTile != null
                    && tile.getPosition().equals(destinationTile.getPosition())) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + PLACE_UNIT_DBOON + planetName + "~" + remaining + "~infantry~"
                                + controlledPlanet,
                        "Place 1 Infantry on " + destination.getRepresentation(game) + " (" + remaining + " remaining)",
                        UnitEmojis.infantry));
            }
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_DBOON, "Done Placing"));
        return buttons;
    }

    // Economic Boon
    public static Button getEconomicBoonCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_EBOON, "Ready Planet with Economic Boon", CardEmojis.RelicCard);
    }

    public static Button getNaturesBoonSpendButton(Player player, String whatIsItFor) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_NBOON + whatIsItFor, "Use Nature's Boon", CardEmojis.RelicCard);
    }

    @ButtonHandler(USE_EBOON)
    public static void resolveEconomicBoon(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasRelicReady("economicboon")) {
            return;
        }

        List<Button> buttons = Helper.getPlanetRefreshButtons(player, game);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        player.addExhaustedRelic("economicboon");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the exhausted planet you wish to ready with _Economic Boon_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_NBOON)
    public static void offerNaturesBoonPlanets(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("naturesboon")) {
            return;
        }

        String whatIsItFor = buttonID.substring(USE_NBOON.length());
        String paymentMessageId = event.getMessageId();
        List<Button> buttons = getNaturesBoonPlanetButtons(player, game, whatIsItFor, paymentMessageId);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", spend a planet before using _Nature's Boon_.");
            return;
        }

        String message =
                player.getRepresentation() + ", choose a planet already spent for this payment with _Nature's Boon_.";
        String buttonPrefix =
                player.factionButtonChecker() + CHOOSE_NBOON_PLANET + whatIsItFor + "|" + paymentMessageId + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(CHOOSE_NBOON_PLANET)
    public static void resolveNaturesBoon(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("naturesboon")) {
            return;
        }

        String payload = buttonID.substring(CHOOSE_NBOON_PLANET.length());
        String[] data = payload.split("\\|", 3);
        if (data.length != 3) {
            return;
        }
        String whatIsItFor = data[0];
        String paymentMessageId = data[1];
        String planetName = data[2];
        List<Button> buttons = getNaturesBoonPlanetButtons(player, game, whatIsItFor, paymentMessageId);
        String message =
                player.getRepresentation() + ", choose a planet already spent for this payment with _Nature's Boon_.";
        String buttonPrefix =
                player.factionButtonChecker() + CHOOSE_NBOON_PLANET + whatIsItFor + "|" + paymentMessageId + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        if (!player.getSpentThingsThisWindow().contains(planetName)
                || !player.getExhaustedPlanets().contains(planetName)
                || !game.getPlanetsInfo().containsKey(planetName)) {
            return;
        }

        player.addExhaustedRelic("naturesboon");
        player.addSpentThing("naturesboon_" + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted _Nature's Boon_ for "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        event.getChannel()
                .retrieveMessageById(paymentMessageId)
                .queue(
                        paymentMessage -> {
                            List<Button> paymentButtons = new ArrayList<>();
                            for (ActionRow row :
                                    paymentMessage.getComponentTree().findAll(ActionRow.class)) {
                                for (ActionRowChildComponentUnion component : row.getComponents()) {
                                    if (component instanceof Button button
                                            && (button.getCustomId() == null
                                                    || !button.getCustomId().contains(USE_NBOON))) {
                                        paymentButtons.add(button);
                                    }
                                }
                            }
                            paymentMessage
                                    .editMessage(Helper.buildSpentThingsMessage(player, game, whatIsItFor))
                                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(paymentButtons))
                                    .queue(Consumers.nop(), BotLogger::catchRestError);
                        },
                        BotLogger::catchRestError);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getNaturesBoonPlanetButtons(
            Player player, Game game, String whatIsItFor, String paymentMessageId) {
        List<Button> buttons = new ArrayList<>();
        for (String spentThing : player.getSpentThingsThisWindow()) {
            if (!player.getExhaustedPlanets().contains(spentThing)
                    || !game.getPlanetsInfo().containsKey(spentThing)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker()
                            + CHOOSE_NBOON_PLANET
                            + whatIsItFor
                            + "|"
                            + paymentMessageId
                            + "|"
                            + spentThing,
                    Helper.getPlanetRepresentation(spentThing, game)));
        }
        return buttons;
    }

    public static void initializeBoon(Game game, Player player, String boon, Tile tile, String planetName) {
        if (game == null || player == null || tile == null) return;
        Planet planet = tile.getPlanetUnitHolders().stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(planetName))
                .findFirst()
                .orElse(null);
        var adjacentPositions = FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false);
        int tokens =
                switch (boon) {
                    case "economicboon" ->
                        (int) game.getTileMap().values().stream()
                                .filter(adjacent -> !adjacent.getTileModel().isHyperlane())
                                .filter(adjacent -> !adjacent.getPosition().equals(tile.getPosition()))
                                .filter(adjacent -> adjacentPositions.contains(adjacent.getPosition()))
                                .mapToLong(adjacent -> adjacent.getPlanetUnitHolders().stream()
                                        .filter(candidate -> !candidate.isSpaceStation())
                                        .count())
                                .sum();
                    case "naturesboon" -> planet == null ? 0 : planet.getResources() + 1;
                    case "diplomaticboon" -> planet == null ? 0 : planet.getInfluence() + 1;
                    case "cosmicboon" ->
                        (int) game.getTileMap().values().stream()
                                        .filter(adjacent ->
                                                !adjacent.getTileModel().isHyperlane())
                                        .filter(adjacent -> adjacentPositions.contains(adjacent.getPosition()))
                                        .filter(adjacent ->
                                                adjacent.getPlanetUnitHolders().isEmpty())
                                        .count()
                                + 1;
                    default -> 0;
                };
        if (tokens == 0) {
            player.removeRelic(boon);
            player.removeExhaustedRelic(boon);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " discarded _"
                            + Mapper.getRelic(boon).getName() + "_ because it has no control tokens.");
            return;
        }
        game.setStoredValue(BOON_TOKENS + player.getFaction() + "_" + boon, Integer.toString(tokens));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + tokens + " control token" + (tokens == 1 ? "" : "s")
                        + " on _" + Mapper.getRelic(boon).getName() + "_.");
    }

    public static int getBoonTokens(Game game, Player player, String boon) {
        try {
            return Integer.parseInt(game.getStoredValue(BOON_TOKENS + player.getFaction() + "_" + boon));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean spendBoonTokens(Game game, Player player, String boon, int amount) {
        int remaining = getBoonTokens(game, player, boon) - amount;
        if (!player.hasRelic(boon) || amount < 1 || remaining < 0) return false;
        if (remaining == 0) {
            game.removeStoredValue(BOON_TOKENS + player.getFaction() + "_" + boon);
            player.removeRelic(boon);
            player.removeExhaustedRelic(boon);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " discarded _"
                            + Mapper.getRelic(boon).getName() + "_ after removing its final control token.");
        } else {
            game.setStoredValue(BOON_TOKENS + player.getFaction() + "_" + boon, Integer.toString(remaining));
        }
        return true;
    }

    public static Button getEconomicBoonStartTurnButton(Game game, Player player) {
        return player.hasRelic("economicboon") && getBoonTokens(game, player, "economicboon") > 0
                ? Buttons.green(player.factionButtonChecker() + "boonEconomic", "Use Economic Boon")
                : null;
    }

    @ButtonHandler("boonEconomic")
    public static void resolveEconomicBoonTokens(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasRelic("economicboon")) return;
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= getBoonTokens(game, player, "economicboon"); amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + ECONOMIC_AMOUNT + amount,
                    "Remove " + amount + " Token" + (amount == 1 ? "" : "s")));
        }
        if (!buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", choose how many _Economic Boon_ tokens to remove.",
                    buttons);
        }
    }

    @ButtonHandler(ECONOMIC_AMOUNT)
    public static void chooseEconomicBoonSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int amount;
        try {
            amount = Integer.parseInt(buttonID.substring(ECONOMIC_AMOUNT.length()));
        } catch (NumberFormatException e) {
            return;
        }
        List<Button> buttons = game.getTileMap().values().stream()
                .filter(tile -> !tile.getTileModel().isHyperlane())
                .filter(tile -> tile.getUnitHolders().values().stream()
                        .anyMatch(holder -> holder.getUnitCount(UnitType.Spacedock, player) > 0))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + ECONOMIC_SYSTEM + amount + "|" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        if (amount > 0 && amount <= getBoonTokens(game, player, "economicboon") && !buttons.isEmpty()) {
            MessageHelper.editMessageWithButtons(
                    event,
                    player.getRepresentationNoPing() + ", choose a space dock system for _Economic Boon_.",
                    buttons);
        }
    }

    @ButtonHandler(ECONOMIC_SYSTEM)
    public static void chooseEconomicBoonShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(ECONOMIC_SYSTEM.length()).split("\\|", 2);
        if (values.length != 2) return;
        int amount;
        try {
            amount = Integer.parseInt(values[0]);
        } catch (NumberFormatException e) {
            return;
        }
        Tile tile = game.getTileByPosition(values[1]);
        if (tile == null
                || tile.getUnitHolders().values().stream()
                        .noneMatch(holder -> holder.getUnitCount(UnitType.Spacedock, player) > 0)) return;
        List<Button> buttons = player.getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .filter(unit -> unit.getUnitType() != UnitType.Fighter && unit.getCost() <= amount)
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + ECONOMIC_SHIP + amount + "|" + values[1] + "|"
                                + unit.getAsyncId(),
                        "Produce " + unit.getName(),
                        unit.getUnitEmoji()))
                .toList();
        if (amount > 0 && amount <= getBoonTokens(game, player, "economicboon") && !buttons.isEmpty()) {
            MessageHelper.editMessageWithButtons(
                    event, player.getRepresentationNoPing() + ", choose a ship to produce.", buttons);
        }
    }

    @ButtonHandler(ECONOMIC_SHIP)
    public static void produceEconomicBoonShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(ECONOMIC_SHIP.length()).split("\\|", 3);
        if (values.length != 3) return;
        int amount;
        try {
            amount = Integer.parseInt(values[0]);
        } catch (NumberFormatException e) {
            return;
        }
        Tile tile = game.getTileByPosition(values[1]);
        UnitModel unit = player.getUnitFromAsyncID(values[2]);
        if (tile == null
                || unit == null
                || !unit.getIsShip()
                || unit.getUnitType() == UnitType.Fighter
                || unit.getCost() > amount
                || tile.getUnitHolders().values().stream()
                        .noneMatch(holder -> holder.getUnitCount(UnitType.Spacedock, player) > 0)
                || !spendBoonTokens(game, player, "economicboon", amount)) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit.getAsyncId());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " produced 1 " + unit.getName() + " with _Economic Boon_.");
        ButtonHelper.deleteMessage(event);
    }

    public static Button getDiplomaticBoonCardsInfoButton(Game game, Player player) {
        return player.hasRelic("diplomaticboon") && getBoonTokens(game, player, "diplomaticboon") > 0
                ? Buttons.gray(player.factionButtonChecker() + "boonDiplomatic", "Use Diplomatic Boon")
                : null;
    }

    @ButtonHandler("boonDiplomatic")
    public static void chooseDiplomaticBoonAmount(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasRelic("diplomaticboon")) return;
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= getBoonTokens(game, player, "diplomaticboon"); amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + DIPLOMATIC_AMOUNT + amount,
                    "Remove " + amount + " Token" + (amount == 1 ? "" : "s")));
        }
        if (!buttons.isEmpty())
            MessageHelper.editMessageWithButtons(
                    event,
                    player.getRepresentationNoPing() + ", choose how many _Diplomatic Boon_ tokens to remove.",
                    buttons);
    }

    @ButtonHandler(DIPLOMATIC_AMOUNT)
    public static void chooseDiplomaticBoonPlacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int amount;
        try {
            amount = Integer.parseInt(buttonID.substring(DIPLOMATIC_AMOUNT.length()));
        } catch (NumberFormatException e) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.getTileModel().isHyperlane()
                    && !tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).isEmpty()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + DIPLOMATIC_PLACE + amount + "|fighter|" + tile.getPosition(),
                        "Place " + amount + " Fighter" + (amount == 1 ? "" : "s") + " In "
                                + tile.getRepresentationForButtons(game, player)));
            }
        }
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet != null)
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + DIPLOMATIC_PLACE + amount + "|infantry|" + planetName,
                        "Place " + amount + " Infantry On " + planet.getRepresentation(game)));
        }
        if (amount > 0 && amount <= getBoonTokens(game, player, "diplomaticboon") && !buttons.isEmpty()) {
            MessageHelper.editMessageWithButtons(
                    event,
                    player.getRepresentationNoPing() + ", choose where to place units with _Diplomatic Boon_.",
                    buttons);
        }
    }

    @ButtonHandler(DIPLOMATIC_PLACE)
    public static void placeDiplomaticBoonUnits(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(DIPLOMATIC_PLACE.length()).split("\\|", 3);
        if (values.length != 3) return;
        int amount;
        try {
            amount = Integer.parseInt(values[0]);
        } catch (NumberFormatException e) {
            return;
        }
        Tile tile = "fighter".equals(values[1]) ? game.getTileByPosition(values[2]) : game.getTileFromPlanet(values[2]);
        boolean eligible = tile != null
                && (("fighter".equals(values[1])
                                && !tile.getSpaceUnitHolder()
                                        .getUnitKeysForPlayer(player)
                                        .isEmpty())
                        || ("infantry".equals(values[1]) && player.getPlanets().contains(values[2])));
        if (!eligible || !spendBoonTokens(game, player, "diplomaticboon", amount)) return;
        AddUnitService.addUnits(
                event,
                tile,
                game,
                player.getColor(),
                amount + " " + values[1] + ("infantry".equals(values[1]) ? " " + values[2] : ""));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + amount + " " + values[1] + (amount == 1 ? "" : "s")
                        + " with _Diplomatic Boon_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerNaturesBoon(Game game, Player player) {
        if (!player.hasRelic("naturesboon") || getBoonTokens(game, player, "naturesboon") < 1) return;
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= Math.min(getBoonTokens(game, player, "naturesboon"), 12); amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + NATURE_AMOUNT + amount,
                    "Remove " + amount + " Token" + (amount == 1 ? "" : "s")));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may use _Nature's Boon_ to increase a controlled planet's resource value this tactical action.",
                buttons);
    }

    @ButtonHandler(NATURE_AMOUNT)
    public static void chooseNaturesBoonPlanetTokens(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int amount;
        try {
            amount = Integer.parseInt(buttonID.substring(NATURE_AMOUNT.length()));
        } catch (NumberFormatException e) {
            return;
        }
        List<Button> buttons = player.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(java.util.Objects::nonNull)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + NATURE_PLANET + amount + "|" + planet.getName(),
                        "Increase " + planet.getRepresentation(game) + " By " + amount))
                .toList();
        if (amount > 0 && amount <= getBoonTokens(game, player, "naturesboon") && !buttons.isEmpty()) {
            MessageHelper.editMessageWithButtons(
                    event, player.getRepresentationNoPing() + ", choose a planet for _Nature's Boon_.", buttons);
        }
    }

    @ButtonHandler(NATURE_PLANET)
    public static void resolveNaturesBoonTokens(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(NATURE_PLANET.length()).split("\\|", 2);
        if (values.length != 2) return;
        int amount;
        try {
            amount = Integer.parseInt(values[0]);
        } catch (NumberFormatException e) {
            return;
        }
        Planet planet = game.getUnitHolderFromPlanet(values[1]);
        if (planet == null || !player.getPlanets().contains(values[1])) return;
        List<String> attachments = new ArrayList<>();
        int remaining = amount;
        for (String attachment : List.of(
                "attachment_naturesboonres3a.png",
                "attachment_naturesboonres3b.png",
                "attachment_naturesboonres2a.png",
                "attachment_naturesboonres2b.png",
                "attachment_naturesboonres1a.png",
                "attachment_naturesboonres1b.png")) {
            int value = attachment.contains("res3") ? 3 : attachment.contains("res2") ? 2 : 1;
            if (remaining >= value && planet.addToken(attachment)) {
                attachments.add(attachment);
                remaining -= value;
            }
        }
        if (remaining > 0 || !spendBoonTokens(game, player, "naturesboon", amount)) {
            attachments.forEach(planet::removeToken);
            return;
        }
        game.setStoredValue(NATURE_BONUS + player.getFaction(), values[1] + "|" + String.join(",", attachments));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " increased " + planet.getRepresentation(game)
                        + "'s resource value by " + amount + " with _Nature's Boon_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void clearNaturesBoon(Game game, Player player) {
        String[] values =
                game.getStoredValue(NATURE_BONUS + player.getFaction()).split("\\|", 2);
        if (values.length == 2) {
            Planet planet = game.getUnitHolderFromPlanet(values[0]);
            if (planet != null) {
                if (values[1].matches("\\d+")) {
                    planet.addResourcesModifier(-Integer.parseInt(values[1]));
                } else {
                    for (String attachment : values[1].split(",")) {
                        if (attachment.startsWith("attachment_positiveres_naturesboon_")) {
                            planet.removeToken(attachment);
                            planet.addResourcesModifier(-1);
                        } else {
                            planet.removeToken(attachment);
                        }
                    }
                }
            }
        }
        game.removeStoredValue(NATURE_BONUS + player.getFaction());
    }

    public static Button getCosmicBoonTokenButton(Game game, Player player) {
        return player.hasRelic("cosmicboon") && getBoonTokens(game, player, "cosmicboon") > 0
                ? Buttons.green(player.factionButtonChecker() + "boonCosmic", "Use Cosmic Boon")
                : null;
    }

    @ButtonHandler("boonCosmic")
    public static void resolveCosmicBoonTokens(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (!player.hasRelic("cosmicboon") || tile == null || getBoonTokens(game, player, "cosmicboon") < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", _Cosmic Boon_ cannot be used without an active system and control token.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (!player.getPlanets().contains(planet.getName())) continue;
            for (String trait : planet.getPlanetTypes()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + COSMIC_EXPLORE + planet.getName() + "|" + trait,
                        "Explore " + planet.getRepresentation(game) + " As " + trait));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible planet to explore with _Cosmic Boon_.");
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", choose a planet in the active system to explore with _Cosmic Boon_.",
                buttons);
    }

    @ButtonHandler(COSMIC_EXPLORE)
    public static void exploreCosmicBoonPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(COSMIC_EXPLORE.length()).split("\\|", 2);
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        Planet planet = values.length == 2 ? game.getUnitHolderFromPlanet(values[0]) : null;
        if (tile == null
                || planet == null
                || !tile.getPlanetUnitHolders().contains(planet)
                || !player.getPlanets().contains(planet.getName())
                || !planet.getPlanetTypes().contains(values[1])
                || !spendBoonTokens(game, player, "cosmicboon", 1)) return;
        ExploreService.explorePlanet(event, tile, planet.getName(), values[1], player, true, game, 1, false);
        ButtonHelper.deleteMessage(event);
    }
}
