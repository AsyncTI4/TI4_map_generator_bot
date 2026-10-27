package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class PharadnAgent extends PlayerPickerAgent {

    static final String ID = "pharadnagent";

    public PharadnAgent() {
        super(ID, "Nokash, the Pharadn");
    }

    public static Button button(Player pharadn) {
        return useButton(pharadn, ID, "Pharadn Agent", FactionEmojis.pharadn);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> planets = target.getPlanetsAllianceMode().stream()
                .map(planet -> Buttons.gray(
                        target.factionButtonChecker() + "pharadnAgentKill_" + planet,
                        Helper.getPlanetRepresentation(planet, use.game())))
                .toList();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target, ", please choose the planet that you wish to destroy all your infantry on.", planets),
                use.noteToUser(target, " controls no planets to destroy infantry on."));
    }
}
