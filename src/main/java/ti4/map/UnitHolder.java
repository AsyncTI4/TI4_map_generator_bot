package ti4.map;

import java.util.List;
import ti4.generator.Mapper;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Space.class, name = "space"),
        @JsonSubTypes.Type(value = Planet.class, name = "planet")
})
abstract public class UnitHolder {
    private final String name;

    private final Point holderCenterPosition;

    // ID, Count
    private final HashMap<String, Integer> units = new HashMap<>();
    // ID, Count
    private final HashMap<String, Integer> unitsDamage = new HashMap<>();
    private final HashSet<String> ccList = new HashSet<>();
    private final HashSet<String> controlList = new HashSet<>();
    protected final HashSet<String> tokenList = new HashSet<>();

    public String getName() {
        return name;
    }

    @JsonCreator
    public UnitHolder(@JsonProperty("name") String name,
            @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        this.name = name;
        this.holderCenterPosition = holderCenterPosition;
    }

    public void addUnit(String unit, Integer count) {
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

    public void removeUnit(String unit, Integer count) {
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

    public void addUnitDamage(String unit, Integer count) {
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

    public void removeUnitDamage(String unit, Integer count) {
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
        unitsDamage.keySet().removeIf(key -> key.startsWith(colorID));
    }

    public void removeAllUnitDamage() {
        unitsDamage.clear();
    }

    public void removeAllUnits(String color) {
        String colorID = Mapper.getColorID(color);
        if (colorID == null)
            return;
        units.keySet().removeIf(key -> key.startsWith(colorID));
    }

    public HashMap<String, Integer> getUnits() {
        return units;
    }

    @JsonIgnore
    public boolean hasUnits() {
        return !getUnits().isEmpty();
    }

    public HashMap<String, Integer> getUnitDamage() {
        return unitsDamage;
    }

    public HashSet<String> getCCList() {
        return ccList;
    }

    public HashSet<String> getTokenList() {
        return tokenList;
    }

    public HashSet<String> getControlList() {
        return controlList;
    }

    public Point getHolderCenterPosition() {
        return holderCenterPosition;
    }

    public HashMap<String, Integer> getUnitAsyncIdsOnHolder(String color) {
        return new HashMap<>(units.entrySet().stream()
                .filter(unitEntry -> getUnitColor(unitEntry.getKey()).equals(color))
                .collect(Collectors.toMap(entry -> getUnitAliasId(entry.getKey()), Entry::getValue)));
    }

    public List<String> getUnitColorsOnHolder() {
        return getUnits().keySet().stream()
                .map(this::getUnitColor)
                .distinct()
                .collect(Collectors.toList());
    }

    public String getUnitAliasId(String unitHolderString) {
        String unitHolderFileSuffix = ".png";
        String unitId = unitHolderString.substring(unitHolderString.indexOf("_") + 1);
        unitId = unitId.replace(unitHolderFileSuffix, "");
        return unitId;
    }

    public String getUnitColor(String unitHolderString) {
        String unitHolderFileSuffix = ".png";
        String unitColor = unitHolderString.substring(0, unitHolderString.indexOf("_"));
        unitColor = unitColor.replace(unitHolderFileSuffix, "");
        return unitColor;
    }
}
