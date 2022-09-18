package ti4.generator;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class UnitTokenPosition {
    private String unitHolderName;
    LinkedHashMap<String, ArrayList<Point>> coordinateMap = new LinkedHashMap<>();
    private boolean removeUnitCoordinate = true;

    public UnitTokenPosition(String unitHolderName) {
        this(unitHolderName, true);
    }

    public UnitTokenPosition(String unitHolderName, boolean removeUnitCoordinate) {
        this.unitHolderName = unitHolderName;
        this.removeUnitCoordinate = removeUnitCoordinate;
    }

    public ArrayList<String> getUnitOrder() {
        ArrayList<String> unitOrder = new ArrayList<>();
        for (Map.Entry<String, ArrayList<Point>> entry : coordinateMap.entrySet()) {
            unitOrder.add(entry.getKey());
        }
        return unitOrder;
    }

    public void addPosition(String id, Point point) {
        ArrayList<Point> points = coordinateMap.computeIfAbsent(id, key -> new ArrayList<>());
        points.add(point);
        coordinateMap.put(id, points);
    }

    public Point getPosition(String id) {
        ArrayList<Point> points = coordinateMap.get(id);
        if (points == null) {
            id = coordinateMap.keySet().stream().filter(id::contains).findFirst().orElse(null);
            if (id == null) {
                return null;
            }
        }
        points = coordinateMap.get(id);
        if (points == null || points.isEmpty()) {
            return null;
        }
        Point point = points.get(0);
        if (removeUnitCoordinate) {
            points.remove(0);
        } else if (points.size() > 1) {
            points.remove(0);
        }
        if (points.isEmpty()) {
            coordinateMap.remove(id);
        } else {
            coordinateMap.put(id, points);
        }
        return new Point(point);
    }
}
