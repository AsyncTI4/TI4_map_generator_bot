package ti4.discord.interactions.commands.event;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class EventCommand extends AbstractParentCommand {

    public EventCommand() {
        super(
                Constants.EVENT,
                "Event handling",
                new DrawEvent(),
                new PutEventTop(),
                new PutEventBottom(),
                LookAtEvents.top(),
                LookAtEvents.bottom(),
                new RevealEvent(),
                new RevealSpecificEvent(),
                EventInEffect.add(),
                EventInEffect.remove(),
                new ShowDiscardedEvents(),
                new ShuffleEvents(),
                new ResetEvents(),
                new PutDiscardBackIntoDeckEvents(),
                new EventInfo(),
                new PlayEvent());
    }
}
