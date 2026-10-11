package ti4.discord.interactions.commands.async;

import ti4.discord.interactions.commands.AbstractParentCommand;

public class AsyncCommand extends AbstractParentCommand {

    public AsyncCommand() {
        super(
                "async",
                "Random Async fun stuff, like Twilight Imperium Global League (TIGL) and Tournaments",
                new ShowHeroes(),
                new ShowTourneyWinners());
    }
}
