package ti4.service.leader.genome;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class ResearchGenome extends Genome<Player> {

    static final String ID = "researchagent";
    static final String SPLICE_KEY_PREFIX = "researchagentSplice";

    public ResearchGenome() {
        super(ID, "Research Genome");
    }

    public static Button button(Player player) {
        return useButton(player, ID, "Research Genome", FactionEmojis.Deepwrought);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Player> use) {
        return " has used " + use.agentName() + " to add 3 cards to the draft.";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        use.game().setStoredValue(SPLICE_KEY_PREFIX + use.user().getFaction(), "Yes");
        return AgentOutcome.none();
    }
}
