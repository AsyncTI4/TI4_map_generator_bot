package ti4.commands.leaders;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
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
        new HeroUnplay()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.LEADERS;
    }

    @Override
    public String getDescription() {
        return "Leaders";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
            CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
