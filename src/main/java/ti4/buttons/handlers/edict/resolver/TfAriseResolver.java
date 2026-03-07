package ti4.buttons.handlers.edict.resolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.UnitEmojis;

public class TfAriseResolver implements EdictResolver {

    @Getter
    String edict = "tf-arise";

    private static List<Button> buttons(Player player) {
        String id = player.finChecker();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(id + "riseOfAMessiah", "Infantry On Every Planet", UnitEmojis.infantry));
        buttons.add(Buttons.green(id + "fighterConscription", "Fighter With Every Ship", UnitEmojis.fighter));
        return buttons;
    }

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), playerPing(player), buttons(player));
    }
}
