package ti4.commands.breakthrough;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

public class BreakthroughUnlock extends GameStateSubcommand {
    public BreakthroughUnlock() {
        super(Constants.BREAKTHROUGH_UNLOCK, "Unlock breakthrough", true, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        BreakthroughCommandHelper.unlockBreakthrough(getGame(), getPlayer());
    }
}
