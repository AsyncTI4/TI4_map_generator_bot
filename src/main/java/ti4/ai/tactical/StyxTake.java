package ti4.ai.tactical;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FracturePrize;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class StyxTake {

    static final StrikeRules RULES = PlanetTake.RULES;

    public static double thisRound(Game game, Player player) {
        return DecisionMemo.remember(() -> compute(game, player), "styx.take", player, game.getRound());
    }

    public static double thisRoundWithout(Game game, Player player, Tile stopped) {
        return DecisionMemo.remember(
                () -> compute(game, player, new IncomingForces(game, new IncomingForces.Exclusion(player, stopped))),
                "styx.take.without",
                player,
                stopped.getPosition(),
                game.getRound());
    }

    private static double compute(Game game, Player player) {
        return compute(game, player, new IncomingForces(game));
    }

    private static double compute(Game game, Player player, IncomingForces incoming) {
        Optional<Tile> styx = styxTile(game);
        if (styx.isEmpty() || FracturePrize.holdsStyx(styx.get(), player)) return 0.0;
        return PlanetTake.chance(
                game,
                player,
                new PlanetTake.Target(styx.get(), FracturePrize.STYX, holderOf(game, styx.get())),
                incoming,
                player);
    }

    private static Optional<Tile> styxTile(Game game) {
        return FracturePrize.tiles(game).stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream().anyMatch(FracturePrize::isStyx))
                .findFirst();
    }

    static Player holderOf(Game game, Tile styx) {
        for (Planet planet : styx.getPlanetUnitHolders()) {
            Player controller = BoardView.controller(game, planet.getName());
            if (FracturePrize.isStyx(planet) && controller != null) return controller;
        }
        return game.getNeutral();
    }
}
