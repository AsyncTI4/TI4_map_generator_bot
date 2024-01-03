
package ti4.commands.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.game.GameCreate;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Helper;
import ti4.helpers.GlobalSettings.ImplementedSettings;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class CreateGameChannels extends GameSubcommandData {
    public CreateGameChannels() {
        super(Constants.CREATE_GAME_CHANNELS, "Create Button For Bothelper to Approve");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME, "Fun Name for the Channel - e.g. pbd###-fun-name-goes-here").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1 @playerName - this will be the game owner, who will complete /game setup").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8 @playerName"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        //GAME NAME
        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        String gameName;
        if (gameNameOption != null) {
            gameName = gameNameOption.getAsString();
        } else {
            gameName = getNextGameName();
            if ("pbd2000".equals(gameName)) {
                 MessageHelper.sendMessageToChannel(event.getMessageChannel(),"No more games can be created. Please contact @Developer to resolve."); // See comments in getAllExistingPBDNumbers
                return;
            }
        }
        if (gameOrRoleAlreadyExists(gameName)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Role or Game: **" + gameName + "** already exists accross all supported servers. Try again with a new name.");
            return;
        }

        //CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName =  getCategoryNameForGame(gameName);
        Category categoryChannel = null;
        List<Category> categories = getAllAvailablePBDCategories();
        for (Category category : categories) {
            if (category.getName().toUpperCase().startsWith(categoryChannelName)) {
                categoryChannel = category;
                break;
            }
        }
        if (categoryChannel == null) categoryChannel = createNewCategory(categoryChannelName);

        //SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();

        //PLAYERS
        List<Member> members = new ArrayList<>();
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
        List<String> guildMemberIDs = guild.getMembers().stream().map(ISnowflake::getId).toList();
        List<Member> missingMembers = new ArrayList<>();
        for (Member member : members) {
            if (!guildMemberIDs.contains(member.getId())) {
                missingMembers.add(member);
            }
        }
        if (missingMembers.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(
                "### Sorry for the inconvenience!\nDue to Discord's limits on Role/Channel/Thread count, we need to create this game on another server.\nPlease use the invite below to join our **");
            sb.append(guild.getName()).append("** server.\n");
            sb.append(Helper.getGuildInviteURL(guild)).append("\n");
            sb.append("The following players need to join the server:\n");
            for (Member member : missingMembers) {
                sb.append("> ").append(member.getAsMention()).append("\n");
            }
            sb.append("You will be automatically added to the game channels when you join the server.");
            
        }

        //CREATE ROLE
        Role role = guild.createRole()
            .setName(gameName)
            .setMentionable(true)
            .complete();

        //ADD PLAYERS TO ROLE
        for (Member member : members) {
            if (missingMembers.contains(member)) continue; //skip members who aren't on the new server yet
            guild.addRoleToMember(member, role).complete();
        }

     
    }

    private static String getNextGameName() {
        List<Integer> existingNums = getAllExistingPBDNumbers();
        if (existingNums.size() == 0) {
            return "pbd1";
        }
        int nextPBDNumber = Collections.max(getAllExistingPBDNumbers()) + 1;
        return "pbd" + nextPBDNumber;
    }

    private static boolean gameOrRoleAlreadyExists(String name) {
        List<Guild> guilds = AsyncTI4DiscordBot.jda.getGuilds();
        List<String> gameAndRoleNames = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            //EXISTING ROLE NAMES
            for (Role role : guild.getRoles()) {
                gameAndRoleNames.add(role.getName());
            }
        }

        // GET ALL EXISTING PBD MAP NAMES
        Set<String> mapNames = new HashSet<>(GameManager.getInstance().getGameNameToGame().keySet());
        gameAndRoleNames.addAll(mapNames);

        //CHECK
        return mapNames.contains(name);
    }

    private static List<Integer> getAllExistingPBDNumbers() {
        List<Guild> guilds = AsyncTI4DiscordBot.jda.getGuilds();
        List<Integer> pbdNumbers = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            List<Role> pbdRoles = guild.getRoles().stream()
                .filter(r -> r.getName().startsWith("pbd"))
                .toList();

            //EXISTING ROLE NAMES
            for (Role role : pbdRoles) {
                String pbdNum = role.getName().replace("pbd", "");
                if (Helper.isInteger(pbdNum)
                && Integer.parseInt(pbdNum) < 2000) // REMOVE AFTER pbd1999 GETS CREATED
                {
                    pbdNumbers.add(Integer.parseInt(pbdNum));
                }
            }
        }

        // GET ALL EXISTING PBD MAP NAMES
        List<String> mapNames = GameManager.getInstance().getGameNameToGame().keySet().stream()
            .filter(mapName -> mapName.startsWith("pbd"))
            .toList();
        for (String mapName : mapNames) {
            String pbdNum = mapName.replace("pbd", "");
            if (Helper.isInteger(pbdNum)
                 && Integer.parseInt(pbdNum) < 2000) // REMOVE AFTER pbd1999 GETS CREATED
            {
                pbdNumbers.add(Integer.parseInt(pbdNum));
            }
        }

        return pbdNumbers;
        //return pbdNumbers.stream().filter(num -> num != 1000).toList();
    }

    private static Guild getNextAvailableServer() {
        // GET CURRENTLY SET GUILD, OR DEFAULT TO PRIMARY
        Guild guild = AsyncTI4DiscordBot.jda
            .getGuildById(GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES.toString(), String.class, AsyncTI4DiscordBot.guildPrimary.getId()));

        // CURRENT SET GUILD HAS ROOM
        if (serverHasRoomForNewFullCategory(guild)) return guild;

        // CHECK IF SECONDARY SERVER HAS ROOM
        guild = AsyncTI4DiscordBot.guildSecondary;
        if (serverHasRoomForNewFullCategory(guild)) {
            GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId()); // SET SECONDARY SERVER AS DEFAULT
            return guild;
        }

        // CHECK IF TERTIARY SERVER HAS ROOM
        guild = AsyncTI4DiscordBot.guildTertiary;
        if (serverHasRoomForNewFullCategory(guild)) {
            GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId()); // SET TERTIARY SERVER AS DEFAULT
            return guild;
        }

        // CHECK IF QUATERNARY SERVER HAS ROOM
        guild = AsyncTI4DiscordBot.guildQuaternary;
        if (serverHasRoomForNewFullCategory(guild)) {
            GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId()); // SET QUATERNARY SERVER AS DEFAULT
            return guild;
        }

        BotLogger.log("`CreateGameChannels.getNextAvailableServer`\n# WARNING: No available servers on which to create a new game category.");
        return null;
    }

    private static boolean serverCanHostNewGame(Guild guild) {
        return guild != null && serverHasRoomForNewRole(guild)
            && serverHasRoomForNewChannels(guild);
    }

    private static boolean serverHasRoomForNewRole(Guild guild) {
        int roleCount = guild.getRoles().size();
        if (roleCount >= 250) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewRole` Cannot create a new role. Server **" + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }
        return true;
    }

    private static boolean serverHasRoomForNewFullCategory(Guild guild) {
        if (guild == null) return false;

        // SPACE FOR 25 ROLES
        int roleCount = guild.getRoles().size();
        if (roleCount > 225) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewFullCategory` Cannot create a new category. Server **" + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }

        // SPACE FOR 50 CHANNELS
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewCategory = 50;
        if (channelCount > (channelMax - channelsCountRequiredForNewCategory)) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewFullCategory` Cannot create a new category. Server **" + guild.getName() + "** currently has " + channelCount + " channels.");
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

    public static String getCategoryNameForGame(String gameName) {
        if (!gameName.startsWith("pbd")) return null;
        String gameNumber = StringUtils.substringAfter(gameName, "pbd");
        if (!Helper.isInteger(gameNumber)) return null;
        int gameNum = Integer.parseInt(gameNumber);
        int lowerBound = gameNum - gameNum % 25 + 1;
        int upperBound = lowerBound + 24;
        if (gameNum % 25 == 0) {
            lowerBound = gameNum - 24;
            upperBound = gameNum;
        }
        return "PBD #" + lowerBound + "-" + upperBound;
    }

    public static List<Category> getAllAvailablePBDCategories() {
        return AsyncTI4DiscordBot.jda.getCategories().stream()
            .filter(category -> category.getName().toUpperCase().startsWith("PBD #"))
            .toList();
    }

    public static Category createNewCategory(String categoryName) {
        Guild guild = getNextAvailableServer();
        if (guild == null) {
            BotLogger.log("`CreateGameChannels.createNewCategory` No available servers to create a new game category");
            return null;
        }
        EnumSet<Permission> allow = EnumSet.of(Permission.VIEW_CHANNEL);
        EnumSet<Permission> deny = EnumSet.of(Permission.VIEW_CHANNEL);
        Role bothelperRole = getRole("Bothelper", guild);
        Role spectatorRole = getRole("Spectator", guild);
        Role everyoneRole = getRole("@everyone", guild);
        ChannelAction<Category> createCategoryAction = guild.createCategory(categoryName);
        if (bothelperRole != null) createCategoryAction.addRolePermissionOverride(bothelperRole.getIdLong(), allow, null);
        if (spectatorRole != null) createCategoryAction.addRolePermissionOverride(spectatorRole.getIdLong(), allow, null);
        if (everyoneRole != null) createCategoryAction.addRolePermissionOverride(everyoneRole.getIdLong(), null, deny);
        return createCategoryAction.complete();
    }

    public static Role getRole(String name, Guild guild) {
        return guild.getRoles().stream()
            .filter(role -> role.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}
