package ti4.buttons.handlers.game;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.commands.CommandHelper;
import ti4.helpers.SearchGameHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.service.game.CreateGameService;
import ti4.spring.jda.JdaService;

@UtilityClass
class CreateGameButtonHandler {

    @ButtonHandler("createGameChannels")
    public static void createGameChannelsButton(ButtonInteractionEvent event) {
        createGameChannels(event);
    }

    private static void createGameChannels(ButtonInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(
                event, event.getUser().getEffectiveName() + " pressed the [Create Game] button");

        if (!CreateGameService.isGameCreationAllowed()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Admins have temporarily turned off game creation, "
                            + "most likely to contain a bug. Please be patient and they'll get back to you on when it's fixed.");
            return;
        }

        if (CreateGameService.isLockedFromCreatingGames(event)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "You created a game within the last 10 minutes and thus are being stopped from creating more until some time "
                            + "has passed. You can have someone else in the game press the button instead.");
            return;
        }

        String buttonMsg = event.getMessage().getContentRaw();
        String gameSillyName = StringUtils.substringBetween(buttonMsg, "Game Fun Name: ", "\n");
        String gameName = CreateGameService.getNextGameName();
        String lastGameName = CreateGameService.getLastGameName();

        if (!GameManager.isValid(lastGameName)) {
            BotLogger.error(
                    new LogOrigin(event),
                    "**Unable to create new games because the last game `" + lastGameName + "` cannot be found."
                            + " Was it deleted but the roles still exist?**");
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "@Bothelper check if the supposed latest PBD game `" + lastGameName
                            + "` exists using `/game info game_name:pbd#`."
                            + " If not, you will need to create this game with"
                            + " `/bothelper create_game_channels game_fun_name:<whatever-name> game_name:<missing"
                            + " pbd# + 1> player1:.......`");
            return;
        }

        if (isLikelyDoublePressedButton(gameName, gameSillyName, lastGameName, event)) return;

        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 3; i <= 10; i++) {
            if (StringUtils.countMatches(buttonMsg, ":") < (i)) {
                break;
            }
            String user = buttonMsg.split(":")[i];
            user = StringUtils.substringBefore(user, ".");
            Member member = event.getGuild().getMemberById(user);
            if (member != null) {
                members.add(member);
                if (!member.getUser().isBot()
                        && !CommandHelper.hasRole(event, JdaService.developerRoles)
                        && !CommandHelper.hasRole(event, JdaService.bothelperRoles)) {
                    int ongoingAmount = SearchGameHelper.searchGames(
                            member.getUser(), event, false, false, false, true, false, true, true, true);
                    int completedAndOngoingAmount = SearchGameHelper.searchGames(
                            member.getUser(), event, false, true, false, true, false, true, true, true);
                    int completedGames = completedAndOngoingAmount - ongoingAmount;
                    if (ongoingAmount > completedGames + 2) {
                        MessageHelper.sendMessageToChannel(
                                event.getChannel(),
                                member.getUser().getAsMention()
                                        + " is at their game limit (# of ongoing games must be equal or less than # of completed games + 3) and so cannot join more games at the moment."
                                        + " Their number of ongoing games is " + ongoingAmount
                                        + " and their number of completed games is " + completedGames + ".\n\n"
                                        + "If you're playing a private game with friends, you can ping a bothelper for a 1-game exemption from the limit.");
                        return;
                    }
                    // Used for specific people we are limiting the amount of games of
                    if ("163392891148959744".equalsIgnoreCase(member.getId())
                            || "774413088072925226".equalsIgnoreCase(member.getId())) {
                        if (ongoingAmount > 4) {
                            MessageHelper.sendMessageToChannel(
                                    event.getChannel(),
                                    member.getUser().getAsMention()
                                            + " is currently under a 5-game limit and cannot join more games at this time");
                            return;
                        }
                    }
                }
            }
            if (gameOwner == null) gameOwner = member;
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
        if (categoryChannel == null) categoryChannel = CreateGameService.createNewGameCategory(categoryChannelName);
        if (categoryChannel == null) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Could not automatically find a category that begins with **" + categoryChannelName
                            + "** - Please create this category.\n# Warning, this may mean all servers are at capacity.");
            return;
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        Game game = CreateGameService.createGameChannels(
                members, event, gameSillyName, gameName, gameOwner, categoryChannel);
        if (game != null) {
            GameManager.save(
                    game, "Created game channels"); // TODO: We should be locking since we're saving? Maybe not here
        }
    }

    private static boolean isLikelyDoublePressedButton(
            String gameName, String gameSillyName, String lastGameName, ButtonInteractionEvent event) {
        if ("pbd1".equalsIgnoreCase(gameName)) return false;

        Game lastGame = GameManager.getManagedGame(lastGameName).getGame();
        boolean lastGameHasSameSillyName = gameSillyName.equalsIgnoreCase(lastGame.getCustomName());
        if (!lastGameHasSameSillyName) {
            return false;
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "The custom name of the last game is the same as the one for this game, so the bot suspects a double press "
                        + "occurred and is cancelling the creation of another game.");
        return true;
    }
}
