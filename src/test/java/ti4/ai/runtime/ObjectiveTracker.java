package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.PublicObjectiveModel;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.statistics.game.WinningPathBreakdown;
import ti4.service.statistics.game.WinningPathHelper;

/**
 * What one self-play game did with its public objectives, read from public state on every step of the run. Each
 * status phase has a snapshot, taken just before it: the public objectives and secrets every seat had scored, and how
 * far short each seat was of every revealed public objective it had not scored. The snapshot closes when the status
 * phase ends, or the game does, and records what each seat scored in it.
 */
final class ObjectiveTracker {

    static final int UNMODELLED = -1;
    private static final String STATUS_PHASE_PREFIX = "status";
    private static final String ACTION_PHASE = "action";

    // One seat facing one revealed public objective it had not scored, at the start of a status phase: how far short it
    // was (0 means it met it, UNMODELLED that the bot cannot measure the objective) and whether it scored it there.
    record Chance(String objective, int round, int shortBy, boolean scored) {}

    // What one seat scored in one round's status phase.
    record StatusScoring(int round, String faction, int publics, int secrets) {}

    // One revealed public objective at the end of the game, with its points and how many seats scored it.
    record Reveal(String objective, int points, int scorers) {}

    // The leader's points by source come from the same helper as the bot's "Winners Path to Victory" statistic, so the
    // batch reads side by side with human games.
    record Summary(
            List<Reveal> reveals,
            List<StatusScoring> statusScoring,
            List<Chance> chances,
            WinningPathBreakdown leaderPath,
            List<String> secretsScored,
            List<String> secretsHeld,
            Map<String, List<Integer>> groundProgress,
            List<MechanizeHolder> mechanize,
            List<String> secretHolders,
            int seats) {

        static final Summary NOTHING = new Summary(
                List.of(),
                List.of(),
                List.of(),
                new WinningPathBreakdown(0, 0, 0, 0, Map.of()),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                0);

        // For each status phase the game reached, from round 1: the share of seats that scored at least one public
        // objective (or secret) in it.
        List<Double> scoringShareByRound(boolean publics) {
            int lastRound =
                    statusScoring.stream().mapToInt(StatusScoring::round).max().orElse(0);
            List<Double> shares = new ArrayList<>();
            for (int round = 1; round <= lastRound; round++) {
                int current = round;
                List<StatusScoring> phase = statusScoring.stream()
                        .filter(scoring -> scoring.round() == current)
                        .toList();
                long scored = phase.stream()
                        .filter(scoring -> (publics ? scoring.publics() : scoring.secrets()) > 0)
                        .count();
                shares.add(phase.isEmpty() ? 0 : (double) scored / phase.size());
            }
            return shares;
        }
    }

    private record Snapshot(
            int round,
            Map<String, Set<String>> publicsBefore,
            Map<String, Integer> secretsBefore,
            List<Pending> pending) {}

    private record Pending(String userId, String objective, int shortBy) {}

    // A Mechanize the Military holder at the start of one status phase: mechs on the board, planets with a mech, mechs
    // in the home system, own carriers (and those in the home system), mechs built so far and how often a mech first
    // arrived on a planet outside the home system while the secret was held.
    record MechanizeHolder(
            String faction,
            int round,
            int mechs,
            int mechPlanets,
            int mechsHome,
            int carriers,
            int carriersHome,
            int built,
            int arrivedAway,
            boolean scored,
            int homeInfantry,
            int keptHome,
            int movableHomeMechs,
            int bareHeldPlanets) {}

    private final List<MechanizeHolder> mechanize = new ArrayList<>();
    // One line per Seize an Icon or Adapt New Strategies holder at each status phase.
    private final List<String> secretHolders = new ArrayList<>();
    private final FringeTracker fringe = new FringeTracker();
    private final Map<String, Integer> mechTotals = new HashMap<>();
    private final Map<String, Integer> mechsBuilt = new HashMap<>();
    private final Map<String, Integer> arrivals = new HashMap<>();
    private final Map<String, Set<String>> awayMechPlanets = new HashMap<>();

    private final ProduceTracker produce = new ProduceTracker();
    private final HomeRaidTracker homeRaids = new HomeRaidTracker();
    private final TraitTracker traits = new TraitTracker();
    private final SeatTracker seatOfTheEmpire = new SeatTracker();
    private final DebateTracker debate = new DebateTracker();
    private final DirectHitTracker directHits = new DirectHitTracker();
    private final PerimeterTracker perimeter = new PerimeterTracker();
    private final SupplyLinesTracker supplyLines = new SupplyLinesTracker();
    private final VoidMartyrTracker voidMartyr = new VoidMartyrTracker();

