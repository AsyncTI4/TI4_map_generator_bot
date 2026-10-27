package ti4.service.leader.agent.modules.twilightsfall;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class ResearchGenomeAgent extends UntargetedAgent {

    static final String ID = "researchagent";
    static final String SPLICE_KEY_PREFIX = "researchagentSplice";

    public ResearchGenomeAgent() {
        super(ID, "Research Genome");
    }

    public static Button button(Player player) {
        return Buttons.gray(
                ownedButtonId(player, ID), useLabel(player, ID, "Research Genome"), FactionEmojis.Deepwrought);
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Player> use) {
        return " has used " + use.agentName() + " to add 3 cards to the draft.";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        use.game().setStoredValue(SPLICE_KEY_PREFIX + use.user().getFaction(), "Yes");
        return AgentOutcome.none();
    }
}
