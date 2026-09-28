package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Scrapyard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class ScrapyardUnitHandler {
    private static final String USE_FUEL_CELL = "useFuelCell_";
    private static final String SELECT_FUEL_CELL_AMOUNT = "selectFuelCellAmount_";
    private static final String SELECT_FUEL_CELL_SYSTEM = "selectFuelCellSystem_";
    private static final String DESTROY_FUEL_CELL_FIGHTER = "destroyFuelCellFighter_";
    private static final String BACK_TO_FUEL_CELL_SYSTEMS = "backToFuelCellSystems";
    private static final String FINISH_FUEL_CELL = "finishFuelCell";
    private static final String USE_DREG = "useScrapyardDreg_";
    private static final String DAMAGE_DREG = "damageScrapyardDreg_";

    public static void addDregCombatButton(
            List<Button> buttons, Game game, Player player, Player opponent, Tile tile, boolean spaceCombat) {
        if (buttons == null
                || game == null
                || player == null
                || opponent == null
                || tile == null
                || !spaceCombat
                || getUndamagedDregCount(tile, player) < 1) {
            return;
        }
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + USE_DREG + tile.getPosition() + "|" + opponent.getFaction(),
                "Use Dreg",
                FactionEmojis.scrapyard));
    }

    @ButtonHandler(USE_DREG)
    public static void useDreg(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_DREG.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Player opponent = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        if (tile == null || opponent == null || opponent == player || getUndamagedDregCount(tile, player) < 1) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = getDregButtons(player, tile, opponent);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose a Dreg in the space area to damage.",
                buttons);
    }

    @ButtonHandler(DAMAGE_DREG)
    public static void damageDreg(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DAMAGE_DREG.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        Player opponent = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        UnitState state = payload.length == 3 ? Units.findUnitState(payload[2]) : null;
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        if (state == null || state.isDamaged()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitKey mech = space == null
                ? null
                : space.getUnitKeysForPlayer(player).stream()
                        .filter(unitKey -> unitKey.unitType() == UnitType.Mech)
                        .filter(unitKey -> {
                            var unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), space);
                            return unit != null && "scrapyard_mech".equals(unit.getId());
                        })
                        .filter(unitKey -> space.getUnitCountForState(unitKey, state) > 0)
                        .findFirst()
                        .orElse(null);
        if (tile == null
                || opponent == null
                || opponent == player
                || mech == null
                || getUndamagedDregCount(tile, player) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        tile.addUnitDamage(space.getName(), mech, 1);
        CommanderUnlockCheckService.checkPlayer(player, "ponthous");
        ButtonHelper.deleteMessage(event);
        List<Button> hitButtons = List.of(
                Buttons.green(
                        opponent.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_1",
                        "Auto-assign 1 Hit"),
                Buttons.red(
                        opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition()
                                + "deleteThis_spacecombat",
                        "Manually Assign 1 Hit"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " damaged 1 Dreg to produce 1 hit. "
                        + opponent.getRepresentationNoPing() + ", assign the produced hit.",
                hitButtons);
    }

    public static void offerFuelCellButton(Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasAnyUnit("scrapyard_fighter", "scrapyard_fighter2")
                || getFuelCellFighterCount(game, player) < 1) return;
        String fuelCell = player.hasUnit("scrapyard_fighter2") ? "Fuel Cell II" : "Fuel Cell I";
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may use _" + fuelCell + "_.",
                Buttons.green(player.factionButtonChecker() + USE_FUEL_CELL, "Use " + fuelCell, UnitEmojis.fighter));
    }

    public static void clearFuelCell(Game game, Player player) {
        if (game != null && player != null) game.removeStoredValue(fuelCellKey(player));
    }

    @ButtonHandler(USE_FUEL_CELL)
    public static void useFuelCell(ButtonInteractionEvent event, Game game, Player player) {
        int fighters = getFuelCellFighterCount(game, player);
        if (fighters < 1 || !player.hasAnyUnit("scrapyard_fighter", "scrapyard_fighter2")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(fuelCellKey(player));
        ButtonHelper.deleteMessage(event);
        sendFuelCellAmountButtons(event, player, fighters, 0);
    }

    @ButtonHandler(SELECT_FUEL_CELL_AMOUNT)
    public static void selectFuelCellAmount(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int fighters = getFuelCellFighterCount(game, player);
        String message = player.getRepresentation() + ", choose how many fighters to destroy with _Fuel Cell_.";
        String prefix = player.factionButtonChecker() + SELECT_FUEL_CELL_AMOUNT;
        List<Button> amounts = getFuelCellAmountButtons(player, fighters);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                amounts,
                List.of(Buttons.gray("deleteButtons", "Decline")),
                message,
                prefix,
                buttonID)) return;
        int amount;
        try {
            amount = Integer.parseInt(buttonID.substring(SELECT_FUEL_CELL_AMOUNT.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (amount < 1 || amount > fighters) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(fuelCellKey(player), amount + "|0");
        ButtonHelper.deleteMessage(event);
        sendFuelCellSystemButtons(event, game, player);
    }

    @ButtonHandler(SELECT_FUEL_CELL_SYSTEM)
    public static void selectFuelCellSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_FUEL_CELL_SYSTEM.length()));
        int[] progress = getFuelCellProgress(game, player);
        if (tile == null || progress == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (progress[1] >= progress[0]) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", return to the system list when you are ready.",
                    List.of(Buttons.gray(
                            player.factionButtonChecker() + BACK_TO_FUEL_CELL_SYSTEMS, "Back to Systems")));
            return;
        }
        if (getFuelCellFighterCount(tile, player) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitHolder space = tile.getSpaceUnitHolder();
        UnitKey fighter = space.getUnitKeysForPlayer(player).stream()
                .filter(key -> key.unitType() == UnitType.Fighter)
                .findFirst()
                .orElse(null);
        if (fighter == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitState state : UnitState.values()) {
            if (space.getUnitCountForState(fighter, state) > 0) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + DESTROY_FUEL_CELL_FIGHTER + tile.getPosition() + "|" + state,
                        "Destroy 1 Fighter" + (state.isDamaged() ? " (damaged)" : ""),
                        UnitEmojis.fighter));
            }
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + BACK_TO_FUEL_CELL_SYSTEMS, "Back to Systems"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", destroy fighters in " + tile.getRepresentationForButtons(game, player)
                        + ".",
                buttons);
    }

    @ButtonHandler(DESTROY_FUEL_CELL_FIGHTER)
    public static void destroyFuelCellFighter(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(DESTROY_FUEL_CELL_FIGHTER.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        UnitState state = payload.length == 2 ? Units.findUnitState(payload[1]) : null;
        int[] progress = getFuelCellProgress(game, player);
        if (tile == null || state == null || progress == null || progress[1] >= progress[0]) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitHolder space = tile.getSpaceUnitHolder();
        UnitKey fighter = space.getUnitKeysForPlayer(player).stream()
                .filter(key -> key.unitType() == UnitType.Fighter)
                .findFirst()
                .orElse(null);
        if (fighter == null || space.getUnitCountForState(fighter, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(fighter, 1, space.getName()), false, state);
        game.setStoredValue(fuelCellKey(player), progress[0] + "|" + (progress[1] + 1));
        selectFuelCellSystem(event, game, player, SELECT_FUEL_CELL_SYSTEM + tile.getPosition());
    }

    @ButtonHandler(BACK_TO_FUEL_CELL_SYSTEMS)
    public static void backToFuelCellSystems(ButtonInteractionEvent event, Game game, Player player) {
        if (getFuelCellProgress(game, player) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        sendFuelCellSystemButtons(event, game, player);
    }

    @ButtonHandler(FINISH_FUEL_CELL)
    public static void finishFuelCell(ButtonInteractionEvent event, Game game, Player player) {
        int[] progress = getFuelCellProgress(game, player);
        if (progress == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int ships = progress[1] / (player.hasUnit("scrapyard_fighter2") ? 2 : 4);
        game.removeStoredValue(fuelCellKey(player));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " destroyed " + progress[1]
                        + " fighter" + (progress[1] == 1 ? "" : "s") + " with _Fuel Cell_. They may apply +1 move to "
                        + ships
                        + " ship" + (ships == 1 ? "" : "s") + " this action.");
    }

    private static void sendFuelCellAmountButtons(ButtonInteractionEvent event, Player player, int fighters, int page) {
        String message = player.getRepresentation() + ", choose how many fighters to destroy with _Fuel Cell_.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(
                        getFuelCellAmountButtons(player, fighters),
                        List.of(Buttons.gray("deleteButtons", "Decline")),
                        player.factionButtonChecker() + SELECT_FUEL_CELL_AMOUNT,
                        25,
                        page,
                        false));
    }

    private static List<Button> getFuelCellAmountButtons(Player player, int fighters) {
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= fighters; amount++) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + SELECT_FUEL_CELL_AMOUNT + amount,
                    Integer.toString(amount),
                    UnitEmojis.fighter));
        }
        return buttons;
    }

    private static void sendFuelCellSystemButtons(ButtonInteractionEvent event, Game game, Player player) {
        int[] progress = getFuelCellProgress(game, player);
        if (progress == null) return;
        List<Button> buttons = game.getTileMap().values().stream()
                .filter(tile -> getFuelCellFighterCount(tile, player) > 0)
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_FUEL_CELL_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        UnitEmojis.fighter))
                .toList();
        List<Button> withDone = new ArrayList<>(buttons);
        withDone.add(Buttons.gray(player.factionButtonChecker() + FINISH_FUEL_CELL, "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose a system containing your fighters. Destroyed: " + progress[1]
                        + " / " + progress[0] + ".",
                withDone);
    }

    private static int getFuelCellFighterCount(Game game, Player player) {
        return game.getTileMap().values().stream()
                .mapToInt(tile -> getFuelCellFighterCount(tile, player))
                .sum();
    }

    private static List<Button> getDregButtons(Player player, Tile tile, Player opponent) {
        List<Button> buttons = new ArrayList<>();
        UnitHolder space = tile.getSpaceUnitHolder();
        for (UnitKey mech : space.getUnitKeysForPlayer(player)) {
            var unit = player.getPriorityUnitByAsyncID(mech.asyncID(), space);
            if (mech.unitType() != UnitType.Mech || unit == null || !"scrapyard_mech".equals(unit.getId())) {
                continue;
            }
            for (UnitState state : UnitState.values()) {
                int count = space.getUnitCountForState(mech, state);
                if (count > 0 && !state.isDamaged()) {
                    buttons.add(Buttons.red(
                            player.factionButtonChecker() + DAMAGE_DREG + tile.getPosition() + "|"
                                    + opponent.getFaction() + "|" + state,
                            "Damage 1 Dreg" + (count > 1 ? " (" + count + " available)" : ""),
                            UnitEmojis.mech));
                }
            }
        }
        return buttons;
    }

    private static int getUndamagedDregCount(Tile tile, Player player) {
        return tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .filter(unitKey -> unitKey.unitType() == UnitType.Mech)
                .filter(unitKey -> {
                    var unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), tile.getSpaceUnitHolder());
                    return unit != null && "scrapyard_mech".equals(unit.getId());
                })
                .mapToInt(unitKey -> Arrays.stream(UnitState.values())
                        .filter(state -> !state.isDamaged())
                        .mapToInt(state -> tile.getSpaceUnitHolder().getUnitCountForState(unitKey, state))
                        .sum())
                .sum();
    }

    private static int getFuelCellFighterCount(Tile tile, Player player) {
        return tile.getSpaceUnitHolder().getUnitCount(UnitType.Fighter, player);
    }

    private static int[] getFuelCellProgress(Game game, Player player) {
        String[] values = game.getStoredValue(fuelCellKey(player)).split("\\|", 2);
        if (values.length != 2) return null;
        try {
            return new int[] {Integer.parseInt(values[0]), Integer.parseInt(values[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fuelCellKey(Player player) {
        return "scrapyardFuelCell" + player.getFaction();
    }
}
