package ti4.map;

import ti4.generator.Mapper;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

abstract public class UnitHolder {
    //ID, Count
    private final HashMap<String, Integer> units = new HashMap<>();
    private final ArrayList<String> ccList = new ArrayList<>();
    private final ArrayList<String> controlList = new ArrayList<>();
    private Point holderCenterPosition;

    private String name;


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
        controlList.add(cc);
    }

    public void removeCC(String cc) {
        ccList.remove(cc);
    }
    public void removeControl(String cc) {
        controlList.remove(cc);
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

    public void removeAllUnits(String color) {
        String colorID = Mapper.getColorID(color);
        units.keySet().removeIf(key -> key.startsWith(colorID));
    }

    public HashMap<String, Integer> getUnits() {
        return units;
    }

    public ArrayList<String> getCCList() {
        return ccList;
    }

    public ArrayList<String> getControlList() {
        return controlList;
    }

    public Point getHolderCenterPosition() {
        return holderCenterPosition;
    }
}
