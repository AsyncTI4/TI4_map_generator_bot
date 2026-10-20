package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class WarGamesBrActionCardHandler {
    private static final String RESOLVE = "resolveWarGamesBr";
    private static final String SELECT_SYSTEM = "warGamesSystem_";
    private static final String ADD_UNIT = "warGamesAdd_";
    private static final String START_COMBAT = "warGamesStart_";
    private static final String ACTIVE_COMBAT_PREFIX = "warGamesActiveCombat_";
    private static final List<NeutralUnit> UNITS = List.of(
            new NeutralUnit(UnitType.Fighter, 1),
            new NeutralUnit(UnitType.Destroyer, 1),
            new NeutralUnit(UnitType.Cruiser, 2),
            new NeutralUnit(UnitType.Carrier, 3),
            new NeutralUnit(UnitType.Dreadnought, 4));

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve War Games"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolve(ButtonInteractionEvent event, Game game, Player player) {
        if (player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " needs 2 trade goods for _War Games_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasActualShipsInSystem(player, tile)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no system containing ships for _War Games_.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentationNoPing() + ", choose where to resolve _War Games_.", buttons);
    }

    @ButtonHandler(SELECT_SYSTEM)
    public static void selectSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_SYSTEM.length()));
        if (tile == null || !FoWHelper.playerHasActualShipsInSystem(player, tile) || player.getTg() < 2) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        player.setTg(player.getTg() - 2);
        game.setStoredValue(stateKey(player), tile.getPosition() + "|0|0|");
        MessageHelper.editMessageWithButtons(
                event, unitPrompt(game, player, tile, 0, 0), getUnitButtons(player, tile, 0, 0));
    }

    @ButtonHandler(ADD_UNIT)
    public static void addUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(ADD_UNIT.length()).split("\\|", 2);
        Tile tile = parts.length == 2 ? game.getTileByPosition(parts[0]) : null;
        NeutralUnit unit = parts.length == 2 ? unit(parts[1]) : null;
        State state = getState(game, player);
        if (tile == null
                || unit == null
                || state == null
                || !tile.getPosition().equals(state.position())
                || state.count() >= 4
                || state.cost() + unit.cost() > 5) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        AddUnitService.addUnits(
                event, tile, game, game.getNeutralColor(), "1 " + unit.type().getValue());
        Map<UnitType, Integer> selectedUnits = new LinkedHashMap<>(state.selectedUnits());
        selectedUnits.merge(unit.type(), 1, Integer::sum);
        State next = new State(state.position(), state.count() + 1, state.cost() + unit.cost(), selectedUnits);
        game.setStoredValue(stateKey(player), next.serialize());
        MessageHelper.editMessageWithButtons(
                event,
                unitPrompt(game, player, tile, next.count(), next.cost()),
                getUnitButtons(player, tile, next.count(), next.cost()));
    }

    @ButtonHandler(START_COMBAT)
    public static void startCombat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(START_COMBAT.length()));
        State state = getState(game, player);
        if (tile == null || state == null || !tile.getPosition().equals(state.position()) || state.count() < 1) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " paid 2 trade goods and begins _War Games_ in "
                        + tile.getRepresentationForButtons(game, player) + ".");
        StartCombatService.startSpaceCombat(game, player, game.getNeutral(), tile, event, "-war-games");
        ButtonHelper.deleteMessage(event);
    }

    public static void replaceDestroyedNeutralUnits(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits, boolean combat) {
        if (!combat || !isCombatActive(game)) return;
        StartCombatService.CurrentCombat currentCombat = StartCombatService.getCurrentCombat(game);
        for (Player player : game.getRealPlayers()) {
            State state = getState(game, player);
            if (state == null || !isCombatActiveForPlayer(game, currentCombat, player, state)) continue;
            Tile tile = game.getTileByPosition(state.position());
            if (tile == null) continue;
            Map<UnitType, Integer> selectedUnits = new LinkedHashMap<>(state.selectedUnits());
            boolean changed = false;
            for (RemovedUnit destroyedUnit : destroyedUnits) {
                if (!game.getNeutralColor().equals(destroyedUnit.unitKey().colorID())) continue;
                UnitType type = destroyedUnit.unitKey().unitType();
                int remaining = selectedUnits.getOrDefault(type, 0);
                int replacements = Math.min(remaining, destroyedUnit.getTotalRemoved());
                if (replacements < 1) continue;
                AddUnitService.addUnits(
                        event, tile, game, game.getNeutralColor(), replacements + " " + type.getValue());
                selectedUnits.put(type, remaining - replacements);
                changed = true;
            }
            if (changed) {
                game.setStoredValue(
                        stateKey(player),
                        new State(state.position(), state.count(), state.cost(), selectedUnits).serialize());
            }
        }
    }

    public static void beginCombat(Game game, Player player, Tile tile) {
        for (Player otherPlayer : game.getRealPlayers()) {
            if (otherPlayer != player) {
                game.removeStoredValue(stateKey(otherPlayer));
            }
            game.removeStoredValue(activeCombatKey(otherPlayer));
        }
        game.setStoredValue(activeCombatKey(player), tile.getPosition());
    }

    public static void clear(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(stateKey(player));
            game.removeStoredValue(activeCombatKey(player));
        }
    }

    public static boolean isCombatActive(Game game) {
        StartCombatService.CurrentCombat currentCombat = StartCombatService.getCurrentCombat(game);
        if (currentCombat == null
                || !currentCombat.factions().contains(game.getNeutral().getFaction())) {
            return false;
        }
        return game.getRealPlayers().stream().anyMatch(player -> {
            State state = getState(game, player);
            return state != null && isCombatActiveForPlayer(game, currentCombat, player, state);
        });
    }

    private static List<Button> getUnitButtons(Player player, Tile tile, int count, int cost) {
        List<Button> buttons = new ArrayList<>();
        for (NeutralUnit unit : UNITS) {
            if (count < 4 && cost + unit.cost() <= 5) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + ADD_UNIT + tile.getPosition() + "|"
                                + unit.type().name(),
                        "Add neutral " + unit.type().humanReadableName() + " (" + unit.cost() + ")"));
            }
        }
        if (count > 0) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + START_COMBAT + tile.getPosition(), "Resolve Space Combat"));
        }
        return buttons;
    }

    private static String unitPrompt(Game game, Player player, Tile tile, int count, int cost) {
        return player.getRepresentationNoPing() + ", add up to " + (4 - count) + " more neutral units costing up to "
                + (5 - cost) + " total in " + tile.getRepresentationForButtons(game, player)
                + ", then resolve the combat.";
    }

    private static String stateKey(Player player) {
        return "warGames_" + player.getFaction();
    }

    private static String activeCombatKey(Player player) {
        return ACTIVE_COMBAT_PREFIX + player.getFaction();
    }

    private static boolean isCombatActiveForPlayer(
            Game game, StartCombatService.CurrentCombat currentCombat, Player player, State state) {
        return currentCombat.tilePosition() != null
                && currentCombat.factions().contains(player.getFaction())
                && currentCombat.tilePosition().equals(state.position())
                && currentCombat.tilePosition().equals(game.getStoredValue(activeCombatKey(player)));
    }

    private static State getState(Game game, Player player) {
        String[] parts = game.getStoredValue(stateKey(player)).split("\\|", 4);
        if (parts.length != 4) return null;
        try {
            Map<UnitType, Integer> selectedUnits = new LinkedHashMap<>();
            if (!parts[3].isBlank()) {
                for (String entry : parts[3].split(",")) {
                    String[] unit = entry.split(":", 2);
                    if (unit.length == 2) selectedUnits.put(UnitType.valueOf(unit[0]), Integer.parseInt(unit[1]));
                }
            }
            return new State(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), selectedUnits);
        } catch (NumberFormatException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static NeutralUnit unit(String name) {
        return UNITS.stream()
                .filter(unit -> unit.type().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private record NeutralUnit(UnitType type, int cost) {}

    private record State(String position, int count, int cost, Map<UnitType, Integer> selectedUnits) {
        private String serialize() {
            String selected = selectedUnits.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(entry -> entry.getKey().name() + ":" + entry.getValue())
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            return position + "|" + count + "|" + cost + "|" + selected;
        }
    }
}
