package ti4.service.rules;

import lombok.experimental.UtilityClass;

/** Shared predicates for special-case planet identities. */
@UtilityClass
public class PlanetRules {

    /** Custodia Vigilia and Ghoti are special planets that many board-only effects must exclude. */
    public static boolean isCustodiaVigiliaOrGhoti(String planetId) {
        String normalized = planetId.toLowerCase();
        return normalized.contains("custodia") || normalized.contains("ghoti");
    }
}
