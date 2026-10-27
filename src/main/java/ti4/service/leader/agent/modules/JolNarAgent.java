package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentPrompts;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SelfOrTargetedAgent;

public final class JolNarAgent extends SelfOrTargetedAgent {

    static final String ID = "jolnaragent";

    public JolNarAgent() {
        super(ID, "Doctor Sucaban, the Jol-Nar");
    }

    public static Button button(Player jolNar) {
        return Buttons.gray(ownedButtonId(jolNar, ID), useLabel(jolNar, ID, "Jol-Nar Agent"), FactionEmojis.Jolnar);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.of(AgentPrompts.removeInfantry(use.game(), use.payload()));
    }
}
