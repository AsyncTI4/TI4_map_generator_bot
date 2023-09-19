package ti4.commands.help;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class HelpCommand implements Command {

    private final Collection<HelpSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.HELP;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void logBack(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userName = user.getName();
        Game userActiveGame = GameManager.getInstance().getUserActiveGame(user.getId());
        String activeGame = "";
        if (userActiveGame != null) {
            activeGame = "Active map: " + userActiveGame.getName();
        }
        String commandExecuted = "User: " + userName + " executed command. " + activeGame + "\n" +
                event.getName() + " " +  event.getInteraction().getSubcommandName() + " " + event.getOptions().stream()
                .map(option -> option.getName() + ":" + getOptionValue(option))
                .collect(Collectors.joining(" "));

        MessageHelper.sendMessageToChannel(event.getChannel(), commandExecuted);
    }

    private String getOptionValue(OptionMapping option) {
        if (option.getName().equals(Constants.PLAYER)){
            return option.getAsUser().getName();
        }
        return option.getAsString();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        HelpSubcommandData executedCommand = null;
        for (HelpSubcommandData subcommand : subcommandData) {
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
    }


    protected String getActionDescription() {
        return "Help";
    }

    private Collection<HelpSubcommandData> getSubcommands() {
        Collection<HelpSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new HelpAction());
        subcommands.add(new SetupTemplatesAction());
        subcommands.add(new ListAbilities());
        subcommands.add(new ListGames());
        subcommands.add(new ListPlanets());
        subcommands.add(new ListTiles());
        subcommands.add(new ListUnits());
        subcommands.add(new ListCommands());
        subcommands.add(new ListMyGames());
        subcommands.add(new ListAgendas());
        subcommands.add(new ListSecretObjectives());
        subcommands.add(new ListPublicObjectives());
        subcommands.add(new ListRelics());
        subcommands.add(new ListActionCards());
        subcommands.add(new ListTechs());
        subcommands.add(new ListLeaders());
        subcommands.add(new ListPromissoryNotes());
        subcommands.add(new HowToMoveUnits());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
