package ti4.service.game;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.RegexHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.TIGLHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

@UtilityClass
public class RematchService {

    public static void rematch(Game game, GenericInteractionCreateEvent event) {
        EndGameService.gameEndStuff(game, event, true);
        secondHalfOfRematch(event, game);
    }

    public static void secondHalfOfRematch(GenericInteractionCreateEvent event, Game game) {
        String name = game.getName();
        String newName = newGameName(name);

        Guild guild = game.getGuild();
        Role gameRole = null;
        if (guild != null) {
            for (Role role : guild.getRoles()) {
                if (game.getName().equals(role.getName().toLowerCase())) {
                    gameRole = role;
                }
            }
        }

        TextChannel tableTalkChannel = game.getTableTalkChannel();
        TextChannel actionsChannel = game.getMainGameChannel();
        if ("The in-limbo PBD Archive"
                        .equals(tableTalkChannel.getParentCategory().getName())
                || "The in-limbo PBD Archive"
                        .equals(actionsChannel.getParentCategory().getName())) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "These game channels are in the archive, so they cannot have a rematch created. Please create new game channels in the \"Making New Games\" forum channel in the HUB server");
            return;
        }

        if (gameRole != null) {
            gameRole.getManager().setName(newName).queue();
        } else {
            gameRole = guild.createRole().setName(newName).setMentionable(true).complete();
            for (Player player : game.getRealPlayers()) {
                Member member = guild.getMemberById(player.getUserID());
                if (member != null) {
                    guild.addRoleToMember(member, gameRole).complete();
                }
            }

            Set<Permission> allow = Set.of(Permission.MESSAGE_MANAGE, Permission.VIEW_CHANNEL);
            tableTalkChannel.getManager().putRolePermissionOverride(gameRole.getIdLong(), allow, null);
            actionsChannel.getManager().putRolePermissionOverride(gameRole.getIdLong(), allow, null);
        }

        // CLOSE THREADS IN CHANNELS
        if (tableTalkChannel != null) {
            for (ThreadChannel threadChannel : tableTalkChannel.getThreadChannels()) {
                threadChannel.getManager().setArchived(true).queue();
            }
            String newTableName = tableTalkChannel.getName().replace(name, newName);
            game.getTableTalkChannel().getManager().setName(newTableName).queue();
        }
        if (actionsChannel != null) {
            for (ThreadChannel threadChannel : actionsChannel.getThreadChannels()) {
                threadChannel.getManager().setArchived(true).queue();
            }
            game.getActionsChannel().getManager().setName(newName + "-actions").queue();
        }
        Member gameOwner = guild.getMemberById(game.getOwnerID());
        if (gameOwner == null) {
            for (Player player : game.getPlayers().values()) {
                gameOwner = guild.getMemberById(player.getUserID());
                break;
            }
        }
        Game newGame = CreateGameService.createNewGame(newName, gameOwner);
        // ADD PLAYERS
        for (Player player : game.getPlayers().values()) {
            if (player.getFaction() != null
                    && !"neutral".equals(player.getFaction())
                    && !"null".equalsIgnoreCase(player.getFaction()))
                newGame.addPlayer(player.getUserID(), player.getUserName());
        }
        newGame.setPlayerCountForMap(newGame.getPlayers().size());
        newGame.setStrategyCardsPerPlayer(
                newGame.getSCList().size() / newGame.getPlayers().size());

        // CREATE CHANNELS
        String newGameName = game.getCustomName();
        Matcher alreadyRematch =
                Pattern.compile(" Rematch #" + RegexHelper.intRegex("num")).matcher(game.getCustomName());
        if (alreadyRematch.find()) {
            newGameName = newGameName.replace(alreadyRematch.group(), "");
            int prevMatch = Integer.parseInt(alreadyRematch.group("num"));
            newGameName += " Rematch #" + (prevMatch + 1);
        } else {
            newGameName += " Rematch #1";
        }
        newGame.setCustomName(newGameName);
        if (tableTalkChannel != null) newGame.setTableTalkChannelID(tableTalkChannel.getId());

        // CREATE ACTIONS CHANNEL AND CLEAR PINS
        String newBotThreadName = newName + Constants.BOT_CHANNEL_SUFFIX;
        newGame.setMainChannelID(actionsChannel.getId());
        actionsChannel
                .retrievePinnedMessages()
                .queue(msgs -> msgs.forEach(msg -> msg.unpin().queue()), BotLogger::catchRestError);

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread =
                actionsChannel.createThreadChannel(newBotThreadName).complete();
        newGame.setBotMapUpdatesThreadID(botThread.getId());
        newGame.setUpPeakableObjectives(5, 1);
        newGame.setUpPeakableObjectives(5, 2);
        // INTRODUCTION TO TABLETALK CHANNEL

        // INTRODUCTION TO BOT-MAP THREAD
        String botGetStartedMessage = gameRole.getAsMention() + " - bot/map channel\n"
                + "This channel is for bot slash commands and updating the map, to help keep the actions channel clean.\n"
                + "### __Use the following commands to get started:__\n"
                + "> `/map add_tile_list {mapString}`, replacing {mapString} with a TTPG map string\n"
                + "> `/player setup` to set player faction and color\n"
                + "> `/game setup` to set player count and additional options\n"
                + "> `/game set_order` to set the starting speaker order\n"
                + "\n"
                + "### __Other helpful commands:__\n"
                + "> `/game replace` to replace a player in the game with a new one\n";
        MessageHelper.sendMessageToChannelAndPin(botThread, botGetStartedMessage);
        MessageHelper.sendMessageToChannelAndPin(botThread, "Website Live Map: https://asyncti4.com/game/" + newName);

        if (game.isCompetitiveTIGLGame()) TIGLHelper.initializeTIGLGame(newGame);

        CreateGameService.presentSetupToPlayers(newGame);
        GameManager.save(newGame, "Rematch");
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }
    }

    private static final Pattern gameNamePattern = Pattern.compile("(?<prefix>[a-zA-Z]+[0-9]+)(?<rematchId>[a-z]*)");

    public static String newGameName(String oldName) {
        Matcher matcher = gameNamePattern.matcher(oldName);
        if (matcher.matches()) {
            String prefix = matcher.group("prefix");
            String existingRematch = matcher.group("rematchId");
            String newRematchId = "b";

            if (existingRematch != null && !existingRematch.isBlank())
                newRematchId = StringHelper.nextId(existingRematch);
            return prefix + newRematchId;
        }

        // old formula if the regex fails
        int charValue = oldName.charAt(oldName.length() - 1);
        String present = oldName.substring(oldName.length() - 1);
        String next = String.valueOf((char) (charValue + 1));

        if (ButtonHelper.isNumeric(present)) {
            return oldName + "b";
        } else {
            return oldName.substring(0, oldName.length() - 1) + next;
        }
    }
}
