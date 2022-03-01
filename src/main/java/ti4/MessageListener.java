package ti4;

import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.generator.GenerateMap;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.message.MessageHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageListener extends ListenerAdapter {

    Logger logger = Logger.getLogger(MessageListener.class.getName());

    private GenerateMap generateMapInstance;
    private ResourceHelper resourceHelper;
    private boolean mapCreationStarted = false;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        CommandManager commandManager = CommandManager.getInstance();
        for (Command command : commandManager.getCommandList()) {

        }


        Message msg = event.getMessage();
        if (msg.getContentRaw().startsWith("map_log")) {
            if (event.isFromType(ChannelType.PRIVATE)) {
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(),
                        event.getMessage().getContentDisplay());
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getId(),
                        event.getMessage().getContentDisplay());
            } else {
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(),
                        event.getTextChannel().getName(), event.getMember().getEffectiveName(),
                        event.getMessage().getContentDisplay());
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getId(),
                        event.getTextChannel().getId(), event.getAuthor().getId(),
                        event.getMessage().getContentDisplay());
            }
        } else if (msg.getContentRaw().equals("map_test")) {
            MessageChannel channel = event.getChannel();
            long time = System.currentTimeMillis();
            channel.sendMessage("I'm alive!") /* => RestAction<Message> */
                    .queue(response /* => Message */ -> {
                        response.editMessageFormat("I'm alive!: %d ms", System.currentTimeMillis() - time).queue();
                    });
        } else if (msg.getContentRaw().equals(":create_map")) {
            if (mapCreationStarted) {
                MessageHelper.replyToMessage(event.getMessage(), "Map already is being created!");
            }
            else {
                resourceHelper = new ResourceHelper();
                File setupFile = resourceHelper.getResource("6player_setup.png");
                generateMapInstance = new GenerateMap(setupFile);
                mapCreationStarted = true;
                MessageHelper.replyToMessage(event.getMessage(), "Map creation started");
            }
        } else if (msg.getContentRaw().equals(":finish_map")) {
            mapCreationStarted = false;
            generateMapInstance = null;
            resourceHelper = null;

            MessageHelper.replyToMessage(event.getMessage(),"Map creation finished");
        } else if (msg.getContentRaw().startsWith(":add_tile")) {
            if (!mapCreationStarted)
            {
                MessageHelper.replyToMessage(event.getMessage(),"Start map creation with :create_map");
            }
            else {
                MessageChannel channel = event.getChannel();
                String message = msg.getContentRaw();
                StringTokenizer tokenizer = new StringTokenizer(message, " ");
                if (tokenizer.countTokens() == 3)
                {
                    String command = tokenizer.nextToken();
                    String planetTileName = tokenizer.nextToken();
                    String position = tokenizer.nextToken();

                    File planet = resourceHelper.getResource("planets/" +planetTileName+".png");
                    generateMapInstance.addTile(planet, position);
                    File file = generateMapInstance.saveImage();
                    MessageHelper.replyToMessage(event.getMessage(), file);
                }


            }
        } else if (msg.getContentRaw().startsWith(":add_unit")) {
            if (!mapCreationStarted)
            {
                MessageHelper.replyToMessage(event.getMessage(),"Start map creation with :create_map");
            }
            else {
                String message = msg.getContentRaw();
                StringTokenizer tokenizer = new StringTokenizer(message, " ");
                if (tokenizer.countTokens() == 3)
                {
                    String command = tokenizer.nextToken();//We skip command but need to parse
                    String unitName = tokenizer.nextToken();
                    String position = tokenizer.nextToken();

                    File planet = resourceHelper.getResource("units/" +unitName+".png");
                    generateMapInstance.addTile(planet, position);
                    File file = generateMapInstance.saveImage();
                    MessageHelper.replyToMessage(event.getMessage(),file);
                }
            }
        }
    }
}