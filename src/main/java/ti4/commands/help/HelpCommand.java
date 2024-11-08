package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class HelpCommand implements Command {

    private final Collection<HelpSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.HELP;
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
        subcommands.add(new HowToMoveUnits());
        subcommands.add(new SampleColors());
        subcommands.add(new SampleDecals());
        subcommands.add(new WhatsTIGL());
        subcommands.add(new Absol());
        subcommands.add(new Monuments());
        subcommands.add(new DiscordantStars());
        subcommands.add(new NewPlayerInfo());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
