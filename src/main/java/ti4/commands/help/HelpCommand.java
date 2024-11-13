package ti4.commands.help;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class HelpCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new HelpAction(),
            new HowToMoveUnits(),
            new SampleColors(),
            new SampleDecals(),
            new WhatsTIGL(),
            new Absol(),
            new Monuments(),
            new DiscordantStars(),
            new NewPlayerInfo()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.HELP;
    }

    @Override
    public String getDescription() {
        return "Help";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
