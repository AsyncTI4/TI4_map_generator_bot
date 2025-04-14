package ti4.service.fow;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.ResourceHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;
import ti4.service.game.HomebrewService;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class CreateFoWGameService {

    @ButtonHandler("createFoWGameChannels")
    public static void createFoWGameChannels(ButtonInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, event.getUser().getEffectiveName() + " pressed the [Create FoW Game] button.");

        if (!CreateGameService.isGameCreationAllowed()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Admins have temporarily turned off game creation, " +
                "most likely to contain a bug. Please be patient and they'll get back to you on when it's fixed.");
            return;
        }

        if (CreateGameService.isLockedFromCreatingGames(event)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "You created a game within the last 10 minutes and thus are being stopped from creating more until some time " +
                    "has passed. You can have someone else in the game press the button instead.");
            return;
        }

        String buttonMsg = event.getMessage().getContentRaw();
        String gameSillyName = StringUtils.substringBetween(buttonMsg, "Game Fun Name: ", "\n");
        String gameName = getNextFOWGameName();
        String lastGame = getLastFOWGameName();
        Game game;
        if(!lastGame.equalsIgnoreCase("fow1")) {
            if (!GameManager.isValid(lastGame)) {
                BotLogger.warning(new BotLogger.LogMessageOrigin(event), "**Unable to create new games because the last game cannot be found. Was it deleted but the roles still exist?**");
                return;
            }
            game = GameManager.getManagedGame(lastGame).getGame();
            if (game != null && game.getCustomName().equalsIgnoreCase(gameSillyName)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "The custom name of the last game is the same as the one for this game, so the bot suspects a double press " +
                        "occurred and is cancelling the creation of another game.");
                return;
            }
        }

        Member gm = event.getGuild().getMemberById(StringUtils.substringBefore(StringUtils.substringBetween(buttonMsg, "GM: ", "\n"), "."));
        List<Member> members = new ArrayList<>();
        for (int i = 4; i <= 10; i++) {
            if (StringUtils.countMatches(buttonMsg, ":") < (i)) {
                break;
            }
            String user = buttonMsg.split(":")[i];
            user = StringUtils.substringBefore(user, ".");
            Member member = event.getGuild().getMemberById(user);
            if (member != null) {
                members.add(member);
            }
        }

        Guild guild = findFoWGuildWithSpace(event.getGuild(), members.size() + 1);
        if (guild == null) {
            MessageHelper.sendMessageToEventChannel(event, "All FoW Server are full. Can not host a new game - please contact @Bothelper to resolve.");
            return;
        }

        event.editButton(null).queue();
        executeCreateFoWGame(guild, gameName, gameSillyName, gm, members, event.getChannel());
    }


    public static void executeCreateFoWGame(Guild guild, String gameName, String gameFunName, Member gameOwner, List<Member> members, MessageChannel eventChannel) {
        //CREATE ROLES
        Role role = guild.createRole()
            .setName(gameName)
            .setMentionable(true)
            .complete();// Must `complete` if we're using this channel as part of an interaction that saves the game

        Role roleGM = guild.createRole()
            .setName(gameName + " GM")
            .setMentionable(true)
            .complete();// Must `complete` if we're using this channel as part of an interaction that saves the game

        guild.addRoleToMember(gameOwner, roleGM).queue();
        //ADD PLAYERS TO ROLE
        for (Member member : members) {
            guild.addRoleToMember(member, role).queue();
        }

        // CREATE GAME
        Game newGame = CreateGameService.createNewGame(gameName, gameOwner);
        newGame.setCustomName(gameFunName);
        newGame.setFowMode(true);
        newGame.setFowOption(FOWOption.MANAGED_COMMS, true);
        newGame.setFowOption(FOWOption.ALLOW_AGENDA_COMMS, true);

        //ADD PLAYERS
        newGame.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        for (Member member : members) {
            newGame.addPlayer(member.getId(), member.getEffectiveName());
        }

        // CREATE CATEGORY
        Role everyone = guild.getRolesByName("@everyone", true).getFirst();
        long permission2 = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue() | Permission.MANAGE_PERMISSIONS.getRawValue() | Permission.MANAGE_THREADS.getRawValue();
        Category category = guild
            .createCategory(gameName)
            .addRolePermissionOverride(everyone.getIdLong(), 0, permission2)
            .addRolePermissionOverride(roleGM.getIdLong(), permission2, 0)
            .complete();// Must `complete` if we're using this channel as part of an interaction that saves the game

        //CREATE CHANNELS
        String newGMChannelName = gameName + "-gm-room";
        String newActionsChannelName = gameName + "-anonymous-announcements-private";
        long gameRoleID = role.getIdLong();
        long gameRoleGMID = roleGM.getIdLong();
        long permission = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();

        // CREATE GM CHANNEL
        TextChannel gmChannel = guild.createTextChannel(newGMChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleGMID, permission, 0)
            .complete();// Must `complete` if we're using this channel as part of an interaction that saves the game
        
        StringBuilder sb = new StringBuilder(roleGM.getAsMention() + " - gm room\n");
        sb.append(getInfoTextFromFile("FoWGMIntro.txt"));
        MessageHelper.sendMessageToChannel(gmChannel, sb.toString());
        HomebrewService.offerGameHomebrewButtons(gmChannel);

        // CREATE Anon Announcements CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();// Must `complete` if we're using this channel as part of an interaction that saves the game
        sb = new StringBuilder(role.getAsMention() + " - announcements channel\n");
        sb.append(getInfoTextFromFile("FoWMainChannelIntro.txt"));
        MessageHelper.sendMessageToChannel(actionsChannel, sb.toString());
        newGame.setMainChannelID(actionsChannel.getId());

        // Individual player channels
        String privateChannelIntro = getInfoTextFromFile("FoWPrivateChannelIntro.txt");
        for (Member member : members) {
            String name = member.getNickname();
            if (name == null) {
                name = member.getEffectiveName();
            }
            TextChannel memberChannel = guild.createTextChannel(gameName + "-" + name + "-private", category)
                .syncPermissionOverrides()
                .addMemberPermissionOverride(member.getIdLong(), permission, 0)
                .complete();// Must `complete` if we're using this channel as part of an interaction that saves the game
            Player player_ = newGame.getPlayer(member.getId());
            player_.setPrivateChannelID(memberChannel.getId());
            sb = new StringBuilder(member.getAsMention() + " - private channel\n");
            sb.append(privateChannelIntro);
            MessageHelper.sendMessageToChannel(memberChannel, sb.toString());
        }

        String message = "Channels have been set up:\n" +
            "> " + gameName + " " + gameFunName + "\n" +
            "> " + gmChannel.getAsMention() + "\n" +
            "> " + actionsChannel.getAsMention() + "\n";
        MessageHelper.sendMessageToChannel(eventChannel, message);

        GameManager.save(newGame, "Create FOW Game Channels");
    }

    private static String getInfoTextFromFile(String file) {
        String path = ResourceHelper.getInstance().getHelpFile(file);
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (Exception e) {
            return file + " IS BLANK";
        }
    }

    public static String getLastFOWGameName() {
        return "fow" + getLastFOWGameNumber();
    }

    public static String getNextFOWGameName() {
        return "fow" + (getLastFOWGameNumber() + 1);
    }

    private static int getLastFOWGameNumber() {
        ArrayList<Integer> existingNums = getAllExistingFOWNumbers();
        if (existingNums.isEmpty()) {
            return 1;
        }
        return Collections.max(getAllExistingFOWNumbers());
    }

    private static ArrayList<Integer> getAllExistingFOWNumbers() {
        List<Guild> guilds = AsyncTI4DiscordBot.jda.getGuilds();
        ArrayList<Integer> fowNumbers = new ArrayList<>();

        // GET ALL FOW ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            System.out.println(guild.getName());
            List<Role> fowRoles = guild.getRoles().stream()
                .filter(r -> r.getName().startsWith("fow"))
                .toList();

            //EXISTING ROLE NAMES
            for (Role role : fowRoles) {
                String fowNum = role.getName().replace("fow", "");
                if (Helper.isInteger(fowNum)) {
                    fowNumbers.add(Integer.parseInt(fowNum));
                }
            }
        }

        // GET ALL EXISTING FOW MAP NAMES
        List<String> gameNames = GameManager.getGameNames().stream()
            .filter(gameName -> gameName.startsWith("fow"))
            .toList();
        for (String gameName : gameNames) {
            String fowNum = gameName.replace("fow", "");
            if (Helper.isInteger(fowNum)) {
                fowNumbers.add(Integer.parseInt(fowNum));
            }
        }
        return fowNumbers;
    }

    public static Member getGM(SlashCommandInteractionEvent event) {
        if (Objects.nonNull(event.getOption(Constants.FOWGM))) {
            return event.getOption(Constants.FOWGM).getAsMember();
        } 
        return event.getMember();
    }

    public static List<Member> getPlayers(SlashCommandInteractionEvent event) {
        List<Member> members = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null) members.add(member);
            } else {
                break;
            }
        }
        return members;
    }

    public static boolean serverCanHostNewGame(Guild guild, int playerCount) {
        return guild != null && serverHasRoomForNewRole(guild)
            && serverHasRoomForNewChannels(guild, playerCount);
    }

    private static boolean serverHasRoomForNewRole(Guild guild) {
        int roleCount = guild.getRoles().size();
        if (roleCount >= 250) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(guild), "`CreateFoWGameService.serverHasRoomForNewRole` Cannot create a new role. Server **" + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }
        return true;
    }

    private static boolean serverHasRoomForNewChannels(Guild guild, int playerCount) {
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewGame = 1 + playerCount;
        if (channelCount > (channelMax - channelsCountRequiredForNewGame)) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(guild), "`CreateFoWGameService.serverHasRoomForNewChannels` Cannot create new channels. Server **" + guild.getName() + "** currently has " + channelCount + " channels.");
            return false;
        }
        return true;
    }

    public static Guild findFoWGuildWithSpace(Guild eventGuild, int playerCount) {
        if (serverCanHostNewGame(eventGuild, playerCount)) {
            return eventGuild;
        }

        for (Guild fowGuild : AsyncTI4DiscordBot.fowServers) {
            if (fowGuild != eventGuild && serverCanHostNewGame(fowGuild, playerCount)) {
                return fowGuild;
            }
        }
        return null;
    }
}
