package ti4.commands.cardsac;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class ACCardsCommand implements Command {

    private final Collection<ACCardsSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getName() {
        return Constants.CARDS_AC;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getName(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ACCardsSubcommandData subCommandExecuted = null;
        String subcommandName = event.getInteraction().getSubcommandName();
        for (ACCardsSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                subCommandExecuted = subcommand;
                break;
            }
        }
        String userID = event.getUser().getId();
        Game game = GameManager.getUserActiveGame(userID);
        GameSaveLoadManager.saveGame(game, event);
        MessageHelper.replyToMessage(event, "Card action executed: " + (subCommandExecuted != null ? subCommandExecuted.getName() : ""));
    }

    protected String getActionDescription() {
        return "Action Cards";
    }

    private Collection<ACCardsSubcommandData> getSubcommands() {
        Collection<ACCardsSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new ACInfo());
        subcommands.add(new DrawAC());
        subcommands.add(new DiscardAC());
        subcommands.add(new PurgeAC());
        subcommands.add(new DiscardACRandom());
        subcommands.add(new ShowAC());
        subcommands.add(new ShowACToAll());
        subcommands.add(new PlayAC());
        subcommands.add(new ShuffleACDeck());
        subcommands.add(new ShowAllAC());
        subcommands.add(new ShowACRemainingCardCount());
        subcommands.add(new ShowAllUnplayedACs());
        subcommands.add(new PickACFromDiscard());
        subcommands.add(new PickACFromPurged());
        subcommands.add(new ShowDiscardActionCards());
        subcommands.add(new ShowPurgedActionCards());
        subcommands.add(new ShuffleACBackIntoDeck());
        subcommands.add(new RevealAndPutACIntoDiscard());
        subcommands.add(new SentAC());
        subcommands.add(new SentACRandom());
        subcommands.add(new DrawSpecificAC());
        subcommands.add(new MakeCopiesOfACs());
        return subcommands;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
