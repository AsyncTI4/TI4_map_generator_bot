package ti4.commands.status;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

public class ListTurnOrder extends StatusSubcommandData {
    public ListTurnOrder() {
        super(Constants.TURN_ORDER, "List Turn order with SC played and Player passed status");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        turnOrder(event, activeGame);
    }

    public static void turnOrder(GenericInteractionCreateEvent event, Game activeGame) {

        if (activeGame.isFoWMode()) {
            MessageHelper.replyToMessage(event, "Turn order does not display when `/game setup fow_mode:YES`");
            return;
        }

        HashMap<Integer, String> order = new HashMap<>();
        int naaluSC = 0;
        for (Player player : activeGame.getRealPlayers()) {
            int sc = player.getLowestSC();
            String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, activeGame, Integer.toString(sc));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                naaluSC = sc;
            }
            boolean passed = player.isPassed();

            Set<Integer> SCs = player.getSCs();
            HashMap<Integer, Boolean> scPlayed = activeGame.getScPlayed();
            String text = "";
            for (int sc_ : SCs) {
                Boolean found = scPlayed.get(sc_);
                boolean isPlayed = found != null ? found : false;
                String scEmoji = isPlayed ? Helper.getSCBackEmojiFromInteger(sc_) : Helper.getSCEmojiFromInteger(sc_);
                if (isPlayed) {
                    text += "~~";
                }
                text += scEmoji + Helper.getSCAsMention(event.getGuild(), sc_, activeGame);
                if (isPlayed) {
                    text += "~~";
                }
            }
            if (passed) {
                text += "~~";
            }
            text += Helper.getPlayerRepresentation(player, activeGame);
            if (passed) {
                text += "~~ - PASSED";
            }

            if (player.getUserID().equals(activeGame.getSpeaker())) {
                text += " " + Emojis.SpeakerToken;
            }

            order.put(sc, text);

        }
        StringBuilder msg = new StringBuilder("__**Turn Order:**__\n");

        if (naaluSC != 0) {
            String text = order.get(naaluSC);
            msg.append("`").append(0).append(".`").append(text).append("\n");
        }
        Integer max = Collections.max(activeGame.getScTradeGoods().keySet());
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
        
        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), msg.toString());
        
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        //We reply in execute command
    }
}
