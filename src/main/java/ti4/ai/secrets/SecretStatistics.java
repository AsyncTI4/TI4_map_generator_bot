package ti4.ai.secrets;

import static java.util.Map.entry;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretStatistics {

    public static final double UNKNOWN_COMPLETION = 0.5;
    public static final double UNKNOWN_OPENING_COMPLETION = 0.15;

    private static final Map<String, Double> COMPLETION = Map.ofEntries(
            entry("lsc", 0.86),
            entry("hrm", 0.84),
            entry("sai", 0.82),
            entry("sb", 0.80),
            entry("eh", 0.77),
            entry("te", 0.77),
            entry("fc", 0.76),
            entry("syc", 0.74),
            entry("fwm", 0.74),
            entry("dyp", 0.70),
            entry("sar", 0.69),
            entry("dhw", 0.69),
            entry("ctr", 0.68),
            entry("mlp", 0.68),
            entry("mtm", 0.66),
            entry("uf", 0.65),
            entry("ans", 0.65),
            entry("btgk", 0.65),
            entry("fsn", 0.65),
            entry("pem", 0.63),
            entry("gamf", 0.63),
            entry("btv", 0.62),
            entry("pe", 0.60),
            entry("mew", 0.57),
            entry("otf", 0.57),
            entry("dfat", 0.54),
            entry("faa", 0.54),
            entry("dp", 0.53),
            entry("mp", 0.51),
            entry("fwp", 0.50),
            entry("baf", 0.49),
            entry("csl", 0.47),
            entry("dtd", 0.47),
            entry("mrm", 0.46),
            entry("dts", 0.45),
            entry("ose", 0.45),
            entry("eap", 0.45),
            entry("dtgs", 0.41),
            entry("ttfd", 0.36),
            entry("bam", 0.25));

    private static final Map<String, Double> OPENING_COMPLETION = Map.ofEntries(
            entry("te", 0.66),
            entry("hrm", 0.62),
            entry("lsc", 0.51),
            entry("dhw", 0.49),
            entry("btgk", 0.37),
            entry("syc", 0.35),
            entry("eh", 0.32),
            entry("fwm", 0.29),
            entry("sb", 0.35),
            entry("fsn", 0.23),
            entry("sai", 0.20),
            entry("pe", 0.20),
            entry("sar", 0.19),
            entry("ose", 0.16),
            entry("csl", 0.14),
            entry("pem", 0.13),
            entry("faa", 0.13),
            entry("mew", 0.12),
            entry("uf", 0.11),
            entry("ctr", 0.11),
            entry("fc", 0.10),
            entry("mrm", 0.09),
            entry("mp", 0.08),
            entry("gamf", 0.08),
            entry("dts", 0.08),
            entry("baf", 0.05),
            entry("otf", 0.03),
            entry("dyp", 0.03),
            entry("dp", 0.03),
            entry("dfat", 0.03),
            entry("bam", 0.03),
            entry("ttfd", 0.02),
            entry("mtm", 0.02),
            entry("mlp", 0.02),
            entry("fwp", 0.02),
            entry("eap", 0.02),
            entry("dtgs", 0.02),
            entry("dtd", 0.02),
            entry("ans", 0.02),
            entry("btv", 0.01));

    public static double completion(String secretId) {
        return COMPLETION.getOrDefault(secretId, UNKNOWN_COMPLETION);
    }

    public static double openingCompletion(String secretId) {
        return OPENING_COMPLETION.getOrDefault(secretId, UNKNOWN_OPENING_COMPLETION);
    }
}
