package ti4.commands.cardsac;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;

public class ACCardsCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
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
            new SentAC(),
            new SentACRandom(),
            new DrawSpecificAC(),
            new MakeCopiesOfACs());

    @Override
    public String getActionId() {
        return Constants.CARDS_AC;
    }

    @Override
    public String getActionDescription() {
        return "Action Cards";
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
