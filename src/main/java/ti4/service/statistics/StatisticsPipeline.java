package ti4.service.statistics;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.apache.commons.lang3.time.StopWatch;
import ti4.message.BotLogger;

public class StatisticsPipeline {

    private static final int EXECUTION_TIME_SECONDS_WARNING_THRESHOLD = 20;
    private static final StatisticsPipeline instance = new StatisticsPipeline();

    private final BlockingQueue<StatisticsPipeline.StatisticsEvent> statisticsQueue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private boolean running = true;

    private StatisticsPipeline() {
        worker = new Thread(() -> {
            while (running || !statisticsQueue.isEmpty()) {
                try {
                    StatisticsPipeline.StatisticsEvent statisticsEvent = statisticsQueue.poll(2, TimeUnit.SECONDS);
                    if (statisticsEvent != null) {
                        run(statisticsEvent);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    BotLogger.log("StatsComputationPipeline worker threw an exception.", e);
                }
            }
        });
    }

    public static void start() {
        instance.worker.start();
    }

    public static boolean shutdown() {
        instance.running = false;
        try {
            instance.worker.join(20000);
            return !instance.worker.isAlive();
        } catch (InterruptedException e) {
            BotLogger.log("MapRenderPipeline shutdown interrupted.");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void queue(StatisticsPipeline.StatisticsEvent event) {
        instance.statisticsQueue.add(event);
    }

    public static void run(StatisticsEvent event) {
        event.event.reply("Your statistics are being processed, please hold...").setEphemeral(true).queue();
        StopWatch stopWatch = StopWatch.createStarted();

        event.runner.run();

        stopWatch.stop();
        Duration timeElapsed = stopWatch.getDuration();
        if (timeElapsed.toSeconds() > EXECUTION_TIME_SECONDS_WARNING_THRESHOLD) {
            BotLogger.log("Render event for " + event.name + " took longer than " + EXECUTION_TIME_SECONDS_WARNING_THRESHOLD +
                " seconds (" + timeElapsed.toSeconds() + ").");
        }
    }

    public record StatisticsEvent(String name, IReplyCallback event, Runnable runner) {}
}
