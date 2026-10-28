package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.strategycard.PlayStrategyCardService;

public class CrisisAgendaResolver implements AgendaResolver {
    @Override
    public String agendaId() {
        return "crisis";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (game.isHomebrewSCMode()) return;
        List<Button> scButtons = new ArrayList<>();
        switch (winner) {
            case "1" -> scButtons.add(Buttons.SPEND_AND_GAIN_COMMAND_TOKENS);
            case "2" -> scButtons.add(Buttons.DIPLO_READY_2_PLANETS);
            case "3" -> scButtons.add(Buttons.SC_DRAW_2_ACTION_CARDS);
            case "4" -> {
                scButtons.add(Buttons.CONSTRUCTION_PLACE_1_SPACE_DOCK);
                scButtons.add(Buttons.CONSTRUCTION_PLACE_1_PDS);
            }
            case "5" -> scButtons.add(Buttons.REPLENISH_COMMODITIES);
            case "6" -> scButtons.add(Buttons.WARFARE_BUILD_AT_HOME);
            case "7" -> {
                scButtons.add(Buttons.GET_A_TECH);
                if (!game.isFowMode() && Helper.getPlayerFromAbility(game, "propagation") != null) {
                    scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Gain 3 Command Tokens (for Nekro)"));
                }
            }
            case "8" -> {
                PlayStrategyCardService.handleSOQueueing(game, false);
                scButtons.add(Buttons.SC_DRAW_SECRET_OBJECTIVE);
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
