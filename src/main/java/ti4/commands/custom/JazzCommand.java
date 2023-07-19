package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.MapGenerator;
import ti4.message.MessageHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JazzCommand extends CustomSubcommandData {
    public JazzCommand() {
        super("jazz_command", "Jazz's custom command");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.getUser().getId().equals("228999251328368640")) {
            String jazz = MapGenerator.jda.getUserById("228999251328368640").getAsMention();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz);
            return;
        }

        List<String> myList = Arrays.asList("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26");
        
        Collections.shuffle(myList);

        StringBuilder sb = new StringBuilder();
        for (String s : myList) {
            sb.append(s).append("\n");
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
