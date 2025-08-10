package ti4.image;

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
    public UnitTokenPosition(
            @JsonProperty("unitHolderName") String unitHolderName,
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

    public void addPositions(String id, List<Point> points) {
        List<Point> existing = coordinateMap.computeIfAbsent(id, key -> new ArrayList<>());
        existing.addAll(points);
        coordinateMap.put(id, existing);
    }

    public Point getPosition(String unitAsyncID) {
        List<Point> points = coordinateMap.get(unitAsyncID);
        if (points == null) {
            unitAsyncID = coordinateMap.keySet().stream()
                    .filter(unitAsyncID::contains)
                    .findFirst()
                    .orElse(null); // TODO This is why Cavalry lands on Cruiser (id = "ca")
            if (unitAsyncID == null) {
                return null;
            }
        }
        points = coordinateMap.get(unitAsyncID);
        if (points == null || points.isEmpty()) {
            return null;
        }
        Point point = points.getFirst();
        if (removeUnitCoordinate) {
            points.removeFirst();
        } else if (points.size() > 1) {
            points.removeFirst();
        }
        if (points.isEmpty()) {
            coordinateMap.remove(unitAsyncID);
        } else {
            coordinateMap.put(unitAsyncID, points);
        }
        return new Point(point);
    }

    @Deprecated
    private static Point offsetToTopLeft(Point p, String purpose) {
        return switch (purpose) {
            case "control" -> new Point(p.x - 36, p.y - 24);
            case "att" -> new Point(p.x - 32, p.y - 17);
            case "sd" -> new Point(p.x - 21, p.y - 21);
            case "pd" -> new Point(p.x - 16, p.y - 18);
            case "tkn_gf" -> new Point(p.x - 35, p.y - 17);
            case "mf" -> new Point(p.x - 22, p.y - 22);
            default -> new Point(p.x, p.y);
        };
    }

    @Deprecated
    public void addCntrPosition(String id, Point point) {
        List<Point> points = coordinateMap.computeIfAbsent(id, key -> new ArrayList<>());
        points.add(offsetToTopLeft(point, id));
        coordinateMap.put(id, points);
    }

    @Deprecated
    public void addCntrPositions(String id, List<Point> points) {
        List<Point> existing = coordinateMap.computeIfAbsent(id, key -> new ArrayList<>());
        existing.addAll(points.stream().map(p -> offsetToTopLeft(p, id)).toList());
        coordinateMap.put(id, existing);
    }
}
