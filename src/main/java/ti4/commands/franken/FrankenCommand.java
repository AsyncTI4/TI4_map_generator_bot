package ti4.commands.franken;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class FrankenCommand implements Command {

    private final Collection<FrankenSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.FRANKEN;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        User user = event.getUser();
        String userID = user.getId();
        if (Objects.equals(event.getInteraction().getSubcommandName(), Constants.FRANKEN_EDIT)) {
            Member member = event.getMember();
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.bothelperRoles) {
                if (roles.contains(role)) {
                    return true;
                }
            }
        }

        if (!GameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return false;
        }
        Game userActiveGame = GameManager.getUserActiveGame(userID);
        if (!userActiveGame.getPlayerIDs().contains(userID) && !userActiveGame.isCommunityMode()) {
            MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
            return false;
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        FrankenSubcommandData executedCommand = null;
        for (FrankenSubcommandData subcommand : subcommandData) {
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
        Game game = GameManager.getUserActiveGame(userID);
        GameSaveLoadManager.saveGame(game, event);
        MessageHelper.replyToMessage(event, "Executed command. Use /show_game to check map");
    }

    protected String getActionDescription() {
        return "Franken";
    }

    private Collection<FrankenSubcommandData> getSubcommands() {
        Collection<FrankenSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new AbilityAdd());
        subcommands.add(new AbilityRemove());
        subcommands.add(new LeaderAdd());
        subcommands.add(new LeaderRemove());
        subcommands.add(new FactionTechAdd());
        subcommands.add(new FactionTechRemove());
        subcommands.add(new PNAdd());
        subcommands.add(new PNRemove());
        subcommands.add(new UnitAdd());
        subcommands.add(new UnitRemove());
        subcommands.add(new StartFrankenDraft());
        subcommands.add(new SetFactionIcon());
        subcommands.add(new SetFactionDisplayName());
        subcommands.add(new FrankenEdit());
        subcommands.add(new ShowFrankenBag());
        subcommands.add(new ShowFrankenHand());
        subcommands.add(new FrankenViewCard());
        subcommands.add(new BanAbility());
        subcommands.add(new ApplyDraftBags());
        subcommands.add(new SetHomeSystemPosition());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        SlashCommandData list = Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands());
        commands.addCommands(list);
    }
}
