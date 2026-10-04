package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentNames;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class CymiaeAgent extends TargetedAgent {

    public static final String ID = "cymiaeagent";

    static String buttonId(Player owner, Player target) {
        return AgentButtonIds.formatOwned(owner, ID, target.getFaction());
    }

    public static Button offer(Player owner) {
        return Buttons.gray(
                buttonId(owner, owner), AgentNames.offerVerb(owner, ID) + "Cymiae Agent", FactionEmojis.cymiae);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Skhot Unit X-12, the Cymiae";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        ActionCardHelper.drawActionCards(use.payload(), 1);
        return AgentOutcome.none();
    }
}
