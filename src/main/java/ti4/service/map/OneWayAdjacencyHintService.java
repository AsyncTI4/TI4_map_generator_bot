package ti4.service.map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.game.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class OneWayAdjacencyHintService {

    static final String HINT_SHOWN = "oneWayAdjacencyHintShown";
    static final String HINT = "-# One-way adjacency is off in this game: this link only limits movement, and counts"
            + " both ways for everything else. Turn on full one-way rules with"
            + " `/custom customization one_way_adjacency:True`.";

    public static void hintOnce(Game game, MessageChannel channel) {
        if (game.usesOneWayAdjacency() || !game.getStoredValue(HINT_SHOWN).isEmpty()) {
            return;
        }
        game.setStoredValue(HINT_SHOWN, "true");
        MessageHelper.sendMessageToChannel(channel, HINT);
    }
}
