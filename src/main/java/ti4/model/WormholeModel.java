package ti4.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class WormholeModel {
    public enum Wormhole {
        ALPHA,
        BETA,
        GAMMA,
        DELTA, ///
        EPSILON,
        ZETA,
        ETA,
        THETA,
        IOTA,
        KAPPA,
        LAMBDA,
        MU,
        NU,
        XI,
        OMICRON,
        PI,
        RHO,
        SIGMA,
        TAU,
        UPSILON,
        PHI,
        CHI,
        PSI,
        OMEGA, ///
        CUSTOM_ERONOUS_WHEPSILON,
        CUSTOM_ERONOUS_WHETA,
        CUSTOM_ERONOUS_WHIOTA,
        CUSTOM_ERONOUS_WHTHETA,
        CUSTOM_ERONOUS_WHZETA, ///
        WHNARROWS,
        WHPROBABILITY,
        WHCHAMPION,
        WHVOYAGE;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public String getWhString() {
            if (!super.toString().startsWith("wh") && !super.toString().startsWith("custom")) {
                return "wh" + super.toString().toLowerCase();
            }
            return super.toString();
        }
    }

    public Wormhole getWormholeFromString(String wh) {
        Map<String, Wormhole> allWormholes = Arrays.stream(Wormhole.values())
                .collect(Collectors.toMap(Wormhole::toString, (wormholeModel -> wormholeModel)));
        if (allWormholes.containsKey(wh.toLowerCase())) return allWormholes.get(wh.toLowerCase());
        return null;
    }
}
