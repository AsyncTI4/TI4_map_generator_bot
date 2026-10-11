package ti4.discord.interactions.commands.leaders;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class LeaderCommand extends AbstractParentCommand {

    public LeaderCommand() {
        super(
                Constants.LEADERS,
                "Leaders",
                new LeaderInfo(),
                new UnlockLeader(),
                new LockLeader(),
                new RefreshLeader(),
                new ExhaustLeader(),
                new PurgeLeader(),
                new ResetLeader(),
                new HeroPlay(),
                new HeroUnplay());
    }
}
