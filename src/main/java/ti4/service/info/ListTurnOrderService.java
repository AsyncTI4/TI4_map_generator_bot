package ti4.service.info;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;

@UtilityClass
public class ListTurnOrderService {

    public static void turnOrder(GenericInteractionCreateEvent event, Game game) {
        turnOrder(event, game, true);
    }

    public static void turnOrder(GenericInteractionCreateEvent event, Game game, boolean pingPeople) {
        if (game.isFowMode()) {
            MessageHelper.replyToMessage(event, "Turn order does not display when `/game setup fow_mode:YES`");
            return;
        }

        HashMap<Integer, String> order = new HashMap<>();
        int naaluSC = 0;
        for (Player player : game.getRealPlayers()) {
            int sc = player.getLowestSC();
            String scNumberIfNaaluInPlay = game.getSCNumberIfNaaluInPlay(player, Integer.toString(sc));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                naaluSC = sc;
            }
            boolean passed = player.isPassed();

            Set<Integer> SCs = player.getSCs();
            Map<Integer, Boolean> scPlayed = game.getScPlayed();
            StringBuilder textBuilder = new StringBuilder();
            for (int sc_ : SCs) {
                Boolean found = scPlayed.get(sc_);
                boolean isPlayed = found != null ? found : false;
                TI4Emoji scEmoji = isPlayed ? CardEmojis.getSCBackFromInteger(sc_) : CardEmojis.getSCFrontFromInteger(sc_);
                if (isPlayed) {
                    textBuilder.append("~~");
                }
                textBuilder.append(scEmoji).append(Helper.getSCAsMention(sc_, game));
                if (isPlayed) {
                    textBuilder.append("~~");
                }
            }
            String text = textBuilder.toString();
            if (passed) {
                text += "~~";
            }
            if (pingPeople || game.isFowMode()) {
                text += player.getRepresentation();
            } else {
                text += player.getFactionEmoji() + " " + player.getUserName();
            }

            if (passed) {
                text += "~~ - passed";
            }

            if (player.getUserID().equals(game.getSpeakerUserID())) {
                text += " " + MiscEmojis.SpeakerToken;
            }

            order.put(sc, text);

        }
        StringBuilder msg = new StringBuilder("__Turn Order:__\n");

        if (naaluSC != 0) {
            String text = order.get(naaluSC);
            msg.append("`").append(0).append(".`").append(text).append("\n");
        }
        Integer max = Collections.max(game.getScTradeGoods().keySet());
        if (ButtonHelper.getKyroHeroSC(game) != 1000) {
            max += 1;
        }
        for (int i = 1; i <= max; i++) {
            if (naaluSC != 0 && i == naaluSC) {
                continue;
            }
            String text = order.get(i);
            if (text != null) {
                msg.append("`").append(i).append(".`").append(text).append("\n");
            }
        }
        msg.append("_ _"); // forced extra line
        TextChannel channel = game.getMainGameChannel();
        if (event instanceof SlashCommandInteractionEvent slash && slash.getChannel() instanceof TextChannel chan) {
            channel = chan;
        }
        MessageHelper.sendMessageToChannel(channel, msg.toString());

    }
}