    private Snapshot open;
    private int lastOpenedRound;
    private final List<StatusScoring> statusScoring = new ArrayList<>();
    private final List<Chance> chances = new ArrayList<>();
    private final Set<String> held = new java.util.LinkedHashSet<>();
    // Mechanize the Military and Occupy the Fringe: the most progress each holder had at any status phase.
    private static final List<String> GROUND_SECRETS = List.of("mtm", "otf");
    private final Map<String, Integer> groundProgress = new java.util.LinkedHashMap<>();

    // The snapshot opens once every seat has passed, before the status phase starts: scores a seat queued during the
    // action phase are applied the moment the status phase begins, so a snapshot taken then would already hold them.
    synchronized void observe(Game game) {
        homeRaids.observe(game);
        traits.observe(game);
        debate.observe(game);
        directHits.observe(game);
        supplyLines.observe(game);
        voidMartyr.observe(game);
        if (pastTheOpeningDiscard(game)) recordHeld(game);
        trackMechs(game);
        produce.observe(game);
        fringe.observe(game, secretHolders);
        perimeter.observe(game);
        boolean scoringWindow = actionPhaseOver(game) || inStatusPhase(game);
        if (open != null && (!scoringWindow || game.getRound() != open.round())) close(game);
        if (scoringWindow && open == null && game.getRound() != lastOpenedRound) open(game);
    }

    synchronized Summary summary(Game game) {
        if (game == null) return Summary.NOTHING;
        homeRaids.lines(game).forEach(System.out::println);
        traits.lines(game).forEach(System.out::println);
        seatOfTheEmpire.lines(game).forEach(System.out::println);
        debate.lines(game).forEach(System.out::println);
        directHits.lines(game).forEach(System.out::println);
        perimeter.lines(game).forEach(System.out::println);
        supplyLines.lines(game).forEach(System.out::println);
        voidMartyr.lines(game).forEach(System.out::println);
        if (open != null) close(game);
        NexusWatch.print(game);
        produce.finish(game);
        fringe.gameEnd(game, secretHolders);
        return new Summary(
                reveals(game),
                List.copyOf(statusScoring),
                List.copyOf(chances),
                leaderPath(game),
                game.getRealPlayers().stream()
                        .flatMap(seat -> seat.getSecretsScored().keySet().stream())
                        .toList(),
                held.stream()
                        .map(entry -> entry.substring(entry.indexOf('|') + 1))
                        .toList(),
                groundProgressByHolder(),
                List.copyOf(mechanize),
                List.copyOf(secretHolders),
                game.getRealPlayers().size());
    }

    // Seats are dealt two secrets and discard one before round 1; the one discarded there was never really held.
    private static boolean pastTheOpeningDiscard(Game game) {
        return game.getRound() > 1 || ACTION_PHASE.equals(game.getPhaseOfGame());
    }

    private void recordHeld(Game game) {
        for (Player seat : game.getRealPlayers()) {
            for (String secret : seat.getSecretsUnscored().keySet()) held.add(seat.getFaction() + "|" + secret);
            for (String secret : seat.getSecretsScored().keySet()) held.add(seat.getFaction() + "|" + secret);
        }
    }

    private static boolean holdsMechanize(Player seat) {
        return seat.getSecretsUnscored().containsKey("mtm")
                || seat.getSecretsScored().containsKey("mtm");
    }

    private void trackMechs(Game game) {
        for (Player seat : game.getRealPlayers()) {
            if (!holdsMechanize(seat)) continue;
            String faction = seat.getFaction();
            int total = 0;
            Set<String> away = new HashSet<>();
            ti4.game.Tile home = seat.getHomeSystemTile();
            for (ti4.game.Tile tile : game.getTileMap().values()) {
                for (ti4.game.UnitHolder holder : tile.getUnitHolders().values()) {
                    int here = ti4.ai.eval.BoardView.count(holder, seat, ti4.helpers.Units.UnitType.Mech);
                    total += here;
                    if (here > 0 && holder instanceof ti4.game.Planet && tile != home) away.add(holder.getName());
                }
            }
            Integer before = mechTotals.put(faction, total);
            if (before != null && total > before) mechsBuilt.merge(faction, total - before, Integer::sum);
            Set<String> previous = awayMechPlanets.put(faction, away);
            if (previous != null) {
                long arrived = away.stream()
                        .filter(planet -> !previous.contains(planet))
                        .count();
                if (arrived > 0) arrivals.merge(faction, (int) arrived, Integer::sum);
            }
        }
    }

