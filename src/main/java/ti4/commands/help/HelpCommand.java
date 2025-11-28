package ti4.commands.help;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.commands.SuspicionLevel;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

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
                    new NewPlayerInfo(),
                    new HowToPlayTI4(),
                    new FoWHelp(),
                    new FoWPlusHelp())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

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

    @Override
    public SuspicionLevel getSuspicionLevel(SlashCommandInteractionEvent event) {
        return SuspicionLevel.NONE;
    }

    public static void showHelpText(GenericInteractionCreateEvent event, String helpFileName) {
        String path = ResourceHelper.getInstance().getHelpFile(helpFileName);
        try {
            String message = Files.readString(Paths.get(path));
            MessageHelper.sendMessageToEventChannel(event, message);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "HELP FILE " + helpFileName + " IS BLANK");
        }
    }
}
