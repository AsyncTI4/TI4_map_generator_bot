package ti4.buttons.handlers.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.map.PersistenceManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.metadata.GameCreationLocks;
import ti4.service.game.CreateGameService;

@UtilityClass
class CreateGameButtonHandler {

    @ButtonHandler("createGameChannels")
    public static void createGameChannelsButton(ButtonInteractionEvent event) {
        AsyncTI4DiscordBot.runAsync("Create game channels button task", () -> createGameChannels(event));
    }

    private static void createGameChannels(ButtonInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, event.getUser().getEffectiveName() + " pressed the [Create Game] button");
        Game mapreference = GameManager.getGame("finreference");

        if (mapreference != null && mapreference.getStoredValue("allowedButtonPress").equalsIgnoreCase("false")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Admins have temporarily turned off game creation, most likely to contain a bug. Please be patient and they'll get back to you on when it's fixed.");
            return;
        }

        if (isLockedFromCreatingGames(event)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "You created a game within the last 10 minutes and thus are being stopped from creating more until some time has passed. You can have someone else in the game press the button instead. ");
            return;
        }

        String buttonMsg = event.getMessage().getContentRaw();
        String gameSillyName = StringUtils.substringBetween(buttonMsg, "Game Fun Name: ", "\n");
        String gameName = CreateGameService.getNextGameName();
        String lastGame = CreateGameService.getLastGameName();
        Game game = GameManager.getGame(lastGame);
        if (game != null && game.getCustomName().equalsIgnoreCase(gameSillyName)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "The custom name of the last game is the same as the one for this game, so the bot suspects a double press occurred and is cancelling the creation of another game. ");
            return;
        }
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 3; i <= 10; i++) {
            if (StringUtils.countMatches(buttonMsg, ":") < (i)) {
                break;
            }
            String user = buttonMsg.split(":")[i];
            user = StringUtils.substringBefore(user, ".");
            Member member2 = event.getGuild().getMemberById(user);
            if (member2 != null)
                members.add(member2);
            if (gameOwner == null)
                gameOwner = member2;
        }

        // CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = CreateGameService.getCategoryNameForGame(gameName);
        Category categoryChannel = null;
        List<Category> categories = CreateGameService.getAllAvailablePBDCategories();
        for (Category category : categories) {
            if (category.getName().toUpperCase().startsWith(categoryChannelName)) {
                categoryChannel = category;
                break;
            }
        }
        if (categoryChannel == null)
            categoryChannel = CreateGameService.createNewCategory(categoryChannelName);
        if (categoryChannel == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not automatically find a category that begins with **" + categoryChannelName
                + "** - Please create this category.\n# Warning, this may mean all servers are at capacity.");
            return;
        }
        event.getMessage().delete().queue();
        CreateGameService.createGameChannels(members, event, gameSillyName, gameName, gameOwner, categoryChannel);
    }

    private static boolean isLockedFromCreatingGames(GenericInteractionCreateEvent event) {
        Member member = event.getMember();
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.bothelperRoles) {
                if (roles.contains(role)) {
                    return false;
                }
            }
        }

        try {
            GameCreationLocks gameCreationLocks = PersistenceManager.readObjectFromJsonFile(GameCreationLocks.JSON_DATA_FILE_NAME, GameCreationLocks.class);
            if (gameCreationLocks == null) {
                gameCreationLocks = new GameCreationLocks();
            }
            String userId = event.getUser().getId();
            boolean isGameCreationLocked = gameCreationLocks.getUsernameToLastGameCreation().containsKey(userId);
            if (isGameCreationLocked) {
                return true;
            }
            gameCreationLocks.getUsernameToLastGameCreation().put(userId, Instant.now());
            PersistenceManager.writeObjectToJsonFile(GameCreationLocks.JSON_DATA_FILE_NAME, gameCreationLocks);
        } catch (Exception e) {
            BotLogger.log("Unable to handle game creation locks.", e);
        }
        return false;
    }

    @ButtonHandler("offerGameHomebrewButtons")
    public static void offerGameHomebrewButtons(MessageChannel channel) {
        CreateGameService.offerGameHomebrewButtons(channel);
    }
}
