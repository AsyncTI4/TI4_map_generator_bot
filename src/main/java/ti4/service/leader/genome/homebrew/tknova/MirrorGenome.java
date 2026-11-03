package ti4.service.leader.genome.homebrew.tknova;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.ExhaustOnlyAgent;
import ti4.service.leader.agent.modules.ExhaustOnlyAgents;
import ti4.service.leader.genome.LeaningGenome;

public final class MirrorGenome extends LeaningGenome<ExhaustOnlyAgent.Choice> {

    static final String ID = "tknovamirroragent";

    public MirrorGenome() {
        super(ID, "Mirror Genome", ExhaustOnlyAgents.FIRMAMENT);
    }

    public static Button button(Player holder) {
        return useButton(holder, ID, "Mirror Genome", FactionEmojis.Firmament);
    }
}
