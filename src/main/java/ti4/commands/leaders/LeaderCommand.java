package ti4.commands.leaders;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class LeaderCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new LeaderInfo(),
                    new UnlockLeader(),
                    new LockLeader(),
                    new RefreshLeader(),
                    new ExhaustLeader(),
                    new PurgeLeader(),
                    new ResetLeader(),
                    new HeroPlay(),
                    new HeroUnplay())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.LEADERS;
    }

    @Override
    public String getDescription() {
        return "Leaders";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
