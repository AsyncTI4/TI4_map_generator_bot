package ti4.ai.promise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiGameWrites;
import ti4.ai.eval.DecidingSeat;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class PromiseBook {

    static final String KEY = "aiPromises";
    static final int MAX_RECORDS = 48;
    private static final String RECORDS = ";";
    private static final String SAVE_REASON = "AI promise";

    public static List<Promise> all(Game game) {
        List<Promise> promises = new ArrayList<>();
        for (String encoded : StringUtils.split(game.getStoredValue(KEY), RECORDS)) {
            Promise.decode(encoded).ifPresent(promises::add);
        }
        return promises;
    }

    public static Optional<Promise> find(Game game, String id) {
        return all(game).stream().filter(promise -> promise.id().equals(id)).findFirst();
    }

    public static void save(Game game, Promise promise) {
        List<Promise> kept = new ArrayList<>(all(game).stream()
                .filter(other -> !other.id().equals(promise.id()))
                .filter(other -> worthKeeping(game, other))
                .toList());
        if (worthKeeping(game, promise)) kept.add(promise);
        kept.sort(Comparator.comparingLong(Promise::offeredAt));
        while (kept.size() > MAX_RECORDS) kept.removeFirst();
        game.setStoredValue(KEY, kept.stream().map(Promise::encode).collect(Collectors.joining(RECORDS)));
    }

    private static boolean worthKeeping(Game game, Promise promise) {
        return promise.thisRound(game) || promise.status() == Promise.Status.BROKEN;
    }

    public static void persist(Game game, Promise promise) {
        AiGameWrites.persist(game, SAVE_REASON, latest -> save(latest, promise));
    }

    public static boolean active(Game game, Player seat) {
        return AiExperiments.enabled(DecidingSeat.orSelf(game, seat), Experiment.PROMISE_DEALS);
    }

    public static List<Promise> bindingOn(Game game, Player promiser) {
        if (!active(game, promiser)) return List.of();
        return all(game).stream()
                .filter(promise -> promise.thisRound(game))
                .filter(Promise::binds)
                .filter(promise -> promise.isPromiser(promiser))
                .toList();
    }

    public static boolean forbidsActivating(Game game, Player promiser, Tile tile) {
        return bindingOn(game, promiser).stream().anyMatch(promise -> promise.forbidsActivating(game, tile));
    }

    public static List<Promise> bindingFor(Game game, Player promisee) {
        if (!active(game, promisee)) return List.of();
        return all(game).stream()
                .filter(promise -> promise.thisRound(game))
                .filter(promise -> promise.status() == Promise.Status.ACCEPTED)
                .filter(promise -> promise.isPromisee(promisee))
                .toList();
    }

    public static List<Promise> brokenAgainst(Game game, Player victim) {
        return all(game).stream()
                .filter(promise -> promise.status() == Promise.Status.BROKEN)
                .filter(promise -> promise.isPromisee(victim))
                .toList();
    }

    public static long brokenBy(Game game, Player promiser, Player victim) {
        return brokenAgainst(game, victim).stream()
                .filter(promise -> promise.isPromiser(promiser))
                .count();
    }

    public static List<Promise> proposedBy(Game game, Player proposer) {
        return all(game).stream()
                .filter(promise -> promise.thisRound(game))
                .filter(promise -> promise.proposer().equals(proposer.getFaction()))
                .toList();
    }

    public static boolean between(Promise promise, Player first, Player second) {
        return (promise.isPromiser(first) && promise.isPromisee(second))
                || (promise.isPromiser(second) && promise.isPromisee(first));
    }
}
