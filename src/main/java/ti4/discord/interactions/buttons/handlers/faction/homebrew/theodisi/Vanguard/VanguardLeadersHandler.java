package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Vanguard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
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
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class VanguardLeadersHandler {
    private static final String UNLOCK = "unlockVanguardCommander";
    private static final String SELECT_PLANET = "selectVanguardAgentPlanet_";
    private static final String PLACE_MECH = "placeVanguardAgentMech_";
    private static final String USE_COMMANDER = "useVanguardCommander_";
    private static final String FINISH_COMMANDER_PAYMENT = "finishVanguardCommanderPayment_";
    private static final String PLACE_COMMANDER_UNIT = "placeVanguardCommanderUnit_";
    private static final String FINISH_HERO_PAYMENT = "finishVanguardHeroPayment";
    private static final String SELECT_HERO_SYSTEM = "selectVanguardHeroSystem_";
    private static final String PLACE_HERO_SHIP = "placeVanguardHeroShip_";
    private static final String FINISH_HERO = "finishVanguardHero";
    private static final String COMMANDER_USED = "vanguardCommanderUsed_";
    private static final String HERO_LIMIT = "vanguardHeroLimit_";
    private static final String HERO_SYSTEM = "vanguardHeroSystem_";
    private static final String HERO_COST = "vanguardHeroCost_";
    private static final String HERO_SHIPS = "vanguardHeroShips_";

    public static Button getCommanderUnlockButton(Player player) {
        if (player == null || !player.hasLeader("vanguardcommander") || player.hasLeaderUnlocked("vanguardcommander")) {
            return null;
        }
        return Buttons.green(
                player.factionButtonChecker() + UNLOCK, "Unlock Vanguard Commander", FactionEmojis.vanguard);
    }

    @ButtonHandler(UNLOCK)
    public static void unlockCommander(ButtonInteractionEvent event, Game game, Player player) {
        if (player == null
                || game == null
                || !player.hasLeader("vanguardcommander")
                || player.hasLeaderUnlocked("vanguardcommander")) {
            return;
        }
        UnlockLeaderService.unlockLeader("vanguardcommander", game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveVanguardAgentTarget(Game game, Player target) {
        if (game == null || target == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        Tile homeSystem = target.getHomeSystemTile();
        for (Tile tile : game.getTileMap().values()) {
            if (homeSystem != null && homeSystem.getPosition().equals(tile.getPosition())) {
                continue;
            }
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (ButtonHelper.getNumberOfGroundForces(target, planet) > 0) {
                    buttons.add(Buttons.green(
                            target.factionButtonChecker() + SELECT_PLANET + planet.getName(),
                            "Choose " + planet.getRepresentation(game)));
                }
            }
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentation() + " has no eligible non-home planet containing their ground forces.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + ", choose a non-home planet containing your ground forces for _Shieldmaiden Gabriella_.",
                buttons);
    }

    @ButtonHandler(SELECT_PLANET)
    public static void selectVanguardAgentPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(SELECT_PLANET.length());
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = tile == null ? null : tile.getUnitHolders().get(planetName) instanceof Planet p ? p : null;
        Tile homeSystem = player.getHomeSystemTile();
        if (planet == null
                || (homeSystem != null && homeSystem.getPosition().equals(tile.getPosition()))
                || ButtonHelper.getNumberOfGroundForces(player, planet) < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That planet is no longer eligible.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + PLACE_MECH + planetName + "_player",
                        "Place 1 Mech",
                        UnitEmojis.mech),
                Buttons.green(
                        player.factionButtonChecker() + PLACE_MECH + planetName + "_neutral",
                        "Place 1 Neutral Mech",
                        UnitEmojis.mech));
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentation() + ", choose which mech to place on " + planet.getRepresentation(game) + ".",
                buttons);
    }

    @ButtonHandler(PLACE_MECH)
    public static void placeVanguardAgentMech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(PLACE_MECH.length());
        int divider = payload.lastIndexOf('_');
        if (divider < 1) {
            return;
        }
        String planetName = payload.substring(0, divider);
        String mechOwner = payload.substring(divider + 1);
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = tile == null ? null : tile.getUnitHolders().get(planetName) instanceof Planet p ? p : null;
        Tile homeSystem = player.getHomeSystemTile();
        if (planet == null
                || (homeSystem != null && homeSystem.getPosition().equals(tile.getPosition()))
                || ButtonHelper.getNumberOfGroundForces(player, planet) < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That planet is no longer eligible.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player mechPlayer = "neutral".equals(mechOwner) ? game.getNeutral() : player;
        if (mechPlayer == null) {
            return;
        }
        AddUnitService.addUnits(event, tile, game, mechPlayer.getColor(), "1 mech " + planetName);
        ButtonHelper.deleteMessage(event);
        if (mechPlayer == game.getNeutral()) {
            StartCombatService.startGroundCombat(player, mechPlayer, game, event, planet, tile);
        }
    }

    public static void offerCommander(
            GenericInteractionCreateEvent event,
            Game game,
            RemovedUnit destroyedUnit,
            List<Player> possibleKillers,
            boolean combat) {
        if (!combat || destroyedUnit == null || possibleKillers == null || possibleKillers.isEmpty()) {
            return;
        }
        Player destroyedUnitOwner = destroyedUnit.getPlayer(game);
        UnitModel destroyedModel = destroyedUnitOwner == null
                ? null
                : destroyedUnitOwner.getPriorityUnitByAsyncID(
                        destroyedUnit.unitKey().asyncID(), destroyedUnit.uh());
        if (destroyedModel == null) {
            return;
        }
        for (Player killer : possibleKillers.stream().distinct().toList()) {
            if (!game.playerHasLeaderUnlockedOrAlliance(killer, "vanguardcommander")
                    || killer == destroyedUnitOwner
                    || !game.getStoredValue(COMMANDER_USED + killer.getFaction())
                            .isBlank()) {
                continue;
            }
            UnitModel replacement = killer.getUnitByType(destroyedModel.getUnitType());
            if (replacement == null) {
                continue;
            }
            String payload = destroyedUnit.tile().getPosition() + "|"
                    + destroyedUnit.unitKey().unitType().name() + "|" + Math.ceil(destroyedModel.getCost());
            List<Button> buttons = List.of(
                    Buttons.green(
                            killer.factionButtonChecker() + USE_COMMANDER + payload,
                            "Use Shieldbrother Michael",
                            FactionEmojis.vanguard),
                    Buttons.red(killer.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    killer.getCorrectChannel(),
                    killer.getRepresentationNoPing() + ", you destroyed " + destroyedModel.getName()
                            + " in combat. You may pay " + (int) Math.ceil(destroyedModel.getCost())
                            + " influence to place 1 " + replacement.getName()
                            + " in that system with **Shieldbrother Michael**.",
                    buttons);
        }
    }

    @ButtonHandler(USE_COMMANDER)
    public static void useCommander(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_COMMANDER.length()).split("\\|", 3);
        if (payload.length != 3 || !game.playerHasLeaderUnlockedOrAlliance(player, "vanguardcommander")) {
            return;
        }
        Tile tile = game.getTileByPosition(payload[0]);
        UnitModel replacement;
        try {
            replacement = player.getUnitByType(ti4.helpers.Units.UnitType.valueOf(payload[1]));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (tile == null
                || replacement == null
                || !game.getStoredValue(COMMANDER_USED + player.getFaction()).isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(COMMANDER_USED + player.getFaction(), "used");
        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        paymentButtons.add(Buttons.green(
                player.factionButtonChecker() + FINISH_COMMANDER_PAYMENT + payload[0] + "|" + replacement.getAsyncId(),
                "Done Paying Influence"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", pay " + payload[2]
                        + " influence for **Shieldbrother Michael**, then press Done Paying Influence.",
                paymentButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_COMMANDER_PAYMENT)
    public static void finishCommanderPayment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(FINISH_COMMANDER_PAYMENT.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        UnitModel replacement = payload.length == 2 ? player.getUnitFromAsyncID(payload[1]) : null;
        if (tile == null
                || replacement == null
                || game.getStoredValue(COMMANDER_USED + player.getFaction()).isBlank()) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            if (replacement.getIsShip() != "space".equals(holder.getName())
                    || holder.countPlayersUnitsWithModelCondition(player, unit -> true) < 1) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_COMMANDER_UNIT + tile.getPosition() + "|" + holder.getName()
                            + "|" + replacement.getAsyncId(),
                    "Place 1 " + replacement.getName() + " on "
                            + ("space".equals(holder.getName()) ? "Space" : ((Planet) holder).getRepresentation(game)),
                    replacement.getUnitEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There is no eligible location in that system.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose where to place your " + replacement.getName()
                        + " with **Shieldbrother Michael**.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_COMMANDER_UNIT)
    public static void placeCommanderUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PLACE_COMMANDER_UNIT.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null || payload.length != 3
                ? null
                : tile.getUnitHolders().get(payload[1]);
        UnitModel replacement = payload.length == 3 ? player.getUnitFromAsyncID(payload[2]) : null;
        if (holder == null
                || replacement == null
                || replacement.getIsShip() != "space".equals(holder.getName())
                || holder.countPlayersUnitsWithModelCondition(player, unit -> true) < 1) {
            return;
        }
        AddUnitService.addUnits(
                event, tile, game, player.getColor(), "1 " + replacement.getAsyncId() + " " + holder.getName());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 " + replacement.getName()
                        + " with **Shieldbrother Michael**.");
        ButtonHelper.deleteMessage(event);
    }

    public static void startHero(GenericInteractionCreateEvent event, Game game, Player player) {
        player.resetSpentThings();
        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "both"));
        paymentButtons.add(Buttons.green(player.factionButtonChecker() + FINISH_HERO_PAYMENT, "Done Spending"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", spend any amount of resources or influence for _Final Aegis - Last Line_, then press Done Spending.",
                paymentButtons);
    }

    @ButtonHandler(FINISH_HERO_PAYMENT)
    public static void finishHeroPayment(ButtonInteractionEvent event, Game game, Player player) {
        int limit = getSpentValue(game, player) + 5;
        game.setStoredValue(HERO_LIMIT + player.getFaction(), Integer.toString(limit));
        game.setStoredValue(HERO_COST + player.getFaction(), "0");
        game.removeStoredValue(HERO_SYSTEM + player.getFaction());
        game.removeStoredValue(HERO_SHIPS + player.getFaction());
        sendHeroSystemButtons(event, game, player, "");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_HERO_SYSTEM)
    public static void selectHeroSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getHeroSystemButtons(game, player),
                player.getRepresentationNoPing() + ", choose a non-home system for _Final Aegis - Last Line_.",
                player.factionButtonChecker() + SELECT_HERO_SYSTEM,
                buttonID)) {
            return;
        }
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_HERO_SYSTEM.length()));
        if (tile == null || tile.isHomeSystem(game) || tile.getTileModel().isHyperlane()) {
            return;
        }
        game.setStoredValue(HERO_SYSTEM + player.getFaction(), tile.getPosition());
        sendHeroShipButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_HERO_SHIP)
    public static void placeHeroShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(game.getStoredValue(HERO_SYSTEM + player.getFaction()));
        UnitModel ship = game.getNeutral().getUnitFromAsyncID(buttonID.substring(PLACE_HERO_SHIP.length()));
        if (tile == null || ship == null || !ship.getIsShip() || hasHeroShipType(game, player, ship)) {
            return;
        }
        float cost = getHeroCost(game, player);
        int limit = getHeroLimit(game, player);
        if (ship.getCost() <= 0 || cost + ship.getCost() > limit) {
            return;
        }
        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), "1 " + ship.getAsyncId());
        game.setStoredValue(HERO_COST + player.getFaction(), Float.toString(cost + ship.getCost()));
        String selected = game.getStoredValue(HERO_SHIPS + player.getFaction());
        game.setStoredValue(
                HERO_SHIPS + player.getFaction(),
                selected.isBlank() ? ship.getAsyncId() : selected + "," + ship.getAsyncId());
        sendHeroShipButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_HERO)
    public static void finishHero(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getStoredValue(HERO_SYSTEM + player.getFaction()));
        String ships = game.getStoredValue(HERO_SHIPS + player.getFaction());
        game.removeStoredValue(HERO_LIMIT + player.getFaction());
        game.removeStoredValue(HERO_SYSTEM + player.getFaction());
        game.removeStoredValue(HERO_COST + player.getFaction());
        game.removeStoredValue(HERO_SHIPS + player.getFaction());
        if (tile != null) {
            String shipNames = ships.isBlank()
                    ? "no ships"
                    : List.of(ships.split(",")).stream()
                            .map(game.getNeutral()::getUnitFromAsyncID)
                            .filter(java.util.Objects::nonNull)
                            .map(UnitModel::getName)
                            .collect(Collectors.joining(", "));
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " resolved _Final Aegis - Last Line_ in "
                            + tile.getRepresentationForButtons(game, player) + ", placing " + shipNames + ".");
            StartCombatService.combatCheck(game, event, tile);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void clearCommanderState(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(COMMANDER_USED + player.getFaction());
        }
    }

    private static void sendHeroSystemButtons(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String message = player.getRepresentationNoPing() + ", choose a non-home system for _Final Aegis - Last Line_.";
        String prefix = player.factionButtonChecker() + SELECT_HERO_SYSTEM;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(getHeroSystemButtons(game, player), prefix, 0));
    }

    private static List<Button> getHeroSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> !tile.getTileModel().isHyperlane())
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_HERO_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static void sendHeroShipButtons(ButtonInteractionEvent event, Game game, Player player) {
        float cost = getHeroCost(game, player);
        int limit = getHeroLimit(game, player);
        List<Button> buttons = game.getNeutral().getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .filter(ship -> ship.getCost() > 0 && cost + ship.getCost() <= limit)
                .filter(ship -> !hasHeroShipType(game, player, ship))
                .map(ship -> Buttons.green(
                        player.factionButtonChecker() + PLACE_HERO_SHIP + ship.getAsyncId(),
                        "Place 1 " + ship.getName() + " (" + ship.getCost() + ")",
                        ship.getUnitEmoji()))
                .collect(Collectors.toCollection(ArrayList::new));
        buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_HERO, "Done Placing Ships"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", place neutral ships of different types with _Final Aegis - Last Line_.\n"
                        + "-# Combined cost: " + cost + "/" + limit + ".",
                buttons);
    }

    private static boolean hasHeroShipType(Game game, Player player, UnitModel ship) {
        return List.of(game.getStoredValue(HERO_SHIPS + player.getFaction()).split(",")).stream()
                .filter(id -> !id.isBlank())
                .map(game.getNeutral()::getUnitFromAsyncID)
                .filter(java.util.Objects::nonNull)
                .anyMatch(selected -> selected.getUnitType() == ship.getUnitType());
    }

    private static int getSpentValue(Game game, Player player) {
        int spent = player.getSpentTgsThisWindow();
        for (String thing : player.getSpentThingsThisWindow()) {
            Planet planet = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(thing.toLowerCase(Locale.ROOT)));
            if (planet != null) {
                spent += Math.max(planet.getResources(), planet.getInfluence());
            }
        }
        return spent;
    }

    private static int getHeroLimit(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(HERO_LIMIT + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static float getHeroCost(Game game, Player player) {
        try {
            return Float.parseFloat(game.getStoredValue(HERO_COST + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
