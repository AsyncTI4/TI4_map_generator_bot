package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class GhotiAgent extends TargetedAgent {

    static final String ID = "ghotiagent";

    public GhotiAgent() {
        super(ID, "Becece, the Ghoti");
    }

    public static Button button(Player ghoti) {
        return Buttons.red(
                ownedButtonId(ghoti, ID, ghoti.getFaction()), useLabel(ghoti, ID, "Ghoti Agent"), FactionEmojis.ghoti);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", please choose how you wish to use " + use.agentName() + ".",
                List.of(
                        Buttons.green("ghotiATG", "Get 1 Trade Good"),
                        Buttons.gray("ghotiAProd", "Produce 2 Additional Units"),
                        Buttons.red("deleteButtons", "Delete This"))));
    }
}
