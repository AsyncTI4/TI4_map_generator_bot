package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentNames;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class AugersAgent extends TargetedAgent {

    public static final String ID = "augersagent";
    private static final int TRADE_GOODS_GAINED = 2;

    static String buttonId(Player owner, Player explorer) {
        return AgentButtonIds.formatOwned(owner, ID, explorer.getFaction());
    }

    public static Button offer(Player owner, Player explorer) {
        return Buttons.green(
                buttonId(owner, explorer),
                AgentNames.offerVerb(owner, ID) + "Ilyxum Agent on " + explorer.getColor(),
                FactionEmojis.augers);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Clodho, the Ilyxum";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        int oldTg = target.getTg();
        target.setTg(oldTg + TRADE_GOODS_GAINED);

        AgentOutcome outcome = AgentOutcome.of(Message.to(
                target,
                target.getFactionEmojiOrColor() + " gained " + TRADE_GOODS_GAINED + " trade goods from "
                        + use.agentName() + ", being used (" + oldTg + "->" + target.getTg() + ")."));
        if (use.game().isFowMode()) {
            outcome = outcome.and(Message.to(
                    use.user(),
                    target.getFactionEmojiOrColor() + " gained " + TRADE_GOODS_GAINED
                            + " trade goods due to agent usage."));
        }

        ButtonHelperAbilities.pillageCheck(target, use.game());
        ButtonHelperAgents.resolveArtunoCheck(target, TRADE_GOODS_GAINED);
        return outcome;
    }
}
