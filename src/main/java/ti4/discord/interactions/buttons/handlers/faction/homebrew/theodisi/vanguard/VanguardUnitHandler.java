package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Vanguard;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.unit.monuments.TwilightsFallMonumentsButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.UnitModelValueInjectionService;

@UtilityClass
public class VanguardUnitHandler {
    private static final String BULWARK = "vanguard_mech";
    private static final String FLAGBEARER = "vanguard_flagship";
    private static final String BULWARK_SUSTAIN = "vanguardBulwarkSustain_";
    private static final String FLAGBEARER_HITS = "vanguardFlagbearerHits_";
    private static final String USE_BULWARK = "useVanguardBulwark_";
    private static final String SELECT_BULWARK_SHIP = "selectVanguardBulwarkShip_";

    public static void offerBulwarkButton(Game game, Player player) {
        if (!player.ownsUnit(BULWARK)) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            int mechCount = tile.getUnitHolders().values().stream()
                    .mapToInt(holder -> holder.getUnitCount(UnitType.Mech, player))
                    .sum();
            boolean hasEligibleShip = tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                    .map(player::getUnitFromUnitKey)
                    .anyMatch(unit -> unit != null && unit.getIsShip() && !unit.getSustainDamage());
            for (int index = 1; index <= mechCount && hasEligibleShip; index++) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + USE_BULWARK + tile.getPosition() + "|" + index,
                        "Use Bulwark in " + tile.getRepresentationForButtons(game, player),
                        FactionEmojis.vanguard));
            }
        }
        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", each _Bulwark_ below may give one ship in its system SUSTAIN DAMAGE until the end of this tactical action.",
                    buttons);
        }
    }

    @ButtonHandler(USE_BULWARK)
    public static void useBulwark(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_BULWARK.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        if (tile == null || !player.ownsUnit(BULWARK)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        int mechCount = tile.getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Mech, player))
                .sum();
        int selectedCount = game.getStoredValue(getBulwarkKey(player))
                        .split(java.util.regex.Pattern.quote(tile.getPosition() + "|"), -1)
                        .length
                - 1;
        if (mechCount < 1 || selectedCount >= mechCount) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitKey key : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
            UnitModel unit = player.getUnitFromUnitKey(key);
            if (unit != null
                    && unit.getIsShip()
                    && !unit.getSustainDamage()
                    && getBulwarkSustainCountInSourceSystem(game, player, tile, key)
                            < tile.getSpaceUnitHolder().getUnitCount(key)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT_BULWARK_SHIP + tile.getPosition() + "|" + key.unitType(),
                        "Give " + unit.getName() + " SUSTAIN DAMAGE",
                        unit.getUnitEmoji()));
            }
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the ship type that gains SUSTAIN DAMAGE.",
                buttons);
    }

    @ButtonHandler(SELECT_BULWARK_SHIP)
    public static void selectBulwarkShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_BULWARK_SHIP.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        UnitType unitType = payload.length == 2 ? Units.findUnitType(payload[1]) : null;
        UnitKey unitKey = unitType == null ? null : Units.getUnitKey(unitType, player.getColorID());
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        int selectedCount = tile == null
                ? 0
                : game.getStoredValue(getBulwarkKey(player))
                                .split(java.util.regex.Pattern.quote(tile.getPosition() + "|"), -1)
                                .length
                        - 1;
        int mechCount = tile == null
                ? 0
                : tile.getUnitHolders().values().stream()
                        .mapToInt(holder -> holder.getUnitCount(UnitType.Mech, player))
                        .sum();
        if (tile == null
                || unit == null
                || !unit.getIsShip()
                || unit.getSustainDamage()
                || getBulwarkSustainCountInSourceSystem(game, player, tile, unitKey)
                        >= tile.getSpaceUnitHolder().getUnitCount(unitKey)
                || selectedCount >= mechCount
                || tile.getSpaceUnitHolder().getUnitCount(unitKey) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(
                getBulwarkKey(player),
                game.getStoredValue(getBulwarkKey(player)) + tile.getPosition() + "|" + unitType + ";");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " gave " + unit.getUnitEmoji() + " " + unit.getName()
                        + " SUSTAIN DAMAGE until the end of this action.");
    }

    public static UnitModel injectBulwarkSustain(Game game, Player player, Tile tile, UnitKey unitKey, UnitModel unit) {
        if (unit == null || !hasBulwarkSustain(game, player, tile, unitKey)) {
            return unit;
        }
        return UnitModelValueInjectionService.injectTemporaryValues(
                unit,
                UnitModelValueInjectionService.UnitValueInjection.of(
                        UnitModelValueInjectionService.BooleanValueInjection.create()
                                .sustainDamage(true)));
    }

    public static int getBulwarkSustainCount(Game game, Player player, Tile tile, UnitKey unitKey) {
        if (tile == null || unitKey == null) {
            return 0;
        }
        String target = "|" + unitKey.unitType() + ";";
        return game.getStoredValue(getBulwarkKey(player)).split(java.util.regex.Pattern.quote(target), -1).length - 1;
    }

    public static boolean hasBulwarkSustain(Game game, Player player, Tile tile, UnitKey unitKey) {
        return getBulwarkSustainCount(game, player, tile, unitKey) > 0;
    }

    private static int getBulwarkSustainCountInSourceSystem(Game game, Player player, Tile tile, UnitKey unitKey) {
        String target = tile.getPosition() + "|" + unitKey.unitType() + ";";
        return game.getStoredValue(getBulwarkKey(player)).split(java.util.regex.Pattern.quote(target), -1).length - 1;
    }

    public static void clearBulwarkSustain(Game game) {
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(BULWARK_SUSTAIN))
                .toList()
                .forEach(game::removeStoredValue);
    }

    public static void addSpaceCombatHitButtons(List<Button> buttons, Game game, Player player, Tile tile, int hits) {
        if (hits < 1 || tile == null) {
            return;
        }
        if (player.ownsUnit(FLAGBEARER)) {
            game.setStoredValue(getFlagbearerHitsKey(player, tile), Integer.toString(hits));
        }
    }

    public static void offerFlagbearerAfterSustain(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        if (!player.ownsUnit(FLAGBEARER) || tile.getSpaceUnitHolder().getDamagedUnitCount(flagship) < 1) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", the Flagbearer used SUSTAIN DAMAGE. You may destroy it to cancel up to 4 additional hits.",
                List.of(
                        Buttons.gray(
                                player.factionButtonChecker() + "useVanguardFlagbearer_" + tile.getPosition(),
                                "Use Flagbearer (Cancel up to 4 Hits)",
                                FactionEmojis.vanguard),
                        Buttons.red(
                                player.factionButtonChecker() + "declineVanguardFlagbearer_" + tile.getPosition(),
                                "Decline")));
    }

    public static boolean offerFlagbearerAfterManualSustain(
            ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        if (!player.ownsUnit(FLAGBEARER)
                || tile.getSpaceUnitHolder().getDamagedUnitCount(flagship) < 1
                || getFlagbearerHits(game, player, tile) < 0) {
            return false;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", the Flagbearer used SUSTAIN DAMAGE. You may destroy it to cancel up to 4 additional hits.",
                List.of(
                        Buttons.gray(
                                player.factionButtonChecker() + "useVanguardFlagbearer_" + tile.getPosition(),
                                "Use Flagbearer (Cancel up to 4 Hits)",
                                FactionEmojis.vanguard),
                        Buttons.red(
                                player.factionButtonChecker() + "declineVanguardFlagbearer_" + tile.getPosition(),
                                "Decline")));
        return true;
    }

    public static void recordSpaceCombatHitAssignment(Game game, Player player, Tile tile, int hits) {
        String key = getFlagbearerHitsKey(player, tile);
        String value = game.getStoredValue(key);
        if (value.isBlank()) {
            return;
        }
        game.setStoredValue(key, Integer.toString(Math.max(0, Integer.parseInt(value) - hits)));
    }

    public static int getFlagbearerHits(Game game, Player player, Tile tile) {
        String value = game.getStoredValue(getFlagbearerHitsKey(player, tile));
        return value.isBlank() ? -1 : Integer.parseInt(value);
    }

    @ButtonHandler("useVanguardFlagbearer_")
    public static void useFlagbearer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring("useVanguardFlagbearer_".length()));
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        if (tile == null
                || !player.ownsUnit(FLAGBEARER)
                || tile.getSpaceUnitHolder().getDamagedUnitCount(flagship) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ParsedUnit unit = ParseUnitService.simpleParsedUnit(player, UnitType.Flagship, tile.getSpaceUnitHolder(), 1);
        DestroyUnitService.destroyUnit(event, tile, game, unit, true);
        int remainingHits = Math.max(0, getFlagbearerHits(game, player, tile));
        int canceledHits = Math.min(4, remainingHits);
        recordSpaceCombatHitAssignment(game, player, tile, canceledHits);
        refreshSpaceCombatHitAssignment(
                event,
                game,
                player,
                tile,
                remainingHits - canceledHits,
                player.getRepresentationNoPing() + " destroyed the Flagbearer and canceled " + canceledHits + " hit"
                        + (canceledHits == 1 ? "." : "s."));
    }

    @ButtonHandler("declineVanguardFlagbearer_")
    public static void declineFlagbearer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring("declineVanguardFlagbearer_".length()));
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        refreshSpaceCombatHitAssignment(
                event,
                game,
                player,
                tile,
                Math.max(0, getFlagbearerHits(game, player, tile)),
                player.getRepresentationNoPing() + " declined to use the Flagbearer.");
    }

    public static void refreshSpaceCombatHitAssignment(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, int hits, String message) {
        if (hits < 1) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits,
                "Auto-assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + "cancelSpaceHits_" + tile.getPosition() + "_" + hits, "Cancel a Hit"));
        VanguardAbilitiesHandler.addInterlockingShieldsButton(buttons, game, player, tile, "cancelSpaceHits", hits);
        TwilightsFallMonumentsButtonHandler.addYellowTfMonumentCancelHitButton(
                buttons, game, player, tile, "space", hits);
        addSpaceCombatHitButtons(buttons, game, player, tile, hits);
        MessageHelper.editMessageWithButtons(
                event,
                message + "\n" + player.getRepresentationNoPing() + ", you may automatically assign "
                        + (hits == 1 ? "the remaining hit." : "the remaining " + hits + " hits. ")
                        + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, hits, event, true),
                buttons);
    }

    private static String getBulwarkKey(Player player) {
        return BULWARK_SUSTAIN + player.getFaction();
    }

    private static String getFlagbearerHitsKey(Player player, Tile tile) {
        return FLAGBEARER_HITS + player.getFaction() + "_" + tile.getPosition();
    }
}
