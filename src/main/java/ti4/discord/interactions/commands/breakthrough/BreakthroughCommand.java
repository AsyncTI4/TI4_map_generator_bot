package ti4.discord.interactions.commands.breakthrough;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class BreakthroughCommand extends AbstractParentCommand {

    public BreakthroughCommand() {
        super(
                Constants.BREAKTHROUGH,
                "Breakthrough handling",
                new BreakthroughActivate(),
                new BreakthroughUnlock(),
                new BreakthroughLock(),
                new BreakthroughReady(),
                new BreakthroughExhaust(),
                new BreakthroughInfo(),
                new BreakthroughSetTg());
    }
}
