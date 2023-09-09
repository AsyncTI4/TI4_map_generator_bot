package ti4.generator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class UnitTokenPosition implements Serializable {
    private String unitHolderName;
    private LinkedHashMap<String, List<Point>> coordinateMap = new LinkedHashMap<>();
    private boolean removeUnitCoordinate;

    @JsonCreator
    public UnitTokenPosition(@JsonProperty("unitHolderName") String unitHolderName,
                             @JsonProperty("coordinateMap") LinkedHashMap<String, List<Point>> coordinateMap,
                             @JsonProperty("removeUnitCoordinate") boolean removeUnitCoordinate) {
        this.unitHolderName = unitHolderName;
        this.coordinateMap = coordinateMap;
        this.removeUnitCoordinate = removeUnitCoordinate;
    }

    public UnitTokenPosition(String unitHolderName) {
        this(unitHolderName, true);
    }

    public UnitTokenPosition(String unitHolderName, boolean removeUnitCoordinate) {
        this.unitHolderName = unitHolderName;
        this.removeUnitCoordinate = removeUnitCoordinate;
    }

    public String getUnitHolderName() { return this.unitHolderName; }
    public LinkedHashMap<String, List<Point>> getCoordinateMap() { return this.coordinateMap; }

    /*public ArrayList<String> getUnitOrder() {
        ArrayList<String> unitOrder = new ArrayList<>();
        for (Map.Entry<String, List<Point>> entry : coordinateMap.entrySet()) {
            unitOrder.add(entry.getKey());
        }
        return unitOrder;
    }*/

    public boolean getRemoveUnitCoordinate() { return this.removeUnitCoordinate; }
    public int getPositionCount(String id) {
        return coordinateMap.get(id).size();
    }


    public void addPosition(String id, Point point) {
        List<Point> points = coordinateMap.computeIfAbsent(id, key -> new ArrayList<>());
        points.add(point);
        coordinateMap.put(id, points);
    }

    public Point getPosition(String id) {
        List<Point> points = coordinateMap.get(id);
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
