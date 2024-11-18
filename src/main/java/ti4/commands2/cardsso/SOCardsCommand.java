package ti4.commands2.cardsso;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class SOCardsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
                    new ListAllScored())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.CARDS_SO;
    }

    @Override
    public String getDescription() {
        return "Secret Objectives";
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
