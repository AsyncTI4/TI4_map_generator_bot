package ti4.service.tech;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.leader.agent.AgentLifecycle;

@UtilityClass
public class TemporalCommandSuiteService {

    public static String label(Game game, String agentName) {
        return (game.isTwilightsFallMode() ? "Token: Ready " : "TCS: Ready ") + agentName;
    }

    public static void offer(Game game, Player agentHolder, String agentId, boolean viaYssaril) {
        for (Player p2 : game.getRealPlayers()) {
            if (!p2.hasTech("tcs") || p2.getExhaustedTechs().contains("tcs")) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            String agentName = AgentLifecycle.agentNameShownTo(game, p2, agentHolder, agentId, viaYssaril);
            String msg;
            if (game.isTwilightsFallMode()) {
                buttons.add(Buttons.green(
                        p2.factionButtonChecker() + "useTCS_" + agentId + "_" + agentHolder.getFaction(),
                        label(game, agentName)));
                msg = p2.getRepresentationNoPing()
                        + " you have the opportunity to spend a command token via _Temporal Command Suite_ to ready "
                        + agentName
                        + ", and potentially resolve a transaction.";
            } else {
                buttons.add(Buttons.green(
                        p2.factionButtonChecker() + "exhaustTCS_" + agentId + "_" + agentHolder.getFaction(),
                        label(game, agentName)));
                msg = p2.getRepresentationNoPing()
                        + " you have the opportunity to exhaust _Temporal Command Suite_ to ready " + agentName
                        + ", and potentially resolve a transaction.";
            }
            buttons.add(Buttons.red(p2.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
        }
    }
}
