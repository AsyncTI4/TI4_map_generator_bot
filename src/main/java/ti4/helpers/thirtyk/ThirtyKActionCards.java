package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class ThirtyKActionCards {

    public static boolean resolveActionCard(ActionCardModel card, Player player, String introMsg) {
        if (card.getSource() != ComponentSource.thirtyk) return false;
        List<Button> buttons = new ArrayList<>();
        if (buttons.isEmpty()) return false;
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), introMsg, buttons);
        return true;
    }
}
