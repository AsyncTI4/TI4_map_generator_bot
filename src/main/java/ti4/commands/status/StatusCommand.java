package ti4.commands.status;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class StatusCommand implements Command {

    private final Collection<StatusSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getName() {
        return Constants.STATUS;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getName(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        StatusSubcommandData executedCommand = null;
        for (StatusSubcommandData subcommand : subcommandData) {
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
        reply(event, null);
    }

    public static void reply(SlashCommandInteractionEvent event, String message) {
        String userID = event.getUser().getId();
        Game game = GameManager.getUserActiveGame(userID);
        GameSaveLoadManager.saveGame(game, event);

        MapRenderPipeline.render(game, event,
                fileUpload -> MessageHelper.replyToMessage(event, fileUpload, false, message, message != null));
    }

    protected String getActionDescription() {
        return "Status phase";
    }

    private Collection<StatusSubcommandData> getSubcommands() {
        Collection<StatusSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new Cleanup());
        subcommands.add(new PersonalCleanup());
        subcommands.add(new RevealStage1());
        subcommands.add(new RevealStage2());
        subcommands.add(new ShufflePublicBack());
        subcommands.add(new ScorePublic());
        subcommands.add(new PeekPublicObjectiveDeck());
        subcommands.add(new UnscorePublic());
        subcommands.add(new AddCustomPO());
        subcommands.add(new RemoveCustomPO());
        subcommands.add(new SCTradeGoods());
        subcommands.add(new ListTurnOrder());
        subcommands.add(new ListTurnStats());
        subcommands.add(new ListDiceLuck());
        subcommands.add(new ListSpends());
        subcommands.add(new MarkFollowed());
        subcommands.add(new POInfo());
        return subcommands;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
