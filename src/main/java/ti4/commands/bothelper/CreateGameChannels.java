package ti4.commands.bothelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.commands.map.CreateGame;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class CreateGameChannels extends BothelperSubcommandData {
    public CreateGameChannels(){
        super(Constants.CREATE_GAME_CHANNELS, "Create Role and Game Channels for a New Game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME, "Fun Name for the Channel - e.g. pbd###-fun-name-goes-here").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CATEGORY, "Category #category-name - only select a category").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1 @playerName - this will be the game owner, who will complete /game setup").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8 @playerName"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Override default game/role name (next pbd###)"));
        // addOptions(new OptionData(OptionType.STRING, Constants.SERVER, "Server to create the channels on (Primary or Secondary) - default is smart selection").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = null;

        // //SERVER TO CREATE ON
        // OptionMapping serverOption = event.getOption(Constants.SERVER);
        // if (serverOption != null ) {
        //     if (serverOption.getAsString().equalsIgnoreCase("Primary")) {
        //         guild = MapGenerator.guildPrimary;
        //     } else if (serverOption.getAsString().equalsIgnoreCase("Secondary")) {
        //         guild = MapGenerator.guildSecondary;
        //     } else {
        //         sendMessage("Bad server **" + serverOption.getAsString() + "**. Try again.");
        //         return;
        //     }
        //     if (!serverCanHostNewGame(guild)) {
        //         sendMessage("Server **" + guild.getName() + "** is not available for new games.");
        //         return;
        //     }
        // } else {
        //     guild = getNextAvailableServer();
        //     if (guild == null) {
        //         sendMessage("No server available for new games.");
        //         return;
        //     }
        // }

        //GAME NAME
        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        String gameName = null;
        if (gameNameOption != null) {
            gameName = gameNameOption.getAsString();
            if (gameOrRoleAlreadyExists(gameName)) {
                sendMessage("Role or Game: **" + gameName + "** already exists accross all supported servers. Try again with a new name.");
                return;
            }
        } else {
            gameName = getNextGameName();
        }

        //CHECK CATEGORY IS VALID
        String categoryChannelName = event.getOption(Constants.CATEGORY).getAsString();
        Category categoryChannel = null;
        if (categoryChannelName != null && !categoryChannelName.isEmpty()) {
            List<Category> categories = MapGenerator.jda.getCategoriesByName(categoryChannelName, false);
            if (categories.size() > 1) {
                sendMessage("Too many categories with this name!!");
                return;
            } else if (categories.isEmpty()) {
                sendMessage("Category not found");
                return;
            } else {
                categoryChannel = MapGenerator.jda.getCategoriesByName(categoryChannelName, false).get(0);
            }
        }

        //CHECK IF CATEGORY EXISTS
        if (categoryChannel == null || categoryChannel.getType() != ChannelType.CATEGORY) {
            sendMessage("Category: **" + categoryChannel.getName() + "** does not exist. Create the category or pick a different category, then try again.");
            return;
        }

        //SET GUILD BASED ON CATEGORY SELECTED
        guild = categoryChannel.getGuild();

        //CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!serverCanHostNewGame(guild)) {
            sendMessage("Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        //CHECK IF CATEGORY HAS ROOM
        Category category = categoryChannel;
        if (category.getChannels().size() > 48) {
            sendMessage("Category: **" + category.getName() + "** is full on server **" + guild.getName() + "**. Create a new category then try again.");
            return;
        } else if (category.getChannels().size() > 45) {
            String message = "Warning: Category: **" + category.getName() + "** is almost full on server **" + guild.getName() + "**.";
            TextChannel bothelperLoungeChannel = MapGenerator.guildPrimary.getTextChannelsByName("bothelper-lounge", true).get(0);
            if (bothelperLoungeChannel != null) {
                MessageHelper.sendMessageToChannel(bothelperLoungeChannel, message);
            } else {
                BotLogger.log(event, message);
            }
        }


        //PLAYERS
        ArrayList<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null) members.add(member);
                if (gameOwner == null) gameOwner = member;
            } else {
                break;
            }
        }

        //CHECK IF GUILD HAS ALL PLAYERS LISTED
        List<String> guildMemberIDs = guild.getMembers().stream().map(m -> m.getId()).toList();
        boolean sendInviteLink = false;
        for (Member member : members) {
            if (!guildMemberIDs.contains(member.getId())) {
                sendMessage(member.getAsMention() + " is not a member of the server **" + guild.getName() + "**. Please use the invite below to join the server and then try this command again.");
                sendInviteLink = true;
            }
        }
        if (sendInviteLink) {
            //guild.retrieveInvites().complete().get(0).getUrl();
            sendMessage("https://discord.gg/zkMP2VbEsA"); //invite link to secondary server, #landing-pad channel, unlimited uses, no expiration date
            return;
        }

        //CREATE ROLE
        Role role = guild.createRole()
        .setName(gameName)
        .setMentionable(true)
        .complete();


        //ADD PLAYERS TO ROLE
        for (Member member : members) {
            guild.addRoleToMember(member, role).complete();
        }

        //CREATE GAME
        CreateGame createGame = new CreateGame();
        Map newMap = createGame.createNewGame(event, gameName, gameOwner);

        //ADD PLAYERS
        for (Member member : members) {
            newMap.addPlayer(member.getId(), member.getEffectiveName());
        }

        //CREATE CHANNELS
        String gameFunName = event.getOption(Constants.GAME_FUN_NAME).getAsString().replaceAll(" ", "-");
        String newChatChannelName = gameName + "-" + gameFunName;
        String newActionsChannelName = gameName + Constants.ACTIONS_CHANNEL_SUFFIX;
        String newBotThreadName = gameName + Constants.BOT_CHANNEL_SUFFIX;
        long gameRoleID = role.getIdLong();
        long permission = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();

        // CREATE TABLETALK CHANNEL
        TextChannel chatChannel = guild.createTextChannel(newChatChannelName, category)
        .syncPermissionOverrides()
        .addRolePermissionOverride(gameRoleID, permission, 0)
        .complete();
        MessageHelper.sendMessageToChannel((MessageChannel) chatChannel, role.getAsMention()+ " - table talk channel");
        newMap.setTableTalkChannelID(chatChannel.getId());

        // CREATE ACTIONS CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, category)
        .syncPermissionOverrides()
        .addRolePermissionOverride(gameRoleID, permission, 0)
        .complete();
        MessageHelper.sendMessageToChannel((MessageChannel) actionsChannel, role.getAsMention() + " - actions channel");
        newMap.setMainGameChannelID(actionsChannel.getId());

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName)
        .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
        .complete();
        newMap.setBotMapUpdatesThreadID(botThread.getId());

        StringBuilder botGetStartedMessage = new StringBuilder(role.getAsMention()).append(" - bot/map channel\n");
        botGetStartedMessage.append("__Use the following commands to get started:__\n");
        botGetStartedMessage.append("> `/game setup` to set player count and additional options\n");
        botGetStartedMessage.append("> `/add_tile_list {mapString}`, replacing {mapString} with a TTPG map string\n");
        botGetStartedMessage.append("> `/game set_order` to set the starting speaker order\n");
        botGetStartedMessage.append("> `/player setup` to set player faction and colour\n");
        botGetStartedMessage.append("> `/player tech_add` for factions who need to add tech\n");
        botGetStartedMessage.append("> `/add_frontier_tokens` to place frontier tokens on empty spaces\n");
        botGetStartedMessage.append("> `/so deal_to_all (count:2)` to deal two" + Emojis.SecretObjective + " to all players\n");
        botGetStartedMessage.append("\n");
        botGetStartedMessage.append("__Other helpful commands:__\n");
        botGetStartedMessage.append("> `/game replace` to replace a player in the game with a new one\n");
        botGetStartedMessage.append("> `/role remove` to remove the game role to any replaced players\n");
        botGetStartedMessage.append("> `/role add` to add the game role to any replacing players\n");
        // botGetStartedMessage.append("> `/status po_reveal_stage1` to reveal the first" + Emojis.Public1 + "Stage 1 Public Objective\n");
        MessageHelper.sendMessageToChannelAndPin((MessageChannel) botThread, botGetStartedMessage.toString());
        MessageHelper.sendMessageToChannelAndPin((MessageChannel) botThread, "Website Live Map: https://ti4.westaddisonheavyindustries.com/game/" + gameName);

        StringBuilder message = new StringBuilder("Role and Channels have been set up:\n");
        message.append("> " + role.getName() + "\n");
        message.append("> " + chatChannel.getAsMention()).append("\n");
        message.append("> " + actionsChannel.getAsMention()).append("\n");
        message.append("> " + botThread.getAsMention()).append("\n");
        sendMessage(message.toString());

        MapSaveLoadManager.saveMap(newMap, event);
    }

    private static String getNextGameName() {
        int nextPBDNumber = Collections.max(getAllExistingPBDNumbers()) + 1;
        return "pbd" + nextPBDNumber;
    }

    private static boolean gameOrRoleAlreadyExists(String name) {
        List<Guild> guilds = MapGenerator.jda.getGuilds();
        ArrayList<String> gameAndRoleNames = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            //EXISTING ROLE NAMES
            for (Role role : guild.getRoles()) {
                gameAndRoleNames.add(role.getName());
            }
        }

        // GET ALL EXISTING PBD MAP NAMES
        HashSet<String> mapNames = new HashSet<>(MapManager.getInstance().getMapList().keySet());
        gameAndRoleNames.addAll(mapNames);

        //CHECK
        if (mapNames.contains(name)) {
            return true;
        } else {
            return false;
        }
    }

    private static ArrayList<Integer> getAllExistingPBDNumbers() {
        List<Guild> guilds = MapGenerator.jda.getGuilds();
        ArrayList<Integer> pbdNumbers = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            System.out.println(guild.getName());
            List<Role> pbdRoles = guild.getRoles().stream()
                .filter(r -> r.getName().startsWith("pbd"))
                .toList();

            //EXISTING ROLE NAMES
            for (Role role : pbdRoles) {
                String pbdNum = role.getName().replace("pbd", "");
                if (Helper.isInteger(pbdNum)) {
                    pbdNumbers.add(Integer.parseInt(pbdNum));
                }
            }
        }

        // GET ALL EXISTING PBD MAP NAMES
        List<String> mapNames = MapManager.getInstance().getMapList().keySet().stream()
            .filter(mapName -> mapName.startsWith("pbd"))
            .toList();
        for (String mapName : mapNames) {
            String pbdNum = mapName.replace("pbd", "");
            if (Helper.isInteger(pbdNum)) {
                pbdNumbers.add(Integer.parseInt(pbdNum));
            }
        }
        return pbdNumbers;
    }

    private static Guild getNextAvailableServer() {
        if (serverCanHostNewGame(MapGenerator.guildPrimary)) {
            return MapGenerator.guildPrimary;
        } else if (serverCanHostNewGame(MapGenerator.guildSecondary)) {
            return MapGenerator.guildSecondary;
        } else {
            return null;
        }
    }

    private static boolean serverCanHostNewGame(Guild guild) {
        if (guild != null   && serverHasRoomForNewRole(guild)
                            && serverHasRoomForNewChannels(guild)) {
            return true;
        }
        return false;
    }

    private static boolean serverHasRoomForNewRole(Guild guild) {
        int roleCount = guild.getRoles().size();
        if (roleCount >= 250) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewRole` Cannot create a new role. Server **" + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }
        return true;
    }

    private static boolean serverHasRoomForNewChannels(Guild guild) {
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewGame = 2;
        if (channelCount > (channelMax - channelsCountRequiredForNewGame)) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewChannels` Cannot create new channels. Server **" + guild.getName() + "** currently has " + channelCount + " channels.");
            return false;
        }
        return true;
    }

    private static Category getNextAvailableCategory(Guild guild) {
        return null;
    }
}
