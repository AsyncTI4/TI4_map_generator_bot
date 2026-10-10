package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.PlanetStake;
import ti4.ai.eval.Threats;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class RetreatRules {

    static final String ANNOUNCED_KEY = "retreatAnnounced|";
    static final double SPARE_SYSTEM_RETREAT = 0.35;
    static final double CRITICAL_SYSTEM_RETREAT = 0.20;
    private static final double CRITICAL_STAKE = ObjectiveValue.VICTORY_POINT_VALUE;
    private static final String ANNOUNCE = "announceARetreat";
    private static final String RETREAT_PREFIX = "retreat_";
    private static final String DESTINATION_PREFIX = "retreatUnitsFrom_";
    private static final String GROUND_PREFIX = "retreatGroundUnits_";
    private static final String DONE = "deleteButtons";
    private static final String DONE_RETREATING = "Done Retreating troops";
    private static final String MECH = "mech";
    private static final String INFANTRY = "infantry";
    private static final String DARK_ENERGY_TAP = "det";
    private static final String ANTIMASS_DEFLECTORS = "amd";
    private static final String MAGMUS_REACTOR = "mr";
    private static final double HOME_VALUE = 10;
    private static final double PLANET_VALUE = 2;
    private static final double THREAT_PENALTY = 3;

    static Optional<AiDecision> next(AiTurnContext context, List<AiPrompt> prompts) {
        return chooseDestination(context, prompts)
                .or(() -> groundForces(context, prompts))
                .or(() -> announceOrRetreat(context, prompts));
    }

    private static Optional<AiDecision> chooseDestination(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        Player seat = context.seat();
        return prompts.stream()
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isOwnedBy(context.faction())
                                && button.handlerId().startsWith(DESTINATION_PREFIX))
                        .map(button -> new Match(prompt, button)))
                .max(Comparator.comparingDouble(match -> destinationValue(game, seat, destinationOf(match.button()))))
                .map(match -> match.press("retreat to " + destinationOf(match.button())));
    }

    private static Optional<AiDecision> groundForces(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            List<PromptButton> moves = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(GROUND_PREFIX))
                    .toList();
            if (moves.isEmpty()) continue;
            Optional<PromptButton> evacuate = evacuation(context.game(), context.seat(), moves);
            if (evacuate.isPresent()) {
                return Optional.of(AiDecision.press(
                        prompt, evacuate.get(), "carry ground forces away from a planet they cannot hold"));
            }
            Optional<PromptButton> done = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && DONE.equals(button.handlerId())
                    && DONE_RETREATING.equals(button.label()));
            if (done.isPresent()) {
                return Optional.of(AiDecision.press(prompt, done.get(), "leave the remaining ground forces"));
            }
        }
        return Optional.empty();
    }

    private static Optional<PromptButton> evacuation(Game game, Player seat, List<PromptButton> moves) {
        Tile from = game.getTileByPosition(partOf(moves.getFirst(), 0));
        Tile to = game.getTileByPosition(partOf(moves.getFirst(), 1));
        if (from == null || to == null) return Optional.empty();
        Optional<Player> invader = game.getRealPlayers().stream()
                .filter(other -> other != seat && BoardView.hasOwnShips(other, from))
                .findFirst();
        int room = spareCapacity(seat, to);
        if (invader.isEmpty() || room <= 0) return Optional.empty();
        return moves.stream()
                .filter(button -> evacuates(game, seat, invader.get(), from, button, room))
                .min(Comparator.comparingInt((PromptButton button) -> MECH.equals(partOf(button, 3)) ? 0 : 1)
                        .thenComparing(button -> -Integer.parseInt(partOf(button, 2))));
    }

    private static boolean evacuates(Game game, Player seat, Player invader, Tile from, PromptButton button, int room) {
        String count = partOf(button, 2);
        String type = partOf(button, 3);
        if (!StringUtils.isNumeric(count) || Integer.parseInt(count) > room) return false;
        if (!MECH.equals(type) && !INFANTRY.equals(type)) return false;
        if (!(from.getUnitHolders().get(partOf(button, 4)) instanceof Planet planet)) return false;
        double garrison = BoardView.GROUND_FORCES.stream()
                .mapToDouble(unit -> BoardView.count(planet, seat, unit)
                        * BoardView.model(seat, unit).map(UnitModel::getCost).orElse(0f))
                .sum();
        double hold = CombatForces.garrisonHoldChance(game, from, planet, seat, invader);
        return hold < garrison / (garrison + PlanetStake.of(game, seat, planet));
    }

    private static int spareCapacity(Player seat, Tile to) {
        UnitHolder space = BoardView.space(to);
        int capacity = BoardView.MOVING_SHIPS.stream()
                .mapToInt(type -> BoardView.count(space, seat, type) * BoardView.capacity(seat, type))
                .sum();
        return capacity - BoardView.count(space, seat, UnitType.Fighter) - BoardView.groundForces(space, seat);
    }

    private static String partOf(PromptButton button, int index) {
        String[] parts =
                StringUtils.removeStart(button.handlerId(), GROUND_PREFIX).split("_", 5);
        return parts.length > index ? parts[index] : "";
    }

    private static Optional<AiDecision> announceOrRetreat(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton roll : prompt.enabledButtons()) {
                if (!CombatRules.isRollFor(game, seat, prompts, roll)) continue;
                String[] parts = roll.handlerId().split("_");
                if (!BoardView.SPACE.equals(parts[2])) continue;
                String position = parts[1];
                Optional<AiDecision> decision = announceOrRetreat(context, prompts, position);
                if (decision.isPresent()) return decision;
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> announceOrRetreat(
            AiTurnContext context, List<AiPrompt> prompts, String position) {
        Game game = context.game();
        Player seat = context.seat();
        String key = ANNOUNCED_KEY + game.getRound() + "|" + position;
        int rounds = CombatRules.tracker(game, seat.getFaction(), position, BoardView.SPACE);
        Optional<String> announced = context.memory().get(key);
        if (announced.isPresent()) {
            if (!StringUtils.isNumeric(announced.get()) || rounds <= Integer.parseInt(announced.get())) {
                return Optional.empty();
            }
            Optional<Match> retreat = combatButton(prompts, (RETREAT_PREFIX + position)::equals);
            retreat.ifPresent(match -> context.memory().put(key, "retreating"));
            return retreat.map(match -> match.press("retreat from " + position + " as announced"));
        }
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !shouldRetreat(game, seat, tile)) return Optional.empty();
        Optional<Match> announce = combatButton(prompts, ANNOUNCE::equals);
        announce.ifPresent(match -> context.memory().put(key, String.valueOf(rounds)));
        return announce.map(match -> match.press("announce a retreat from a losing fight"));
    }

    static boolean shouldRetreat(Game game, Player seat, Tile tile) {
        if (tile == seat.getHomeSystemTile() || BoardView.nonFighterShips(BoardView.space(tile), seat) == 0) {
            return false;
        }
        Optional<Player> opponent = game.getRealPlayers().stream()
                .filter(other -> other != seat && BoardView.hasOwnShips(other, tile))
                .findFirst();
        if (opponent.isEmpty() || !hasDestination(game, seat, tile)) return false;
        return CombatForces.spaceWinChance(game, tile, seat, opponent.get()) < retreatBelow(game, seat, tile);
    }

    static double retreatBelow(Game game, Player seat, Tile tile) {
        double stake = 0;
        for (Planet planet : BoardView.planets(tile)) {
            if (!seat.getPlanets().contains(planet.getName())) continue;
            stake += PlanetStake.of(game, seat, planet) + PlanetStake.structures(planet, seat);
        }
        ObjectiveValue objectives = new ObjectiveValue(game, seat);
        stake += Math.max(0, -objectives.gain(objectives.before().withoutShipsIn(tile.getPosition())));
        double criticality = Math.min(1, stake / CRITICAL_STAKE);
        return SPARE_SYSTEM_RETREAT - (SPARE_SYSTEM_RETREAT - CRITICAL_SYSTEM_RETREAT) * criticality;
    }

    private static boolean hasDestination(Game game, Player seat, Tile tile) {
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .filter(destination -> destination != null && destination != tile)
                .anyMatch(destination -> canRetreatTo(game, seat, destination));
    }

    private static boolean canRetreatTo(Game game, Player seat, Tile destination) {
        if (destination.isAsteroidField() && !seat.hasTech(ANTIMASS_DEFLECTORS)) return false;
        if (destination.isSupernova() && !seat.hasTech(MAGMUS_REACTOR)) return false;
        if (BoardView.hasEnemyShips(game, seat, destination)) return false;
        if (Tile.playerCanRetreatHere(seat).test(destination)) return true;
        return seat.hasTech(DARK_ENERGY_TAP) && !BoardView.hasEnemyUnits(game, seat, destination);
    }

    private static double destinationValue(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return Double.NEGATIVE_INFINITY;
        double value = tile == seat.getHomeSystemTile() ? HOME_VALUE : 0;
        value += PLANET_VALUE
                * BoardView.planets(tile).stream()
                        .filter(planet -> seat.getPlanets().contains(planet.getName()))
                        .count();
        value += BoardView.nonFighterShips(BoardView.space(tile), seat);
        if (Threats.anyEnemyCanReach(game, seat, tile)) value -= THREAT_PENALTY;
        return value;
    }

    private static String destinationOf(PromptButton button) {
        String[] parts =
                StringUtils.removeStart(button.handlerId(), DESTINATION_PREFIX).split("_");
        return parts.length > 1 ? parts[1] : "";
    }

    private static Optional<Match> combatButton(List<AiPrompt> prompts, Predicate<String> id) {
        return prompts.stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isUnowned() && id.test(button.handlerId()))
                        .map(button -> new Match(prompt, button)))
                .findFirst();
    }
}
