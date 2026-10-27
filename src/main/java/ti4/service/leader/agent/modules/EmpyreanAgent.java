package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentPrompts;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SelfOrTargetedAgent;

public final class EmpyreanAgent extends SelfOrTargetedAgent {

    static final String ID = "empyreanagent";

    public EmpyreanAgent() {
        super(ID, "Acamar, the Empyrean");
    }

    public static Button button(Player empyrean) {
        return useButton(empyrean, ID, "Empyrean Agent", FactionEmojis.Empyrean);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.of(AgentPrompts.gainCommandTokens(use.game(), use.payload()));
    }
}
