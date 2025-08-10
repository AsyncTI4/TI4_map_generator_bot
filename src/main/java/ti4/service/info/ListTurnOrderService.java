package ti4.service.info;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
        for (Player player : game.getRealPlayers()) {
            order.put(player.getInitiative(), buildPlayerScText(game, player, pingPeople));
        }

        StringBuilder msg = new StringBuilder("__Turn Order:__\n");
        for (Player p : game.getActionPhaseTurnOrder()) {
            msg.append("`").append(p.getInitiative()).append(".`");
            msg.append(buildPlayerScText(game, p, pingPeople)).append("\n");
        }
        msg.append("_ _"); // forced extra line

        TextChannel channel = game.getMainGameChannel();
        if (event instanceof SlashCommandInteractionEvent slash && slash.getChannel() instanceof TextChannel chan) {
            channel = chan;
        }
        MessageHelper.sendMessageToChannel(channel, msg.toString());
    }

    private static String buildPlayerScText(Game game, Player player, boolean pingPeople) {
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

        if (passed) {
            textBuilder.append("~~");
        }

        if (pingPeople || game.isFowMode()) {
            textBuilder.append(player.getRepresentation());
        } else {
            textBuilder.append(player.getFactionEmoji() + " " + player.getUserName());
        }

        if (passed) {
            textBuilder.append("~~ - passed");
        }

        if (player.isSpeaker()) {
            textBuilder.append(" " + MiscEmojis.SpeakerToken);
        }

        return textBuilder.toString();
    }
}
