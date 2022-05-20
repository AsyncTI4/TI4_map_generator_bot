package ti4.map;

import ti4.generator.Mapper;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

abstract public class UnitHolder {
    //ID, Count
    private final HashMap<String, Integer> units = new HashMap<>();
    //ID, Count
    private final HashMap<String, Integer> unitsDamage = new HashMap<>();
    private final HashSet<String> ccList = new HashSet<>();
    private final HashSet<String> controlList = new HashSet<>();
    protected final HashSet<String> tokenList = new HashSet<>();
    private final Point holderCenterPosition;

    private final String name;


    public String getName() {
        return name;
    }

    protected UnitHolder(String name, Point holderCenterPosition) {
        this.name = name;
        this.holderCenterPosition = holderCenterPosition;
    }

    public void addUnit(String unit, Integer count) {
        if (count > 0 && count < 100) {
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

    public void addToken(String cc) {
        tokenList.add(cc);
    }
    public void removeToken(String cc) {
        tokenList.remove(cc);
    }

    public void removeAllCC() {
        ccList.clear();
    }

    public void removeUnit(String unit, Integer count) {
        if (count > 0) {
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
        if (count > 0 && count < 100) {
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
        if (count > 0) {
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
        unitsDamage.keySet().removeIf(key -> key.startsWith(colorID));
    }

    public void removeAllUnitDamage() {
        unitsDamage.clear();
    }


    public void removeAllUnits(String color) {
        String colorID = Mapper.getColorID(color);
        units.keySet().removeIf(key -> key.startsWith(colorID));
    }

    public HashMap<String, Integer> getUnits() {
        return units;
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
}
