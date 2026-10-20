package ti4.ai.strategy;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
class ResearchPrior {

    static final double WEIGHT = 1.0;
    private static final double TYPICAL_RATE = 0.25;
    private static final double RATE_SCALE = 2;
    private static final int HACAN_GAMES = 993;
    private static final int XXCHA_GAMES = 791;
    private static final int SARDAKK_GAMES = 991;
    private static final Map<String, Integer> SARDAKK_RESEARCH = Map.ofEntries(
            Map.entry("cv2", 831),
            Map.entry("gd", 821),
            Map.entry("exo2", 813),
            Map.entry("aida", 730),
            Map.entry("vpw", 591),
            Map.entry("lwd", 423),
            Map.entry("dd2", 420),
            Map.entry("ff2", 408),
            Map.entry("amd", 276),
            Map.entry("ws", 261),
            Map.entry("inf2", 255),
            Map.entry("det", 245),
            Map.entry("asc", 217),
            Map.entry("sr", 216),
            Map.entry("x89c4", 167),
            Map.entry("fl", 165),
            Map.entry("pa", 162),
            Map.entry("da", 142),
            Map.entry("md", 141),
            Map.entry("sdn", 140),
            Map.entry("cr2", 133),
            Map.entry("st", 121),
            Map.entry("pds2", 116),
            Map.entry("sd2", 102),
            Map.entry("bs", 93),
            Map.entry("nm", 86),
            Map.entry("sar", 60),
            Map.entry("td", 53),
            Map.entry("hm", 52),
            Map.entry("ie", 36),
            Map.entry("dxa", 30),
            Map.entry("ps", 26),
            Map.entry("pi", 16),
            Map.entry("gls", 6));
    private static final Map<String, Integer> HACAN_RESEARCH = Map.ofEntries(
            Map.entry("gd", 846),
            Map.entry("ws", 785),
            Map.entry("qdn", 687),
            Map.entry("dn2", 459),
            Map.entry("aida", 439),
            Map.entry("asc", 313),
            Map.entry("md", 312),
            Map.entry("cv2", 285),
            Map.entry("sd2", 249),
            Map.entry("lwd", 238),
            Map.entry("fl", 236),
            Map.entry("det", 189),
            Map.entry("pi", 189),
            Map.entry("da", 179),
            Map.entry("pm", 172),
            Map.entry("sdn", 162),
            Map.entry("sr", 148),
            Map.entry("dd2", 143),
            Map.entry("td", 131),
            Map.entry("nm", 100),
            Map.entry("x89c4", 82),
            Map.entry("cr2", 82),
            Map.entry("hm", 82),
            Map.entry("sar", 76),
            Map.entry("ie", 71),
            Map.entry("bs", 62),
            Map.entry("pa", 57),
            Map.entry("ps", 53),
            Map.entry("ff2", 32),
            Map.entry("pds2", 28),
            Map.entry("gls", 11),
            Map.entry("inf2", 5),
            Map.entry("dxa", 1));
    private static final Map<String, Integer> XXCHA_RESEARCH = Map.ofEntries(
            Map.entry("pds2", 494),
            Map.entry("ps", 436),
            Map.entry("nf", 424),
            Map.entry("gd", 413),
            Map.entry("it", 381),
            Map.entry("x89c4", 217),
            Map.entry("cr2", 217),
            Map.entry("aida", 214),
            Map.entry("amd", 204),
            Map.entry("det", 197),
            Map.entry("hm", 186),
            Map.entry("md", 185),
            Map.entry("ws", 177),
            Map.entry("bs", 166),
            Map.entry("dn2", 163),
            Map.entry("cv2", 129),
            Map.entry("sr", 95),
            Map.entry("nm", 87),
            Map.entry("sdn", 83),
            Map.entry("lwd", 79),
            Map.entry("fl", 56),
            Map.entry("sd2", 53),
            Map.entry("ie", 47),
            Map.entry("ff2", 47),
            Map.entry("asc", 44),
            Map.entry("td", 33),
            Map.entry("pi", 32),
            Map.entry("pa", 32),
            Map.entry("st", 28),
            Map.entry("sar", 28),
            Map.entry("dd2", 26),
            Map.entry("inf2", 19),
            Map.entry("da", 17),
            Map.entry("dxa", 1));

