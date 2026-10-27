package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class KortaliAgent extends TargetedAgent {

    static final String ID = "kortaliagent";

    public KortaliAgent() {
        super(ID, "Queen Lucreia, the Kortali");
    }

    public static Button button(Player kortali, Player opponent) {
        return Buttons.gray(
                kortali.factionButtonChecker() + AgentButtonIds.format(ID, opponent.getColor()),
                "Use Kortali Agent",
                FactionEmojis.kortali);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        Player kortali = use.user();
        List<String> fragments = target.getFragments();
        if (fragments.isEmpty()) {
            return AgentOutcome.of(
                    Message.to(kortali, target.getFactionEmojiOrColor() + " has no relic fragments to take."));
        }
        String fragment = fragments.get(ThreadLocalRandom.current().nextInt(fragments.size()));
        target.removeFragment(fragment);
        kortali.addFragment(fragment);

        ExploreModel card = Mapper.getExplore(fragment);
        String fragmentName = card == null ? fragment : card.getName();
        String message = target.getFactionEmojiOrColor() + " lost a " + fragmentName + " to "
                + kortali.getFactionEmojiOrColor() + " due to " + use.agentName() + ".";
        return AgentOutcome.notifyInFog(use.game(), target, message, kortali);
    }
}
