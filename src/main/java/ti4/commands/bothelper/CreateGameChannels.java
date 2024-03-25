package ti4.commands.bothelper;

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
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.game.GameCreate;
import ti4.generator.Mapper;
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
import ti4.model.LeaderModel;

public class CreateGameChannels extends BothelperSubcommandData {
    public CreateGameChannels() {
        super(Constants.CREATE_GAME_CHANNELS, "Create Role and Game Channels for a New Game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME,
                "Fun Name for the Channel - e.g. pbd###-fun-name-goes-here").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1,
                "Player1 @playerName - this will be the game owner, who will complete /game setup").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8 @playerName"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME,
                "Override default game/role name (next pbd###)"));
        addOptions(new OptionData(OptionType.STRING, Constants.CATEGORY,
                "Override default Category #category-name (PBD #XYZ-ZYX)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // GAME NAME
        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        String gameName;
        if (gameNameOption != null) {
            gameName = gameNameOption.getAsString();
        } else {
            gameName = getNextGameName();
        }
        if (gameOrRoleAlreadyExists(gameName)) {
            sendMessage("Role or Game: **" + gameName
                    + "** already exists accross all supported servers. Try again with a new name.");
            return;
        }

        // CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = event.getOption(Constants.CATEGORY, null, OptionMapping::getAsString);
        Category categoryChannel = null;
        if (categoryChannelName != null && !categoryChannelName.isEmpty()) {
            List<Category> categoriesWithName = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryChannelName, false);
            if (categoriesWithName.size() > 1) {
                sendMessage("Too many categories with this name!!");
                return;
            } else if (categoriesWithName.isEmpty()) {
                sendMessage("Category not found");
                return;
            } else {
                categoryChannel = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryChannelName, false).get(0);
            }
        } else { // CATEGORY WAS NOT PROVIDED, FIND OR CREATE ONE
            categoryChannelName = getCategoryNameForGame(gameName);
            if (categoryChannelName == null) {
                sendMessage(
                        "Category could not be automatically determined. Please provide a category name for this game.");
                return;
            }
            List<Category> categories = getAllAvailablePBDCategories();
            for (Category category : categories) {
                if (category.getName().startsWith(categoryChannelName)) {
                    categoryChannel = category;
                    break;
                }
            }
            if (categoryChannel == null)
                categoryChannel = createNewCategory(categoryChannelName);
            if (categoryChannel == null) {
                sendMessage("Could not automatically find a category that begins with **" + categoryChannelName
                        + "** - Please create this category.");
                return;
            }
        }

        // CHECK IF CATEGORY EXISTS
        if (categoryChannel == null || categoryChannel.getType() != ChannelType.CATEGORY) {
            sendMessage("Category: **" + categoryChannelName
                    + "** does not exist. Create the category or pick a different category, then try again.");
            return;
        }

        // SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();
        if (guild == null) {
            sendMessage("Error: Guild is null");
            return;
        }

        // CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!serverCanHostNewGame(guild)) {
            sendMessage(
                    "Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        // CHECK IF CATEGORY HAS ROOM
        Category category = categoryChannel;
        if (category.getChannels().size() > 48) {
            sendMessage("Category: **" + category.getName() + "** is full on server **" + guild.getName()
                    + "**. Create a new category then try again.");
            return;
        }

        // PLAYERS
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null)
                    members.add(member);
                if (gameOwner == null)
                    gameOwner = member;
            } else {
                break;
            }
        }
        String gameFunName = event.getOption(Constants.GAME_FUN_NAME).getAsString();

        createGameChannels(members, event, gameFunName, gameName, gameOwner, categoryChannel);
    }

    public static void createGameChannels(List<Member> members, GenericInteractionCreateEvent event, String gameFunName,
            String gameName, Member gameOwner, Category categoryChannel) {
        // SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();
        if (guild == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Error: Guild is null");
            return;
        }

        // CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!serverCanHostNewGame(guild)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        // CHECK IF CATEGORY HAS ROOM
        Category category = categoryChannel;
        if (category.getChannels().size() > 48) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Category: **" + category.getName()
                    + "** is full on server **" + guild.getName() + "**. Create a new category then try again.");
            return;
        }

        // CHECK IF GUILD HAS ALL PLAYERS LISTED
        List<Member> missingMembers = inviteUsersToServer(guild, members, event.getMessageChannel());

        // CREATE ROLE
        Role role = guild.createRole()
                .setName(gameName)
                .setMentionable(true)
                .complete();

        // ADD PLAYERS TO ROLE
        for (Member member : members) {
            if (missingMembers.contains(member))
                continue; // skip members who aren't on the new server yet
            guild.addRoleToMember(member, role).complete();
        }

        // CREATE GAME
        Game newGame = GameCreate.createNewGame(event, gameName, gameOwner);

        // ADD PLAYERS
        for (Member member : members) {
            newGame.addPlayer(member.getId(), member.getEffectiveName());
        }
        newGame.setPlayerCountForMap(members.size());
        newGame.setStrategyCardsPerPlayer(newGame.getSCList().size() / members.size());

        // CREATE CHANNELS
        newGame.setCustomName(gameFunName);
        gameFunName = gameFunName.replace(" ", "-");
        gameFunName = gameFunName.replace(".", "");
        gameFunName = gameFunName.replace(":", "");
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
        newGame.setTableTalkChannelID(chatChannel.getId());

        // CREATE ACTIONS CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, category)
                .syncPermissionOverrides()
                .addRolePermissionOverride(gameRoleID, permission, 0)
                .complete();
        newGame.setMainGameChannelID(actionsChannel.getId());

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName).complete();
        newGame.setBotMapUpdatesThreadID(botThread.getId());

        // INTRODUCTION TO TABLETALK CHANNEL
        String tabletalkGetStartedMessage = role.getAsMention() + " - table talk channel\n" +
                "This channel is for typical over the table converstion, as you would over the table while playing the game in real life.\n"
                +
                "If this group has agreed to whispers (secret conversations), you can create private threads off this channel.\n"
                +
                "Typical things that go here are: general conversation, deal proposals, memes - everything that isn't either an actual action in the game or a bot command\n"
                +
                role.getAsMention() +
                " if you are playing with strangers, you should take a few moments at the start here to discuss how you're going handle disputes and take-backs. Async is an odd format, it can get messy "
                +
                "and takebacks are often not only advisable but necessary. A common standard is no new relevant information, but if you want to get more specific or do something else (like you can only takeback if the whole table says so) then state that here. \n"
                +
                "Regarding disputes, playing a diplomatic game with strangers online, with no tone to go off of or human face to empathize with, can often lead to harsh words and hurt feelings. No matter what happens mechanically in the game, you should always "
                +
                "strive to treat the other people with respect, patience, and hopefully kindness. If you cannot, you should step away, and if you ever feel the need to leave a game permanently, we do have a replacement system that gets a fair amount of use (ping or dm a bothelper for specifics)";
        MessageHelper.sendMessageToChannelAndPin(chatChannel, tabletalkGetStartedMessage);

        // INTRODUCTION TO ACTIONS CHANNEL
        String actionsGetStartedMessage = role.getAsMention() + " - actions channel\n" +
                "This channel is for taking actions in the game, primarily using buttons or the odd slash command.\n" +
                "Generally, you dont want to chat in here once the game starts, as ideally this channel is a clean ledger of what has happened in the game for others to quickly read.\n";
        MessageHelper.sendMessageToChannelAndPin(actionsChannel, actionsGetStartedMessage);

        // MESSAGE ABOUT AGGRESSION METAS
        String agressionMsg = "Strangers playing with eachother for the first time can have different aggression metas, and be unpleasantly surprised when they find themselves playing with others who dont share that meta."
                + " Therefore, you can use the buttons below to anonymously share your aggression meta, and if a conflict seems apparent, you can have a conversation about it, or leave the game if the difference is too much and the conversation went badly. These have no binding effect on the game, they just are for setting expectations and starting necessary conversations at the start, rather than in a tense moment 3 weeks down the line"
                + ". \nThe conflict metas are loosely classified as the following: \n- Friendly -- No early home system takes, only as aggressive as the objectives require them to be, expects a person's four \"slice\" tiles to be respected, generally open to and looking for a diplomatic solution rather than a forceful one."
                + "\n- Anything goes -- Is comfortable in a friendly or aggressive environment, is ready for any trouble that comes their way, even if that trouble is someone activating their home system round 2. Tournament games would be this by default. "
                + "\n- Aggressive -- Likes to exploit military weakness to extort and/or claim land, even early in the game, and even if the objectives dont necessarily relate. Their slice is where their plastic is, and that plastic may be in your home system. ";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("anonDeclare_Friendly", "Friendly"));
        buttons.add(Button.primary("anonDeclare_Anything Goes", "Anything Goes"));
        buttons.add(Button.danger("anonDeclare_Aggressive", "Aggressive"));
        newGame.setUndoButton(false);
        MessageHelper.sendMessageToChannel(actionsChannel, agressionMsg, buttons);
        newGame.setUndoButton(true);
        ButtonHelper.offerPlayerSetupButtons(actionsChannel, newGame);

        // INTRODUCTION TO BOT-MAP THREAD
        String botGetStartedMessage = role.getAsMention() + " - bot/map channel\n" +
                "This channel is for bot slash commands and updating the map, to help keep the actions channel clean.\n"
                +
                "### __Use the following commands to get started:__\n" +
                "> `/map add_tile_list {mapString}`, replacing {mapString} with a TTPG map string\n" +
                "> `/player setup` to set player faction and color\n" +
                "> `/game setup` to set player count and additional options\n" +
                "> `/game set_order` to set the starting speaker order\n" +
                "\n" +
                "### __Other helpful commands:__\n" +
                "> `/game replace` to replace a player in the game with a new one\n";
        MessageHelper.sendMessageToChannelAndPin(botThread, botGetStartedMessage);
        MessageHelper.sendMessageToChannelAndPin(botThread,
                "Website Live Map: https://ti4.westaddisonheavyindustries.com/game/" + gameName);

        String message = "Role and Channels have been set up:\n" + "> " + role.getName() + "\n" +
                "> " + chatChannel.getAsMention() + "\n" +
                "> " + actionsChannel.getAsMention() + "\n" +
                "> " + botThread.getAsMention() + "\n";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);

        GameSaveLoadManager.saveMap(newGame, event);
        GameCreate.reportNewGameCreated(newGame);

        // AUTOCLOSE THREAD AFTER RUNNING COMMAND
        if (event.getChannel() instanceof ThreadChannel thread) {
            thread.getManager()
                    .setName(StringUtils.left(newGame.getName() + "-launched - " + thread.getName(), 100))
                    .setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR)
                    .queue();
        }
    }

    /**
     * @param guild   guild to invite users to
     * @param members list of users
     * @param channel channel to post message to
     * @return the list of missing members
     */
    public static List<Member> inviteUsersToServer(Guild guild, List<Member> members, MessageChannel channel) {
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
            MessageHelper.sendMessageToChannel(channel, sb.toString());
        }
        return missingMembers;
    }

    public static String getNextGameName() {
        List<Integer> existingNums = getAllExistingPBDNumbers();
        if (existingNums.isEmpty()) {
            return "pbd1";
        }
        int nextPBDNumber = Collections.max(getAllExistingPBDNumbers()) + 1;
        return "pbd" + nextPBDNumber;
    }

    public static String getLastGameName() {
        List<Integer> existingNums = getAllExistingPBDNumbers();
        if (existingNums.isEmpty()) {
            return "pbd1";
        }
        int nextPBDNumber = Collections.max(getAllExistingPBDNumbers());
        return "pbd" + nextPBDNumber;
    }

    private static boolean gameOrRoleAlreadyExists(String name) {
        List<Guild> guilds = AsyncTI4DiscordBot.jda.getGuilds();
        List<String> gameAndRoleNames = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            // EXISTING ROLE NAMES
            for (Role role : guild.getRoles()) {
                gameAndRoleNames.add(role.getName());
            }
        }

        // GET ALL EXISTING PBD MAP NAMES
        Set<String> mapNames = new HashSet<>(GameManager.getInstance().getGameNameToGame().keySet());
        gameAndRoleNames.addAll(mapNames);

        // CHECK
        return mapNames.contains(name);
    }

    private static List<Integer> getAllExistingPBDNumbers() {
        List<Guild> guilds = new ArrayList<>(AsyncTI4DiscordBot.guilds);
        List<Integer> pbdNumbers = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            List<Role> pbdRoles = guild.getRoles().stream()
                    .filter(r -> r.getName().startsWith("pbd"))
                    .toList();

            // EXISTING ROLE NAMES
            for (Role role : pbdRoles) {
                String pbdNum = role.getName().replace("pbd", "");
                if (Helper.isInteger(pbdNum)) {
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
            if (Helper.isInteger(pbdNum)) {
                pbdNumbers.add(Integer.parseInt(pbdNum));
            }
        }

        return pbdNumbers;
    }

    private static Guild getNextAvailableServer() {
        // GET CURRENTLY SET GUILD, OR DEFAULT TO PRIMARY
        Guild guild = AsyncTI4DiscordBot.jda
                .getGuildById(GlobalSettings.getSetting(
                        GlobalSettings.ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES.toString(), String.class,
                        AsyncTI4DiscordBot.guildPrimary.getId()));

        // CURRENT SET GUILD HAS ROOM
        if (serverHasRoomForNewFullCategory(guild))
            return guild;

        // CHECK IF SECONDARY SERVER HAS ROOM
        guild = AsyncTI4DiscordBot.guildSecondary;
        if (serverHasRoomForNewFullCategory(guild)) {
            GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId());
            return guild;
        }

        // CHECK IF TERTIARY SERVER HAS ROOM
        guild = AsyncTI4DiscordBot.guildTertiary;
        if (serverHasRoomForNewFullCategory(guild)) {
            GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId());
            return guild;
        }

        // CHECK IF QUATERNARY SERVER HAS ROOM
        guild = AsyncTI4DiscordBot.guildQuaternary;
        if (serverHasRoomForNewFullCategory(guild)) {
            GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId());
            return guild;
        }

        // CHECK IF QUINARY SERVER HAS ROOM
        guild = AsyncTI4DiscordBot.guildQuinary;
        if (guild != null && serverHasRoomForNewFullCategory(guild)) {
            GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId());
            return guild;
        }

        BotLogger.log(
                "`CreateGameChannels.getNextAvailableServer`\n# WARNING: No available servers on which to create a new game category.");
        return null;
    }

    private static boolean serverCanHostNewGame(Guild guild) {
        return guild != null && serverHasRoomForNewRole(guild)
                && serverHasRoomForNewChannels(guild);
    }

    private static boolean serverHasRoomForNewRole(Guild guild) {
        int roleCount = guild.getRoles().size();
        if (roleCount >= 250) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewRole` Cannot create a new role. Server **"
                    + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }
        return true;
    }

    private static boolean serverHasRoomForNewFullCategory(Guild guild) {
        if (guild == null)
            return false;

        // SPACE FOR 25 ROLES
        int roleCount = guild.getRoles().size();
        if (roleCount > 225) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewFullCategory` Cannot create a new category. Server **"
                    + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }

        // SPACE FOR 50 CHANNELS
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewCategory = 1 + 2 * Math.max(1, Math.min(25,
                GlobalSettings.getSetting(ImplementedSettings.MAX_GAMES_PER_CATEGORY.toString(), Integer.class, 10)));
        if (channelCount > (channelMax - channelsCountRequiredForNewCategory)) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewFullCategory` Cannot create a new category. Server **"
                    + guild.getName() + "** currently has " + channelCount + " channels.");
            return false;
        }

        return true;
    }

    private static boolean serverHasRoomForNewChannels(Guild guild) {
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewGame = 2;
        if (channelCount > (channelMax - channelsCountRequiredForNewGame)) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewChannels` Cannot create new channels. Server **"
                    + guild.getName() + "** currently has " + channelCount + " channels.");
            return false;
        }
        return true;
    }

    public static String getCategoryNameForGame(String gameName) {
        if (!gameName.startsWith("pbd")) {
            return null;
        }
        String gameNumberStr = StringUtils.substringAfter(gameName, "pbd");
        if (!Helper.isInteger(gameNumberStr)) {
            return null;
        }

        // Find existing category name
        int gameNumber = Integer.parseInt(gameNumberStr);
        for (Category category : getAllAvailablePBDCategories()) {
            try {
                int lowerBound = Integer.parseInt(StringUtils.substringBetween(category.getName(), "PBD #", "-"));
                int upperBound = Integer.parseInt(
                        StringUtils.substringBefore(StringUtils.substringAfter(category.getName(), "-"), " "));
                if (lowerBound <= gameNumber && gameNumber <= upperBound) {
                    return category.getName();
                }
            } catch (Exception e) {
                BotLogger.log("Could not parse integers within category name: " + category.getName());
            }
        }

        // Derive a category name logically
        int maxGamesPerCategory = Math.max(1, Math.min(25,
                GlobalSettings.getSetting(ImplementedSettings.MAX_GAMES_PER_CATEGORY.toString(), Integer.class, 10)));
        int gameNumberMod = gameNumber % maxGamesPerCategory;
        int lowerBound = gameNumber - gameNumberMod;
        int upperBound = lowerBound + maxGamesPerCategory - 1;
        return "PBD #" + lowerBound + "-" + upperBound;
    }

    public static List<Category> getAllAvailablePBDCategories() {
        return AsyncTI4DiscordBot.getAvailablePBDCategories();
    }

    public static Category createNewCategory(String categoryName) {
        Guild guild = getNextAvailableServer();
        if (guild == null) {
            BotLogger.log("`CreateGameChannels.createNewCategory` No available servers to create a new game category");
            return null;
        }

        // ADD LEADER NAME TO CATEGORY NAME FOR FUN
        // List<LeaderModel> leaders = new ArrayList<>(Mapper.getLeaders().values());
        // Collections.shuffle(leaders);
        // if (!leaders.isEmpty()) categoryName = categoryName + " - " +
        // leaders.get(0).getName();

        EnumSet<Permission> allow = EnumSet.of(Permission.VIEW_CHANNEL);
        EnumSet<Permission> deny = EnumSet.of(Permission.VIEW_CHANNEL);
        Role bothelperRole = getRole("Bothelper", guild);
        Role spectatorRole = getRole("Spectator", guild);
        Role everyoneRole = getRole("@everyone", guild);
        ChannelAction<Category> createCategoryAction = guild.createCategory(categoryName);
        if (bothelperRole != null)
            createCategoryAction.addRolePermissionOverride(bothelperRole.getIdLong(), allow, null);
        if (spectatorRole != null)
            createCategoryAction.addRolePermissionOverride(spectatorRole.getIdLong(), allow, null);
        if (everyoneRole != null)
            createCategoryAction.addRolePermissionOverride(everyoneRole.getIdLong(), null, deny);
        return createCategoryAction.complete();
    }

    public static Role getRole(String name, Guild guild) {
        return guild.getRoles().stream()
                .filter(role -> role.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
