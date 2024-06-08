package ti4.generator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UnitTokenPosition implements Serializable {
    private final String unitHolderName;
    private LinkedHashMap<String, List<Point>> coordinateMap = new LinkedHashMap<>();
    private final boolean removeUnitCoordinate;

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

    public String getUnitHolderName() {
        return unitHolderName;
    }

    public Map<String, List<Point>> getCoordinateMap() {
        return coordinateMap;
    }

    public boolean getRemoveUnitCoordinate() {
        return removeUnitCoordinate;
    }

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
