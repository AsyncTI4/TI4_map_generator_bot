package ti4.buttons.handlers.agenda.resolver;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.strategycard.PlayStrategyCardService;

public class CrisisAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "crisis";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (game.isHomebrewSCMode()) return;
        List<Button> scButtons = new ArrayList<>();
        switch (winner) {
            case "1" -> scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens"));
            case "2" -> scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
            case "3" -> scButtons.add(Buttons.gray("sc_ac_draw", "Draw 2 Action Cards", CardEmojis.ActionCard));
            case "4" -> {
                scButtons.add(Buttons.green("construction_spacedock", "Place 1 Space Dock", UnitEmojis.spacedock));
                scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
            }
            case "5" -> scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
            case "6" -> scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
            case "7" -> {
                scButtons.add(Buttons.GET_A_TECH);
                if (Helper.getPlayerFromAbility(game, "propagation") != null) {
                    scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Gain 3 Command Tokens (for Nekro)"));
                }
            }
            case "8" -> {
                PlayStrategyCardService.handleSOQueueing(game, false);
                scButtons.add(Buttons.gray("sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
            }
        }
        scButtons.add(Buttons.blue("sc_no_follow_" + winner, "Not Following"));
        MessageHelper.sendMessageToChannelWithButtons(
                game.getMainGameChannel(),
                "You may use these button to resolve the secondary ability of **"
                        + Helper.getSCName(Integer.parseInt(winner), game) + "**.",
                scButtons);
    }
}
