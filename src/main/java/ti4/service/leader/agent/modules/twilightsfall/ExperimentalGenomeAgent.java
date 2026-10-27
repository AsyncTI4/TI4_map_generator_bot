package ti4.service.leader.agent.modules.twilightsfall;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentPrompts;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SelfOrTargetedAgent;

public final class ExperimentalGenomeAgent extends SelfOrTargetedAgent {

    static final String ID = "experimentalagent";

    public ExperimentalGenomeAgent() {
        super(ID, "Experimental Genome");
    }

    public static Button button(Player player) {
        return Buttons.gray(
                ownedButtonId(player, ID), useLabel(player, ID, "Experimental Genome"), FactionEmojis.Jolnar);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.of(AgentPrompts.removeInfantry(use.game(), use.payload()));
    }
}
