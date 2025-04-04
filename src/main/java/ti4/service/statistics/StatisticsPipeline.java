package ti4.service.statistics;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import ti4.helpers.TimedRunnable;
import ti4.message.BotLogger;

public class StatisticsPipeline {

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
                    BotLogger.error("StatsComputationPipeline worker threw an exception.", e);
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
            BotLogger.error("StatisticsPipeline shutdown interrupted.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void queue(StatisticsPipeline.StatisticsEvent event) {
        instance.statisticsQueue.add(event);
    }

    public static void run(StatisticsEvent event) {
        event.event.getHook().sendMessage("Your statistics are being processed, please hold...").setEphemeral(true).queue();
        new TimedRunnable(event.name, event.runnable).run();
    }

    public record StatisticsEvent(String name, IReplyCallback event, Runnable runnable) {}
}