    private static final int LETNEV_GAMES = 1099;
    private static final Map<String, Integer> LETNEV_RESEARCH = Map.ofEntries(
            Map.entry("nes", 945),
            Map.entry("gd", 888),
            Map.entry("da", 584),
            Map.entry("lwd", 546),
            Map.entry("dn2", 372),
            Map.entry("dd2", 359),
            Map.entry("asc", 327),
            Map.entry("cv2", 266),
            Map.entry("ws", 238),
            Map.entry("sr", 186),
            Map.entry("fl", 185),
            Map.entry("aida", 167),
            Map.entry("det", 153),
            Map.entry("st", 132),
            Map.entry("sdn", 127),
            Map.entry("x89c4", 120),
            Map.entry("l4", 115),
            Map.entry("cr2", 82),
            Map.entry("nm", 64),
            Map.entry("md", 45),
            Map.entry("pa", 41),
            Map.entry("hm", 35),
            Map.entry("sar", 34),
            Map.entry("pi", 29),
            Map.entry("bs", 23),
            Map.entry("ie", 17),
            Map.entry("ff2", 17),
            Map.entry("pds2", 16),
            Map.entry("td", 11),
            Map.entry("sd2", 7),
            Map.entry("dxa", 2),
            Map.entry("inf2", 2),
            Map.entry("gls", 2));
    private static final int SOL_GAMES = 1194;
    private static final Map<String, Integer> SOL_RESEARCH = Map.ofEntries(
            Map.entry("ac2", 1124),
            Map.entry("gd", 1082),
            Map.entry("sr", 654),
            Map.entry("ff2", 540),
            Map.entry("lwd", 506),
            Map.entry("so2", 497),
            Map.entry("x89c4", 396),
            Map.entry("bs", 369),
            Map.entry("fl", 326),
            Map.entry("hm", 232),
            Map.entry("dn2", 208),
            Map.entry("det", 178),
            Map.entry("aida", 137),
            Map.entry("sdn", 106),
            Map.entry("ws", 97),
            Map.entry("td", 85),
            Map.entry("st", 85),
            Map.entry("md", 49),
            Map.entry("dxa", 48),
            Map.entry("pi", 47),
            Map.entry("ie", 45),
            Map.entry("pa", 45),
            Map.entry("asc", 34),
            Map.entry("sd2", 32),
            Map.entry("ps", 31),
            Map.entry("dd2", 31),
            Map.entry("da", 23),
            Map.entry("pds2", 14),
            Map.entry("cr2", 11),
            Map.entry("gls", 9),
            Map.entry("sar", 8));

    static double nudge(String faction, String alias) {
        return switch (faction) {
            case "sol" -> nudge(SOL_RESEARCH, SOL_GAMES, alias);
            case "hacan" -> nudge(HACAN_RESEARCH, HACAN_GAMES, alias);
            case "xxcha" -> nudge(XXCHA_RESEARCH, XXCHA_GAMES, alias);
            case "sardakk" -> nudge(SARDAKK_RESEARCH, SARDAKK_GAMES, alias);
            case "letnev" -> nudge(LETNEV_RESEARCH, LETNEV_GAMES, alias);
            default -> 0;
        };
    }

    private static double nudge(Map<String, Integer> researched, int games, String alias) {
        Integer count = researched.get(alias);
        if (count == null) return 0;
        double rate = (double) count / games;
        return WEIGHT * Math.clamp(RATE_SCALE * (rate - TYPICAL_RATE), -WEIGHT, WEIGHT);
    }
}
