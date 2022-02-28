package generator;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class PositionMapper {

    private static Map<String, Point> positionMap = new HashMap<>();

    static {
        positionMap.put("0", new Point(0, 0));
        positionMap.put("3a", new Point(788, 0));
        positionMap.put("3r", new Point(513, 150));
        positionMap.put("3b", new Point(1058, 150));
        positionMap.put("2a", new Point(788, 300));
    }

    @CheckForNull
    public static Point getPosition(String position) {
        return positionMap.get(position);
    }
}
