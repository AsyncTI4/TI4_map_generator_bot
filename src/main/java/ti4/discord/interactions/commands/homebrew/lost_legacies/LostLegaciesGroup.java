package ti4.discord.interactions.commands.homebrew.lost_legacies;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.SubcommandGroup;

public class LostLegaciesGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new RevLeaderFix(),
                    new RelicTradingHub(),
                    new ViewStonebornFragments(),
                    new ViewPurgedStonebornFragments(),
                    new HydromorphicDraw(),
                    new HydromorphicDiscard(),
                    new HydromorphicSend(),
                    new HydromorphicDrawSpecific(),
                    new AddFragmentumToken(),
                    new RemoveFragmentumToken(),
                    new StonebornUnleash())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public LostLegaciesGroup() {
        super("lost_legacies", "Lost Legacies commands");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }
}
