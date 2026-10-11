package ti4.discord.interactions.commands.tf;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class TwilightFallCommand extends AbstractParentCommand {

    public TwilightFallCommand() {
        super(
                Constants.TF_COMMAND,
                "Twilights Fall Commands",
                DrawRandomSpliceCard.ability(),
                DrawRandomSpliceCard.unit(),
                DrawRandomSpliceCard.genome(),
                new DrawSpecificParadigm(),
                new DrawRandomParadigm(),
                new RadicalAdvancement(),
                new FixColors(),
                new GuildAgents(),
                new ReverseSplice(),
                new StartNewSplice(),
                new AddToSplice(),
                new DoEdictPhase(),
                new SetupStartingFleet(),
                new DiscardVeiledCard(),
                new DoVeiledCardAction());
    }
}
