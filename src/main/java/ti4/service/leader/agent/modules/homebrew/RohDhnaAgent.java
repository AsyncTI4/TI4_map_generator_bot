package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentPrompts;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class RohDhnaAgent extends TargetedAgent {

    static final String ID = "rohdhnaagent";

    public RohDhnaAgent() {
        super(ID, "Rond Bri'ay, the Roh'Dhna");
    }

    public static Button button(Player rohDhna) {
        return Buttons.red(
                ownedButtonId(rohDhna, ID, rohDhna.getFaction()),
                useLabel(rohDhna, ID, "Roh'Dhna Agent"),
                FactionEmojis.rohdhna);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        target.addSpentThing("Exhausted " + use.agentNameFor(target) + ", for 1 command token.");
        return AgentOutcome.of(AgentPrompts.gainCommandTokens(use.game(), target));
    }
}
