package ti4.service.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import ti4.discord.JdaService;
import ti4.executors.CircuitBreaker;
import ti4.logging.BotLogger;

@UtilityClass
public class TournamentPacing {

    static final long BASE_PAUSE_MILLIS = 2000;
    static final long MAX_PAUSE_MILLIS = 60_000;
    static final long SLOW_STEP_MILLIS = 10_000;
    static final long SLOW_GATEWAY_PING_MILLIS = 500;
    static final int SLOW_GATEWAY_MULTIPLIER = 4;
    private static final int WARN_AFTER_SECONDS = 3600;
    private static final TournamentJobGuard<String> RUNNING = new TournamentJobGuard<>();

    public record Result(int done, List<String> problems, boolean stoppedForLoad) {

        public String summary(String verb) {
            List<String> lines = new ArrayList<>();
            lines.add(verb + " " + done + " game(s).");
            if (!problems.isEmpty()) lines.add(String.join("\n", problems));
            if (stoppedForLoad) lines.add("Stopped early because the bot is under load. Press the button again later.");
            return String.join("\n", lines);
        }
    }

    public static <T> boolean forEach(String jobKey, List<T> items, Function<T, String> step, Consumer<Result> onDone) {
        if (CircuitBreaker.isOpen()) return false;
        return RUNNING.submit(jobKey, jobKey, WARN_AFTER_SECONDS, () -> onDone.accept(runSequentially(items, step)));
    }

    private static <T> Result runSequentially(List<T> items, Function<T, String> step) {
        List<String> problems = new ArrayList<>();
        int done = 0;
        for (int i = 0; i < items.size(); i++) {
            long start = System.currentTimeMillis();
            String problem;
            try {
                problem = step.apply(items.get(i));
            } catch (RuntimeException e) {
                BotLogger.error("Tournament batch step failed", e);
                problem = "Unexpected error: " + e.getMessage();
            }
            if (problem == null) done++;
            else problems.add(problem);
            boolean last = i == items.size() - 1;
            if (!last && !waitAfter(System.currentTimeMillis() - start)) {
                return new Result(done, problems, true);
            }
        }
        return new Result(done, problems, false);
    }

    public static boolean waitAfter(long stepMillis) {
        if (CircuitBreaker.isOpen()) return false;
        long gatewayPing = JdaService.jda == null ? 0 : JdaService.jda.getGatewayPing();
        try {
            Thread.sleep(pauseAfter(stepMillis, gatewayPing));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !CircuitBreaker.isOpen();
    }

    static long pauseAfter(long stepMillis, long gatewayPingMillis) {
        long pause = stepMillis > SLOW_STEP_MILLIS ? Math.max(BASE_PAUSE_MILLIS, stepMillis) : BASE_PAUSE_MILLIS;
        if (gatewayPingMillis > SLOW_GATEWAY_PING_MILLIS) pause *= SLOW_GATEWAY_MULTIPLIER;
        return Math.min(pause, MAX_PAUSE_MILLIS);
    }
}
