package ti4.service.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
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
            List<Message> bonusMessages, int seconds, String message, String gameName, Consumer<Game> resolve) {
        long endTime = System.currentTimeMillis() + seconds * 1000L;
        return msg -> drumrollStep(msg, bonusMessages, message, gameName, resolve, 1, endTime);
    }

    private static void drumrollStep(
            Message msg,
            List<Message> bonusMessages,
            String message,
            String gameName,
            Consumer<Game> resolve,
            int iteration,
            long endTime) {
        if (!JdaService.isReadyToReceiveCommands() || System.currentTimeMillis() >= endTime) {
            finishDrumroll(msg, bonusMessages, gameName, resolve);
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
                            drumrollStep(msg, bonusMessages, message, gameName, resolve, iteration + 1, endTime);
                        },
                        failure -> finishDrumroll(msg, bonusMessages, gameName, resolve));
    }

    private static void finishDrumroll(
            Message msg, List<Message> bonusMessages, String gameName, Consumer<Game> resolve) {
        msg.delete().queue(Consumers.nop(), BotLogger::catchRestError);
        for (Message bonus : bonusMessages) {
            bonus.delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
        if (!JdaService.isReadyToReceiveCommands()) return;
        var managed = GameManager.getManagedGame(gameName);
        if (managed == null || managed.getGame() == null) return;
        resolve.accept(managed.getGame());
    }

    public void doDrumroll(MessageChannel main, String msg, int sec, String gameName, Consumer<Game> resolve) {
        doDrumrollMultiChannel(main, msg, sec, gameName, resolve, null, null);
    }

    public void doDrumrollMirrored(
            MessageChannel main,
            String msg,
            int sec,
            String gameName,
            Consumer<Game> resolve,
            MessageChannel channel2,
            String msg2) {
        List<MessageChannel> chans = channel2 == null ? Collections.emptyList() : List.of(channel2);
        List<String> msgs = msg2 == null ? Collections.emptyList() : List.of(msg2);
        doDrumrollMultiChannel(main, msg, sec, gameName, resolve, chans, msgs);
    }

    private void doDrumrollMultiChannel(
            MessageChannel main,
            String msg,
            int sec,
            String gameName,
            Consumer<Game> resolve,
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
        Consumer<Message> function = drumrollFunction(bonusMessages, sec, msg, gameName, resolve);
        MessageHelper.splitAndSentWithAction(initialDrumroll, main, function);
    }
}
