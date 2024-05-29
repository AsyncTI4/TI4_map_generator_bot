package ti4.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.awt.Point;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "javaClassType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Space.class, name = "Space"),
    @JsonSubTypes.Type(value = Planet.class, name = "Planet")
})
abstract public class UnitHolder {
    private final String name;

    private final Point holderCenterPosition;

    // ID, Count
    private final Map<UnitKey, Integer> units = new HashMap<>();
    private final Map<UnitKey, Integer> unitsDamage = new HashMap<>();

    private final Set<String> ccList = new HashSet<>();
    private final Set<String> controlList = new HashSet<>();
    protected final Set<String> tokenList = new HashSet<>();

    public String getName() {
        return name;
    }

    @JsonCreator
    public UnitHolder(@JsonProperty("name") String name,
        @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        this.name = name;
        this.holderCenterPosition = holderCenterPosition;
    }

    public void addUnit(UnitKey unit, Integer count) {
        if (count != null && count > 0) {
            Integer unitCount = units.get(unit);
            if (unitCount != null) {
                unitCount += count;
                units.put(unit, unitCount);
            } else {
                units.put(unit, count);
            }
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
        if (count != null && count > 0) {
            Integer unitCount = units.get(unit);
            if (unitCount != null) {
                unitCount -= count;
                if (unitCount > 0) {
                    units.put(unit, unitCount);
                } else {
                    units.remove(unit);
                }
            }
        }
    }

    public void addUnitDamage(UnitKey unit, Integer count) {
        if (count != null && count > 0) {
            Integer unitCount = unitsDamage.get(unit);
            if (unitCount != null) {
                unitCount += count;
                unitsDamage.put(unit, unitCount);
            } else {
                unitsDamage.put(unit, count);
            }
        }
    }

    public void removeUnitDamage(UnitKey unit, Integer count) {
        if (count != null && count > 0) {
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
        return units;
    }

    @NotNull
    public Integer getUnitCount(UnitType unitType, Player player) {
        return getUnitCount(unitType, player.getColor());
    }

    @NotNull
    public Integer getUnitCount(UnitType unitType, String color) {
        if (unitType == null || color == null) return 0;
        String colorIDofUnit = Mapper.getColorID(color);
        if (colorIDofUnit == null) {
            colorIDofUnit = color;
        }
        String effinColor = colorIDofUnit;
        return units.entrySet().stream()
            .filter(e -> e.getKey().getUnitType() == unitType && e.getKey().getColorID().equals(effinColor))
            .findFirst().map(Entry::getValue).orElse(0);
    }

    @JsonIgnore
    public boolean hasUnits() {
        return !getUnits().isEmpty();
    }

    @JsonProperty("unitsDamage")
    public Map<UnitKey, Integer> getUnitDamage() {
        return unitsDamage;
    }

    @NotNull
    public Integer getUnitDamageCount(UnitType unitType, String color) {
        return unitsDamage.entrySet().stream()
            .filter(e -> e.getKey().getUnitType() == unitType && e.getKey().getColorID().equals(color))
            .findFirst().map(Entry::getValue).orElse(0);
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

    public Map<String, Integer> getUnitAsyncIdsOnHolder(String color) {
        return new HashMap<>(units.entrySet().stream()
            .filter(unitEntry -> getUnitColor(unitEntry.getKey()).equals(color))
            .collect(Collectors.toMap(entry -> getUnitAliasId(entry.getKey()), Entry::getValue)));
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
