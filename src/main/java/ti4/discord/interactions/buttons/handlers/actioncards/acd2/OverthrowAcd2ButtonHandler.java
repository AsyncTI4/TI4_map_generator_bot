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
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;

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
        List<Integer> scs = game.getSCList();
        List<Button> scButtons = new ArrayList<>();
        if (scs.contains(1)) {
            scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens"));
        }
        if (scs.contains(2)) {
            scButtons.add(Buttons.gray("anarchy2secondary", "Ready a Card (Other Than Strategy Card)"));
            scButtons.add(Buttons.green("diploRefresh2", "Ready Planets"));
        }
        if (scs.contains(4)) {
            scButtons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (scs.contains(5)) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (scs.contains(6)) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (scs.contains(7)) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (scs.contains(8)) {
            scButtons.add(Buttons.green("resolveAnarchy8Secondary", "Lift Command Token"));
        }
        if (scs.contains(9)) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (scs.contains(11)) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));
        return scButtons;
    }
}
