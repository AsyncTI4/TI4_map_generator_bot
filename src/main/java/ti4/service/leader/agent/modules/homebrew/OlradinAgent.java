package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class OlradinAgent extends TargetedAgent {

    static final String ID = "olradinagent";
    private static final String READY_PREFIX = "refreshWithOlradinAgent_";

    public OlradinAgent() {
        super(ID, "Baggil Wildpaw, the Olradin");
    }

    public static Button button(Player olradin) {
        return Buttons.gray(
                ownedButtonId(olradin, ID, olradin.getFaction()),
                useLabel(olradin, ID, "Olradin Agent"),
                FactionEmojis.olradin);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> planets = target.getExhaustedPlanets().stream()
                .map(planet -> Buttons.green(
                        target.factionButtonChecker() + AgentStepIds.flagCopy(READY_PREFIX + planet, use.viaYssaril()),
                        "Ready " + Helper.getPlanetRepresentation(planet, use.game())))
                .toList();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", please choose the planet, of a __different__ trait from the one you just exhausted,"
                                + " that you wish to ready.",
                        planets),
                Message.addressedTo(target, " has no exhausted planet to ready."));
    }
}
