package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.choir;

import java.util.ArrayList;
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
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.TechEmojis;

@UtilityClass
public class ChoirTechHandler {
    private static final String TOMB_LAMENT = "bechoirylament";
    private static final String TOMB_REQUIEM = "bechoiryrequiem";
    private static final String ECHO_LAMENT = "bechoirrlament";
    private static final String ECHO_REQUIEM = "bechoirrrequiem";
    private static final String TOMB_DISCOUNT = "choirTombResonanceDiscount_";
    private static final String TOMB_TARGET = "choirTombTarget_";
    private static final String ECHO_START = "choirEchoStart_";
    private static final String ECHO_TARGET = "choirEchoTarget_";

    public static void addTombResonancePaymentButtons(List<Button> buttons, Game game, Player player, Tile tile) {
        if (buttons == null || game == null || player == null || tile == null || getTombSide(player) == null) {
            return;
        }
        game.removeStoredValue(getTombDiscountKey(player));
        String side = getTombSide(player);
        buttons.addAll(getTombTargets(game, player, tile, side));
    }

    @ButtonHandler(TOMB_TARGET)
    public static void resolveTombResonanceTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(TOMB_TARGET.length()).split("\\|", 4);
        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getSpaceUnitHolder();
        UnitKey unitKey = payload.length == 4 ? getUnitKey(holder, payload[1], payload[2]) : null;
        UnitModel unit = unitKey == null ? null : game.getUnitFromUnitKey(unitKey);
        String side = payload.length == 4 ? payload[3] : null;
        boolean requiem = TOMB_REQUIEM.equals(side);
        if (tile == null
                || holder == null
                || unitKey == null
                || unit == null
                || !unit.getIsShip()
                || game.getStoredValue("producedUnitCostFor" + player.getFaction())
                        .isBlank()
                || !isTombSide(side)
                || !side.equals(getTombSide(player))) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        if (requiem ? holder.getDamagedUnitCount(unitKey) < 1 : availableUndamagedShips(holder, unitKey) < 1) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        if (requiem) {
            holder.removeDamagedUnit(unitKey, 1);
        } else {
            holder.addDamagedUnit(unitKey, 1);
        }
        reduceProductionPaymentCost(game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void addOblivionsEchoCombatButton(
            List<Button> buttons, Game game, Player player, Player opponent, Tile tile) {
        if (buttons == null || game == null || player == null || opponent == null || tile == null) {
            return;
        }
        String side = getEchoSide(player);
        if (side == null || !player.hasTechReady(side)) {
            return;
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + ECHO_START + tile.getPosition() + "|" + opponent.getFaction() + "|"
                        + side,
                "Use Oblivion's Echo",
                TechEmojis.WarfareTech));
    }

