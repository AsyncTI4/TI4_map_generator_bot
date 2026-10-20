package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiMemory;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class FragmentSecrets {

    static final String HERETICAL_WORKS = "dhw";
    static final int FRAGMENTS_NEEDED = 2;
    static final int FIRST_ASK = 2;
    static final int MAX_PRICE = 9;
    static final double BASE_VALUE = 3.0;
    private static final double POINT_SHARE = 0.9;
    private static final String ASKS_KEY = "fragmentSecretAsks|";

    static boolean holds(Player seat) {
        return seat.getSecretsUnscored().containsKey(HERETICAL_WORKS);
    }

    static int held(Player player) {
        return ComponentValues.FRAGMENT_KINDS.stream()
                .mapToInt(kind -> ComponentValues.fragmentsOfKind(player, kind))
                .sum();
    }

    static int missing(Player seat) {
        return holds(seat) ? Math.max(0, FRAGMENTS_NEEDED - held(seat)) : 0;
    }

    static boolean chases(Player seat) {
        return missing(seat) > 0;
    }

    static double perFragment(Game game, Player seat) {
        double share = POINT_SHARE * ScoringStakes.scoreValue(game, seat, 1) / Math.max(1, missing(seat));
        return Math.max(BASE_VALUE, share);
    }

    static int askFloor(AiMemory memory, Game game, Player owner) {
        return Math.min(MAX_PRICE, FIRST_ASK + NoteSecrets.step(game) * asks(memory, owner));
    }

    static void asked(AiMemory memory, Player owner) {
        memory.put(asksKey(owner), String.valueOf(asks(memory, owner) + 1));
    }

    private static int asks(AiMemory memory, Player owner) {
        return memory.get(asksKey(owner)).map(Integer::parseInt).orElse(0);
    }

    private static String asksKey(Player owner) {
        return ASKS_KEY + owner.getFaction();
    }
}
