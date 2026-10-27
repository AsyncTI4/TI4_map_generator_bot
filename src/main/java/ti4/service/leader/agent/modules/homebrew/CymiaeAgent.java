package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class CymiaeAgent extends TargetedAgent {

    static final String ID = "cymiaeagent";

    public CymiaeAgent() {
        super(ID, "Skhot Unit X-12, the Cymiae");
    }

    public static Button button(Player cymiae) {
        return Buttons.gray(AgentButtonIds.format(ID, cymiae.getFaction()), "Use Cymiae Agent", FactionEmojis.cymiae);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        ActionCardHelper.drawActionCards(use.payload(), 1);
        return AgentOutcome.none();
    }
}
