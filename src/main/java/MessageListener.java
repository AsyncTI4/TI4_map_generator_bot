import generator.GenerateMap;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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
        Message msg = event.getMessage();
        if (msg.getContentRaw().startsWith("map_log")) {
            if (event.isFromType(ChannelType.PRIVATE)) {
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(),
                        event.getMessage().getContentDisplay());
            } else {
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(),
                        event.getTextChannel().getName(), event.getMember().getEffectiveName(),
                        event.getMessage().getContentDisplay());
            }
        } else if (msg.getContentRaw().equals("map_test")) {
            MessageChannel channel = event.getChannel();
            long time = System.currentTimeMillis();
            channel.sendMessage("I'm alive!") /* => RestAction<Message> */
                    .queue(response /* => Message */ -> {
                        response.editMessageFormat("I'm alive!: %d ms", System.currentTimeMillis() - time).queue();
                    });
        }
       else if (msg.getContentRaw().equals("map_reaction")) {
            MessageChannel channel = event.getChannel();
            long time = System.currentTimeMillis();
            event.getChannel().sendMessage("reaction")
                    .flatMap(message -> message.addReaction("✔"))
                    .queue();
        }
        else if (msg.getContentRaw().equals(":image")) {
            MessageChannel channel = event.getChannel();
            URL resource = MessageListener.class.getResource("AbyzFria.png");
            try {
                File planetFile = Paths.get(resource.toURI()).toFile();
                OutputStream os = new ByteArrayOutputStream();
                 byte[] bytes = Files.readAllBytes(planetFile.toPath());

                event.getMessage().reply(planetFile).queue();
//                event.getChannel().sendFile(bytes, "planet image").queue();
            } catch (URISyntaxException e) {
                logger.log(Level.SEVERE, "Could not find resource", e);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not convert to bytes", e);
            }
//            File planet = new File("E:\\DEV TI4\\TI4_map_generator_discord_bot\\src\\main\\resources\\AbyzFria.png");
//            event.getMessage().reply(planet).queue();
//            event.getChannel().sendFile(planet, "planet image").queue();
        } else if (msg.getContentRaw().equals(":create_map")) {
            if (mapCreationStarted) {
                MessageChannel channel = event.getChannel();
                channel.sendMessage("Map already is being created!") /* => RestAction<Message> */
                        .queue(response /* => Message */ -> {
                            response.editMessageFormat("Map is being create, end with :finish_map").queue();
                        });
            }
            else {
                resourceHelper = new ResourceHelper();
                File setupFile = resourceHelper.getResource("6player_setup.png");
                generateMapInstance = new GenerateMap(setupFile);
                mapCreationStarted = true;
                event.getMessage().reply("Map creation started").queue();
            }
        } else if (msg.getContentRaw().equals(":finish_map")) {
            mapCreationStarted = false;
            generateMapInstance = null;
            resourceHelper = null;

            event.getMessage().reply("Map creation finished").queue();
        } else if (msg.getContentRaw().startsWith(":add_tile")) {
            if (!mapCreationStarted)
            {
                event.getMessage().reply("Start map creation with :create_map").queue();
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

                    File planet = resourceHelper.getResource("planets/"+planetTileName+".png");
                    generateMapInstance.addTile(planet, position);
                    File file = generateMapInstance.saveImage();
                    event.getMessage().reply(file).queue();
                }


            }
        } else if (msg.getContentRaw().startsWith(":add_unit")) {
            if (!mapCreationStarted)
            {
                event.getMessage().reply("Start map creation with :create_map").queue();
            }
            else {
                String message = msg.getContentRaw();
                StringTokenizer tokenizer = new StringTokenizer(message, " ");
                if (tokenizer.countTokens() == 3)
                {
                    String command = tokenizer.nextToken();
                    String unitName = tokenizer.nextToken();
                    String position = tokenizer.nextToken();

                    File planet = resourceHelper.getResource("units/"+unitName+".png");
                    generateMapInstance.addTile(planet, position);
                    File file = generateMapInstance.saveImage();
                    event.getMessage().reply(file).queue();
                }


            }
        }

//        ResourceHelper resourceHelper = new ResourceHelper();
//        File setupFile = resourceHelper.getResource("6player_setup.png");
//        GenerateMap instance = new GenerateMap(setupFile);
//
//
//        File nebula = resourceHelper.getResource("planets/nebula.png");
//        File saudor = resourceHelper.getResource("planets/saudor.png");
//        File wellon = resourceHelper.getResource("planets/wellon.png");
//        instance.addTile(setupFile, "0");
//        instance.addTile(nebula, "3a");
//        instance.addTile(saudor, "3r");
//        instance.addTile(wellon, "3b");
//        instance.addTile(nebula, "2a");
//
//        instance.saveImage();
    }
}