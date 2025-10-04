package ti4.service.draft;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.executors.CircuitBreaker;
import ti4.executors.ExecutionHistoryManager;
import ti4.helpers.TimedRunnable;
import ti4.map.Game;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.service.draft.NucleusSliceGeneratorService.NucleusOutcome;
import ti4.service.draft.draftables.SliceDraftable;

// TODO: This affects game state, and should lock buttons for a given game while working.

@UtilityClass
public class SliceGenerationPipeline {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final int EXECUTION_TIME_SECONDS_WARNING_THRESHOLD = 10;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    private static void generate(MiltyGenerateEvent miltyEvent) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var timedRunnable = new TimedRunnable(
                "Milty Generate event task for " + miltyEvent.specs.getGame().getName(),
                EXECUTION_TIME_SECONDS_WARNING_THRESHOLD,
                () -> {
                    try {
                        boolean outcome = SliceGeneratorService.generateSlices(
                                miltyEvent.event, miltyEvent.sliceDraftable, miltyEvent.tileManager, miltyEvent.specs);
                        miltyEvent.callback().accept(outcome);
                    } catch (Exception e) {
                        BotLogger.error(new LogOrigin(miltyEvent.specs.getGame()), "Failed to render event.", e);
                    }
                });

        ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
    }

    private static void generate(NucleusGenerateEvent nucleusEvent) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var timedRunnable = new TimedRunnable(
                "Nucleus Generate event task for " + nucleusEvent.game.getName(),
                EXECUTION_TIME_SECONDS_WARNING_THRESHOLD,
                () -> {
                    try {
                        NucleusOutcome outcome = NucleusSliceGeneratorService.generateNucleusAndSlices(
                                nucleusEvent.event, nucleusEvent.game, nucleusEvent.nucleusSpecs);
                        nucleusEvent.callback().accept(outcome);
                    } catch (Exception e) {
                        BotLogger.error(new LogOrigin(nucleusEvent.game), "Failed to render event.", e);
                    }
                });

        ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
    }

    public static void queue(
            GenericInteractionCreateEvent event,
            SliceDraftable sliceDraftable,
            DraftTileManager tileManager,
            DraftSpec specs,
            Consumer<Boolean> callback) {
        if (specs == null || specs.getGame() == null) {
            throw new IllegalArgumentException("specs nor game cannot be null in render pipeline");
        }
        generate(new MiltyGenerateEvent(event, sliceDraftable, tileManager, specs, callback));
    }

    public static void queue(
            GenericInteractionCreateEvent event,
            Game game,
            NucleusSpecs nucleusSpecs,
            Consumer<NucleusOutcome> callback) {
        if (game == null || nucleusSpecs == null) {
            throw new IllegalArgumentException("game nor nucleusSpecs cannot be null in render pipeline");
        }
        generate(new NucleusGenerateEvent(event, game, nucleusSpecs, callback));
    }

    public static boolean shutdown() {
        EXECUTOR_SERVICE.shutdownNow();
        try {
            return EXECUTOR_SERVICE.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            BotLogger.error("SliceGenerationPipeline shutdown interrupted.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    record MiltyGenerateEvent(
            GenericInteractionCreateEvent event,
            SliceDraftable sliceDraftable,
            DraftTileManager tileManager,
            DraftSpec specs,
            Consumer<Boolean> callback) {}

    record NucleusGenerateEvent(
            GenericInteractionCreateEvent event,
            Game game,
            NucleusSpecs nucleusSpecs,
            Consumer<NucleusOutcome> callback) {}
}
