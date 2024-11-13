package ti4.commands.cardsac;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class ACCardsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new ACInfo(),
                    new DrawAC(),
                    new DiscardAC(),
                    new PurgeAC(),
                    new DiscardACRandom(),
                    new ShowAC(),
                    new ShowACToAll(),
                    new PlayAC(),
                    new ShuffleACDeck(),
                    new ShowAllAC(),
                    new ShowACRemainingCardCount(),
                    new ShowAllUnplayedACs(),
                    new PickACFromDiscard(),
                    new PickACFromPurged(),
                    new ShowDiscardActionCards(),
                    new ShowPurgedActionCards(),
                    new ShuffleACBackIntoDeck(),
                    new RevealAndPutACIntoDiscard(),
                    new SendAC(),
                    new SentACRandom(),
                    new DrawSpecificAC(),
                    new MakeCopiesOfACs())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.CARDS_AC;
    }

    @Override
    public String getDescription() {
        return "Action Cards";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
                CommandHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
