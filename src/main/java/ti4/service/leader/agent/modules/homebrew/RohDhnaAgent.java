package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class RohDhnaAgent extends TargetedAgent {

    static final String ID = "rohdhnaagent";
    static final String ORIGINAL_TOKENS_KEY_PREFIX = "originalCCsFor";

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
        target.addSpentThing("Exhausted " + use.agentName() + ", for 1 command token.");
        use.game().setStoredValue(ORIGINAL_TOKENS_KEY_PREFIX + target.getFaction(), target.getCCRepresentation());
        return AgentOutcome.of(Message.withButtons(
                target,
                target.getRepresentationUnfogged() + ", your current command tokens are " + target.getCCRepresentation()
                        + ". Use buttons to gain command tokens.",
                ButtonHelper.getGainCCButtons(target)));
    }
}
