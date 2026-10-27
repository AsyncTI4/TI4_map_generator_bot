package ti4.service.leader.agent;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.emoji.TI4Emoji;

public final class ExhaustOnlyAgent extends AgentModule<ExhaustOnlyAgent.Choice> {

    public record Choice(Optional<Player> target) {}

    private final String buttonLabel;
    private final TI4Emoji emoji;

    public ExhaustOnlyAgent(String agentId, String displayName, String buttonLabel, TI4Emoji emoji) {
        super(agentId, displayName);
        this.buttonLabel = buttonLabel;
        this.emoji = emoji;
    }

    public Button button(Player owner) {
        return Buttons.gray(ownedButtonId(owner, agentId()), useLabel(owner, agentId(), buttonLabel), emoji);
    }

    @Override
    public Optional<Choice> decode(Game game, Player user, String payload) {
        return Optional.of(new Choice(AgentTargets.player(game, payload)));
    }

    @Override
    public String exhaustAnnouncement(AgentUse<Choice> use) {
        String onTarget = use.payload()
                .target()
                .filter(target -> target != use.user())
                .map(target -> " on " + target.getRepresentationNoPing())
                .orElse("");
        return " has exhausted " + use.agentName() + onTarget + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Choice> use) {
        return AgentOutcome.none();
    }
}
