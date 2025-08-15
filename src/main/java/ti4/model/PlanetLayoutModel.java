package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.image.UnitTokenPosition;

@Data
public class PlanetLayoutModel {
    private String unitHolderName;
    private ResInfLocation resourcesLocation;
    private Point centerPosition; // px
    private Integer planetRadius = 55;
    private Integer extraIcons = 0;

    public enum ResInfLocation {
        BottomLeft,
        BottomRight,
        TopLeft,
        TopRight
    }

    public UnitTokenPosition generateUnitTokenPosition() {
        UnitTokenPosition positions = new UnitTokenPosition(unitHolderName);
        // Tokens
        positions.addCntrPosition("control", getControlOffset());
        positions.addCntrPositions("att", getAttachmentOffsets(4));
        // Structures
        positions.addCntrPosition("sd", getSpaceDockOffset());
        positions.addCntrPositions("pd", getPdsOffsets());
        // Ground Forces
        positions.addCntrPositions("tkn_gf", getInfantryOffsets());
        positions.addCntrPositions("mf", getMechOffsets());
        return positions;
    }

    private Point polarToCartesian(double thetaDegrees, double radius, boolean offsetFromCenter) {
        double theta = Math.toRadians(-1 * thetaDegrees);
        int x = Math.clamp(Math.round(Math.cos(theta) * radius), -2 * planetRadius, 2 * planetRadius);
        int y = Math.clamp(Math.round(Math.sin(theta) * radius), -2 * planetRadius, 2 * planetRadius);
        Point p = new Point(x, y);
        if (offsetFromCenter) p.translate(centerPosition.x, centerPosition.y);
        return p;
    }

    /** Center of control token relative to tile */
    public Point getControlOffset() {
        return new Point(centerPosition.x + 2, centerPosition.y + 12);
    }

    /** Center of attachment tokens relative to tile */
    private List<Point> getAttachmentOffsets(int distinctAttachments) {
        List<Point> points = new ArrayList<>();

        double theta =
                switch (resourcesLocation) {
                    case BottomRight -> 225.0;
                    case BottomLeft -> 325.0;
                    case TopLeft -> 25.0;
                    case TopRight -> 45.0;
                };
        double deltaTheta =
                switch (resourcesLocation) {
                    case BottomLeft, TopRight -> 25.0;
                    case BottomRight, TopLeft -> -25.0;
                };
        if (planetRadius > 110) deltaTheta /= 2;

        for (int i = 0; i < distinctAttachments; i++) {
            points.add(polarToCartesian(theta, planetRadius + 5, true));
            theta += deltaTheta;
        }
        return points;
    }

    private Point getStructureOffset(int index) {
        Integer icons = extraIcons != null ? extraIcons : 0;
        double deltaTheta =
                switch (resourcesLocation) {
                    case BottomLeft, TopRight -> -30.0;
                    case BottomRight, TopLeft -> 30.0;
                };
        if (planetRadius > 110) deltaTheta /= 2;

        double adjustment = icons * deltaTheta;
        double theta =
                switch (resourcesLocation) {
                            case BottomLeft -> 192.0;
                            case TopLeft -> 165.0;
                            case BottomRight -> -10.0;
                            case TopRight -> 12.0;
                        }
                        + adjustment
                        + (deltaTheta * index);

        return polarToCartesian(theta, planetRadius + 8, true);
    }

    private Point getSpaceDockOffset() {
        return getStructureOffset(0);
    }

    private List<Point> getPdsOffsets() {
        List<Point> points = new ArrayList<>();
        points.add(new Point(centerPosition.x - 17, centerPosition.y + 33));
        points.add(new Point(centerPosition.x, centerPosition.y + 33));
        points.add(new Point(centerPosition.x + 17, centerPosition.y + 33));
        points.add(new Point(centerPosition.x - 6, centerPosition.y + 44));
        points.add(new Point(centerPosition.x + 10, centerPosition.y + 44));
        return points;
    }

    private List<Point> getInfantryOffsets() {
        List<Point> points = new ArrayList<>();
        points.add(new Point(centerPosition.x + 20, centerPosition.y));
        points.add(new Point(centerPosition.x + 20, centerPosition.y + 42));
        return points;
    }

    private List<Point> getMechOffsets() {
        List<Point> points = new ArrayList<>();
        points.add(new Point(centerPosition.x - 9, centerPosition.y - 34));
        points.add(new Point(centerPosition.x + 9, centerPosition.y - 34));
        points.add(new Point(centerPosition.x - 16, centerPosition.y - 21));
        points.add(new Point(centerPosition.x + 2, centerPosition.y - 21));
        points.add(new Point(centerPosition.x + 20, centerPosition.y - 21));
        return points;
    }
}
