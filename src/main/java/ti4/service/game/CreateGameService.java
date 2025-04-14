package ti4.service.game;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.ResourceHelper;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.TIGLHelper;
import ti4.helpers.ThreadArchiveHelper;
import ti4.image.ImageHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.image.FileUploadService;
import ti4.service.option.GameOptionService;
import ti4.settings.GlobalSettings;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class CreateGameService {

    public static Game createNewGame(String gameName, Member gameOwner) {
        Game newGame = new Game();
        newGame.newGameSetup();
        String ownerID = gameOwner.getId();
        newGame.setOwnerID(ownerID);
        newGame.setOwnerName(gameOwner.getEffectiveName());
        newGame.setName(gameName);
        newGame.setAutoPing(true);
        newGame.setAutoPingSpacer(24);
        newGame.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        GameManager.save(newGame, "Game created");
        return newGame;
    }

    public static void reportNewGameCreated(Game game) {
        if (game == null)
            return;

        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true)
            .stream().findFirst().orElse(null);
        if (bothelperLoungeChannel == null)
            return;
        List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
        if (threadChannels.isEmpty())
            return;
        String threadName = "game-starts-and-ends";
        // SEARCH FOR EXISTING OPEN THREAD
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                String guildName = game.getGuild() == null ? "Server Unknown" : game.getGuild().getName();
                MessageHelper.sendMessageToChannel(threadChannel_,
                    "Game: **" + game.getName() + "** on server **" + guildName + "** has been created.");
                break;
            }
        }
    }

    public static Game createGameChannels(List<Member> members, GenericInteractionCreateEvent event, String gameFunName, String gameName, Member gameOwner, Category categoryChannel) {
        // SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();

        // MAKE ROOM FOR MORE THREADS
        ThreadArchiveHelper.checkThreadLimitAndArchive(guild);

        // CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!serverCanHostNewGame(guild)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return null;
        }

        // CHECK IF CATEGORY HAS ROOM
        if (categoryChannel.getChannels().size() > 48) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Category: **" + categoryChannel.getName()
                + "** is full on server **" + guild.getName() + "**. Create a new category then try again.");
            return null;
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
        Game newGame = createNewGame(gameName, gameOwner);

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
        TextChannel chatChannel = guild.createTextChannel(newChatChannelName, categoryChannel)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();
        newGame.setTableTalkChannelID(chatChannel.getId());

        // CREATE ACTIONS CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, categoryChannel)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();
        newGame.setMainChannelID(actionsChannel.getId());

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName)
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
            .complete();
        newGame.setBotMapUpdatesThreadID(botThread.getId());
        introductionToBotMapUpdatesThread(newGame);
        introductionForNewPlayers(newGame);

        // Create Cards Info Threads
        // for (Player player : newGame.getPlayers().values()) {
        //     player.createCardsInfoThreadChannelsIfRequired();
        // }

        // Report Channel Creation back to Launch channel
        String message = "Role and Channels have been set up:\n> " +
            role.getName() + "\n> " +
            chatChannel.getAsMention() + "\n> " +
            actionsChannel.getAsMention();
        MessageHelper.sendMessageToEventChannel(event, message);

        reportNewGameCreated(newGame);

        presentSetupToPlayers(newGame);

        // AUTOCLOSE LAUNCH THREAD AFTER RUNNING COMMAND
        if (event.getChannel() instanceof ThreadChannel thread && thread.getParentChannel().getName().equals("making-new-games")) {
            newGame.setLaunchPostThreadID(thread.getId());
            ThreadChannelManager manager = thread.getManager()
                .setName(StringUtils.left(newGame.getName() + "-launched [FULL] - " + thread.getName(), 100))
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
            if (thread.getName().toLowerCase().contains("tigl") || newGame.getCustomName().toLowerCase().contains("tigl")) {
                TIGLHelper.initializeTIGLGame(newGame);
            }
            if (missingMembers.isEmpty()) {
                manager = manager.setArchived(true);
            }
            manager.queue();
        }

        return newGame;
    }

    private static void presentSetupToPlayers(Game game) {
        introductionToTableTalkChannel(game);
        introductionToActionsChannel(game);
        sendMessageAboutAggressionMetas(game);

        MessageChannel actionsChannel = game.getActionsChannel();

        Button miltyButton = Buttons.green("miltySetup", "Start Milty Setup");
        Button addMapString = Buttons.green("addMapString~MDL", "Add Prebuilt Map String");
        MessageHelper.sendMessageToChannelWithButtons(actionsChannel, "How would you like to set up the players and map?", List.of(miltyButton, addMapString));

        //Button offerOptions = Buttons.green("offerGameOptionButtons", "Options");
        GameOptionService.offerGameOptionButtons(game, actionsChannel);
        //MessageHelper.sendMessageToChannelWithButton(actionsChannel, "Want to change some options? ", offerOptions);

        HomebrewService.offerGameHomebrewButtons(actionsChannel);
        ButtonHelper.offerPlayerSetupButtons(actionsChannel, game);
        MessageHelper.sendMessageToChannel(actionsChannel, "Reminder that all games played on this server must abide by the [AsyncTI4 Code of Conduct](https://discord.com/channels/943410040369479690/1082164664844169256/1270758780367274006)");
    }

    private static void introductionToBotMapUpdatesThread(Game game) {
        ThreadChannel botThread = game.getBotMapUpdatesThread();
        if (botThread == null) {
            return;
        }
        String botGetStartedMessage = game.getPing() + " - bot/map channel\n" +
            "This channel is for bot slash commands and updating the map, to help keep the actions channel clean.\n" +
            "### __Use the following commands to get started:__\n" +
            "> `/map add_tile_list` and insert your TTPG map string\n" +
            "> `/player setup` to set player faction and color\n" +
            "> `/game setup` to set player count and additional options\n" +
            "> `/game set_order` to set the starting speaker order if you're using a weird map\n" +
            "> `/milty setup` to bring up a menu for handling a specific milty draft\n" +
            "> `/milty quickstart` to quickly launch a milty draft that doesnt deviate too much\n\n" +
            "### __Other helpful commands:__\n" +
            "> `/game replace` to replace a player in the game with a new one\n";
        MessageHelper.sendMessageToChannelAndPin(botThread, botGetStartedMessage);
        MessageHelper.sendMessageToChannelAndPin(botThread, "Website Live Map: https://www.AsyncTI4.com/game/" + game.getName());
    }

    private static void sendMessageAboutAggressionMetas(Game game) {
        String aggressionMsg = """
            Strangers playing with eachother for the first time can have different aggression metas, and be unpleasantly surprised when they find themselves playing with others who don't share that meta.\
             Therefore, you can use the buttons below to anonymously share your aggression meta, and if a conflict seems apparent, you can have a conversation about it, or leave the game if the difference is too much and the conversation went badly. These have no binding effect on the game, they just are for setting expectations and starting necessary conversations at the start, rather than in a tense moment 3 weeks down the line\
            .\s
            The conflict metas are loosely classified as the following:\s
            - Friendly -- No early home system takes, only as destructive as the objectives require them to be, expects a person's four "slice" tiles to be respected, generally open to and looking for a diplomatic solution rather than a forceful one.\

            - No Strong Preference -- Can handle a friendly or aggressive environment, is ready for any trouble that comes their way, even if that trouble is someone activating their home system round 2.\

            - Aggressive -- Likes to exploit military weakness to extort and/or claim land, even early in the game, and even if the objectives don't necessarily relate. Their slice is where their plastic is, and that plastic may be in your home system.\s""";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("anonDeclare_Friendly", "Friendly"));
        buttons.add(Buttons.blue("anonDeclare_No Strong Preference", "No Strong Preference"));
        buttons.add(Buttons.red("anonDeclare_Aggressive", "Aggressive"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(game.getActionsChannel(), aggressionMsg, buttons);
    }

    private static void introductionToActionsChannel(Game game) {
        String actionsGetStartedMessage = game.getPing() + " - actions channel\n" +
            "This channel is for taking actions in the game, primarily using buttons or the odd slash command.\n" +
            "Generally, you don't want to chat in here once the game starts, as ideally this channel is a clean ledger of what has happened in the game for others to quickly read.\n";
        MessageHelper.sendMessageToChannelAndPin(game.getActionsChannel(), actionsGetStartedMessage);
    }

    private static void introductionToTableTalkChannel(Game game) {
        TextChannel chatChannel = game.getTableTalkChannel();
        String tabletalkGetStartedMessage = game.getPing() + " - table talk channel\n" +
            "This channel is for typical over the table conversation, as you would over the table while playing the game in real life.\n" +
            "If this group has agreed to whispers (secret conversations), you can create private threads off this channel, or utilize the bots in built whispers (explained in more detail in your cards info once you're set up).\n" +
            "Typical things that go here are: general conversation, deal proposals, memes - everything that isn't either an actual action in the game or a bot command\n" +
            game.getPing() + " if you are playing with strangers, you should take a few moments at the start here to discuss how you're going handle disputes and take-backs. Async is an odd format, it can get messy " +
            "and takebacks are often not only advisable but necessary. A common standard is no new relevant information, but if you wish to get more specific or do something else (like you can only takeback if the whole table says so) then state that here. \n" +
            "Regarding disputes, playing a diplomatic game with strangers online, with no tone to go off of or human face to empathize with, can often lead to harsh words and hurt feelings. No matter what happens mechanically in the game, you should always " +
            "strive to treat the other people with respect, patience, and hopefully kindness. If you cannot, you should step away, and if you ever feel the need to leave a game permanently, we do have a replacement system that gets a fair amount of use (ping a bothelper for specifics)";
        MessageHelper.sendMessageToChannelAndPin(chatChannel, tabletalkGetStartedMessage);
    }

    private static void introductionForNewPlayers(Game game) {
        List<Player> newPlayers = new ArrayList<>();
        TextChannel chatChannel = game.getTableTalkChannel();
        ThreadChannel botThread = game.getBotMapUpdatesThread();

        // Find new players
        for (Player player : game.getPlayers().values()) {
            if (ButtonHelper.isPlayerNew(player.getUserID())) {
                newPlayers.add(player);
            }
        }
        if (newPlayers.isEmpty()) {
            return;
        }

        chatChannel.createThreadChannel("Info for Players new to AsyncTI4")
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
            .queue(introThread -> {
                try {
                    StringBuilder msg = new StringBuilder();
                    for (Player p : newPlayers) {
                        msg.append(p.getRepresentation());
                    }
                    msg.append(getNewPlayerInfoText());
                    String message = msg.toString();
                    if (botThread != null) {
                        message = message.replace("the bot-map-updates thread", botThread.getJumpUrl());
                    }
                    MessageHelper.sendMessageToChannel(introThread, message);
                    BufferedImage colorsImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile("Compiled_Async_colors.png"), 731, 593);
                    FileUpload fileUpload = FileUploadService.createFileUpload(colorsImage, "colors");
                    MessageHelper.sendFileUploadToChannel(introThread, fileUpload);
                } catch (Exception e) {
                    BotLogger.error(new BotLogger.LogMessageOrigin(game), "newPlayerIntro", e);
                }
            }, null);
    }

    /**
     * @param guild guild to invite users to
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
        if (!missingMembers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(
                "### Sorry for the inconvenience!\nDue to Discord's limits on Role/Channel/Thread count, we need to create this game on another server.\nPlease use the invite below to join our **");
            sb.append(guild.getName()).append("** server.\n");
            sb.append(Helper.getGuildInviteURL(guild, missingMembers.size() + 10)).append("\n");
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

    public static boolean gameOrRoleAlreadyExists(String name) {
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
        gameAndRoleNames.addAll(GameManager.getGameNames());

        // CHECK
        return gameAndRoleNames.contains(name);
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
        List<String> gameNames = GameManager.getGameNames().stream()
            .filter(gameName -> gameName.startsWith("pbd"))
            .toList();
        for (String gameName : gameNames) {
            String pbdNum = gameName.replace("pbd", "");
            if (Helper.isInteger(pbdNum)) {
                pbdNumbers.add(Integer.parseInt(pbdNum));
            }
        }

        return pbdNumbers;
    }

    @Nullable
    private static Guild getNextAvailableServerOrdinal() {
        List<Guild> guilds = AsyncTI4DiscordBot.serversToCreateNewGamesOn;

        // GET CURRENTLY SET GUILD, OR DEFAULT TO PRIMARY
        Guild guild = AsyncTI4DiscordBot.jda
            .getGuildById(GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES.toString(), String.class,
                AsyncTI4DiscordBot.guildPrimary.getId()));

        int index = guilds.indexOf(guild);
        if (index == -1) { // NOT FOUND
            BotLogger.warning("`CreateGameService.getNextAvailableServer` WARNING: Current guild is not in the list of available overflow servers: ***" + guild.getName() + "***");
        }

        // CHECK IF CURRENT GUILD HAS ROOM (INDEX = X)
        if (serverHasRoomForNewFullCategory(guild)) {
            return guild;
        }

        // CHECK NEXT GUILDS IN LINE STARTING AT CURRENT (INDEX = X+1 to ♾)
        for (int i = index + 1; i < guilds.size(); i++) {
            guild = guilds.get(i);
            if (serverHasRoomForNewFullCategory(guild)) {
                GlobalSettings.setSetting(GlobalSettings.ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId());
                return guild;
            }
        }

        // CHECK STARTING FROM BEGINNING UP TO INDEX (INDEX = 0 to X-1)
        for (int i = 0; i < index; i++) {
            guild = guilds.get(i);
            if (serverHasRoomForNewFullCategory(guild)) {
                GlobalSettings.setSetting(GlobalSettings.ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId());
                return guild;
            }
        }

        // ALL OVERFLOWS FULL, CHECK PRIMARY
        if (serverHasRoomForNewFullCategory(AsyncTI4DiscordBot.guildPrimary)) {
            // Don't set GlobalSetting to check for new overflow each time
            return AsyncTI4DiscordBot.guildPrimary;
        }

        BotLogger.warning("`CreateGameService.getNextAvailableServer`\n# WARNING: No available servers on which to create a new game category.");
        return null;
    }

    @Nullable
    private static Guild getServerWithMostCapacity() {
        List<Guild> guilds = AsyncTI4DiscordBot.serversToCreateNewGamesOn.stream()
            .filter(CreateGameService::serverHasRoomForNewFullCategory)
            .sorted(Comparator.comparing(CreateGameService::getServerCapacityForNewGames))
            .toList();

        if (guilds.isEmpty() && serverHasRoomForNewFullCategory(AsyncTI4DiscordBot.guildPrimary)) {
            return AsyncTI4DiscordBot.guildPrimary;
        }

        if (guilds.isEmpty()) {
            BotLogger.warning("`CreateGameService.getServerWithMostCapacity` No available servers to create a new game category");
            return null;
        }

        String debugText = guilds.stream()
            .map(g -> g.getName() + ": " + getServerCapacityForNewGames(g))
            .collect(Collectors.joining("\n"));
        BotLogger.info("Server Game Capacity Check:\n" + debugText);
        return guilds.getLast();
    }

    public static boolean serverCanHostNewGame(Guild guild) {
        return guild != null && serverHasRoomForNewRole(guild)
            && serverHasRoomForNewChannels(guild);
    }

    private static boolean serverHasRoomForNewRole(Guild guild) {
        int roleCount = guild.getRoles().size();
        if (roleCount >= 250) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(guild), "`CreateGameService.serverHasRoomForNewRole` Cannot create a new role. Server **"
                + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }
        return true;
    }

    private static int getServerCapacityForNewGames(Guild guild) {
        int maxRoleCount = 250;
        int roleCount = guild.getRoles().size();
        int gameCountByRole = maxRoleCount - roleCount;

        int maxChannelCount = 500;
        int channelCount = guild.getChannels().size();
        int gameCountByChannel = (maxChannelCount - channelCount) / 2;

        return Math.min(gameCountByRole, gameCountByChannel);
    }

    private static boolean serverHasRoomForNewFullCategory(Guild guild) {
        if (guild == null)
            return false;

        int maxGamesPerCategory = Math.max(1, Math.min(25, GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.MAX_GAMES_PER_CATEGORY.toString(), Integer.class, 10)));

        // SPACE FOR 25 ROLES
        int roleCount = guild.getRoles().size();
        if (roleCount > (250 - maxGamesPerCategory)) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(guild), "`CreateGameService.serverHasRoomForNewFullCategory` Cannot create a new category. Server **"
                + guild.getName() + "** currently has **" + roleCount + "** roles and a new category requires space for " + maxGamesPerCategory + " roles.");
            return false;
        }

        // SPACE FOR 50 CHANNELS
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewCategory = 1 + 2 * maxGamesPerCategory;
        if (channelCount > (channelMax - channelsCountRequiredForNewCategory)) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(guild), "`CreateGameService.serverHasRoomForNewFullCategory` Cannot create a new category. Server **"
                + guild.getName() + "** currently has " + channelCount + " channels and a new category requires space for "
                + channelsCountRequiredForNewCategory + " new channels (including 1 for the category itself)");
            return false;
        }

        return true;
    }

    private static boolean serverHasRoomForNewChannels(Guild guild) {
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewGame = 2;
        if (channelCount > (channelMax - channelsCountRequiredForNewGame)) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(guild), "`CreateGameService.serverHasRoomForNewChannels` Cannot create new channels. Server **"
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
                BotLogger.error("Could not parse integers within category name: " + category.getName(), e);
            }
        }

        // Derive a category name logically
        int maxGamesPerCategory = Math.max(1, Math.min(25, GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.MAX_GAMES_PER_CATEGORY.toString(), Integer.class, 10)));
        int gameNumberMod = gameNumber % maxGamesPerCategory;
        int lowerBound = gameNumber - gameNumberMod;
        int upperBound = lowerBound + maxGamesPerCategory - 1;
        return "PBD #" + lowerBound + "-" + upperBound;
    }

    public static List<Category> getAllAvailablePBDCategories() {
        return AsyncTI4DiscordBot.getAvailablePBDCategories();
    }

    public static Category createNewCategory(String categoryName) {
        Guild guild = getServerWithMostCapacity();
        if (guild == null) {
            BotLogger.warning("`CreateGameService.createNewCategory` No available servers to create a new game category");
            return null;
        }

        List<Category> categories = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryName, false);
        if (!categories.isEmpty()) {
            String message = categories.stream().map(Channel::getAsMention).collect(Collectors.joining("\n"));
            BotLogger.info("Game Channel Creation - Category Already Exists:\n" + message);
            return categories.getFirst();
        }

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

    //TODO: Can this just be guild.getRolesByName?
    public static Role getRole(String name, Guild guild) {
        return guild.getRoles().stream()
            .filter(role -> role.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public static String getNewPlayerInfoText() {
        String path = ResourceHelper.getInstance().getHelpFile("NewPlayerIntro.txt");
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (Exception e) {
            return "NewPlayerIntro HELP FILE IS BLANK";
        }
    }

    public static boolean isLockedFromCreatingGames(GenericInteractionCreateEvent event) {
        Member member = event.getMember();
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.bothelperRoles) {
                if (roles.contains(role)) {
                    return false;
                }
            }
        }

        var userSettings = UserSettingsManager.get(event.getUser().getId());
        if (userSettings.isLockedFromCreatingGames()) {
            return true;
        }
        userSettings.setLockedFromCreatingGamesUntil(LocalDateTime.now().plusMinutes(10));
        UserSettingsManager.save(userSettings);
        return false;
    }

    public static boolean isGameCreationAllowed() {
        return GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.ALLOW_GAME_CREATION.toString(), Boolean.class, true);
    }
}
