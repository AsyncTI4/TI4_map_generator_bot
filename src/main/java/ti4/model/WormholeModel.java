package ti4.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WormholeModel {
    public enum Wormhole {

        ALPHA,
        BETA,
        GAMMA,
        DELTA,
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
        OMEGA;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }
    public Wormhole getWormholeFromString(String wh) {
        Map<String, Wormhole> allWormholes = Arrays.stream(Wormhole.values())
                .collect(
                        Collectors.toMap(
                                Wormhole::toString,
                                (wormholeModel -> wormholeModel)
                        )
                );
        if (allWormholes.containsKey(wh.toLowerCase()))
            return allWormholes.get(wh.toLowerCase());
        return null;
    }
}