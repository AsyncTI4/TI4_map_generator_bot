package ti4.commands.player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

public class PlayerCommand implements ParentCommand {

    private final Collection<PlayerSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getName() {
        return Constants.PLAYER;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return CommandHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        PlayerSubcommandData executedCommand = null;
        for (PlayerSubcommandData subcommand : subcommandData) {
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
        String gameName = CommandHelper.getGameName(event);
        Game game = GameManager.getGame(gameName);
        GameSaveLoadManager.saveGame(game, event);

        MapRenderPipeline.renderToWebsiteOnly(game, event);
    }

    public String getDescription() {
        return "Player";
    }

    private Collection<PlayerSubcommandData> getSubcommands() {
        Collection<PlayerSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new Stats());
        subcommands.add(new Setup());
        subcommands.add(new SCPlay());
        subcommands.add(new SCUnplay());
        subcommands.add(new Pass());
        subcommands.add(new AbilityInfo());
        subcommands.add(new TurnEnd());
        subcommands.add(new TurnStart());
        subcommands.add(new SCPick());
        subcommands.add(new SCUnpick());
        subcommands.add(new Speaker());
        subcommands.add(new SendTG());
        subcommands.add(new SendCommodities());
        subcommands.add(new SendDebt());
        subcommands.add(new ClearDebt());
        subcommands.add(new ChangeColor());
        subcommands.add(new CorrectFaction());
        subcommands.add(new ChangeUnitDecal());
        subcommands.add(new UnitInfo());
        subcommands.add(new AddAllianceMember());
        subcommands.add(new RemoveAllianceMember());
        subcommands.add(new AddTeamMate());
        subcommands.add(new RemoveTeamMate());
        subcommands.add(new SetStatsAnchor());
        subcommands.add(new CCsButton());
        return subcommands;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), getDescription())
                .addSubcommands(getSubcommands()));
    }
}
