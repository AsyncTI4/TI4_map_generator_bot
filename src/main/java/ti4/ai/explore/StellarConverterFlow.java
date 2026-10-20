package ti4.ai.explore;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.PlanetStake;
import ti4.ai.perception.AiPrompt;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

class StellarConverterFlow implements RelicFlow {

    static final String TARGET_PREFIX = "stellarConvert_";
    static final double MIN_TARGET = 4.0;
    private static final String RELIC = "stellarconverter";
    private static final String TARGET = "target";
    private static final double RIVAL_SHARE = 0.6;
    private static final double LEADER_SHARE = 0.9;
    private static final double INFANTRY = 0.5;
    private static final double MECH = 2.0;

    @Override
    public String name() {
        return "Stellar Converter";
    }

    @Override
    public String relicId() {
        return RELIC;
    }

    @Override
    public double netValue(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        return targets(game, seat).stream()
                .mapToDouble(planet -> worth(game, seat, planet))
                .filter(value -> value >= MIN_TARGET)
                .max()
                .orElse(0);
    }

    @Override
    public String firstStage() {
        return TARGET;
    }

    @Override
    public Optional<AiDecision> step(AiTurnContext context, List<AiPrompt> visible, List<String> fields) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Match> best = visible.stream()
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isOwnedBy(context.faction())
                                && button.handlerId().startsWith(TARGET_PREFIX)
                                && !context.alreadyPressed(prompt, button))
                        .map(button -> new Match(prompt, button)))
                .filter(match -> worth(game, seat, planetOf(match.button().handlerId())) >= MIN_TARGET)
                .max(Comparator.comparingDouble(
                        match -> worth(game, seat, planetOf(match.button().handlerId()))));
        best.ifPresent(match -> RelicFlows.advance(context, this, RelicFlows.DONE));
        return best.map(match ->
                match.press("destroy " + planetOf(match.button().handlerId()) + " with the Stellar Converter"));
    }

    private static String planetOf(String handlerId) {
        return StringUtils.substringAfter(handlerId, TARGET_PREFIX);
    }

    private static List<String> targets(Game game, Player seat) {
        return ButtonHelper.getButtonsForStellar(seat, game).stream()
                .map(button -> planetOf(button.getCustomId()))
                .toList();
    }

    static double worth(Game game, Player seat, String name) {
        Planet planet = game.getPlanetsInfo().get(name);
        if (planet == null) return 0;
        Player owner = BoardView.controller(game, name);
        if (owner == null
                || owner == seat
                || BoardView.count(planet, seat, UnitType.Infantry) > 0
                || ownsUnits(planet, seat)) {
            return 0;
        }
        double held =
                BoardView.planetValue(planet) + PlanetStake.structures(planet, owner) + groundForces(planet, owner);
        return held * (leads(game, owner) ? LEADER_SHARE : RIVAL_SHARE);
    }

    private static boolean ownsUnits(Planet planet, Player seat) {
        return BoardView.count(planet, seat, UnitType.Mech) > 0
                || BoardView.count(planet, seat, UnitType.Pds) > 0
                || BoardView.count(planet, seat, UnitType.Spacedock) > 0;
    }

    private static double groundForces(Planet planet, Player owner) {
        return INFANTRY * BoardView.count(planet, owner, UnitType.Infantry)
                + MECH * BoardView.count(planet, owner, UnitType.Mech);
    }

    private static boolean leads(Game game, Player player) {
        int points = player.getTotalVictoryPoints();
        return game.getRealPlayers().stream()
                .filter(other -> other != player)
                .allMatch(other -> points > other.getTotalVictoryPoints());
    }
}
