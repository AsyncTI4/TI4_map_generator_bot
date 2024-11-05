package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class PlayerCommand implements Command {

    private final Collection<PlayerSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.PLAYER;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (event.getName().equals(getActionID())) {
            String userID = event.getUser().getId();
            GameManager gameManager = GameManager.getInstance();
            if (!gameManager.isUserWithActiveGame(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Game userActiveGame = gameManager.getUserActiveGame(userID);
            if (!userActiveGame.getPlayerIDs().contains(userID) && !userActiveGame.isCommunityMode()) {
                MessageHelper.replyToMessage(event,
                    "You're not a player of the game, please call function /join gameName");
                return false;
            }
            return true;
        }
        return false;
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
        String userID = event.getUser().getId();
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(game, event);

        MapRenderPipeline.renderToWebsiteOnly(game, event);
    }

    protected String getActionDescription() {
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
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
