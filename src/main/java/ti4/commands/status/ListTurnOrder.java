package ti4.commands.status;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ListTurnOrder extends StatusSubcommandData {
    public ListTurnOrder() {
        super(Constants.TURN_ORDER, "List Turn order with SC played and Player passed status");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        turnOrder(event, game, false);
    }

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
                String scEmoji = isPlayed ? Emojis.getSCBackEmojiFromInteger(sc_) : Emojis.getSCEmojiFromInteger(sc_);
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
                text += "~~ - PASSED";
            }

            if (player.getUserID().equals(game.getSpeakerUserID())) {
                text += " " + Emojis.SpeakerToken;
            }

            order.put(sc, text);

        }
        StringBuilder msg = new StringBuilder("__**Turn Order:**__\n");

        if (naaluSC != 0) {
            String text = order.get(naaluSC);
            msg.append("`").append(0).append(".`").append(text).append("\n");
        }
        Integer max = Collections.max(game.getScTradeGoods().keySet());
        if (ButtonHelper.getKyroHeroSC(game) != 1000) {
            max = max + 1;
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

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        //We reply in execute command
    }
}
