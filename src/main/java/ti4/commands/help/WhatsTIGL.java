package ti4.commands.help;

import java.nio.file.Files;
import java.nio.file.Paths;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;

class WhatsTIGL extends Subcommand {

    public WhatsTIGL() {
        super("what_is_tigl", "Quick description of what TIGL is");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String path = ResourceHelper.getInstance().getHelpFile("WhatsTIGL.txt");
        try {
            String message = new String(Files.readAllBytes(Paths.get(path)));
            MessageHelper.sendMessageToEventChannel(event, message);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "See Pins: https://discord.com/channels/943410040369479690/1003741148017336360");
        }
    }
}
