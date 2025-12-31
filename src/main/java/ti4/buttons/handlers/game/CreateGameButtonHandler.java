package ti4.buttons.handlers.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.commands.CommandHelper;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.SearchGameHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.service.game.CreateGameService;
import ti4.service.statistics.AverageTurnTimeService;
import ti4.spring.jda.JdaService;

@UtilityClass
class CreateGameButtonHandler {

    @ButtonHandler("createGameChannels")
    @ButtonHandler("launchGame")
    public static void createGameChannelsButton(ButtonInteractionEvent event) {
        createGameChannels(event);
    }

    @ModalHandler("signupModal")
    public static void finishSignup(ModalInteractionEvent event) {
        List<Member> members = event.getValue("players").getAsMentions().getMembers();
        List<Member> membersOG = fetchMembersFromMessage(event);
        for (Member member : members) {
            if (membersOG.contains(member)) continue;
            membersOG.add(member);
            MessageHelper.sendMessageToEventChannel(event, member.getAsMention() + " joined the game.");
        }
        event.getMessage().editMessage(generateMemberListMessage(membersOG)).queue();
    }

    @ButtonHandler("editPlayers~MDL")
    public static void editPlayers(ButtonInteractionEvent event) {
        String modalID = "signupModal";
        String fieldID = "players";
        EntitySelectMenu menu = EntitySelectMenu.create(fieldID, SelectTarget.USER)
                .setPlaceholder("Choose your players") // shows the placeholder indicating what this menu is for
                .setRequiredRange(1, 8)
                .build();

        Modal modal = Modal.create(modalID, "Players For The Game")
                .addComponents(Label.of("Select Players", menu))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static List<Member> fetchMembersFromMessage(ButtonInteractionEvent event) {
        String buttonMsg = event.getMessage().getContentRaw();
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < StringUtils.countMatches(buttonMsg, "<@"); i++) {
            String user = buttonMsg.split("@")[i + 1];
            user = StringUtils.substringBefore(user, ">");
            Member member = event.getGuild().getMemberById(user);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    public static List<Member> fetchMembersFromMessage(ModalInteractionEvent event) {
        String buttonMsg = event.getMessage().getContentRaw();
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < StringUtils.countMatches(buttonMsg, "<@"); i++) {
            String user = buttonMsg.split("@")[i + 1];
            user = StringUtils.substringBefore(user, ">");
            Member member = event.getGuild().getMemberById(user);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    public static String generateMemberListMessage(List<Member> members) {
        StringBuilder memberList = new StringBuilder();
        int x = 1;
        List<User> users = members.stream().map(Member::getUser).toList();
        List<ManagedGame> userGames = users.stream()
                .map(user -> GameManager.getManagedPlayer(user.getId()))
                .filter(Objects::nonNull)
                .map(ManagedPlayer::getGames)
                .flatMap(Collection::stream)
                .distinct()
                .toList();

        Map<String, Map.Entry<Integer, Long>> playerTurnTimes = new HashMap<>();
        for (ManagedGame game : userGames) {
            AverageTurnTimeService.getAverageTurnTimeForGame(game.getGame(), playerTurnTimes, new HashMap<>());
        }
        for (Member member : members) {
            memberList.append(x + ". " + member.getUser().getAsMention());

            int ongoingAmount = SearchGameHelper.searchGames(
                    member.getUser(), null, false, false, false, true, false, true, true, true);
            int completedAndOngoingAmount = SearchGameHelper.searchGames(
                    member.getUser(), null, false, true, false, true, false, true, true, true);
            int completedGames = completedAndOngoingAmount - ongoingAmount;
            if (ongoingAmount > completedGames + 2) {
                memberList
                        .append(" ⚠️ (Above game limit: ")
                        .append(ongoingAmount)
                        .append(" ongoing, ")
                        .append(completedGames + 2)
                        .append("-game limit)");
            } else {
                memberList.append(" " + completedGames + " games completed. ");
            }
            if (playerTurnTimes.containsKey(member.getUser().getId())) {
                User user = member.getUser();
                int turnCount = playerTurnTimes.get(user.getId()).getKey();
                long totalMillis = playerTurnTimes.get(user.getId()).getValue();
                if (turnCount == 0 || totalMillis == 0) continue;
                long averageTurnTime = totalMillis / turnCount;
                memberList.append("`").append(" ");
                memberList.append(DateTimeHelper.getTimeRepresentationToSeconds(averageTurnTime));
                memberList.append("` average turn time.");
            }
            memberList.append("\n");
            x++;
        }
        return memberList.toString();
    }

    @ButtonHandler("joinGameList")
    public static void joinGameList(ButtonInteractionEvent event) {
        List<Member> members = fetchMembersFromMessage(event);
        if (!members.contains(event.getMember())) {
            members.add(event.getMember());
        }
        event.getMessage().editMessage(generateMemberListMessage(members)).queue();
        MessageHelper.sendMessageToEventChannel(event, event.getUser().getEffectiveName() + " joined the game.");
    }

    @ButtonHandler("leaveGameList")
    public static void leaveGameList(ButtonInteractionEvent event) {
        List<Member> members = fetchMembersFromMessage(event);
        members.remove(event.getMember());
        event.getMessage().editMessage(generateMemberListMessage(members)).queue();
        MessageHelper.sendMessageToEventChannel(event, event.getUser().getEffectiveName() + " left the game.");
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
        String gameSillyName = "tabletalk";
        // StringUtils.substringBetween(buttonMsg, "Game Fun Name: ", "\n");
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
            if (!fetchMembersFromMessage(event).isEmpty()) {
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
        if (members.isEmpty()) {
            members = fetchMembersFromMessage(event);
            if (gameOwner == null) members.get(0);
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
