package ti4.commands.cardsso;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class SOCardsCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new DrawSO(),
            new DiscardSO(),
            new SOInfo(),
            new ShowSO(),
            new ShowSOToAll(),
            new ScoreSO(),
            new DealSO(),
            new UnscoreSO(),
            new ShowAllSO(),
            new ShowAllSOToAll(),
            new ShowRandomSO(),
            new DealSOToAll(),
            new DrawSpecificSO(),
            new ShowUnScoredSOs(),
            new ListAllScored());

    @Override
    public String getActionId() {
        return Constants.CARDS_SO;
    }

    @Override
    public String getActionDescription() {
        return "Secret Objectives";
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
