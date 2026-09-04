package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.strategycard.StrategyCardSecondaryButtonService;

@UtilityClass
class OverthrowAcd2ButtonHandler {

    @ButtonHandler("resolveOverthrow")
    public static void resolveOverthrow(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getOverthrowAbilityButtons(game);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the strategy card ability to resolve for _Overthrow_. Resolve the **secondary**"
                        + " ability of 1 of that player's strategy cards — or, if you gained control of a planet"
                        + " in their home system, the **primary** ability instead. No command token is spent.",
                buttons);
    }

    private static List<Button> getOverthrowAbilityButtons(Game game) {
        List<Button> scButtons =
                new ArrayList<>(StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, game.getSCList()));
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));
        return scButtons;
    }
}
