package ti4.ai.explore;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.Threats;
import ti4.ai.eval.ValueScale;
import ti4.ai.legendary.LegendaryPlanets;
import ti4.ai.perception.AiPrompt;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;

class NanoforgeFlow implements RelicFlow {

    static final String PLANET_PREFIX = "nanoforgePlanet_";
    private static final String RELIC = "nanoforge";
    private static final String PLANET = "planet";
    private static final int BONUS = 2;
    private static final double EXPOSED_SHARE = 0.6;

    @Override
    public String name() {
        return "Nano-Forge";
    }

    @Override
    public String relicId() {
        return RELIC;
    }

    @Override
    public double netValue(AiTurnContext context) {
        return eligible(context.game(), context.seat()).stream()
                .mapToDouble(planet -> worth(context.game(), context.seat(), planet))
                .max()
                .orElse(0);
    }

    @Override
    public String firstStage() {
        return PLANET;
    }

    @Override
    public Optional<AiDecision> step(AiTurnContext context, List<AiPrompt> visible, List<String> fields) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Match> best = visible.stream()
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isUnowned()
                                && button.handlerId().startsWith(PLANET_PREFIX)
                                && !context.alreadyPressed(prompt, button))
                        .map(button -> new Match(prompt, button)))
                .max(Comparator.comparingDouble(match -> worth(game, seat, planetOf(match))));
        best.ifPresent(match -> RelicFlows.advance(context, this, RelicFlows.DONE));
        return best.map(match -> match.press("attach Nano-Forge to " + planetOf(match)));
    }

    private static String planetOf(Match match) {
        return match.button().handlerId().substring(PLANET_PREFIX.length());
    }

    private static List<String> eligible(Game game, Player seat) {
        return seat.getPlanetsAllianceMode().stream()
                .filter(name -> {
                    Planet planet = game.getPlanetsInfo().get(name);
                    return planet != null
                            && !planet.isFake()
                            && !planet.isSpaceStation(game)
                            && !ButtonHelper.isPlanetLegendaryOrHome(name, game, false, null);
                })
                .toList();
    }

    private static double worth(Game game, Player seat, String name) {
        double gained = ValueScale.spendable(BONUS, BONUS) + LegendaryPlanets.holdingValue(null);
        Tile tile = game.getTileFromPlanet(name);
        boolean exposed = tile != null && Threats.anyEnemyCanReach(game, seat, tile);
        return exposed ? gained * EXPOSED_SHARE : gained;
    }
}
