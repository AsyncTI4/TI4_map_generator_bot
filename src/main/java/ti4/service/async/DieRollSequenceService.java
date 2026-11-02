package ti4.service.async;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.discord.JdaService;
import ti4.helpers.DiceHelper.Die;
import ti4.logging.BotLogger;

@UtilityClass
public class DieRollSequenceService {

    private static final long ACTIVITY_DELAY_MS = 1000;
    private static final long REVEAL_DELAY_MS = 1500;

    public static void play(
            MessageChannel channel, String header, List<String> activities, List<Die> rolls, Runnable onCompletion) {
        if (!JdaService.isReadyToReceiveCommands()) return;
        if (rolls.isEmpty()) {
            onCompletion.run();
            return;
        }
        channel.sendMessage(render(header, activities, rolls, 0))
                .queue(
                        message -> step(message, header, activities, rolls, 1, onCompletion),
                        failure -> abort(failure, onCompletion));
    }

    static String render(String header, List<String> activities, List<Die> rolls, int step) {
        StringBuilder sb = new StringBuilder(header);
        for (int i = 0; i * 2 < step; i++) {
            sb.append('\n').append(activities.get(i));
            if (i * 2 + 1 < step) {
                sb.append(' ').append(rolls.get(i).getGreenDieIfSuccessOrRedDieIfFailure());
            }
        }
        return sb.toString();
    }

    private static void step(
            Message message, String header, List<String> activities, List<Die> rolls, int step, Runnable onCompletion) {
        if (step > rolls.size() * 2 || !JdaService.isReadyToReceiveCommands()) {
            onCompletion.run();
            return;
        }
        long delay = step % 2 == 1 ? ACTIVITY_DELAY_MS : REVEAL_DELAY_MS;
        message.editMessage(render(header, activities, rolls, step))
                .queueAfter(
                        delay,
                        TimeUnit.MILLISECONDS,
                        success -> step(message, header, activities, rolls, step + 1, onCompletion),
                        failure -> abort(failure, onCompletion));
    }

    private static void abort(Throwable failure, Runnable onCompletion) {
        BotLogger.catchRestError(failure);
        onCompletion.run();
    }
}
