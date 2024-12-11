package ti4.commands2.bothelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;

class CreateFOWGameChannels extends Subcommand {

    public CreateFOWGameChannels() {
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
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        //GAME NAME
        String gameName = getNextFOWGameName();

        //CHECK IF GIVEN CATEGORY IS VALID
        Guild guild = event.getGuild();
        if (guild == null) {
            MessageHelper.sendMessageToEventChannel(event, "Guild was null");
            return;
        }

        //CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!serverCanHostNewGame(guild)) {
            MessageHelper.sendMessageToEventChannel(event, "Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        //PLAYERS
        List<Member> members = new ArrayList<>();
        Member gameOwner;
        if (Objects.nonNull(event.getOption("fowgm"))) {
            gameOwner = event.getOption("fowgm").getAsMember();
        } else {
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
        List<String> guildMemberIDs = guild.getMembers().stream().map(ISnowflake::getId).toList();
        boolean sendInviteLink = false;
        int count = 0;
        for (Member member : members) {
            if (!guildMemberIDs.contains(member.getId())) {
                MessageHelper.sendMessageToEventChannel(event, member.getAsMention() + " is not a member of the server **" + guild.getName() + "**. Please use the invite below to join the server and then try this command again.");
                sendInviteLink = true;
                count++;
            }
        }
        if (sendInviteLink) {
            MessageHelper.sendMessageToEventChannel(event, Helper.getGuildInviteURL(guild, count + 1));
            return;
        }

        //CREATE ROLES
        Role role = guild.createRole()
            .setName(gameName)
            .setMentionable(true)
            .queue();

        Role roleGM = guild.createRole()
            .setName(gameName + " GM")
            .setMentionable(true)
            .queue();

        guild.addRoleToMember(gameOwner, roleGM).queue();
        //ADD PLAYERS TO ROLE
        for (Member member : members) {
            guild.addRoleToMember(member, role).queue();
        }

        // CREATE GAME
        Game newGame = CreateGameService.createNewGame(gameName, gameOwner);
        newGame.setFowMode(true);

        //ADD PLAYERS
        newGame.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        for (Member member : members) {
            newGame.addPlayer(member.getId(), member.getEffectiveName());
        }

        // CREATE CATEGORY
        Role everyone = guild.getRolesByName("@everyone", true).getFirst();
        long permission2 = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue() | Permission.MANAGE_PERMISSIONS.getRawValue() | Permission.MANAGE_THREADS.getRawValue();
        Category category = guild.createCategory(gameName).addRolePermissionOverride(everyone.getIdLong(), 0, permission2).addRolePermissionOverride(roleGM.getIdLong(), permission2, 0).queue();

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
            .queue();
        MessageHelper.sendMessageToChannel(gmChannel, roleGM.getAsMention() + " - gm room");
        CreateGameService.offerGameHomebrewButtons(gmChannel);

        // CREATE Anon Announcements CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .queue();
        MessageHelper.sendMessageToChannel(actionsChannel, role.getAsMention() + " - actions channel");
        newGame.setMainChannelID(actionsChannel.getId());

        // Individual player channels
        for (Member member : members) {
            String name = member.getNickname();
            if (name == null) {
                name = member.getEffectiveName();
            }
            TextChannel memberChannel = guild.createTextChannel(gameName + "-" + name + "-private", category)
                .syncPermissionOverrides()
                .addMemberPermissionOverride(member.getIdLong(), permission, 0)
                .queue();
            Player player_ = newGame.getPlayer(member.getId());
            player_.setPrivateChannelID(memberChannel.getId());
        }

        String message = "Role and Channels have been set up:\n" + "> " + role.getName() + "\n" +
            "> " + gmChannel.getAsMention() + "\n" +
            "> " + actionsChannel.getAsMention() + "\n";
        MessageHelper.sendMessageToEventChannel(event, message);

        GameManager.save(newGame, "Create FOW Game Channels");
    }

    private static String getNextFOWGameName() {
        ArrayList<Integer> existingNums = getAllExistingFOWNumbers();
        if (existingNums.isEmpty()) {
            return "fow1";
        }
        int nextPBDNumber = Collections.max(getAllExistingFOWNumbers()) + 1;
        return "fow" + nextPBDNumber;
    }

    private static ArrayList<Integer> getAllExistingFOWNumbers() {
        List<Guild> guilds = AsyncTI4DiscordBot.jda.getGuilds();
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
        List<String> gameNames = GameManager.getGameNames().stream()
            .filter(gameName -> gameName.startsWith("fow"))
            .toList();
        for (String gameName : gameNames) {
            String pbdNum = gameName.replace("fow", "");
            if (Helper.isInteger(pbdNum)) {
                pbdNumbers.add(Integer.parseInt(pbdNum));
            }
        }
        return pbdNumbers;
    }

    private static boolean serverCanHostNewGame(Guild guild) {
        return guild != null && serverHasRoomForNewRole(guild)
            && serverHasRoomForNewChannels(guild);
    }

    private static boolean serverHasRoomForNewRole(Guild guild) {
        int roleCount = guild.getRoles().size();
        if (roleCount >= 250) {
            BotLogger.log("`CreateGameService.serverHasRoomForNewRole` Cannot create a new role. Server **" + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }
        return true;
    }

    private static boolean serverHasRoomForNewChannels(Guild guild) {
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewGame = 2;
        if (channelCount > (channelMax - channelsCountRequiredForNewGame)) {
            BotLogger.log("`CreateGameService.serverHasRoomForNewChannels` Cannot create new channels. Server **" + guild.getName() + "** currently has " + channelCount + " channels.");
            return false;
        }
        return true;
    }
}
