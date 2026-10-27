package ti4.service.leader.agent.modules;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAgents;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class JolNarAgent extends AgentModule<Player> {

    static final String ID = "jolnaragent";

    public JolNarAgent() {
        super(ID, "Doctor Sucaban, the Jol-Nar");
    }

    public static Button button(Player jolNar) {
        return Buttons.gray(ownedButtonId(jolNar, ID), useLabel(jolNar, ID, "Jol-Nar Agent"), FactionEmojis.Jolnar);
    }

    public static Message offerInfantryRemoval(Game game, Player target) {
        return Message.withButtons(
                target,
                target.getRepresentationUnfogged() + ", you may use the buttons to remove infantry.",
                ButtonHelperAgents.getJolNarAgentButtons(target, game));
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.of(offerInfantryRemoval(use.game(), use.payload()));
    }
}
