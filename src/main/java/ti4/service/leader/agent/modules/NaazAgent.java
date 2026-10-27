package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SelfOrTargetedAgent;

public final class NaazAgent extends SelfOrTargetedAgent {

    static final String ID = "naazagent";

    public NaazAgent() {
        super(ID, "Garv and Gunn, the Naaz-Rokha");
    }

    public static Button button(Player naaz) {
        return Buttons.green(
                ownedButtonId(naaz, ID, naaz.getFaction()),
                useLabel(naaz, ID, "Naaz-Rokha Agents"),
                FactionEmojis.Naaz);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player explorer = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        explorer,
                        ", please choose which planet you wish to explore.",
                        ButtonHelper.getButtonsToExploreAllPlanets(explorer, use.game())),
                Message.addressedTo(explorer, " has no planets to explore."));
    }
}
