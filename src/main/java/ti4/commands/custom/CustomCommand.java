package ti4.commands.custom;

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

public class CustomCommand implements Command {

    private final Collection<CustomSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.CUSTOM;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionID(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        CustomSubcommandData executedCommand = null;
        for (CustomSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }
        if (executedCommand == null) {
            reply(event);
        } else {
            executedCommand.reply(event);
        }
    }

    public static void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = UserGameContextManager.getContextGame(userID);
        GameSaveLoadManager.saveGame(game, event);
    }

    protected String getActionDescription() {
        return "Custom";
    }

    private Collection<CustomSubcommandData> getSubcommands() {
        Collection<CustomSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new SoRemoveFromGame());
        subcommands.add(new SoAddToGame());
        subcommands.add(new AgendaRemoveFromGame());
        subcommands.add(new ACRemoveFromGame());
        subcommands.add(new SCAddToGame());
        subcommands.add(new SCRemoveFromGame());
        subcommands.add(new PoRemoveFromGame());
        subcommands.add(new DiscardSpecificAgenda());
        // subcommands.add(new FixSODeck());
        subcommands.add(new SetThreadName());
        subcommands.add(new PeekAtObjectiveDeck());
        subcommands.add(new PeekAtStage1());
        subcommands.add(new PeekAtStage2());
        subcommands.add(new SetUpPeakableObjectives());
        subcommands.add(new SwapStage1());
        subcommands.add(new ShuffleBackInUnrevealedObj());
        subcommands.add(new SwapStage2());
        subcommands.add(new RevealSpecificStage1());
        subcommands.add(new RevealSpecificStage2());
        subcommands.add(new SpinTilesInRings());
        subcommands.add(new OfferAutoPassOptions());
        subcommands.add(new OfferAFKTimeOptions());
        subcommands.add(new ChangeToBaseGame());
        subcommands.add(new CustomizationOptions());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
