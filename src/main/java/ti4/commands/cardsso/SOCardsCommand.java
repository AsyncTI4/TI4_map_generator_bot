package ti4.commands.cardsso;

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
import ti4.map.UserGameContextManager;

public class SOCardsCommand implements Command {

    private final Collection<SOCardsSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionId() {
        return Constants.CARDS_SO;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getActionId(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String gameName = UserGameContextManager.getContextGame(userId);
        Game game = GameManager.getGame(gameName);

        String subcommandName = event.getInteraction().getSubcommandName();
        for (SOCardsSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                break;
            }
        }

        GameSaveLoadManager.saveGame(game, event);
    }

    protected String getActionDescription() {
        return "Secret Objectives";
    }

    private Collection<SOCardsSubcommandData> getSubcommands() {
        Collection<SOCardsSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DrawSO());
        subcommands.add(new DiscardSO());
        subcommands.add(new SOInfo());
        subcommands.add(new ShowSO());
        subcommands.add(new ShowSOToAll());
        subcommands.add(new ScoreSO());
        subcommands.add(new DealSO());
        subcommands.add(new UnscoreSO());
        subcommands.add(new ShowAllSO());
        subcommands.add(new ShowAllSOToAll());
        subcommands.add(new ShowRandomSO());
        subcommands.add(new DealSOToAll());
        subcommands.add(new DrawSpecificSO());
        subcommands.add(new ShowUnScoredSOs());
        subcommands.add(new ListAllScored());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionId(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
