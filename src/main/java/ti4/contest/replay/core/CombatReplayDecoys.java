package ti4.contest.replay.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.service.emoji.ColorEmojis;
import tools.jackson.databind.json.JsonMapper;

/**
 * Captures, applies, and renders replay-only decoy unit state for combat recordings.
 */
@UtilityClass
public class CombatReplayDecoys {

    private static final JsonMapper MAPPER = JsonMapperManager.basic();
    private static final Map<String, List<DecoyUnit>> DEBUG_DECOY_UNITS_BY_COMBAT = new ConcurrentHashMap<>();

    public String buildJson(Player attacker, Player defender, Tile tile) {
        return buildJson(attacker, defender, tile, false);
    }

    public String buildJson(Player attacker, Player defender, Tile tile, boolean decoysEnabled) {
        List<DecoyUnit> debugDecoyUnits = consumeDebugDecoyUnits(attacker, defender, tile);
        if (!decoysEnabled) return null;

        Abilities abilities = build(debugDecoyUnits);
        return abilities.hasDecoys() ? write(abilities) : null;
    }

    public void addDebugDecoyUnit(Game game, Tile tile, Player player, UnitType unitType) {
        String key = debugCombatKey(game.getName(), tile.getPosition());
        List<DecoyUnit> units = new ArrayList<>(DEBUG_DECOY_UNITS_BY_COMBAT.getOrDefault(key, List.of()));
        for (int i = 0; i < units.size(); i++) {
            DecoyUnit unit = units.get(i);
            if (unit.faction().equalsIgnoreCase(player.getFaction()) && unit.unitType() == unitType) {
                units.set(
                        i,
                        new DecoyUnit(
                                unit.faction(),
                                unit.factionEmoji(),
                                unit.colorId(),
                                unit.unitType(),
                                unit.unitHolderName(),
                                unit.count() + 1));
                DEBUG_DECOY_UNITS_BY_COMBAT.put(key, units);
                return;
            }
        }
        units.add(new DecoyUnit(
                player.getFaction(), player.getFactionEmoji(), player.getColorID(), unitType, Constants.SPACE, 1));
        DEBUG_DECOY_UNITS_BY_COMBAT.put(key, units);
    }

    public void setDebugDecoyUnits(Game game, Tile tile, List<DecoyUnit> units) {
        DEBUG_DECOY_UNITS_BY_COMBAT.put(debugCombatKey(game.getName(), tile.getPosition()), List.copyOf(units));
    }

    public boolean hasDebugDecoyState(Game game, Tile tile) {
        return DEBUG_DECOY_UNITS_BY_COMBAT.containsKey(debugCombatKey(game.getName(), tile.getPosition()));
    }

    public String renderDebugDecoySummary(Game game, Tile tile) {
        Abilities abilities = new Abilities(new Decoy(DEBUG_DECOY_UNITS_BY_COMBAT.getOrDefault(
                debugCombatKey(game.getName(), tile.getPosition()), List.of())));
        if (!abilities.hasDecoys()) return "No decoys selected.";
        return renderVanishedUnits(abilities);
    }

    public String appendDebugDecoySummary(String summary, Game game, Tile tile) {
        Abilities abilities = new Abilities(new Decoy(DEBUG_DECOY_UNITS_BY_COMBAT.getOrDefault(
                debugCombatKey(game.getName(), tile.getPosition()), List.of())));
        if (!abilities.hasDecoys()) return summary;

        StringBuilder builder = new StringBuilder(summary);
        if (!summary.endsWith("\n")) builder.append('\n');
        builder.append("Replay-Only Decoys\n");
        for (DecoyUnit unit : abilities.decoy().units()) {
            builder.append(unit.factionEmoji())
                    .append(ColorEmojis.getColorEmojiWithName(unit.colorId()))
                    .append(" `")
                    .append(unit.count())
                    .append("x` ")
                    .append(unit.unitType().getUnitTypeEmoji())
                    .append(' ')
                    .append(unit.unitType().humanReadableName())
                    .append(" `[Decoy]`\n");
        }
        builder.append("----------\n");
        return builder.toString();
    }

    public void clearDebugDecoys(Game game, Tile tile) {
        DEBUG_DECOY_UNITS_BY_COMBAT.remove(debugCombatKey(game.getName(), tile.getPosition()));
    }

    public Abilities read(String json) {
        if (StringUtils.isBlank(json)) return new Abilities(null);
        return readJson(json);
    }

    public Game applyToTile(Game game, String tilePosition, Abilities abilities) {
        if (!abilities.hasDecoys()) return game;

        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return game;
        for (DecoyUnit decoyUnit : abilities.decoy().units()) {
            UnitHolder holder = tile.getUnitHolders().get(decoyUnit.unitHolderName());
            if (holder == null) continue;
            holder.addUnitsWithStates(
                    Units.getUnitKey(decoyUnit.unitType(), decoyUnit.colorId()), List.of(decoyUnit.count(), 0, 0, 0));
        }
        return game;
    }

    public CombatRollPayload applyToRoll(CombatRollPayload payload, Abilities abilities) {
        if (!abilities.hasDecoys()) return payload;

        List<DecoyUnit> availableDecoys = new ArrayList<>(abilities.decoy().units());
        List<CombatRollPayload.UnitRoll> unitRolls = new ArrayList<>();
        for (CombatRollPayload.UnitRoll unitRoll : payload.unitRolls()) {
            DecoyUnit decoyUnit = takeDecoyForRoll(payload, unitRoll, availableDecoys);
            unitRolls.add(decoyUnit == null ? unitRoll : withDecoys(unitRoll, decoyUnit.count()));
        }
        return new CombatRollPayload(
                payload.header(), payload.notes(), payload.modifiers(), unitRolls, payload.total());
    }

