package ti4.ai.trade;

import java.util.List;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.RelicValue;
import ti4.game.Player;

@UtilityClass
public class FragmentValue {

    public static final String CULTURAL = "CRF";
    public static final String INDUSTRIAL = "IRF";
    public static final String HAZARDOUS = "HRF";
    public static final String UNKNOWN = "URF";
    public static final List<String> KINDS = List.of(CULTURAL, INDUSTRIAL, HAZARDOUS, UNKNOWN);
    public static final int PER_RELIC = 3;
    public static final double SINGLE_SHARE = 0.2;
    public static final double PAIR_SHARE = 0.45;
    public static final double TRADE_TEMPO = 1.0;
    public static final double HERETICAL_WORKS_BONUS = 2.0;
    static final int HERETICAL_WORKS_FRAGMENTS = 2;
    private static final String HERETICAL_WORKS = "dhw";

    public record Holdings(int cultural, int industrial, int hazardous, int unknown) {

        public static Holdings of(Player player) {
            return new Holdings(player.getCrf(), player.getIrf(), player.getHrf(), player.getUrf());
        }

        public int total() {
            return cultural + industrial + hazardous + unknown;
        }

        public int of(String kind) {
            return switch (kind) {
                case CULTURAL -> cultural;
                case INDUSTRIAL -> industrial;
                case HAZARDOUS -> hazardous;
                case UNKNOWN -> unknown;
                default -> 0;
            };
        }

        public Holdings with(String kind, int delta) {
            return switch (kind) {
                case CULTURAL -> new Holdings(Math.max(0, cultural + delta), industrial, hazardous, unknown);
                case INDUSTRIAL -> new Holdings(cultural, Math.max(0, industrial + delta), hazardous, unknown);
                case HAZARDOUS -> new Holdings(cultural, industrial, Math.max(0, hazardous + delta), unknown);
                case UNKNOWN -> new Holdings(cultural, industrial, hazardous, Math.max(0, unknown + delta));
                default -> this;
            };
        }

        public int relics() {
            return progress().relics();
        }

        Progress progress() {
            int relics = cultural / PER_RELIC + industrial / PER_RELIC + hazardous / PER_RELIC;
            int[] leftovers = IntStream.of(cultural % PER_RELIC, industrial % PER_RELIC, hazardous % PER_RELIC)
                    .filter(left -> left > 0)
                    .boxed()
                    .sorted((a, b) -> b - a)
                    .mapToInt(Integer::intValue)
                    .toArray();
            int fillers = unknown;
            int pairs = 0;
            int singles = 0;
            for (int left : leftovers) {
                int missing = PER_RELIC - left;
                if (fillers >= missing) {
                    fillers -= missing;
                    relics++;
                } else if (left == 2) {
                    pairs++;
                } else {
                    singles++;
                }
            }
            relics += fillers / PER_RELIC;
            int looseFillers = fillers % PER_RELIC;
            int joined = Math.min(looseFillers, singles);
            pairs += joined;
            singles -= joined;
            looseFillers -= joined;
            pairs += looseFillers / 2;
            singles += looseFillers % 2;
            return new Progress(relics, pairs, singles);
        }
    }

    record Progress(int relics, int pairs, int singles) {}

    public static boolean holdsHereticalWorks(Player player) {
        return player.getSecretsUnscored().containsKey(HERETICAL_WORKS);
    }

    public static double worth(Holdings holdings, boolean hereticalWorks) {
        Progress progress = holdings.progress();
        double value = RelicValue.BASE
                * (progress.relics() + PAIR_SHARE * progress.pairs() + SINGLE_SHARE * progress.singles());
        if (hereticalWorks) value += HERETICAL_WORKS_BONUS * Math.min(holdings.total(), HERETICAL_WORKS_FRAGMENTS);
        return value;
    }

    public static double gain(Holdings holdings, String kind, int amount, boolean hereticalWorks) {
        return worth(holdings.with(kind, amount), hereticalWorks) - worth(holdings, hereticalWorks);
    }

    public static double loss(Holdings holdings, String kind, int amount, boolean hereticalWorks) {
        return worth(holdings, hereticalWorks) - worth(holdings.with(kind, -amount), hereticalWorks);
    }

    public static double gainedFragment(Player seat, String kind) {
        return gain(Holdings.of(seat), kind, 1, holdsHereticalWorks(seat));
    }

    public static boolean completesARelic(Player seat, String kind) {
        Holdings held = Holdings.of(seat);
        return held.with(kind, 1).relics() > held.relics();
    }

    public static boolean canSpare(Player seat, String kind) {
        Holdings held = Holdings.of(seat);
        return held.of(kind) > 0 && held.with(kind, -1).relics() == held.relics();
    }
}
