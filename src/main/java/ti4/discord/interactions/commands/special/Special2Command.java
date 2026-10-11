package ti4.discord.interactions.commands.special;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class Special2Command extends AbstractParentCommand {

    public Special2Command() {
        super(
                Constants.SPECIAL + "2",
                "More special commands",
                new SetupNeutralPlayer(),
                new GeneratePainBoxMapString(),
                new SearchWinningPath(),
                new SetExpedition(),
                new Galvanize(),
                new LoreCommand(),
                new Skulls(),
                new ImportDeckConfig());
    }
}
