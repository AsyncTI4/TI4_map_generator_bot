package ti4.commands.bothelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

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
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import ti4.MapGenerator;
import ti4.commands.game.GameCreate;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class CreateFOWGameChannels extends BothelperSubcommandData {
    public CreateFOWGameChannels(){
        super(Constants.CREATE_FOW_GAME_CHANNELS, "Create Role and Game Channels for a New FOW Game");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1 @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.FOWGM, "Default GM is whoever runs this command"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Override default game/role name (next fow###)"));
        // addOptions(new OptionData(OptionType.STRING, Constants.SERVER, "Server to create the channels on (Primary or Secondary) - default is smart selection").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = null;

        
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
            gameName = getNextFOWGameName();
        }

        //CHECK IF GIVEN CATEGORY IS VALID
        
        
            

        guild = event.getGuild();
        //CHECK IF CATEGORY EXISTS
       

     

        //CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!serverCanHostNewGame(guild)) {
            sendMessage("Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        


        //PLAYERS
        ArrayList<Member> members = new ArrayList<>();
        Member gameOwner = null;
        if (Objects.nonNull(event.getOption("fowgm"))) {
            gameOwner = event.getOption("fowgm").getAsMember();
        }else{
            gameOwner = event.getMember();
        }
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null) members.add(member);
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
            sendMessage(Helper.getGuildInviteURL(guild));
            return;
        }
        Role everyone = guild.getRolesByName("@everyone",true).get(0);
        long permission2 = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue() | Permission.MANAGE_PERMISSIONS.getRawValue() | Permission.MANAGE_THREADS.getRawValue();
        Category category =  guild.createCategory(gameName).addRolePermissionOverride(everyone.getIdLong(), 0, permission2).addMemberPermissionOverride(gameOwner.getIdLong(), permission2, 0 ).complete();
        
        //CREATE ROLE
        Role role = guild.createRole()
        .setName(gameName)
        .setMentionable(true)
        .complete();

        Role roleGM = guild.createRole()
        .setName(gameName+" GM")
        .setMentionable(true)
        .complete();

        guild.addRoleToMember(gameOwner, roleGM).complete();
        //ADD PLAYERS TO ROLE
        for (Member member : members) {
            guild.addRoleToMember(member, role).complete();
        }

        //CREATE GAME
        Map newMap = GameCreate.createNewGame(event, gameName, gameOwner);

        //ADD PLAYERS
        newMap.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        for (Member member : members) {
            newMap.addPlayer(member.getId(), member.getEffectiveName());
        }

        newMap.setFoWMode(true);
        //CREATE CHANNELS
        String newChatChannelName = gameName + "-gm-room";
        String newActionsChannelName = gameName + "-anonymous-announcements-private";
        long gameRoleID = role.getIdLong();
        long gameRoleGMID = roleGM.getIdLong();
        long permission = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();
        
        // CREATE GM CHANNEL
        TextChannel chatChannel = guild.createTextChannel(newChatChannelName, category)
        .syncPermissionOverrides()
        .addRolePermissionOverride(gameRoleGMID, permission, 0)
        .complete();
        MessageHelper.sendMessageToChannel((MessageChannel) chatChannel, roleGM.getAsMention()+ " - gm room");
        

        // CREATE Anon Announcements CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, category)
        .syncPermissionOverrides()
        .addRolePermissionOverride(gameRoleID, permission, 0)
        .complete();
        MessageHelper.sendMessageToChannel((MessageChannel) actionsChannel, role.getAsMention() + " - actions channel");
        newMap.setMainGameChannelID(actionsChannel.getId());

        // Individual player channels
        for (Member member : members) {
            String name = member.getNickname();
            if(name == null){
                name = member.getEffectiveName();
            }
            TextChannel memberChannel = guild.createTextChannel(gameName+"-"+name+"-private", category)
            .syncPermissionOverrides()
            .addMemberPermissionOverride(member.getIdLong(), permission, 0)
            .complete();
            Player player_ = newMap.getPlayer(member.getId());
            player_.setPrivateChannelID(memberChannel.getId());
        }
       
        

        

        StringBuilder message = new StringBuilder("Role and Channels have been set up:\n");
        message.append("> " + role.getName() + "\n");
        message.append("> " + chatChannel.getAsMention()).append("\n");
        message.append("> " + actionsChannel.getAsMention()).append("\n");
        sendMessage(message.toString());

        MapSaveLoadManager.saveMap(newMap, event);
    }

    
    private static String getNextFOWGameName() {
        ArrayList<Integer> existingNums = getAllExistingFOWNumbers();
        if (existingNums.size() == 0) {
            return "fow1";
        }
        int nextPBDNumber = Collections.max(getAllExistingFOWNumbers()) + 1;
        return "fow" + nextPBDNumber;
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
    private static ArrayList<Integer> getAllExistingFOWNumbers() {
        List<Guild> guilds = MapGenerator.jda.getGuilds();
        ArrayList<Integer> pbdNumbers = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            System.out.println(guild.getName());
            List<Role> pbdRoles = guild.getRoles().stream()
                .filter(r -> r.getName().startsWith("fow"))
                .toList();

            //EXISTING ROLE NAMES
            for (Role role : pbdRoles) {
                String pbdNum = role.getName().replace("fow", "");
                if (Helper.isInteger(pbdNum)) {
                    pbdNumbers.add(Integer.parseInt(pbdNum));
                }
            }
        }

        // GET ALL EXISTING PBD MAP NAMES
        List<String> mapNames = MapManager.getInstance().getMapList().keySet().stream()
            .filter(mapName -> mapName.startsWith("fow"))
            .toList();
        for (String mapName : mapNames) {
            String pbdNum = mapName.replace("fow", "");
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

    private static String getCategoryNameForGame(String gameName) {
        if (!gameName.startsWith("fow")) return null;
        String gameNumber = StringUtils.substringAfter(gameName, "fow");
        if (!Helper.isInteger(gameNumber)) return null;
        int gameNum = Integer.parseInt(gameNumber);     
        return "FOW #" + gameNum;
    }
    
    public static List<Category> getAllAvailablePBDCategories() {
        List<Category> categories = MapGenerator.jda.getCategories().stream()
            .filter(category -> category.getName().toUpperCase().startsWith("PBD #"))
            .toList();

        return categories;
    }
}
