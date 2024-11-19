package ti4.commands2.event;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class EventCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new DrawEvent(),
                    new PutEventTop(),
                    new PutEventBottom(),
                    new LookAtTopEvent(),
                    new LookAtBottomEvent(),
                    new RevealEvent(),
                    new RevealSpecificEvent(),
                    new AddEvent(),
                    new RemoveEvent(),
                    new ShowDiscardedEvents(),
                    new ShuffleEvents(),
                    new ResetEvents(),
                    new PutDiscardBackIntoDeckEvents(),
                    new EventInfo(),
                    new PlayEvent())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.EVENT;
    }

    @Override
    public String getDescription() {
        return "Event handling";
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
