package ti4.map;

import java.awt.*;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.UnitModel;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "javaClassType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Space.class, name = "Space"),
    @JsonSubTypes.Type(value = Planet.class, name = "Planet")
})
@Data
public abstract class UnitHolder {

    private final String name;
    private final Point holderCenterPosition;

    private final Map<UnitKey, List<Integer>> unitsByState = new HashMap<>();
    private final Set<String> ccList = new LinkedHashSet<>();
    private final Set<String> controlList = new LinkedHashSet<>();
    protected final Set<String> tokenList = new LinkedHashSet<>();

    public String getName() {
        return name;
    }

    @JsonCreator
    protected UnitHolder(
            @JsonProperty("name") String name, @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        this.name = name;
        this.holderCenterPosition = holderCenterPosition;
    }

    public abstract String getRepresentation(Game game);

    public void inheritEverythingFrom(UnitHolder other) {
        unitsByState.putAll(other.unitsByState);

        ccList.addAll(other.ccList);
        controlList.addAll(other.controlList);
        tokenList.addAll(other.tokenList);
    }

    public void addUnit(UnitKey unit, Integer count) {
        if (count == null || count <= 0) {
            return;
        }

        unitsByState.compute(unit, (uk, ls) -> {
            if (ls == null) ls = UnitState.emptyList();
            ls.set(0, ls.getFirst() + count);
            return ls;
        });
    }

    public void addUnitsWithStates(UnitKey unit, List<Integer> counts) {
        if (getTotalUnitCount(counts) <= 0) {
            return;
        }

        unitsByState.compute(unit, (uk, ls) -> {
            if (ls == null) ls = UnitState.emptyList();
            for (int i = 0; i < ls.size(); i++) {
                if (i < counts.size()) {
                    ls.set(i, ls.get(i) + counts.get(i));
                } else {
                    break;
                }
            }
            return ls;
        });
    }

    public void addCC(String cc) {
        ccList.add(cc);
    }

    public boolean hasCC(String cc) {
        return ccList.contains(cc);
    }

    public void addControl(String cc) {
        controlList.clear();
        controlList.add(cc);
    }

    public void removeCC(String cc) {
        ccList.remove(cc);
    }

    public void removeControl(String cc) {
        controlList.remove(cc);
    }

    /**
     * Adds a variety of tokens from faction effects and other game mechanics (sleeper tokens,
     * frontier tokens, wormhole tokens, etc).
     */
    public boolean addToken(String cc) {
        return tokenList.add(cc);
    }

    public boolean removeToken(String cc) {
        return tokenList.remove(cc);
    }

    public void removeAllTokens() {
        tokenList.clear();
    }

    public void removeAllCC() {
        ccList.clear();
    }

    public List<Integer> removeUnit(UnitKey unit, int count) {
        return removeUnit(unit, count, null);
    }

    public List<Integer> removeUnit(UnitKey unit, int count, UnitState preferredState) {
        if (count <= 0) return UnitState.emptyList();

        List<Integer> counts = unitsByState.get(unit);
        int totalCount = getTotalUnitCount(counts);
        if (totalCount <= 0) {
            unitsByState.remove(unit);
            return UnitState.emptyList();
        }

        List<Integer> unitsRemoved = UnitState.emptyList();

        Set<UnitState> removeOrder = new LinkedHashSet<>();
        if (preferredState != null) removeOrder.add(preferredState);
        removeOrder.addAll(UnitState.defaultRemoveOrder());

        for (UnitState state : removeOrder) {
            int amt = getUnitStateCount(counts, state);
            int index = state.ordinal();
            if (amt >= count) {
                unitsRemoved.set(index, count);
                counts.set(index, amt - count);
                count = 0;
                break;
            } else {
                unitsRemoved.set(index, amt);
                count -= amt;
                counts.set(index, 0);
            }
        }

        int leftover = getTotalUnitCount(counts);
        if (leftover <= 0) {
            unitsByState.remove(unit);
        } else {
            unitsByState.put(unit, counts);
        }

        return unitsRemoved;
    }

    public int addDamagedUnit(UnitKey unit, int count) {
        return flipUnitStates(unit, count, UnitState.DMG, false);
    }