    private void recordMechanize(Game game) {
        for (Player seat : game.getRealPlayers()) {
            if (!holdsMechanize(seat)) continue;
            ti4.game.Tile home = seat.getHomeSystemTile();
            int mechs = 0;
            int planets = 0;
            int mechsHome = 0;
            int carriers = 0;
            int carriersHome = 0;
            for (ti4.game.Tile tile : game.getTileMap().values()) {
                for (ti4.game.UnitHolder holder : tile.getUnitHolders().values()) {
                    int here = ti4.ai.eval.BoardView.count(holder, seat, ti4.helpers.Units.UnitType.Mech);
                    mechs += here;
                    if (here > 0 && holder instanceof ti4.game.Planet) planets++;
                    if (tile == home) mechsHome += here;
                    int carriersHere = ti4.ai.eval.BoardView.count(holder, seat, ti4.helpers.Units.UnitType.Carrier);
                    carriers += carriersHere;
                    if (tile == home) carriersHome += carriersHere;
                }
            }
            String faction = seat.getFaction();
            mechanize.add(new MechanizeHolder(
                    faction,
                    game.getRound(),
                    mechs,
                    planets,
                    mechsHome,
                    carriers,
                    carriersHome,
                    mechsBuilt.getOrDefault(faction, 0),
                    arrivals.getOrDefault(faction, 0),
                    seat.getSecretsScored().containsKey("mtm"),
                    homeInfantry(seat, home),
                    kept(game, seat),
                    Math.max(0, mechsHome - Math.max(0, kept(game, seat) - homeInfantry(seat, home))),
                    bareHeldPlanets(game, seat, home)));
        }
    }

    // Ground forces the planner keeps at home when the ships there leave; while Mechanize is held infantry stay first.
    private static int kept(Game game, Player seat) {
        return ti4.ai.tactical.TacticalPlanner.groundKeptHomeWhileTransportsLeave(game, seat);
    }

    private static int homeInfantry(Player seat, ti4.game.Tile home) {
        if (home == null) return 0;
        return home.getUnitHolders().values().stream()
                .mapToInt(holder -> ti4.ai.eval.BoardView.count(holder, seat, ti4.helpers.Units.UnitType.Infantry))
                .sum();
    }

    private static int bareHeldPlanets(Game game, Player seat, ti4.game.Tile home) {
        int bare = 0;
        for (String name : seat.getPlanets()) {
            ti4.game.Tile tile = game.getTileFromPlanet(name);
            if (tile == null || tile == home) continue;
            if (game.getUnitHolderFromPlanet(name) instanceof ti4.game.Planet planet
                    && !planet.isSpaceStation(game)
                    && ti4.ai.eval.BoardView.count(planet, seat, ti4.helpers.Units.UnitType.Mech) == 0) {
                bare++;
            }
        }
        return bare;
    }

    private void recordIconAndStrategies(Game game) {
        for (Player seat : game.getRealPlayers()) {
            if (seat.getSecretsUnscored().containsKey("sai")
                    || seat.getSecretsScored().containsKey("sai")) {
                List<String> legendary = seat.getPlanets().stream()
                        .filter(name -> game.getPlanetsInfo().get(name) != null
                                && game.getPlanetsInfo().get(name).isLegendary())
                        .toList();
                secretHolders.add(String.format(
                        "Seize an Icon %s r%d: legendary held %s%s",
                        seat.getFaction(),
                        game.getRound(),
                        legendary,
                        seat.getSecretsScored().containsKey("sai") ? ", scored" : ""));
            }
            if (seat.getSecretsUnscored().containsKey("ans")
                    || seat.getSecretsScored().containsKey("ans")) {
                boolean inScar = game.getTileMap().values().stream()
                        .anyMatch(tile -> tile.isScar(game) && ti4.ai.eval.BoardView.hasOwnShips(seat, tile));
                secretHolders.add(String.format(
                        "Adapt New Strategies %s r%d: own faction techs %d (bot counts %d), route %s, ships in a scar %s, strategy tokens %d%s",
                        seat.getFaction(),
                        game.getRound(),
                        ti4.ai.secrets.SecretValue.ownFactionTechnologies(seat),
                        ti4.service.info.ListPlayerInfoService.getPlayerProgressOnObjective("ans", game, seat),
                        ti4.ai.strategy.TechPath.researchesNeeded(game, seat, "ans"),
                        inScar,
                        seat.getStrategicCC(),
                        seat.getSecretsScored().containsKey("ans") ? ", scored" : ""));
            }
        }
    }

    private void recordGroundProgress(Game game) {
        for (Player seat : game.getRealPlayers()) {
            ti4.ai.scoring.ObjectiveValue value = null;
            for (String secret : GROUND_SECRETS) {
                if (!seat.getSecretsUnscored().containsKey(secret)) continue;
                if (value == null) value = new ti4.ai.scoring.ObjectiveValue(game, seat);
                int progress = value.progress(secret, value.before());
                groundProgress.merge(seat.getFaction() + "|" + secret, progress, Math::max);
            }
            for (String secret : GROUND_SECRETS) {
                if (seat.getSecretsScored().containsKey(secret))
                    groundProgress.put(seat.getFaction() + "|" + secret, 99);
            }
        }
    }

