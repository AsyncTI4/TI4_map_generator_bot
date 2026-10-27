package ti4.service.leader.agent.modules.homebrew;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class TyrisAgent extends UntargetedAgent {

    static final String ID = "tyrisagent";

    public TyrisAgent() {
        super(ID, "Echo-Weaver Tzara, the Tyris");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player tyris = use.user();
        List<Button> buttons = new ArrayList<>(ButtonHelper.getEchoAvailableSystems(use.game(), tyris));
        buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        return AgentOutcome.of(Message.withButtons(
                tyris,
                tyris.getRepresentationUnfogged()
                        + ", use buttons to place a frontier token in a system with no planets.",
                buttons));
    }
}
