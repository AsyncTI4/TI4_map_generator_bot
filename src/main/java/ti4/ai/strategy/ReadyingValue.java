package ti4.ai.strategy;

import java.util.Collection;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ReadyingValue {

    public record Pick(String planet, double value, boolean unlocksScore) {}

    public static double of(Game game, Player seat, Collection<String> planets) {
        if (planets.isEmpty()) return 0;
        return CardValueScale.READY_SHARE
                * DiplomacyUse.readying(game, seat, planets).use();
    }

    public static Optional<Pick> bestSingle(Game game, Player seat, Collection<String> candidates) {
        return DiplomacyUse.best(game, seat, candidates, 1)
                .map(choice -> new Pick(
                        choice.planets().getFirst(),
                        choice.unlocksScore()
                                ? CardValueScale.SPEND_UNLOCK_SHARE * CardValueScale.VICTORY_POINT
                                : CardValueScale.READY_SHARE * choice.use(),
                        choice.unlocksScore()));
    }
}
