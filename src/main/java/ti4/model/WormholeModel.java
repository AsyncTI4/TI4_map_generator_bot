package ti4.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public class WormholeModel {
    public enum Wormhole {
        ALPHA("α"),
        BETA("β"),
        GAMMA("γ"),
        DELTA("δ"),
        @JsonAlias("CUSTOM_ERONOUS_WHEPSILON")
        EPSILON("ε"),
        @JsonAlias("CUSTOM_ERONOUS_WHZETA")
        ZETA("ζ"),
        @JsonAlias("CUSTOM_ERONOUS_WHETA")
        ETA("η"),
        @JsonAlias("CUSTOM_ERONOUS_WHTHETA")
        THETA("θ"),
        @JsonAlias("CUSTOM_ERONOUS_WHIOTA")
        IOTA("ι"),
        KAPPA("κ"),
        LAMBDA("λ"),
        MU("μ"),
        NU("ν"),
        XI("ξ"),
        OMICRON("ο"),
        PI("π"),
        RHO("ρ"),
        SIGMA("σ"),
        TAU("τ"),
        UPSILON("υ"),
        PHI("φ"),
        CHI("χ"),
        PSI("ψ"),
        OMEGA("ω"),
        @JsonAlias("WHNARROWS")
        NARROWS("N"),
        @JsonAlias("WHPROBABILITY")
        PROBABILITY("P"),
        @JsonAlias("WHCHAMPION")
        CHAMPION("C"),
        @JsonAlias("WHVOYAGE")
        VOYAGE("V"),
        VORTEX("Vx"),
        ALPHA_RAT("α", true),
        BETA_RAT("β", true),
        GAMMA_RAT("γ", true),
        DELTA_RAT("δ", true),
        EPSILON_RAT("ε", true),
        ZETA_RAT("ζ", true),
        ETA_RAT("η", true),
        THETA_RAT("θ", true),
        IOTA_RAT("ι", true),
        KAPPA_RAT("κ", true);

        private final String symbol;
        private final boolean ratRace;

        Wormhole(String symbol) {
            this(symbol, false);
        }

        Wormhole(String symbol, boolean ratRace) {
            this.symbol = symbol;
            this.ratRace = ratRace;
        }

        public String symbol() {
            return symbol;
        }

        public boolean isRatRace() {
            return ratRace;
        }

        public String tokenId() {
            return ratRace ? "whrat" + toString().replace("_rat", "") : "wh" + this;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }
}