    @ButtonHandler(ECHO_START)
    public static void startOblivionsEcho(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(ECHO_START.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        Player opponent = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        String side = getEchoSide(player);
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (tile == null
                || opponent == null
                || side == null
                || !player.hasTechReady(side)
                || opponent != getCombatOpponent(game, player, combat, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = getEchoTargets(game, player, opponent, tile, side);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no eligible ships for _Oblivion's Echo_.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        player.exhaustTech(side);
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " exhausted _Oblivion's Echo_. Choose a ship to "
                        + (ECHO_REQUIEM.equals(side) ? "repair." : "damage."),
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(ECHO_TARGET)
    public static void resolveOblivionsEcho(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(ECHO_TARGET.length()).split("\\|", 5);
        Tile tile = payload.length == 5 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getSpaceUnitHolder();
        UnitKey target = holder == null || payload.length != 5 ? null : getUnitKey(holder, payload[2], payload[3]);
        Player opponent = payload.length != 5 ? null : game.getPlayerFromColorOrFaction(payload[1]);
        String side = payload.length == 5 ? payload[4] : null;
        boolean requiem = ECHO_REQUIEM.equals(side);
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (tile == null
                || holder == null
                || target == null
                || opponent == null
                || !isEchoSide(side)
                || !side.equals(getEchoSide(player))
                || opponent != getCombatOpponent(game, player, combat, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (opponent == player) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitModel targetModel = getUnitModel(game, target);
        if (!isShip(targetModel, target) || (!requiem && !unitBelongsToPlayer(opponent, target))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (requiem) {
            if (holder.getDamagedUnitCount(target) < 1) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            holder.removeDamagedUnit(target, 1);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " repaired 1 " + getUnitName(targetModel, target)
                            + " with _Oblivion's Echo_ and produced 1 hit.");
            CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, tile, 1);
        } else {
            if (availableUndamagedShips(holder, target) < 1) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            holder.addDamagedUnit(target, 1);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " damaged 1 " + getUnitName(targetModel, target)
                            + " with _Oblivion's Echo_.");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getTombTargets(Game game, Player player, Tile tile, String side) {
        if (side == null || tile == null) {
            return List.of();
        }
        boolean requiem = TOMB_REQUIEM.equals(side);
        List<Button> buttons = new ArrayList<>();
        UnitHolder holder = tile.getSpaceUnitHolder();
        for (UnitKey unitKey : holder.getUnitKeys()) {
            UnitModel unit = game.getUnitFromUnitKey(unitKey);
            if (unit == null || !unit.getIsShip() || !unit.getSustainDamage()) {
                continue;
            }
            if (requiem ? holder.getDamagedUnitCount(unitKey) < 1 : availableUndamagedShips(holder, unitKey) < 1) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + TOMB_TARGET + tile.getPosition() + "|" + unitKey.asyncID() + "|"
                            + unitKey.colorID() + "|" + side,
                    (requiem ? "Repair " : "Damage ") + unit.getName(),
                    TechEmojis.CyberneticTech));
        }
        return buttons;
    }

    private static List<Button> getEchoTargets(Game game, Player player, Player opponent, Tile tile, String side) {
        if (side == null) {
            return List.of();
        }
        boolean requiem = ECHO_REQUIEM.equals(side);
        List<Button> buttons = new ArrayList<>();
        UnitHolder holder = tile.getSpaceUnitHolder();
        for (UnitKey unitKey : holder.getUnitKeys()) {
            UnitModel unit = getUnitModel(game, unitKey);
            boolean eligible = requiem
                    ? unitBelongsToPlayer(player, unitKey)
                            && isShip(unit, unitKey)
                            && holder.getDamagedUnitCount(unitKey) > 0
                    : unitBelongsToPlayer(opponent, unitKey)
                            && isShip(unit, unitKey)
                            && availableUndamagedShips(holder, unitKey) > 0;
            if (!eligible) {
                continue;
            }
            addEchoTarget(buttons, player, opponent, tile, unitKey, unit, side);
        }
        return buttons;
    }

    private static void addEchoTarget(
            List<Button> buttons,
            Player player,
            Player opponent,
            Tile tile,
            UnitKey unitKey,
            UnitModel unit,
            String side) {
        buttons.add(Buttons.red(
                player.factionButtonChecker() + ECHO_TARGET + tile.getPosition() + "|" + opponent.getFaction() + "|"
                        + unitKey.asyncID() + "|" + unitKey.colorID() + "|" + side,
                (ECHO_REQUIEM.equals(side) ? "Repair " : "Damage ") + getUnitName(unit, unitKey),
                TechEmojis.WarfareTech));
    }

    private static boolean unitBelongsToPlayer(Player player, UnitKey unitKey) {
        return player.getColor().equals(unitKey.getColor());
    }

    private static boolean isShip(UnitModel unit, UnitKey unitKey) {
        return (unit != null && unit.getIsShip())
                || switch (unitKey.unitType()) {
                    case Fighter, Destroyer, Cruiser, Carrier, Dreadnought, Flagship, Warsun, Deep -> true;
                    default -> false;
                };
    }

    private static String getUnitName(UnitModel unit, UnitKey unitKey) {
        return unit == null ? unitKey.humanReadableName() : unit.getName();
    }

    private static UnitModel getUnitModel(Game game, UnitKey unitKey) {
        UnitModel unit = game.getUnitFromUnitKey(unitKey);
        if (unit != null) {
            return unit;
        }
        unit = Mapper.getUnit(unitKey.asyncID());
        return unit == null ? Mapper.getUnit(unitKey.unitType().plainName()) : unit;
    }

    private static Player getCombatOpponent(
            Game game, Player player, StartCombatService.CurrentCombat combat, Tile tile) {
        if (combat == null
                || !tile.getPosition().equals(game.getCurrentActiveSystem())
                || !tile.getPosition().equals(combat.tilePosition())
                || !combat.factions().contains(player.getFaction())) {
            return null;
        }
        return combat.factions().stream()
                .filter(faction -> !faction.equals(player.getFaction()))
                .map(game::getPlayerFromColorOrFaction)
                .filter(other -> other != null && other != player)
                .findFirst()
                .orElse(null);
    }

    private static UnitKey getUnitKey(UnitHolder holder, String asyncId, String colorId) {
        return holder.getUnitKeys().stream()
                .filter(unitKey ->
                        unitKey.asyncID().equals(asyncId) && unitKey.colorID().equals(colorId))
                .findFirst()
                .orElse(null);
    }

    private static int availableUndamagedShips(UnitHolder holder, UnitKey unitKey) {
        return holder.getUnitCount(unitKey) - holder.getDamagedUnitCount(unitKey);
    }

    private static int reduceProductionPaymentCost(Game game, Player player) {
        int currentCost;
        try {
            currentCost = Integer.parseInt(game.getStoredValue("producedUnitCostFor" + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
        int reducedCost = Math.max(0, currentCost - 1);
        game.setStoredValue("producedUnitCostFor" + player.getFaction(), Integer.toString(reducedCost));
        game.setStoredValue(
                getTombDiscountKey(player), Integer.toString(getTombResonanceProductionDiscount(game, player) + 1));
        player.setTotalExpenses(Math.max(0, player.getTotalExpenses() - 1));
        return reducedCost;
    }

    public static int getTombResonanceProductionDiscount(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(getTombDiscountKey(player)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void clearTombResonanceProductionContext(Game game, Player player) {
        game.removeStoredValue(getTombDiscountKey(player));
    }

    private static String getTombSide(Player player) {
        return player.hasTech(TOMB_LAMENT) ? TOMB_LAMENT : player.hasTech(TOMB_REQUIEM) ? TOMB_REQUIEM : null;
    }

    private static String getEchoSide(Player player) {
        return player.hasTech(ECHO_LAMENT) ? ECHO_LAMENT : player.hasTech(ECHO_REQUIEM) ? ECHO_REQUIEM : null;
    }

    private static boolean isTombSide(String side) {
        return TOMB_LAMENT.equals(side) || TOMB_REQUIEM.equals(side);
    }

    private static boolean isEchoSide(String side) {
        return ECHO_LAMENT.equals(side) || ECHO_REQUIEM.equals(side);
    }

    private static String getTombDiscountKey(Player player) {
        return TOMB_DISCOUNT + player.getFaction();
    }
}
