package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
class EconomyTargets {

    private static final String FRONTLINE_TARGET = "placeOneNDone_skipbuild_3gf_";
    private static final String CRUISER_TARGET = "placeOneNDone_skipbuild_cruiser_";
    private static final String DONE_GAINING = "Done Gaining";
    static final int SUMMIT_TOKENS = 2;
    private static final double DOCK_OR_CARRIER = 3.0;
    private static final double NEAR_ENEMY_SHIPS = 2.0;
    private static final double PLANET_WORTH_SHARE = 0.25;
    private static final int COMFORTABLE_GARRISON = 2;

    static Optional<AiDecision> frontline(AiTurnContext context, String key, CardPlay play) {
        List<Match> offered =
                CardPrompts.ownedPublicAll(context, play.since() - CardPrompts.CLOCK_SKEW_MILLIS, FRONTLINE_TARGET);
        Game game = context.game();
        Player seat = context.seat();
        Optional<Match> best = offered.stream()
                .max(Comparator.comparingDouble((Match match) -> frontlineValue(
                                game,
                                seat,
                                StringUtils.removeStart(match.button().handlerId(), FRONTLINE_TARGET)))
                        .thenComparing(match -> match.button().handlerId(), Comparator.reverseOrder()));
        if (best.isEmpty()) return Optional.empty();
        PlayMemory.moveTo(context, key, play, Stage.DONE);
        return Optional.of(best.get().press("place 3 infantry with Frontline Deployment"));
    }

    static double frontlineValue(Game game, Player seat, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null) return 0;
        double value = PLANET_WORTH_SHARE * (planet.getResources() + planet.getInfluence());
        UnitHolder space = BoardView.space(tile);
        boolean loads = tile.getPlanetUnitHolders().stream()
                        .anyMatch(holder -> BoardView.count(holder, seat, UnitType.Spacedock) > 0)
                || BoardView.ships(space, seat).keySet().stream().anyMatch(type -> BoardView.capacity(seat, type) > 0);
        if (loads) value += DOCK_OR_CARRIER;
        for (String position : FoWHelper.getAdjacentTiles(game, tile.getPosition(), seat, false)) {
            Tile nearby = game.getTileByPosition(position);
            if (nearby != null && nearby != tile && BoardView.hasEnemyShips(game, seat, nearby)) {
                value += NEAR_ENEMY_SHIPS;
                break;
            }
        }
        return value - Math.max(0, BoardView.groundForces(planet, seat) - COMFORTABLE_GARRISON);
    }

    static Optional<AiDecision> warEffort(AiTurnContext context, String key, CardPlay play) {
        List<Match> offered =
                CardPrompts.ownedPublicAll(context, play.since() - CardPrompts.CLOCK_SKEW_MILLIS, CRUISER_TARGET);
        if (offered.isEmpty()) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        Set<String> legal = new HashSet<>();
        ActionCardValue.cruiserTiles(game, seat).forEach(tile -> legal.add(tile.getPosition()));
        Optional<Match> best = offered.stream()
                .filter(match ->
                        legal.contains(StringUtils.removeStart(match.button().handlerId(), CRUISER_TARGET)))
                .max(Comparator.comparingDouble(match -> cruiserValue(
                        game, seat, StringUtils.removeStart(match.button().handlerId(), CRUISER_TARGET))));
        PlayMemory.moveTo(context, key, play, Stage.DONE);
        return best.map(match -> match.press("place a cruiser with War Effort"));
    }

    private static double cruiserValue(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return 0;
        double value = BoardView.nonFighterShips(BoardView.space(tile), seat);
        for (String nearby : FoWHelper.getAdjacentTiles(game, position, seat, false)) {
            Tile adjacent = game.getTileByPosition(nearby);
            if (adjacent != null && adjacent != tile && BoardView.hasEnemyShips(game, seat, adjacent)) {
                value += NEAR_ENEMY_SHIPS * 5;
                break;
            }
        }
        if (tile == seat.getHomeSystemTile()) value += 1;
        return value;
    }

    static Optional<AiDecision> summitTokens(AiTurnContext context, String key, CardPlay play) {
        String faction = context.faction();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < play.since() - CardPrompts.CLOCK_SKEW_MILLIS) continue;
            Optional<PromptButton> done = prompt.firstEnabled(button -> button.isOwnedBy(faction)
                    && "deleteButtons".equals(button.handlerId())
                    && button.label().startsWith(DONE_GAINING));
            if (done.isEmpty()) continue;
            int wanted;
            if (StringUtils.isNumeric(play.extra())) {
                wanted = Integer.parseInt(play.extra());
            } else {
                wanted = Math.min(
                        SUMMIT_TOKENS, CommandTokenPolicy.gainWithinReinforcements(context.game(), context.seat()));
                PlayMemory.store(context, key, play.withExtra(String.valueOf(wanted)));
            }
            if (CommandTokenPolicy.netGainSoFar(context.game(), context.seat()) < wanted) {
                String pool = CommandTokenPolicy.poolToGrow(context.game(), context.seat());
                Optional<PromptButton> grow =
                        prompt.firstEnabled(button -> button.isOwnedBy(faction) && pool.equals(button.handlerId()));
                if (grow.isPresent()) {
                    return Optional.of(AiDecision.press(prompt, grow.get(), "gain a command token from Summit"));
                }
            }
            PlayMemory.moveTo(context, key, play, Stage.DONE);
            return Optional.of(AiDecision.press(prompt, done.get(), "finish gaining Summit's command tokens"));
        }
        return Optional.empty();
    }
}