    private Map<String, List<Integer>> groundProgressByHolder() {
        Map<String, List<Integer>> bySecret = new java.util.TreeMap<>();
        groundProgress.forEach((key, progress) -> bySecret.computeIfAbsent(
                        key.substring(key.indexOf('|') + 1), secret -> new ArrayList<>())
                .add(progress));
        return bySecret;
    }

    private static boolean actionPhaseOver(Game game) {
        return ACTION_PHASE.equals(game.getPhaseOfGame())
                && !game.getRealPlayers().isEmpty()
                && game.getRealPlayers().stream().allMatch(Player::isPassed);
    }

    private static boolean inStatusPhase(Game game) {
        String phase = game.getPhaseOfGame();
        return phase != null && phase.startsWith(STATUS_PHASE_PREFIX);
    }

    private void open(Game game) {
        recordGroundProgress(game);
        traits.record(game);
        NexusWatch.record(game);
        recordMechanize(game);
        produce.record(game);
        seatOfTheEmpire.record(game);
        supplyLines.record(game);
        recordIconAndStrategies(game);
        perimeter.record(game);
        Map<String, Set<String>> publics = new HashMap<>();
        Map<String, Integer> secrets = new HashMap<>();
        List<Pending> pending = new ArrayList<>();
        for (Player seat : game.getRealPlayers()) {
            Set<String> scored = scoredPublics(game, seat);
            publics.put(seat.getUserID(), scored);
            secrets.put(seat.getUserID(), seat.getSecretsScored().size());
            for (String objective : revealedPublics(game)) {
                if (!scored.contains(objective)) {
                    pending.add(new Pending(seat.getUserID(), objective, shortBy(game, seat, objective)));
                }
            }
        }
        open = new Snapshot(game.getRound(), publics, secrets, pending);
        lastOpenedRound = game.getRound();
    }

    private void close(Game game) {
        Map<String, Set<String>> newlyScored = new HashMap<>();
        for (Player seat : game.getRealPlayers()) {
            Set<String> before = open.publicsBefore().get(seat.getUserID());
            if (before == null) continue;
            Set<String> scoredNow = new HashSet<>(scoredPublics(game, seat));
            scoredNow.removeAll(before);
            newlyScored.put(seat.getUserID(), scoredNow);
            int secrets = seat.getSecretsScored().size() - open.secretsBefore().getOrDefault(seat.getUserID(), 0);
            statusScoring.add(new StatusScoring(open.round(), seat.getFaction(), scoredNow.size(), secrets));
        }
        for (Pending pending : open.pending()) {
            boolean scored =
                    newlyScored.getOrDefault(pending.userId(), Set.of()).contains(pending.objective());
            chances.add(new Chance(pending.objective(), open.round(), pending.shortBy(), scored));
        }
        open = null;
    }

    private static int shortBy(Game game, Player seat, String objective) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        if (threshold <= 0) return UNMODELLED;
        int progress = ListPlayerInfoService.getPlayerProgressOnObjective(objective, game, seat);
        return Math.max(0, threshold - progress);
    }

    private static List<String> revealedPublics(Game game) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> Mapper.getPublicObjective(id) != null)
                .toList();
    }

    private static Set<String> scoredPublics(Game game, Player seat) {
        Set<String> scored = new HashSet<>();
        game.getScoredPublicObjectives().forEach((id, userIds) -> {
            if (Mapper.getPublicObjective(id) != null && userIds.contains(seat.getUserID())) scored.add(id);
        });
        return scored;
    }

    private static List<Reveal> reveals(Game game) {
        List<Reveal> reveals = new ArrayList<>();
        for (String id : revealedPublics(game)) {
            PublicObjectiveModel model = Mapper.getPublicObjective(id);
            List<String> scorers = game.getScoredPublicObjectives().getOrDefault(id, List.of());
            int seats = (int) game.getRealPlayers().stream()
                    .filter(seat -> scorers.contains(seat.getUserID()))
                    .count();
            reveals.add(new Reveal(id, model.getPoints(), seats));
        }
        return reveals;
    }

    private static WinningPathBreakdown leaderPath(Game game) {
        return game.getRealPlayers().stream()
                .max(Comparator.comparingInt(Player::getTotalVictoryPoints))
                .map(leader -> WinningPathHelper.breakDownWinningPath(game, leader))
                .orElse(Summary.NOTHING.leaderPath());
    }
}
