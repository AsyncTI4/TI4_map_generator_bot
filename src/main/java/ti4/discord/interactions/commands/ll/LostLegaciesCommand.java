package ti4.discord.interactions.commands.ll;

import ti4.discord.interactions.commands.AbstractParentCommand;

public class LostLegaciesCommand extends AbstractParentCommand {

    public LostLegaciesCommand() {
        super("ll", "Lost Legacies Commands", new RevLeaderFix(), new RelicTradingHub());
    }
}
