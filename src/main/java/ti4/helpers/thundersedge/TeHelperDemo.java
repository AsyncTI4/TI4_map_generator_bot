package ti4.helpers.thundersedge;

import java.util.List;
import lombok.Getter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TeHelperDemo {
    @Getter
    private static final List<String> ExcludedFactions = List.of("cabal", "argent");
}
