package ti4.commands.bothelper;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

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
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.ResourceHelper;
import ti4.buttons.Buttons;
import ti4.commands.game.GameCreate;
import ti4.commands.game.WeirdGameSetup;
import ti4.generator.MapGenerator;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.GlobalSettings;
import ti4.helpers.GlobalSettings.ImplementedSettings;
import ti4.helpers.Helper;
import ti4.helpers.ImageHelper;
import ti4.helpers.TIGLHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

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
            MessageHelper.sendMessageToEventChannel(event, "Role or Game: **" + gameName
                + "** already exists accross all supported servers. Try again with a new name.");
            return;
        }

        // CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = event.getOption(Constants.CATEGORY, null, OptionMapping::getAsString);
        Category categoryChannel = null;
        if (categoryChannelName != null && !categoryChannelName.isEmpty()) {
            List<Category> categoriesWithName = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryChannelName, false);
            if (categoriesWithName.size() > 1) {
                MessageHelper.sendMessageToEventChannel(event, "Too many categories with this name!!");
                return;
            } else if (categoriesWithName.isEmpty()) {
                MessageHelper.sendMessageToEventChannel(event, "Category not found");
                return;
            } else {
                categoryChannel = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryChannelName, false).get(0);
            }
        } else { // CATEGORY WAS NOT PROVIDED, FIND OR CREATE ONE
            categoryChannelName = getCategoryNameForGame(gameName);
            if (categoryChannelName == null) {
                MessageHelper.sendMessageToEventChannel(event, "Category could not be automatically determined. Please provide a category name for this game.");
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
                MessageHelper.sendMessageToEventChannel(event, "Could not automatically find a category that begins with **" + categoryChannelName
                    + "** - Please create this category.");
                return;
            }
        }

        // CHECK IF CATEGORY EXISTS
        if (categoryChannel == null || categoryChannel.getType() != ChannelType.CATEGORY) {
            MessageHelper.sendMessageToEventChannel(event, "Category: **" + categoryChannelName
                + "** does not exist. Create the category or pick a different category, then try again.");
            return;
        }

        // SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();
        if (guild == null) {
            MessageHelper.sendMessageToEventChannel(event, "Error: Guild is null");
            return;
        }

        // CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!serverCanHostNewGame(guild)) {
            MessageHelper.sendMessageToEventChannel(event, "Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        // CHECK IF CATEGORY HAS ROOM
        Category category = categoryChannel;
        if (category.getChannels().size() > 48) {
            MessageHelper.sendMessageToEventChannel(event, "Category: **" + category.getName() + "** is full on server **" + guild.getName()
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
        newGame.setMainChannelID(actionsChannel.getId());

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = null;
        try {
            botThread = actionsChannel.createThreadChannel(newBotThreadName)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                .complete();
            newGame.setBotMapUpdatesThreadID(botThread.getId());
        } catch (Exception e) {
            MessageHelper.sendMessageToChannel(newGame.getMainGameChannel(), "You will need to make your own bot-map-updates thread. Ping bothelper if you don't know how");
        }
        List<Player> newPlayers = new ArrayList<>();
        ThreadChannel introThread = null;
        for (Player player : newGame.getPlayers().values()) {
            if (ButtonHelper.isPlayerNew(newGame, player)) {
                newPlayers.add(player);
            }
        }

        try {
            if (!newPlayers.isEmpty()) {
                introThread = chatChannel.createThreadChannel("Info for new players")
                    .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                    .complete();
                String msg = "";
                for (Player p : newPlayers) {
                    msg = msg + p.getRepresentation();
                }
                msg = msg + "\nHullo there and welcome to TI4 async! Below are some non-obvious things you should be aware of as you embark upon your first game:\n\n";

                msg = msg + "## 1. Viewing The Map. \n As the game progresses, the bot will share images of the map in " + (botThread != null ? botThread.getJumpUrl() : "")
                    + ". These images are snapshots of the map at the moment they were created. Below each map, you'll find a \"Website View\" button that takes you to a site where you can see the images in higher resolution than what Discord allows (you can also open the images in your browser for higher resolution). The Website View always shows the most recently generated map, the same one that would be at the bottom of the bot-map-thread. Please note that not every change in the game will result in a new map being created, as we want to save bot resources. This means that sometimes the latest map may not reflect the most current game state. To ensure you're viewing the most up-to-date image, you can refresh the map at any time by clicking the \"Show Game\" button located beneath each generated map. This will generate a new map in the thread and update Website View";

                msg = msg
                    + "\n## 2. Finding Hidden Buttons\n To save space, some buttons are not immediately visible and require you to click on others to access them. Many actions, such as those related to agents, heroes, or miscellaneous tasks, can be found under the \"Component Action\" button that appears at the start of your turn. Some agents or abilities with unique timing can be accessed through a button in your cards info thread. Additionally, you can find a lot of information about abilities and the game state by clicking the \"Player Info\" button located beneath each generated map in the [bot-map-thread link here]. If your cards info thread disappears due to thread limitations, you can bring it back by clicking the \"Cards Info\" button next to the \"Player Info\" button.";
                msg = msg
                    + "\n## 3. The UNDO Button. \nEvery time you press a button, it creates a new save point for the game. If you make a mistake or misclick, you can use the \"UNDO\" button to revert the game to the last save point. However, this button only works if you were the last person to press a button, to prevent undoing someone else’s actions by mistake. If you want to undo an action but multiple players have pressed buttons at the same time, we recommend pinging Bothelper for help or asking an experienced player to use /slash commands to resolve the issue without undoing. For more information on /slash commands, you can refer to this guide: https://docs.google.com/document/d/1yrVH0lEzYj1MbXNzQIK3thgBWAVIZmErluPHLot-OQg/edit#heading=h.ycxk8j7mf3c";
                msg = msg
                    + "\n## 4. Whispers. \nThe bot has a whisper function that allows you to send private messages. To use it, start your message with the keyphrase \"to\" followed by the color or faction of the player you want to contact. For example, if you want to message the Sol player who is blue, you would type \"tosol Hello there\" or \"toblue Hello there\" (note that the quotes should not be included, and the keyphrase is not case sensitive, so ToSol, TOSOL, toSol, or Tosol all work). Make sure to send these messages in your cards info thread, not in the public channel, as the bot will provide a summary of your message afterward. Keep in mind that not all games allow whispers, so check with your group to see if they are permitted.";
                msg = msg
                    + "\n## 5.Changing Colors \nPlayers often setup with similar colors, which can be confusing when their units are close together. Fortunately, you can easily change your color at any time using the /player change color command. A picture of available colors is provided below. Additionally, you can give your units patterns to differentiate them from other players' units by using the /player change_unit_decal command. If you encounter any issues with these commands, experienced players or Bothelper can assist you by giving advice or doing the commands for you";
                BufferedImage colorsImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile("Compiled_Async_colors.png"), 731, 593);
                MessageHelper.sendMessageToChannel(introThread, msg);
                FileUpload fileUpload = MapGenerator.uploadToDiscord(colorsImage, 1.0f, "colors");
                MessageHelper.sendFileUploadToChannel(introThread, fileUpload);
            }
        } catch (Exception e) {
        }
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
            "Generally, you don't want to chat in here once the game starts, as ideally this channel is a clean ledger of what has happened in the game for others to quickly read.\n";
        MessageHelper.sendMessageToChannelAndPin(actionsChannel, actionsGetStartedMessage);

        // MESSAGE ABOUT AGGRESSION METAS
        String agressionMsg = "Strangers playing with eachother for the first time can have different aggression metas, and be unpleasantly surprised when they find themselves playing with others who don't share that meta."
            + " Therefore, you can use the buttons below to anonymously share your aggression meta, and if a conflict seems apparent, you can have a conversation about it, or leave the game if the difference is too much and the conversation went badly. These have no binding effect on the game, they just are for setting expectations and starting necessary conversations at the start, rather than in a tense moment 3 weeks down the line"
            + ". \nThe conflict metas are loosely classified as the following: \n- Friendly -- No early home system takes, only as destructive as the objectives require them to be, expects a person's four \"slice\" tiles to be respected, generally open to and looking for a diplomatic solution rather than a forceful one."
            + "\n- No Strong Preference -- Can handle a friendly or aggressive environment, is ready for any trouble that comes their way, even if that trouble is someone activating their home system round 2."
            + "\n- Aggressive -- Likes to exploit military weakness to extort and/or claim land, even early in the game, and even if the objectives don't necessarily relate. Their slice is where their plastic is, and that plastic may be in your home system. ";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("anonDeclare_Friendly", "Friendly"));
        buttons.add(Buttons.blue("anonDeclare_No Strong Preference", "No Strong Preference"));
        buttons.add(Buttons.red("anonDeclare_Aggressive", "Aggressive"));
        newGame.setUndoButtonOffered(false);
        MessageHelper.sendMessageToChannel(actionsChannel, agressionMsg, buttons);
        newGame.setUndoButtonOffered(true);
        ButtonHelper.offerPlayerSetupButtons(actionsChannel, newGame);

        Button miltyButton = Buttons.green("miltySetup", "Start Milty Setup");
        MessageHelper.sendMessageToChannelWithButton(actionsChannel, "Want to set up a Milty Draft?", miltyButton);

        List<Button> buttons2 = new ArrayList<>();
        buttons2.add(Buttons.green("getHomebrewButtons", "Yes, have homebrew"));
        buttons2.add(Buttons.red("deleteButtons", "No Homebrew"));
        MessageHelper.sendMessageToChannel(actionsChannel, "If you plan to have a supported homebrew mode in this game, please indicate so with these buttons. 4/4/4 is a type of homebrew btw", buttons2);

        List<Button> buttons3 = new ArrayList<>();
        buttons3.add(Buttons.green("enableAidReacts", "Yes, Enable Aid Reacts"));
        buttons3.add(Buttons.red("deleteButtons", "No Aid Reacts"));
        MessageHelper.sendMessageToChannel(actionsChannel, "A frequently used aid is the bot reacting with your faction emoji when you speak, to help others remember your faction. You can enable that with this button. Other such customization options, or if you want to turn this off, are under `/custom customization`", buttons3);

        List<Button> hexBorderButtons = new ArrayList<>();
        hexBorderButtons.add(Buttons.green("showHexBorders_dash", "Dashed line"));
        hexBorderButtons.add(Buttons.blue("showHexBorders_solid", "Solid line"));
        hexBorderButtons.add(Buttons.red("showHexBorders_off", "Off (default)"));
        MessageHelper.sendMessageToChannel(actionsChannel, "Show borders around systems with player's ships, either a dashed line or a solid line. You can also control this setting with `/custom customization`", hexBorderButtons);

        // INTRODUCTION TO BOT-MAP THREAD
        String botGetStartedMessage = role.getAsMention() + " - bot/map channel\n" +
            "This channel is for bot slash commands and updating the map, to help keep the actions channel clean.\n"
            +
            "### __Use the following commands to get started:__\n" +
            "> `/map add_tile_list {mapString}`, replacing {mapString} with a TTPG map string\n" +
            "> `/player setup` to set player faction and color\n" +
            "> `/game setup` to set player count and additional options\n" +
            "> `/game set_order` to set the starting speaker order if you're using a weird map\n" +
            "> `/milty setup` to bring up a menu for handling a specific milty draft\n" +
            "> `/milty quickstart` to quickly launch a milty draft that doesnt deviate too much\n" +
            "\n" +
            "### __Other helpful commands:__\n" +
            "> `/game replace` to replace a player in the game with a new one\n";
        if (botThread != null) {
            MessageHelper.sendMessageToChannelAndPin(botThread, botGetStartedMessage);
            MessageHelper.sendMessageToChannelAndPin(botThread, "Website Live Map: https://ti4.westaddisonheavyindustries.com/game/" + gameName);
        }

        MessageHelper.sendMessageToChannel(actionsChannel, "Reminder that all games played on this server must abide by the async code of conduct, which is described here: https://discord.com/channels/943410040369479690/1082164664844169256/1270758780367274006");

        String message = "Role and Channels have been set up:\n" + "> " + role.getName() + "\n" +
            "> " + chatChannel.getAsMention() + "\n" +
            "> " + actionsChannel.getAsMention() + "\n";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);

        newGame.setUpPeakableObjectives(5, 1);
        newGame.setUpPeakableObjectives(5, 2);

        GameSaveLoadManager.saveMap(newGame, event);
        GameCreate.reportNewGameCreated(newGame);

        // AUTOCLOSE LAUNCH THREAD AFTER RUNNING COMMAND
        if (event.getChannel() instanceof ThreadChannel thread) {
            if (thread.getParentChannel().getName().equals("making-new-games")) {
                newGame.setLaunchPostThreadID(thread.getId());
                ThreadChannelManager manager = thread.getManager()
                    .setName(StringUtils.left(newGame.getName() + "-launched [FULL] - " + thread.getName(), 100))
                    .setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR);
                if (thread.getName().toLowerCase().contains("tigl")) {
                    TIGLHelper.initializeTIGLGame(newGame);
                }
                if (missingMembers.isEmpty()) {
                    manager.setArchived(true);
                }
                manager.queue();
            }
        }
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
        if (missingMembers.size() > 0) {
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

    @Nullable
    private static Guild getNextAvailableServer() {
        List<Guild> guilds = AsyncTI4DiscordBot.serversToCreateNewGamesOn;

        // GET CURRENTLY SET GUILD, OR DEFAULT TO PRIMARY
        Guild guild = AsyncTI4DiscordBot.jda
            .getGuildById(GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES.toString(), String.class,
                AsyncTI4DiscordBot.guildPrimary.getId()));

        int index = guilds.indexOf(guild);
        if (index == -1) { // NOT FOUND
            BotLogger.log("`CreateGameChannels.getNextAvailableServer` WARNING: Current guild is not in the list of available overflow servers: ***" + guild.getName() + "***");
        }

        // CHECK IF CURRENT GUILD HAS ROOM (INDEX = X)
        if (serverHasRoomForNewFullCategory(guild)) {
            return guild;
        }

        // CHECK NEXT GUILDS IN LINE STARTING AT CURRENT (INDEX = X+1 to ♾)
        for (int i = index + 1; i < guilds.size(); i++) {
            guild = guilds.get(i);
            if (serverHasRoomForNewFullCategory(guild)) {
                GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId());
                return guild;
            }
        }

        // CHECK STARTING FROM BEGINNING UP TO INDEX (INDEX = 0 to X-1)
        for (int i = 0; i < index; i++) {
            guild = guilds.get(i);
            if (serverHasRoomForNewFullCategory(guild)) {
                GlobalSettings.setSetting(ImplementedSettings.GUILD_ID_FOR_NEW_GAME_CATEGORIES, guild.getId());
                return guild;
            }
        }

        // ALL OVERFLOWS FULL, CHECK PRIMARY
        if (serverHasRoomForNewFullCategory(AsyncTI4DiscordBot.guildPrimary)) {
            // Don't set GlobalSetting to check for new overflow each time
            return AsyncTI4DiscordBot.guildPrimary;
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
        if (roleCount > 200) {
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
