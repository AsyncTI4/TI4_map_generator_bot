package ti4.service.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class DrumrollService {

    private static String drumrollString(String message, int iteration) {
        StringBuilder sb = new StringBuilder();
        if (message != null) sb.append(message).append('\n');
        sb.append("# Drumroll please.... ").append(MiscEmojis.RollDice).append('\n');
        sb.append("# 🥁").append(" 🥁".repeat(iteration));
        return sb.toString();
    }

    private static Consumer<Message> drumrollFunction(
            List<Message> bonusMessages, int seconds, String message, Runnable onCompletion) {
        long endTime = System.currentTimeMillis() + seconds * 1000L;
        return msg -> drumrollStep(msg, bonusMessages, message, onCompletion, 1, endTime);
    }

    private static void drumrollStep(
            Message msg,
            List<Message> bonusMessages,
            String message,
            Runnable onCompletion,
            int iteration,
            long endTime) {
        if (!JdaService.isReadyToReceiveCommands() || System.currentTimeMillis() >= endTime) {
            finishDrumroll(msg, bonusMessages, onCompletion);
            return;
        }
        String drumroll = drumrollString(message, iteration);
        msg.editMessage(drumroll)
                .queueAfter(
                        2,
                        TimeUnit.SECONDS,
                        success -> {
                            for (Message bonus : bonusMessages) {
                                bonus.editMessage(drumroll).queue(Consumers.nop(), BotLogger::catchRestError);
                            }
                            drumrollStep(msg, bonusMessages, message, onCompletion, iteration + 1, endTime);
                        },
                        failure -> finishDrumroll(msg, bonusMessages, onCompletion));
    }

    private static void finishDrumroll(Message msg, List<Message> bonusMessages, Runnable onCompletion) {
        msg.delete().queue(Consumers.nop(), BotLogger::catchRestError);
        for (Message bonus : bonusMessages) {
            bonus.delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
        if (!JdaService.isReadyToReceiveCommands()) return;
        onCompletion.run();
    }

    public void doDrumroll(MessageChannel main, String msg, int sec, Runnable onCompletion) {
        doDrumrollMultiChannel(main, msg, sec, onCompletion, null, null);
    }

    public void doDrumrollMirrored(
            MessageChannel main, String msg, int sec, Runnable onCompletion, MessageChannel channel2, String msg2) {
        List<MessageChannel> chans = channel2 == null ? Collections.emptyList() : List.of(channel2);
        List<String> msgs = msg2 == null ? Collections.emptyList() : List.of(msg2);
        doDrumrollMultiChannel(main, msg, sec, onCompletion, chans, msgs);
    }

    private void doDrumrollMultiChannel(
            MessageChannel main,
            String msg,
            int sec,
            Runnable onCompletion,
            List<MessageChannel> bonusChannels,
            List<String> altMessages) {
        List<Message> bonusMessages = new ArrayList<>();
        if (bonusChannels != null) {
            List<String> msgs = new ArrayList<>();
            if (altMessages != null) msgs.addAll(altMessages);
            while (msgs.size() < bonusChannels.size()) msgs.add(msg);

            for (int i = 0; i < bonusChannels.size(); i++) {
                MessageChannel mc = bonusChannels.get(i);
                String initial = drumrollString(msgs.get(i), 0);
                bonusMessages.add(mc.sendMessage(initial).complete());
            }
        }

        String initialDrumroll = drumrollString(msg, 0);
        Consumer<Message> function = drumrollFunction(bonusMessages, sec, msg, onCompletion);
        MessageHelper.splitAndSentWithAction(initialDrumroll, main, function);
    }
}
