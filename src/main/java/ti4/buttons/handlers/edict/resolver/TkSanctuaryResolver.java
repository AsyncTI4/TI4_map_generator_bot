package ti4.buttons.handlers.edict.resolver;

import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TkSanctuaryResolver implements EdictResolver {

    @Getter
    public String edict = "tk-sanctuary";

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), playerPing(player), buttons);
    }
}
