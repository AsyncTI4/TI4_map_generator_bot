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

public final class MentakAgent extends TargetedAgent {

    public static final String ID = "mentakagent";

    static String buttonId(Player owner, Player pillaged) {
        return AgentButtonIds.formatOwned(owner, ID, pillaged.getFaction());
    }

    public static Button offer(Player owner, Player pillaged) {
        return Buttons.green(
                buttonId(owner, pillaged), AgentNames.offerVerb(owner, ID) + "Mentak Agent", FactionEmojis.Mentak);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Suffi An, the Mentak";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        ActionCardHelper.drawActionCards(use.user(), 1);
        ActionCardHelper.drawActionCards(use.payload(), 1);
        return AgentOutcome.none();
    }
}
