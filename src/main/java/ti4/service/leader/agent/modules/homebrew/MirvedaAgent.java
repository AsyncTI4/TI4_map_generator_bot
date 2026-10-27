package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
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

    static final String ID = "mirvedaagent";

    public MirvedaAgent() {
        super(ID, "Logic Machina, the Mirveda");
    }

    public static Button button(Player mirveda) {
        return Buttons.gray(
                AgentButtonIds.format(ID, mirveda.getFaction()), "Use Mirveda Agent", FactionEmojis.mirveda);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        if (target.getStrategicCC() < 1) {
            return AgentOutcome.of(Message.inPressedChannel(
                    "Target does not have any command tokens in their strategy pool, and so nothing has happened."));
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