    public String renderDisappearanceMessage(Abilities abilities) {
        if (!abilities.hasDecoys()) return null;

        String vanished = renderVanishedUnits(abilities);
        return "## Sensor Echoes Fade\n"
                + "As the Lazax recorders close the battlefile, "
                + vanished
                + " shimmer out of formation and vanish from every tactical display. No wreckage remains; only the "
                + "uneasy certainty that some of those ships were never truly there.";
    }

    private Abilities build(List<DecoyUnit> debugDecoyUnits) {
        List<DecoyUnit> decoyUnits = debugDecoyUnits == null ? List.of() : debugDecoyUnits;
        return new Abilities(decoyUnits.isEmpty() ? null : new Decoy(decoyUnits));
    }

    private List<DecoyUnit> consumeDebugDecoyUnits(Player attacker, Player defender, Tile tile) {
        Game game = attacker == null ? null : attacker.getGame();
        if (game == null && defender != null) game = defender.getGame();
        if (game == null || tile == null) return null;
        return DEBUG_DECOY_UNITS_BY_COMBAT.remove(debugCombatKey(game.getName(), tile.getPosition()));
    }

    private String debugCombatKey(String gameName, String tilePosition) {
        return gameName + "|" + tilePosition;
    }

    private String renderVanishedUnits(Abilities abilities) {
        Map<String, Integer> vanishedGroups = new LinkedHashMap<>();
        for (DecoyUnit unit : abilities.decoy().units()) {
            String key = unit.factionEmoji() + "|" + unit.unitType().humanReadableName();
            vanishedGroups.merge(key, unit.count(), Integer::sum);
        }

        StringBuilder vanished = new StringBuilder();
        for (Map.Entry<String, Integer> group : vanishedGroups.entrySet()) {
            if (!vanished.isEmpty()) {
                vanished.append(", ");
            }
            vanished.append(renderVanishedGroup(group));
        }
        return vanished.toString();
    }

    private DecoyUnit takeDecoyForRoll(
            CombatRollPayload payload, CombatRollPayload.UnitRoll unitRoll, List<DecoyUnit> availableDecoys) {
        for (DecoyUnit decoyUnit : List.copyOf(availableDecoys)) {
            if (matches(decoyUnit, payload, unitRoll)) {
                availableDecoys.remove(decoyUnit);
                return decoyUnit;
            }
        }
        return null;
    }

    private boolean matches(DecoyUnit decoyUnit, CombatRollPayload payload, CombatRollPayload.UnitRoll unitRoll) {
        return decoyUnit.unitType().plainName().equalsIgnoreCase(unitRoll.baseType())
                && decoyUnit
                        .colorId()
                        .equalsIgnoreCase(Mapper.getColorID(payload.header().actorColor()));
    }

    private CombatRollPayload.UnitRoll withDecoys(CombatRollPayload.UnitRoll unitRoll, int decoyCount) {
        List<CombatRollPayload.DieRoll> dice = new ArrayList<>(unitRoll.dice());
        for (int i = 0; i < decoyCount * unitRoll.dicePerUnit(); i++) {
            dice.add(new CombatRollPayload.DieRoll(
                    randomMiss(unitRoll.effectiveThreshold()),
                    unitRoll.effectiveThreshold(),
                    false,
                    DieRollSource.DECOY));
        }
        return new CombatRollPayload.UnitRoll(
                unitRoll.unitId(),
                unitRoll.asyncId(),
                unitRoll.baseType(),
                unitRoll.unitName(),
                unitRoll.unitDisplayName(),
                unitRoll.unitEmoji(),
                unitRoll.quantity() + decoyCount,
                unitRoll.dicePerUnit(),
                unitRoll.extraDice(),
                unitRoll.printedHitsOn(),
                unitRoll.modifier(),
                unitRoll.effectiveThreshold(),
                unitRoll.segmentType(),
                dice,
                unitRoll.hits());
    }

    private int randomMiss(int effectiveThreshold) {
        if (effectiveThreshold <= 1) return ThreadLocalRandom.current().nextInt(1, 11);
        return ThreadLocalRandom.current().nextInt(1, Math.min(10, effectiveThreshold - 1) + 1);
    }

    private String renderVanishedGroup(Map.Entry<String, Integer> group) {
        String[] parts = group.getKey().split("\\|", 2);
        int count = group.getValue();
        return parts[0] + " " + count + " " + StringUtils.uncapitalize(parts[1]) + (count == 1 ? "" : "s");
    }

    @SneakyThrows
    private String write(Abilities abilities) {
        return MAPPER.writeValueAsString(abilities);
    }

    @SneakyThrows
    private Abilities readJson(String json) {
        return MAPPER.readValue(json, Abilities.class);
    }

    public record Abilities(Decoy decoy) {

        public boolean hasDecoys() {
            return decoy != null && !decoy.units().isEmpty();
        }
    }

    public record Decoy(List<DecoyUnit> units) {

        public Decoy {
            units = units == null ? List.of() : List.copyOf(units);
        }
    }

    public record DecoyUnit(
            String faction, String factionEmoji, String colorId, UnitType unitType, String unitHolderName, int count) {}
}
