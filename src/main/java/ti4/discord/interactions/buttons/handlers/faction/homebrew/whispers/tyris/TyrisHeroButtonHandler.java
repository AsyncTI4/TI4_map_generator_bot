package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.tyris;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class TyrisHeroButtonHandler {

    public static boolean isHeroActiveThisRound(Game game, Player player) {
        return !game.getStoredValue("tyrisHeroRound" + game.getRound() + "_" + player.getFaction())
                .isEmpty();
    }

    public static void offerHeroAtStartOfTurn(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "leader_tyrishero",
                "Use Fatebreaker Azhurak (Unlimited Actions)",
                FactionEmojis.tyris));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", you may use Fatebreaker Azhurak at the start of this turn for unlimited actions.",
                buttons);
    }
}
