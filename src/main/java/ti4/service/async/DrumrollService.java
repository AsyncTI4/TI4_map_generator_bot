package ti4.service.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.AsyncTI4DiscordBot;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class DrumrollService {

    private static void sleepForTwoSeconds() {
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (Exception ignored) {
        }
    }

    private static String drumrollString(String message, int iteration) {
        StringBuilder sb = new StringBuilder();
        if (message != null) sb.append(message).append("\n");
        sb.append("# Drumroll please.... ").append(MiscEmojis.RollDice).append("\n");
        sb.append("# 🥁").append(" 🥁".repeat(iteration));
        return sb.toString();
    }

    private MessageHelper.MessageFunction drumrollFunction(
            List<Message> bonusMessages, int seconds, String message, String gameName, Predicate<Game> resolve) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + seconds * 1000L;

        return msg -> {
            int iteration = 1;
            sleepForTwoSeconds();
            while (System.currentTimeMillis() < endTime) {
                String drumroll = drumrollString(message, iteration);
                msg.editMessage(drumroll).queue(null, null);
                for (Message bonus : bonusMessages) {
                    bonus.editMessage(drumroll).queue(null, null);
                }
                iteration++;
                sleepForTwoSeconds();
                if (!AsyncTI4DiscordBot.isReadyToReceiveCommands())
                    break; // if the bot needs to shut down, cancel all drumrolls
            }
            msg.delete().queue(null, null);
            for (Message bonus : bonusMessages) {
                bonus.delete().queue(null, null);
            }

            Game reloadedGame = GameManager.getManagedGame(gameName).getGame();
            if (resolve.test(reloadedGame)) GameManager.save(reloadedGame, "Post-Drumroll");
        };
    }

    public void doDrumroll(MessageChannel main, String msg, int sec, String gameName, Predicate<Game> resolve) {
        doDrumrollMultiChannel(main, msg, sec, gameName, resolve, null, null);
    }

    public void doDrumrollMirrored(
            MessageChannel main,
            String msg,
            int sec,
            String gameName,
            Predicate<Game> resolve,
            MessageChannel channel2,
            String msg2) {
        List<MessageChannel> chans = channel2 == null ? Collections.emptyList() : List.of(channel2);
        List<String> msgs = msg2 == null ? Collections.emptyList() : List.of(msg2);
        doDrumrollMultiChannel(main, msg, sec, gameName, resolve, chans, msgs);
    }

    public void doDrumrollMultiChannel(
            MessageChannel main,
            String msg,
            int sec,
            String gameName,
            Predicate<Game> resolve,
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
        MessageHelper.MessageFunction function = drumrollFunction(bonusMessages, sec, msg, gameName, resolve);
        MessageHelper.splitAndSentWithAction(initialDrumroll, main, function);
    }
}
