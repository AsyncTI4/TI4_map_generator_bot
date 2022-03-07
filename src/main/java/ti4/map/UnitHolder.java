package ti4.map;

import ti4.generator.Mapper;
import ti4.helpers.Constants;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

abstract public class UnitHolder {
    //ID, Count
    private final HashMap<String, Integer> units = new HashMap<>();
    private Point holderCenterPosition;

    private String name = null;


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

    public HashMap<Point, String> getUnitsForImage() {
        HashMap<Point, String> unitPositions = new HashMap<>();

        int unitCount = 0;
        int unitCountGFFF = 0;
        for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
            String key = unitEntry.getKey();
            if (!Constants.GF.equals(key) && !Constants.FF.equals(key)) {
                unitCount += unitEntry.getValue();
            } else {
                unitCountGFFF += unitEntry.getValue();
            }
        }
        if (unitCount > 0 || unitCountGFFF > 0) {
            int degreeChange = 360 / (Math.max(unitCount + 1, 6));
            int degree = 0;
            int radius = name.equals(Constants.SPACE) ? Constants.SPACE_RADIUS : Constants.RADIUS;
            for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                String key = unitEntry.getKey();
                Integer value = unitEntry.getValue();
                if (Constants.GF.equals(key) || Constants.FF.equals(key)) {

                }
                for (int i = 0; i < value; i++) {
                    int x = (int) (radius * Math.sin(degree));
                    int y = (int) (radius * Math.cos(degree));
                    unitPositions.put(new Point(x, y), key);
                    degree += degreeChange;
                }
            }
        }
        return unitPositions;
    }

    public HashMap<String, Integer> getUnits() {
        return units;
    }

    public Point getHolderCenterPosition() {
        return holderCenterPosition;
    }
}
