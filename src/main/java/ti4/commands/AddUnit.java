package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.ResourceHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.StringTokenizer;

public class AddUnit implements Command {

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(Constants.ADD_UNIT);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.replyToMessage(event,"Not implemented yet");
//        if (CreateMap.generateMapInstance == null)
//        {
//            MessageHelper.replyToMessage(event.getMessage(),"Start map creation with :create_map");
//        }
//        else {
//            Message msg = event.getMessage();
//            String message = msg.getContentRaw();
//            StringTokenizer tokenizer = new StringTokenizer(message, " ");
//            if (tokenizer.countTokens() == 3)
//            {
//                String command = tokenizer.nextToken();//Left command parsing as we need to remove it for code
//                String planetTileName = tokenizer.nextToken();
//                String position = tokenizer.nextToken();
//
//                String path = ResourceHelper.getInstance().getUnitFile(planetTileName);
//                if (path == null)
//                {
//                    MessageHelper.replyToMessage(msg, "Could not find tile");
//                    return;
//                }
//                File planet = new File(path);
//                CreateMap.generateMapInstance.addTile(planet, position);
//                File file = CreateMap.generateMapInstance.saveImage();
//                MessageHelper.replyToMessage(event.getMessage(), file);
//            }
//        }
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(Constants.ADD_UNIT, "Add unit to map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAME, "Unit name")
                                .setRequired(true))
//                        .addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map")
//                                .setRequired(true))

        );
    }
}
