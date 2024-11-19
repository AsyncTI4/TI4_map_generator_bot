package ti4.service;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class PlayerReactService {

    public static boolean checkForASpecificPlayerReact(String messageId, Player player, Game game) {
        boolean foundReact = false;
        try {
            if (game.getStoredValue(messageId) != null
                && game.getStoredValue(messageId).contains(player.getFaction())) {
                return true;
            }
//            game.getMainGameChannel().retrieveMessageById(messageId).queue(mainMessage -> {
//                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
//                if (game.isFowMode()) {
//                    int index = 0;
//                    for (Player player_ : game.getPlayers().values()) {
//                        if (player_ == player)
//                            break;
//                        index++;
//                    }
//                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, messageId));
//                }
//                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
//            });
        } catch (Exception e) {
            game.removeMessageIDForSabo(messageId);
            return true;
        }
        return foundReact;
    }
}