    public int removeDamagedUnit(UnitKey unit, int count) {
        return flipUnitStates(unit, count, UnitState.DMG, true);
    }

    // magic
    private int flipUnitStates(UnitKey unit, int count, int bit, boolean isSet) {
        if (count <= 0) return 0;

        List<Integer> counts = unitsByState.get(unit);
        if (getTotalUnitCount(counts) <= 0) return 0;

        int amtFlipped = 0;
        List<UnitState> ord = isSet ? UnitState.defaultRemoveStatusOrder() : UnitState.defaultAddStatusOrder();
        for (UnitState state : ord) {
            if (!isSet && (state.ordinal() & bit) > 0) continue; // ignore states with bit setdo
            if (isSet && (state.ordinal() & bit) == 0) continue; // ignore states without bit set
            // e.g. damaged units
            int origIndex = state.ordinal();
            int origAmt = getUnitStateCount(counts, state);

            // e.g. undamaged units
            int newIndex = origIndex ^ bit;
            int newAmt = getUnitStateCount(counts, UnitState.values()[newIndex]);

            if (origAmt >= count) {
                counts.set(origIndex, origAmt - count);
                counts.set(newIndex, newAmt + count);
                amtFlipped += count;
                count = 0;
                break;
            } else {
                counts.set(origIndex, 0);
                counts.set(newIndex, newAmt + origAmt);
                amtFlipped += origAmt;
                count -= origAmt;
            }
        }
        return amtFlipped;
    }

    public void removeAllUnitDamage(String color) {
        String colorID = Mapper.getColorID(color);
        for (UnitKey uk : unitsByState.keySet())
            if (uk.getColorID().equals(colorID)) removeDamagedUnit(uk, getUnitCount(uk));
    }

    public void removeAllUnitDamage() {
        for (UnitKey uk : unitsByState.keySet()) removeDamagedUnit(uk, getUnitCount(uk));
    }

    public void removeAllUnits(String color) {
        String colorID = Mapper.getColorID(color);
        if (colorID == null) return;
        unitsByState.keySet().removeIf(key -> key.getColorID().equals(colorID));
    }

