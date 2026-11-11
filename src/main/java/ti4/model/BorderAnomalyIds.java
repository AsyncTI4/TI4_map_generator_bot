package ti4.model;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BorderAnomalyIds {
    public static final String VOID_TETHER = "void_tether";
    public static final String SPATIAL_TEAR = "spatial_tear";

    public static final List<String> REFERENCED_IN_CODE = List.of(VOID_TETHER, SPATIAL_TEAR);
}
