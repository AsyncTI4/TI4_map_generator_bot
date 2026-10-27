package ti4.service.leader.agent.modules;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class XxchaAgent extends TargetedAgent {

    static final String ID = "xxchaagent";

    public XxchaAgent() {
        super(ID, "Ggrocuto Rinn, the Xxcha");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player xxcha = use.user();
        Player planetOwner = use.payload();
        List<Button> planets = planetOwner.getExhaustedPlanets().stream()
                .map(planet -> Buttons.gray(
                        xxcha.factionButtonChecker() + "khraskHeroStep4Ready_" + planetOwner.getFaction() + "_"
                                + planet,
                        Helper.getPlanetRepresentation(planet, use.game())))
                .toList();
        return AgentOutcome.offer(
                Message.addressedTo(xxcha, ", please choose the planet you wish to ready.", planets),
                Message.addressedTo(xxcha, ", there is no exhausted planet to ready."));
    }
}
