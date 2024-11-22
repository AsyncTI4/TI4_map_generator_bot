package ti4.map;

import java.awt.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "javaClassType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Space.class, name = "Space"),
    @JsonSubTypes.Type(value = Planet.class, name = "Planet")
})
abstract public class UnitHolder {

    private final String name;

    private final Point holderCenterPosition;

    // ID, Count
    private final Map<UnitKey, Integer> units = new LinkedHashMap<>();
    private final Map<UnitKey, Integer> unitsDamage = new LinkedHashMap<>();

    private final Set<String> ccList = new LinkedHashSet<>();
    private final Set<String> controlList = new LinkedHashSet<>();
    protected final Set<String> tokenList = new LinkedHashSet<>();

    public String getName() {
        return name;
    }

    @JsonCreator
    public UnitHolder(@JsonProperty("name") String name,
        @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        this.name = name;
        this.holderCenterPosition = holderCenterPosition;
    }

    public void inheritEverythingFrom(UnitHolder other) {
        units.putAll(other.getUnits());
        unitsDamage.putAll(other.getUnitDamage());

        ccList.addAll(other.getCCList());
        controlList.addAll(other.getControlList());
        tokenList.addAll(other.getTokenList());
    }

    public void addUnit(UnitKey unit, Integer count) {
        if (count == null || count <= 0) {
            return;
        }
        Integer unitCount = units.get(unit);
        if (unitCount != null) {
            unitCount += count;
            units.put(unit, unitCount);
        } else {
            units.put(unit, count);
        }
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

    public void removeUnit(UnitKey unit, Integer count) {
        if (count == null || count <= 0) {
            return;
        }
        Integer unitCount = units.get(unit);
        if (unitCount == null) {
            return;
        }
        unitCount -= count;
        if (unitCount > 0) {
            units.put(unit, unitCount);
        } else {
            units.remove(unit);
        }
    }

    public void addUnitDamage(UnitKey unit, Integer count) {
        if (count == null || count <= 0) {
            return;
        }
        Integer unitCount = unitsDamage.get(unit);
        if (unitCount != null) {
            unitCount += count;
            unitsDamage.put(unit, unitCount);
        } else {
            unitsDamage.put(unit, count);
        }
    }

    public void removeUnitDamage(UnitKey unit, Integer count) {
        if (count == null || count <= 0) {
            return;
        }
        Integer unitCount = unitsDamage.get(unit);
        if (unitCount != null) {
            unitCount -= count;
            if (unitCount > 0) {
                unitsDamage.put(unit, unitCount);
            } else {
                unitsDamage.remove(unit);
            }
        }
    }

    public void removeAllUnitDamage(String color) {
        String colorID = Mapper.getColorID(color);
        if (colorID == null)
            return;
        unitsDamage.keySet().removeIf(key -> key.getColorID().equals(colorID));
    }

    public void removeAllUnitDamage() {
        unitsDamage.clear();
    }

    public void removeAllUnits(String color) {
        String colorID = Mapper.getColorID(color);
        if (colorID == null)
            return;
        units.keySet().removeIf(key -> key.getColorID().equals(colorID));
    }

    public void removeAllShips(Player player) {
        units.keySet().removeIf(key -> key.getColorID().equals(player.getColorID()) && player.getUnitFromUnitKey(key).getIsShip());
    }

    public Map<UnitKey, Integer> getUnits() {
        for (UnitKey uk : units.keySet())
            if (units.get(uk) == null || units.get(uk) <= 0)
                units.remove(uk);
        return units;
    }

    @JsonIgnore
    public int getUnitCount() {
        int count = 0;
        for (Integer x : units.values())
            if (x != null) count += x;
        return count;
    }

    public int getUnitCount(UnitType unitType, Player player) {
        return getUnitCount(unitType, player.getColor());
    }

    public int getUnitCount(UnitType unitType, String color) {
        UnitKey uk = Units.getUnitKey(unitType, Mapper.getColorID(color));
        return getUnitCount(uk);
    }

    public int getUnitCount(UnitKey unitKey) {
        return Optional.ofNullable(getUnits().get(unitKey)).orElse(0);
    }

    @JsonIgnore
    public boolean hasUnits() {
        for (Integer count : units.values())
            if (count > 0) return true;
        return false;
    }

    @JsonProperty("unitsDamage")
    public Map<UnitKey, Integer> getUnitDamage() {
        return unitsDamage;
    }

    @NotNull
    public Integer getUnitDamageCount(UnitType unitType, String colorID) {
        return unitsDamage.entrySet().stream()
            .filter(e -> e.getKey().getUnitType() == unitType && e.getKey().getColorID().equals(colorID))
            .findFirst().map(Entry::getValue).orElse(0);
    }

    @NotNull
    @JsonIgnore
    public Integer getUnitDamageCount(UnitKey unitKey) {
        return getUnitDamageCount(unitKey.getUnitType(), unitKey.getColorID());
    }

    /**
     * Get the Command Counter list.
     */
    @JsonProperty("commandCounterList")
    public Set<String> getCCList() {
        return ccList;
    }

    public Set<String> getTokenList() {
        return tokenList;
    }

    public Set<String> getControlList() {
        return controlList;
    }

    public Point getHolderCenterPosition() {
        return holderCenterPosition;
    }

    public Map<String, Integer> getUnitAsyncIdsOnHolder(String colorID) {
        return new HashMap<>(units.entrySet().stream()
            .filter(unitEntry -> getUnitColor(unitEntry.getKey()).equals(Mapper.getColorID(colorID)))
            .collect(Collectors.toMap(entry -> getUnitAliasId(entry.getKey()), Entry::getValue)));
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
        return getUnits().keySet().stream()
            .map(this::getUnitColor)
            .distinct()
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public String getUnitAliasId(UnitKey unitKey) {
        return unitKey.asyncID();
    }

    @JsonIgnore
    public String getUnitColor(UnitKey unitKey) {
        return unitKey.getColorID();
    }
}
