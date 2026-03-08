package ti4.buttons.handlers.edict.resolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TkEndorseResolver implements EdictResolver {

    @Getter
    public String edict = "tk-endorse";

    private List<Button> buttons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String id = player.finChecker() + "electEndorse_";
        for (Player p2 : game.getRealPlayers()) {
            buttons.add(Buttons.green(id + p2.getFaction(), p2.getFogSafeDisplayName(), p2.fogSafeEmoji()));
        }
        return buttons;
    }

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), playerPing(player), buttons(game, player));
    }

    @ButtonHandler("electEndorse")
    public static void electEndorse(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        game.discardSpecificAgenda("tk-endorse");
        game.addLaw("tk-endorse", p2.getFaction());
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), p2.getRepresentation() + " has been _Endorse_d.");

        ButtonHelper.deleteMessage(event);
    }
}
