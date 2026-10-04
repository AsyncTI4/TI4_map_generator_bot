package ti4.service.leader.agent.modules;

import java.util.List;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.helpers.ButtonHelperCommanders;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class MirvedaAgent extends TargetedAgent {

    public static final String ID = "mirvedaagent";

    public static String buttonId(Player owner, Player target) {
        return AgentButtonIds.formatOwned(owner, ID, target.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Logic Machina, the Mirveda";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        if (target.getStrategicCC() < 1) {
            return AgentOutcome.of(Message.inPressedChannel(
                    "Target does not have any command tokens in their strategy pool, and so nothing has happend."));
        }
        target.setStrategicCC(target.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(
                target, use.game(), use.event(), "used " + FactionEmojis.mirveda + " Logic Machina");
        return AgentOutcome.of(
                Message.to(
                        target,
                        target.getRepresentationUnfogged()
                                + ", 1 command token has been removed from your strategy pool due to use of "
                                + use.agentName() + ". You may add it back if you didn't agree to the agent."),
                Message.withButtons(
                        target,
                        target.getRepresentationUnfogged()
                                + ", please research a technology of a color which matches one of the prerequisites on the unit upgrade you just gained.",
                        List.of(Buttons.GET_A_TECH, Buttons.red("deleteButtons", "Delete This"))));
    }
}
