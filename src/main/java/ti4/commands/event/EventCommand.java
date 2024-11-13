package ti4.commands.event;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class EventCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
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
            new PlayEvent());

    @Override
    public String getActionId() {
        return Constants.EVENT;
    }

    @Override
    public String getActionDescription() {
        return "Event handling";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
