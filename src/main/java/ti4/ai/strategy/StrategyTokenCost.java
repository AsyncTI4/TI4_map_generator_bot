package ti4.ai.strategy;

import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringReserve;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class StrategyTokenCost {

    private static final int PLENTIFUL = 3;
    private static final double PLENTIFUL_SHARE = 0.75;
    private static final double LAST_TOKEN_SHARE = 1.5;

    public static OptionalDouble toSpend(Game game, Player seat) {
        if (!canSpare(game, seat)) return OptionalDouble.empty();
        return OptionalDouble.of(CardValueScale.STRATEGY_TOKEN * scarcity(seat.getStrategicCC()));
    }

    public static boolean canSpare(Game game, Player seat) {
        int reserved = ScoringReserve.of(game, seat).tokens();
        int scar = ScarToken.kept(game, seat);
        return seat.getStrategicCC() - scar >= 1 && seat.getStrategicCC() + seat.getTacticalCC() - 1 - scar >= reserved;
    }

    private static double scarcity(int strategyTokens) {
        if (strategyTokens >= PLENTIFUL) return PLENTIFUL_SHARE;
        return strategyTokens <= 1 ? LAST_TOKEN_SHARE : 1.0;
    }
}