    /** Return the set unit keys that are actually on this unitholder (quantity > 0) */
    @JsonIgnore
    public Set<UnitKey> getUnitKeys() {
        return unitsByState.entrySet().stream()
                .filter(e -> getTotalUnitCount(e.getValue()) > 0)
                .map(Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Deprecated
    @JsonIgnore
    public Map<UnitKey, Integer> getUnits() {
        Map<UnitKey, Integer> units = new HashMap<>();
        for (Entry<UnitKey, List<Integer>> entry : unitsByState.entrySet()) {
            UnitKey uk = entry.getKey();
            List<Integer> counts = entry.getValue();
            if (getTotalUnitCount(counts) <= 0) {
                unitsByState.remove(uk);
            } else {
                units.put(uk, getTotalUnitCount(counts));
            }
        }
        return units;
    }

    @Deprecated
    @JsonIgnore
    public Map<UnitKey, Integer> getUnitDamage() {
        Map<UnitKey, Integer> units = new HashMap<>();
        for (UnitKey uk : unitsByState.keySet()) {
            int amt = getDamagedUnitCount(uk);
            if (amt > 0) units.put(uk, amt);
        }
        return units;
    }

    @JsonIgnore
    public int getUnitCount() {
        int count = 0;
        for (UnitKey uk : unitsByState.keySet()) count += getUnitCount(uk);
        return count;
    }

    public int getUnitCount(UnitType unitType, Player player) {
        return getUnitCount(unitType, player.getColor());
    }

    public int getUnitCountForState(UnitKey unitKey, UnitState state) {
        List<Integer> states = unitsByState.getOrDefault(unitKey, UnitState.emptyList());
        if (states.size() <= state.ordinal()) return 0;
        return states.get(state.ordinal());
    }

    public int getUnitCountForState(UnitType unitType, Player player, UnitState state) {
        return getUnitCountForState(Units.getUnitKey(unitType, player.getColor()), state);
    }

    public int getUnitCount(UnitType unitType, String color) {
        UnitKey uk = Units.getUnitKey(unitType, Mapper.getColorID(color));
        return getUnitCount(uk);
    }

    public int getUnitCount(UnitKey unitKey) {
        return getTotalUnitCount(unitsByState.get(unitKey));
    }

    public int getUnitCount(String colorID) {
        return unitsByState.entrySet().stream()
                .filter(e -> e.getKey().getColorID().equals(colorID))
                .mapToInt(e -> getTotalUnitCount(e.getValue()))
                .sum();
    }

    @JsonIgnore
    public boolean hasUnits() {
        for (List<Integer> counts : unitsByState.values()) if (getTotalUnitCount(counts) > 0) return true;
        return false;
    }

    @JsonIgnore
    public int getTotalDamagedCount() {
        return unitsByState.values().stream()
                .mapToInt(UnitHolder::getDamagedUnitStateCount)
                .sum();
    }

    public int getDamagedUnitCount(UnitKey unitKey) {
        return Optional.ofNullable(unitsByState.get(unitKey))
                .map(UnitHolder::getDamagedUnitStateCount)
                .orElse(0);
    }

    public int getDamagedUnitCount(UnitType unitType, String colorID) {
        return getDamagedUnitCount(Units.getUnitKey(unitType, colorID));
    }

    public int getDamagedUnitCount(String colorID) {
        return unitsByState.entrySet().stream()
                .filter(e -> e.getKey().getColorID().equals(colorID))
                .mapToInt(e -> getDamagedUnitStateCount(e.getValue()))
                .sum();
    }

    public Point getHolderCenterPosition() {
        return new Point(holderCenterPosition);
    }

    public Point getHolderCenterPosition(Tile tile) {
        if (Constants.TOKEN_PLANETS.contains(name)) {
            return Helper.getTokenPlanetCenterPosition(tile, name);
        }
        return getHolderCenterPosition();
    }

    public Map<UnitKey, List<Integer>> getUnitsByStateForPlayer(Player p) {
        return new HashMap<>(unitsByState.entrySet().stream()
                .filter(e -> e.getKey().getColorID().equals(p.getColorID()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
    }

    public Map<UnitKey, List<Integer>> getUnitsByStateForPlayer(String color) {
        return new HashMap<>(unitsByState.entrySet().stream()
                .filter(e -> e.getKey().getColorID().equals(Mapper.getColorID(color)))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
    }

    public Map<String, Integer> getUnitAsyncIdsOnHolder(String colorID) {
        return new HashMap<>(unitsByState.keySet().stream()
                .filter(uk -> uk.getColorID().equals(Mapper.getColorID(colorID)))
                .collect(Collectors.toMap(UnitKey::asyncID, this::getUnitCount)));
    }

    public String getPlayersUnitListOnHolder(Player player) {
        return getUnitAsyncIdsOnHolder(player.getColorID()).entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(","));
    }

    public String getPlayersUnitListEmojisOnHolder(Player player) {
        return Helper.getUnitListEmojis(getPlayersUnitListOnHolder(player));
    }

    @JsonIgnore
    public List<String> getUnitColorsOnHolder() {
        return getUnits().keySet().stream().map(UnitKey::getColorID).distinct().collect(Collectors.toList());
    }

    private static int getUnitStateCount(List<Integer> counts, UnitState state) {
        return counts == null ? 0 : counts.get(state.ordinal());
    }

    private static int getTotalUnitCount(List<Integer> counts) {
        if (counts == null) return 0;
        int tot = 0;
        for (UnitState state : UnitState.values()) tot += getUnitStateCount(counts, state);
        return tot;
    }

    private static int getDamagedUnitStateCount(List<Integer> counts) {
        if (counts == null) return 0;
        int tot = 0;
        for (UnitState state : UnitState.values()) tot += state.isDamaged() ? getUnitStateCount(counts, state) : 0;
        return tot;
    }

    @JsonIgnore
    public int countPlayersUnitsWithModelCondition(Player p, Predicate<? super UnitModel> condition) {
        return getUnits().entrySet().stream()
                .filter(e -> e.getValue() > 0 && p.unitBelongsToPlayer(e.getKey()))
                .filter(e -> Optional.ofNullable(p.getUnitFromUnitKey(e.getKey()))
                        .map(condition::test)
                        .orElse(false))
                .mapToInt(Entry::getValue)
                .sum();
    }
}
