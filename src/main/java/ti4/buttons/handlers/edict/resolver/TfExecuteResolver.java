package ti4.buttons.handlers.edict.resolver;

import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TfExecuteResolver implements EdictResolver {

    @Getter
    public String edict = "tf-execute";

    private static List<Button> buttons(Player player) {
        String id = player.finChecker() + "resolvePlagueStep";
        return List.of(
                Buttons.green(id + "11", "Resolve Execute"),
                Buttons.green(id + "12", "Resolve Execute"),
                Buttons.green(id + "13", "Resolve Execute"));
    }

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), playerPing(player), buttons(player));
    }
}
