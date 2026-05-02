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
import ti4.model.UnitModel;
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

    public String addDecoy(String abilitiesJson, DecoyUnit decoyUnit) {
        if (decoyUnit == null) return abilitiesJson;
        Abilities abilities = read(abilitiesJson);
        List<DecoyUnit> units = new ArrayList<>();
        if (abilities.hasDecoys()) {
            units.addAll(abilities.decoy().units());
        }
        addOrMergeDecoyUnit(units, decoyUnit);
        return write(new Abilities(new Decoy(units)));
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
        List<DecoyUnit> decoyUnits =
                DEBUG_DECOY_UNITS_BY_COMBAT.getOrDefault(debugCombatKey(game.getName(), tile.getPosition()), List.of());
        if (decoyUnits.isEmpty()) return "No decoys selected.";
        return renderVanishedUnits(decoyUnits);
    }

    public String appendDebugDecoySummary(String summary, Game game, Tile tile) {
        List<DecoyUnit> decoyUnits =
                DEBUG_DECOY_UNITS_BY_COMBAT.getOrDefault(debugCombatKey(game.getName(), tile.getPosition()), List.of());
        if (decoyUnits.isEmpty()) return summary;

        StringBuilder builder = new StringBuilder(summary);
        if (!summary.endsWith("\n")) builder.append('\n');
        builder.append("Replay-Only Decoys\n");
        for (DecoyUnit unit : decoyUnits) {
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
        applyReplayTechnologies(game, abilities);

        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return game;
        for (DecoyUnit decoyUnit : abilities.decoy().units()) {
            UnitHolder holder = tile.getUnitHolders().get(decoyUnit.unitHolderName());
            if (holder == null) continue;
            String colorId = normalizedColorId(decoyUnit.colorId());
            if (colorId == null) continue;
            holder.addUnitsWithStates(
                    Units.getUnitKey(decoyUnit.unitType(), colorId), List.of(decoyUnit.count(), 0, 0, 0));
        }
        return game;
    }

    public boolean hasWarSunDecoyForPlayer(Abilities abilities, Player player) {
        if (!abilities.hasDecoys() || player == null) return false;
        for (DecoyUnit decoyUnit : abilities.decoy().units()) {
            if (decoyUnit.unitType() != UnitType.Warsun) continue;
            if (decoyMatchesPlayer(decoyUnit, player)) return true;
        }
        return false;
    }

    public CombatRollPayload applyToRoll(CombatRollPayload payload, Abilities abilities) {
        if (!abilities.hasDecoys()) return payload;

        List<CombatRollPayload.UnitRoll> unitRolls = new ArrayList<>();
        List<DecoyUnit> appliedDecoys = new ArrayList<>();
        for (CombatRollPayload.UnitRoll unitRoll : payload.unitRolls()) {
            DecoyUnit decoyUnit =
                    findDecoyForRoll(payload, unitRoll, abilities.decoy().units());
            if (decoyUnit == null) {
                unitRolls.add(unitRoll);
            } else {
                appliedDecoys.add(decoyUnit);
                unitRolls.add(withDecoys(unitRoll, decoyUnit.count()));
            }
        }
        for (DecoyUnit decoyUnit : abilities.decoy().units()) {
            if (appliedDecoys.contains(decoyUnit)) continue;
            if (!matchesRollActor(decoyUnit, payload)) continue;
            CombatRollPayload.UnitRoll syntheticRoll = syntheticDecoyRoll(payload, decoyUnit);
            if (syntheticRoll != null) {
                unitRolls.add(syntheticRoll);
            }
        }
        return new CombatRollPayload(
                payload.header(), payload.notes(), payload.modifiers(), unitRolls, payload.total());
    }

    public String renderDisappearanceMessage(Abilities abilities) {
        if (!abilities.hasDecoys()) return null;

        String vanished = renderVanishedUnits(abilities.decoy().units());
        return "## False Colors Revealed\n"
                + "As the battle ends, "
                + vanished
                + " drop their false colors and vanish from the formation. No wreckage remains.";
    }

    private Abilities build(List<DecoyUnit> debugDecoyUnits) {
        List<DecoyUnit> decoyUnits = debugDecoyUnits == null ? List.of() : debugDecoyUnits;
        return new Abilities(decoyUnits.isEmpty() ? null : new Decoy(decoyUnits));
    }

    private void addOrMergeDecoyUnit(List<DecoyUnit> decoyUnits, DecoyUnit decoyUnit) {
        for (int i = 0; i < decoyUnits.size(); i++) {
            DecoyUnit existing = decoyUnits.get(i);
            if (existing.faction().equalsIgnoreCase(decoyUnit.faction())
                    && existing.colorId().equalsIgnoreCase(decoyUnit.colorId())
                    && existing.unitHolderName().equalsIgnoreCase(decoyUnit.unitHolderName())
                    && existing.unitType() == decoyUnit.unitType()) {
                decoyUnits.set(
                        i,
                        new DecoyUnit(
                                existing.faction(),
                                existing.factionEmoji(),
                                existing.colorId(),
                                existing.unitType(),
                                existing.unitHolderName(),
                                existing.count() + decoyUnit.count()));
                return;
            }
        }
        decoyUnits.add(decoyUnit);
    }

    private void applyReplayTechnologies(Game game, Abilities abilities) {
        if (game == null || !abilities.hasDecoys()) return;
        for (DecoyUnit decoyUnit : abilities.decoy().units()) {
            if (decoyUnit.unitType() != UnitType.Warsun) continue;
            String colorId = normalizedColorId(decoyUnit.colorId());
            Player player = game.getPlayerFromColorOrFaction(colorId);
            if (player == null) player = game.getPlayerFromColorOrFaction(decoyUnit.faction());
            if (player == null) continue;
            grantReplayWarSunTech(player);
        }
    }

    private void grantReplayWarSunTech(Player player) {
        player.getUnitsOwned().add("warsun");
        if (!player.getTechs().contains("ws")) {
            player.getTechs().add("ws");
        }
    }

    private boolean decoyMatchesPlayer(DecoyUnit decoyUnit, Player player) {
        String colorId = normalizedColorId(decoyUnit.colorId());
        return colorId.equalsIgnoreCase(player.getColorID())
                || decoyUnit.faction().equalsIgnoreCase(player.getFaction());
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

    private String renderVanishedUnits(List<DecoyUnit> decoyUnits) {
        Map<String, Integer> vanishedGroups = new LinkedHashMap<>();
        for (DecoyUnit unit : decoyUnits) {
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

    private DecoyUnit findDecoyForRoll(
            CombatRollPayload payload, CombatRollPayload.UnitRoll unitRoll, List<DecoyUnit> decoyUnits) {
        for (DecoyUnit decoyUnit : decoyUnits) {
            if (matches(decoyUnit, payload, unitRoll)) {
                return decoyUnit;
            }
        }
        return null;
    }

    private boolean matches(DecoyUnit decoyUnit, CombatRollPayload payload, CombatRollPayload.UnitRoll unitRoll) {
        String actorColorId = Mapper.getColorID(payload.header().actorColor());
        if (actorColorId == null) actorColorId = payload.header().actorColor();
        return decoyUnit.unitType().plainName().equalsIgnoreCase(unitRoll.baseType())
                && normalizedColorId(decoyUnit.colorId()).equalsIgnoreCase(actorColorId);
    }

    private boolean matchesRollActor(DecoyUnit decoyUnit, CombatRollPayload payload) {
        if (payload == null || payload.header() == null) return false;
        String actorColorId = normalizedColorId(payload.header().actorColor());
        return normalizedColorId(decoyUnit.colorId()).equalsIgnoreCase(actorColorId)
                || decoyUnit.faction().equalsIgnoreCase(payload.header().actorFaction());
    }

    private CombatRollPayload.UnitRoll syntheticDecoyRoll(CombatRollPayload payload, DecoyUnit decoyUnit) {
        if (payload == null || payload.header() == null || decoyUnit == null || decoyUnit.count() <= 0) return null;
        UnitModel unitModel = Mapper.getUnit(decoyUnit.unitType().plainName());
        if (unitModel == null) return null;
        int dicePerUnit = unitModel.getCombatDieCountForAbility(payload.header().rollType());
        if (dicePerUnit <= 0) return null;
        int printedHitsOn =
                unitModel.getCombatDieHitsOnForAbility(payload.header().rollType());
        int effectiveThreshold = printedHitsOn;
        List<CombatRollPayload.DieRoll> dice = new ArrayList<>();
        for (int i = 0; i < decoyUnit.count() * dicePerUnit; i++) {
            dice.add(new CombatRollPayload.DieRoll(
                    randomMiss(effectiveThreshold), effectiveThreshold, false, DieRollSource.DECOY));
        }
        return new CombatRollPayload.UnitRoll(
                unitModel.getId(),
                unitModel.getAsyncId(),
                unitModel.getBaseType(),
                unitModel.getName(),
                syntheticDisplayName(unitModel),
                unitModel.getUnitEmoji().toString(),
                decoyUnit.count(),
                dicePerUnit,
                0,
                printedHitsOn,
                0,
                effectiveThreshold,
                CombatRollPayload.RollSegmentType.PRIMARY,
                dice,
                0);
    }

    private String syntheticDisplayName(UnitModel unitModel) {
        if (unitModel.getUpgradesFromUnitId().isPresent()
                || unitModel.getFaction().isPresent()) {
            return unitModel.getName();
        }
        return "";
    }

    private String normalizedColorId(String color) {
        String colorId = Mapper.getColorID(color);
        return colorId == null ? color : colorId;
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
