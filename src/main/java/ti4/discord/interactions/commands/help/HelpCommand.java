package ti4.discord.interactions.commands.help;

import java.nio.file.Files;
import java.nio.file.Paths;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.ResourceHelper;
import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class HelpCommand extends AbstractParentCommand {

    public HelpCommand() {
        super(
                Constants.HELP,
                "Help",
                new HelpAction(),
                new HowToMoveUnits(),
                new SampleColors(),
                new SampleDecals(),
                new WhatsTIGL(),
                new HelpTextSubcommand("absol", "Show Helpful Absol stuff", "Absol.txt"),
                new HelpTextSubcommand("monuments", "Show Helpful Monuments stuff", "Monuments.txt"),
                new HelpTextSubcommand("discordant_stars", "Display DS Help Text", "DS.txt"),
                new NewPlayerInfo(),
                new HelpTextSubcommand(
                        "how_to_play_ti4", "Display some helpful stuff for learning TI4", "HowToPlayTI4.txt"),
                new HelpTextSubcommand("fow", "Explain Fog of War", "FoWHelp.txt"),
                new FoWPlusHelp());
    }

    static void showHelpText(GenericInteractionCreateEvent event, String helpFileName) {
        String path = ResourceHelper.getHelpFile(helpFileName);
        try {
            String message = Files.readString(Paths.get(path));
            MessageHelper.sendMessageToEventChannel(event, message);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "HELP FILE " + helpFileName + " IS BLANK");
        }
    }
}
