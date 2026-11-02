package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class XinUnitHandler {
    private static final String REPAIR_SENTINEL = "repairXinSentinel_";
    private static final String ASSIGN_CELESTIAL_DRAGON_HIT = "assignCelestialDragonHit_";
    private static final String AUTO_ASSIGN_CELESTIAL_DRAGON_HITS = "autoAssignCelestialDragonHits_";
    private static final String CELESTIAL_DRAGON_HITS = "celestialDragonHits_";
    private static final String CELESTIAL_DRAGON_ASSIGNMENT_SUMMARY = "celestialDragonAssignmentSummary_";

    public static void captureSentinelDestroyedInfantry(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        Map<Player, Integer> capturedByPlayer = new HashMap<>();
        for (RemovedUnit destroyedUnit : destroyedUnits) {
            if (destroyedUnit.unitKey().unitType() != UnitType.Infantry
                    || !(destroyedUnit.uh() instanceof Planet planet)) {
                continue;
            }
            for (Player xinPlayer : game.getRealPlayers()) {
                if (!hasSteelSentinel(xinPlayer) || !hasSentinelOnPlanet(xinPlayer, planet)) {
                    continue;
                }
                capturedByPlayer.merge(xinPlayer, destroyedUnit.getTotalRemoved(), Integer::sum);
            }
        }
        capturedByPlayer.forEach((xinPlayer, captured) -> {
            AddUnitService.addUnits(
                    event, xinPlayer.getNomboxTile(), game, xinPlayer.getColor(), captured + " infantry");
            MessageHelper.sendMessageToChannel(
                    xinPlayer.getCorrectChannel(),
                    xinPlayer.getRepresentationNoPing() + " captured " + captured + " infantry with **Sentinel**.");
        });
    }

    public static void addSentinelRepairButton(List<Button> buttons, Player player, Tile tile, UnitHolder unitHolder) {
        if (!(unitHolder instanceof Planet)
                || !hasSteelSentinel(player)
                || !hasDamagedSentinel(player, unitHolder)
                || capturedInfantry(player) < 2) {
            return;
        }
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + REPAIR_SENTINEL + tile.getPosition() + "|" + unitHolder.getName(),
                "Repair Sentinel (Return 2 Infantry)",
                FactionEmojis.xin));
    }

    @ButtonHandler(REPAIR_SENTINEL)
    public static void repairSentinel(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(REPAIR_SENTINEL.length()).split("\\|", 2);
        Tile tile = values.length == 2 ? game.getTileByPosition(values[0]) : null;
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        if (!(unitHolder instanceof Planet)
                || !hasSteelSentinel(player)
                || !hasDamagedSentinel(player, unitHolder)
                || capturedInfantry(player) < 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You cannot repair a Sentinel here right now.");
            return;
        }
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "2 infantry");
        unitHolder.removeDamagedUnit(sentinelKey(player), 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " returned 2 captured infantry to repair 1 **Sentinel**.");
    }

    private static boolean hasSteelSentinel(Player player) {
        return player.hasUnit("xin_mech") || player.hasUnit("xin_mechsteel");
    }

    private static boolean hasStarSentinel(Player player) {
        return player.hasUnit("xin_mech") || player.hasUnit("xin_mechstar");
    }

    public static boolean canUseStarSentinelCoexistence(Player player, UnitHolder unitHolder) {
        return player.getPlanets().contains(unitHolder.getName())
                && hasStarSentinel(player)
                && hasSentinelOnPlanet(player, unitHolder);
    }

    public static int getCelestialDragonHits(Player player, CombatRollType rollType, CombatRollPayload payload) {
        if (rollType != CombatRollType.combatround
                || !player.hasUnit("xin_flagship")
                || player.getStarbalanceCounter() != player.getSteelbalanceCounter()) {
            return 0;
        }
        return payload.unitRolls().stream()
                .filter(unitRoll -> "xin_flagship".equals(unitRoll.unitId()))
                .mapToInt(CombatRollPayload.UnitRoll::hits)
                .sum();
    }

    public static boolean offerCelestialDragonHitAssignment(
            GenericInteractionCreateEvent event, Game game, Player player, Player target, Tile tile, int hits) {
        if (hits < 1) {
            return false;
        }
        List<Button> buttons = getCelestialDragonHitButtons(game, player, target, tile);
        if (buttons.isEmpty()) {
            return false;
        }
        game.setStoredValue(celestialDragonHitsKey(player, target, tile), Integer.toString(hits));
        game.removeStoredValue(celestialDragonAssignmentSummaryKey(player, target, tile));
        MessageChannel channel = game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel();
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentationNoPing() + ", assign " + hits + " hit" + (hits == 1 ? "" : "s")
                        + " produced by **Celestial Dragon**. It shows buttons for both sustaining and destroying, however normal hit assignment rules still apply.\n"
                        + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(target, game, tile, hits, event, true),
                buttons);
        return true;
    }

    @ButtonHandler(ASSIGN_CELESTIAL_DRAGON_HIT)
    public static void assignCelestialDragonHit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values =
                buttonID.substring(ASSIGN_CELESTIAL_DRAGON_HIT.length()).split("\\|", 5);
        if (values.length != 5) {
            return;
        }
        Tile tile = game.getTileByPosition(values[0]);
        Player target = game.getPlayerFromColorOrFaction(values[1]);
        UnitType unitType = Units.findUnitType(values[2]);
        UnitState state;
        try {
            state = UnitState.valueOf(values[3]);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (tile == null || target == null || !isCelestialDragonAssignmentAvailable(game, player, target, tile)) {
            return;
        }
        UnitHolder space = tile.getSpaceUnitHolder();
        UnitKey unitKey = Units.getUnitKey(unitType, target.getColorID());
        if (space.getUnitCountForState(unitKey, state) < 1) {
            return;
        }
        if ("sustain".equals(values[4])) {
            UnitModel unitModel = target.getUnitFromUnitKey(unitKey);
            if (state.isDamaged()
                    || unitModel == null
                    || !ButtonHelper.unitCanSustainDamage(game, target, tile, unitModel)) {
                return;
            }
            space.addDamagedUnit(unitKey, 1);
        } else {
            ParsedUnit unit = ParseUnitService.simpleParsedUnit(target, unitType, space, 1);
            DestroyUnitService.destroyUnit(event, tile, game, unit, true, state);
        }
        String assignment =
                "> " + ("sustain".equals(values[4]) ? "Sustained " : "Destroyed ") + unitKey.unitEmoji() + "\n";
        String summaryKey = celestialDragonAssignmentSummaryKey(player, target, tile);
        game.setStoredValue(summaryKey, game.getStoredValue(summaryKey) + assignment);
        int remainingHits = getCelestialDragonHitsRemaining(game, player, target, tile) - 1;
        List<Button> buttons = getCelestialDragonHitButtons(game, player, target, tile);
        if (remainingHits < 1 || buttons.isEmpty()) {
            game.removeStoredValue(celestialDragonHitsKey(player, target, tile));
            String summary = game.getStoredValue(summaryKey);
            int assignedHits = (int) summary.lines().count();
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    target.getFactionEmoji() + " assigned " + (assignedHits == 1 ? "the hit" : "the hits")
                            + " from **Celestial Dragon** in the following way:\n" + summary);
            game.removeStoredValue(summaryKey);
            ButtonHelper.deleteMessage(event);
        } else {
            game.setStoredValue(celestialDragonHitsKey(player, target, tile), Integer.toString(remainingHits));
            MessageHelper.editMessageButtons(event, buttons);
        }
    }

    @ButtonHandler(AUTO_ASSIGN_CELESTIAL_DRAGON_HITS)
    public static void autoAssignCelestialDragonHits(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values =
                buttonID.substring(AUTO_ASSIGN_CELESTIAL_DRAGON_HITS.length()).split("\\|", 2);
        Tile tile = values.length == 2 ? game.getTileByPosition(values[0]) : null;
        Player target = values.length == 2 ? game.getPlayerFromColorOrFaction(values[1]) : null;
        if (tile == null || target == null || !isCelestialDragonAssignmentAvailable(game, player, target, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int hits = getCelestialDragonHitsRemaining(game, player, target, tile);
        String summary = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(target, game, tile, hits, event, false);
        game.removeStoredValue(celestialDragonHitsKey(player, target, tile));
        game.removeStoredValue(celestialDragonAssignmentSummaryKey(player, target, tile));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), summary);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getCelestialDragonHitButtons(Game game, Player player, Player target, Tile tile) {
        List<Button> buttons = new java.util.ArrayList<>();
        UnitHolder space = tile.getSpaceUnitHolder();
        for (UnitKey unitKey : space.getUnitKeys()) {
            if (!target.unitBelongsToPlayer(unitKey)) {
                continue;
            }
            UnitModel unitModel = target.getUnitFromUnitKey(unitKey);
            if (unitModel == null) {
                continue;
            }
            for (UnitState state : UnitState.values()) {
                if (space.getUnitCountForState(unitKey, state) < 1) {
                    continue;
                }
                String payload = tile.getPosition() + "|" + target.getFaction() + "|" + unitKey.unitTypeVal() + "|"
                        + state.name() + "|";
                if (!state.isDamaged() && ButtonHelper.unitCanSustainDamage(game, target, tile, unitModel)) {
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + ASSIGN_CELESTIAL_DRAGON_HIT + payload + "sustain",
                            "Sustain " + unitKey.humanReadableName(),
                            unitKey.unitEmoji()));
                }
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + ASSIGN_CELESTIAL_DRAGON_HIT + payload + "destroy",
                        "Destroy " + (state.isDamaged() ? "Damaged " : "") + unitKey.humanReadableName(),
                        unitKey.unitEmoji()));
            }
        }
        if (!buttons.isEmpty()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + AUTO_ASSIGN_CELESTIAL_DRAGON_HITS + tile.getPosition() + "|"
                            + target.getFaction(),
                    "Auto-assign Hits"));
        }
        return buttons;
    }

    private static boolean isCelestialDragonAssignmentAvailable(Game game, Player player, Player target, Tile tile) {
        return getCelestialDragonHitsRemaining(game, player, target, tile) > 0
                && player.hasUnit("xin_flagship")
                && player.getStarbalanceCounter() == player.getSteelbalanceCounter();
    }

    private static int getCelestialDragonHitsRemaining(Game game, Player player, Player target, Tile tile) {
        String value = game.getStoredValue(celestialDragonHitsKey(player, target, tile));
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static String celestialDragonHitsKey(Player player, Player target, Tile tile) {
        return CELESTIAL_DRAGON_HITS + player.getFaction() + "|" + target.getFaction() + "|" + tile.getPosition();
    }

    private static String celestialDragonAssignmentSummaryKey(Player player, Player target, Tile tile) {
        return CELESTIAL_DRAGON_ASSIGNMENT_SUMMARY + player.getFaction() + "|" + target.getFaction() + "|"
                + tile.getPosition();
    }

    private static boolean hasSentinelOnPlanet(Player player, UnitHolder unitHolder) {
        return unitHolder.getUnitCount(sentinelKey(player)) > 0;
    }

    private static boolean hasDamagedSentinel(Player player, UnitHolder unitHolder) {
        return unitHolder.getDamagedUnitCount(sentinelKey(player)) > 0;
    }

    private static int capturedInfantry(Player player) {
        return player.getNomboxTile().getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player);
    }

    private static UnitKey sentinelKey(Player player) {
        return Units.getUnitKey(UnitType.Mech, player.getColorID());
    }
}
