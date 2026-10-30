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
        VORTEX("Vx");

        private final String symbol;

        Wormhole(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }
}
