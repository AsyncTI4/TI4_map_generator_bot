package ti4.service.leader.genome;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.modules.JolNarAgent;

public final class ExperimentalGenome extends LeaningGenome<Player> {

    static final String ID = "experimentalagent";

    public ExperimentalGenome() {
        super(ID, "Experimental Genome", new JolNarAgent());
    }

    public static Button button(Player player) {
        return useButton(player, ID, "Experimental Genome", FactionEmojis.Jolnar);
    }
}
