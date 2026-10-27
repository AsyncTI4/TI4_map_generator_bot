package ti4.service.leader.agent.modules.homebrew.twilightkart;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.modules.YinAgent;

public final class SplittingGenomeAgent extends AgentModule<SplittingGenomeAgent.Replacement> {

    static final String ID = "tknovasplittingagent";

    public SplittingGenomeAgent() {
        super(ID, "Splitting Genome");
    }

    record Replacement(Tile system, Player owner) {}

    public static Button button(Player holder, String position) {
        return Buttons.gray(
                ownedButtonId(holder, ID, position), useLabel(holder, ID, "Splitting Genome"), FactionEmojis.Yin);
    }

    @Override
    public Optional<Replacement> decode(Game game, Player user, String payload) {
        Tile system = game.getTileByPosition(StringUtils.substringBefore(payload, "_"));
        if (system == null) {
            return Optional.empty();
        }
        return AgentTargets.playerOrUser(game, user, StringUtils.substringAfter(payload, "_"))
                .map(owner -> new Replacement(system, owner));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Replacement> use) {
        Player owner = use.payload().owner();
        return AgentOutcome.of(Message.addressedTo(
                owner,
                ", use buttons to resolve " + use.agentNameFor(owner) + ".",
                YinAgent.placementButtons(use.game(), owner, use.payload().system(), true)));
    }
}
