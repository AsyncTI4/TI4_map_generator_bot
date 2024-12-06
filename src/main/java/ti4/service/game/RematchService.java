package ti4.service.game;

import java.util.ArrayList;
import java.util.List;
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
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.RegexHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class RematchService {

    public static void rematch(Game game, GenericInteractionCreateEvent event) {
        EndGameService.gameEndStuff(game, event, true);
        secondHalfOfRematch(event, game);
    }

    public static void secondHalfOfRematch(GenericInteractionCreateEvent event, Game game) {
        String name = game.getName();
        int charValue = name.charAt(name.length() - 1);
        String present = name.substring(name.length() - 1);
        String next = String.valueOf((char) (charValue + 1));
        String newName;
        if (ButtonHelper.isNumeric(present)) {
            newName = name + "b";
        } else {
            newName = name.substring(0, name.length() - 1) + next;
        }

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
        if (gameRole != null) {
            gameRole.getManager().setName(newName).queue();
        } else {
            gameRole = guild.createRole()
                .setName(newName)
                .setMentionable(true)
                .complete();
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
        Game newGame = CreateGameService.createNewGame(event, newName, gameOwner);
        // ADD PLAYERS
        for (Player player : game.getPlayers().values()) {
            if (!player.getFaction().equals("neutral"))
                newGame.addPlayer(player.getUserID(), player.getUserName());
        }
        newGame.setPlayerCountForMap(newGame.getPlayers().size());
        newGame.setStrategyCardsPerPlayer(newGame.getSCList().size() / newGame.getPlayers().size());

        // CREATE CHANNELS
        String newGameName = game.getCustomName();
        Matcher alreadyRematch = Pattern.compile(" Rematch #" + RegexHelper.intRegex("num"))
            .matcher(game.getCustomName());
        if (alreadyRematch.find()) {
            newGameName = newGameName.replace(alreadyRematch.group(), "");
            int prevMatch = Integer.parseInt(alreadyRematch.group("num"));
            newGameName = newGameName + " Rematch #" + (prevMatch + 1);
        } else {
            newGameName = newGameName + " Rematch #1";
        }
        newGame.setCustomName(newGameName);
        if (tableTalkChannel != null)
            newGame.setTableTalkChannelID(tableTalkChannel.getId());

        // CREATE ACTIONS CHANNEL AND CLEAR PINS
        String newBotThreadName = newName + Constants.BOT_CHANNEL_SUFFIX;
        newGame.setMainChannelID(actionsChannel.getId());
        actionsChannel.retrievePinnedMessages().queue(msgs -> msgs.forEach(msg -> msg.unpin().queue()), BotLogger::catchRestError);

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName)
            .complete();
        newGame.setBotMapUpdatesThreadID(botThread.getId());
        newGame.setUpPeakableObjectives(5, 1);
        newGame.setUpPeakableObjectives(5, 2);
        // INTRODUCTION TO TABLETALK CHANNEL
        String tabletalkGetStartedMessage = gameRole.getAsMention() + " - table talk channel\n" +
            "This channel is for typical over the table converstion, as you would over the table while playing the game in real life.\n"
            +
            "If this group has agreed to whispers (secret conversations), you may create private threads off this channel.\n"
            +
            "Typical things that go here are: general conversation, deal proposals, memes - everything that isn't either an actual action in the game or a bot command\n";
        MessageHelper.sendMessageToChannelAndPin(tableTalkChannel, tabletalkGetStartedMessage);

        // INTRODUCTION TO ACTIONS CHANNEL
        String actionsGetStartedMessage = gameRole.getAsMention() + " - actions channel\n" +
            "This channel is for taking actions in the game, primarily using buttons or the odd slash command.\n" +
            "Please keep this channel clear of any chat with other players. Ideally this channel is a nice clean ledger of what has physically happened in the game.\n";
        MessageHelper.sendMessageToChannelAndPin(actionsChannel, actionsGetStartedMessage);
        ButtonHelper.offerPlayerSetupButtons(actionsChannel, newGame);

        // INTRODUCTION TO BOT-MAP THREAD
        String botGetStartedMessage = gameRole.getAsMention() + " - bot/map channel\n" +
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
        MessageHelper.sendMessageToChannelAndPin(botThread, "Website Live Map: https://ti4.westaddisonheavyindustries.com/game/" + newName);

        List<Button> buttons2 = new ArrayList<>();
        buttons2.add(Buttons.green("getHomebrewButtons", "Yes, have homebrew"));
        buttons2.add(Buttons.red("deleteButtons", "No Homebrew"));
        MessageHelper.sendMessageToChannel(actionsChannel, "If you plan to have a supported homebrew mode in this game, please indicate so with these buttons", buttons2);
        GameManager.save(newGame, event);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }
    }
}
