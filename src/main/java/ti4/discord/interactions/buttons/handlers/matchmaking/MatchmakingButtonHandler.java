package ti4.discord.interactions.buttons.handlers.matchmaking;

import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.MAX_QUEUE_TIME_OPTIONS_TO_HOURS;
import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.NO_PACE_OPTION;
import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.PACE_RESTRICTION_OPTIONS;
import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.PLAYER_COUNT_OPTIONS;
import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.RESTRICTION_OPTIONS;
import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.VICTORY_POINT_OPTIONS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroup;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedPlayer;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;
import ti4.spring.service.statistics.UserGameInfoService;
import ti4.spring.service.statistics.matchmaking.MatchmakerService;
import ti4.spring.service.statistics.matchmaking.ViewMatchmakingQueueService;

@UtilityClass
class MatchmakingButtonHandler {

    private static final String QUEUE_FOR_GAME_BUTTON_ID = "queueForGame~MDL";
    private static final String QUEUE_AS_GROUP_BUTTON_ID = "queueAsGroup~MDL";
    private static final String LEAVE_QUEUE_BUTTON_ID = "leaveQueueForGame";
    private static final String VIEW_QUEUE_BUTTON_ID = "viewMatchmakingQueue";
    private static final String ADDITIONAL_SETTINGS_BUTTON_ID = "queueForGameAdditionalSettings~MDL";
    private static final String PICK_GROUP_MEMBERS_BUTTON_ID = "queueGroupPickMembers~MDL";
    private static final String QUEUE_FOR_GAME_MODAL_ID = "queueForGameModal";
    private static final String QUEUE_AS_GROUP_MODAL_ID = "queueAsGroupModal";
    private static final String GROUP_MEMBERS_MODAL_ID = "queueGroupMembersModal";
    private static final String ADDITIONAL_SETTINGS_MODAL_ID = "queueForGameAdditionalSettingsModal";

    private static final String EXPANSIONS_ID = "queue_expansions";
    private static final String PLAYER_COUNTS_ID = "queue_player_counts";
    private static final String VICTORY_POINTS_ID = "queue_victory_points";
    private static final String PACE_RESTRICTIONS_ID = "queue_pace_restrictions";
    private static final String RESTRICTIONS_ID = "queue_restrictions";
    private static final String MAX_QUEUE_TIME_ID = "queue_max_time";
    private static final String AVOID_PLAYERS_ID = "queue_avoid_players";
    private static final String GROUP_MEMBERS_ID = "queue_group_members";
    private static final int MAX_GROUP_MEMBERS = 7;

    private static final String DEFAULT_MAX_QUEUE_TIME = "8 hours";
    private static final List<String> DEFAULT_EXPANSION_OPTIONS =
            List.of(MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION);
    private static final List<String> DEFAULT_PLAYER_COUNT_OPTIONS = List.of("6");
    private static final List<String> DEFAULT_VICTORY_POINT_OPTIONS = List.of("10");
    private static final List<String> DEFAULT_PACE_OPTIONS = List.of(NO_PACE_OPTION);
    private static final List<String> DEFAULT_RESTRICTION_OPTIONS =
            List.of(MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION, MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION);
    private static final int MAX_AVOID_PLAYERS = 25;

    @ButtonHandler(value = QUEUE_FOR_GAME_BUTTON_ID, save = false)
    public static void offerQueueForGameModal(ButtonInteractionEvent event) {
        if (rejectIfCannotQueue(event)) return;
        event.replyModal(buildQueueModal(QUEUE_FOR_GAME_MODAL_ID, "Queue for Game", event))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = QUEUE_AS_GROUP_BUTTON_ID, save = false)
    public static void offerQueueAsGroupModal(ButtonInteractionEvent event) {
        if (rejectIfCannotQueue(event)) return;
        event.replyModal(buildQueueModal(QUEUE_AS_GROUP_MODAL_ID, "Queue as Group", event))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static boolean rejectIfCannotQueue(ButtonInteractionEvent event) {
        MatchmakerService matchmakerService = SpringContext.getBean(MatchmakerService.class);
        if (MatchmakerService.isQueueingDisabled()) {
            event.reply("Queueing is currently disabled.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return true;
        }
        if (matchmakerService.isUserQueued(event.getUser().getId())) {
            event.reply(
                            "You are already queued for a game. To change your preferences, you must first leave the queue.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return true;
        }
        return false;
    }

    private static Modal buildQueueModal(String modalId, String title, ButtonInteractionEvent event) {
        String userId = event.getUser().getId();
        UserSettings userSettings = UserSettingsManager.get(userId);

        final boolean REQUIRE_SELECTION = true;
        CheckboxGroup expansions = buildCheckboxGroup(
                EXPANSIONS_ID,
                MatchmakingOptions.EXPANSION_OPTIONS,
                userSettings.getMatchmakingExpansions(),
                DEFAULT_EXPANSION_OPTIONS,
                REQUIRE_SELECTION);
        CheckboxGroup playerCounts = buildCheckboxGroup(
                PLAYER_COUNTS_ID,
                PLAYER_COUNT_OPTIONS,
                userSettings.getMatchmakingPlayerCounts(),
                DEFAULT_PLAYER_COUNT_OPTIONS,
                REQUIRE_SELECTION);
        CheckboxGroup victoryPoints = buildCheckboxGroup(
                VICTORY_POINTS_ID,
                VICTORY_POINT_OPTIONS,
                userSettings.getMatchmakingVictoryPointGoals(),
                DEFAULT_VICTORY_POINT_OPTIONS,
                REQUIRE_SELECTION);
        CheckboxGroup paces = buildCheckboxGroup(
                PACE_RESTRICTIONS_ID,
                filterPaceRestrictionsByIfPlayerHasCompletedRequiredGame(userId),
                userSettings.getMatchmakingPaces(),
                DEFAULT_PACE_OPTIONS,
                REQUIRE_SELECTION);
        CheckboxGroup restrictions = buildCheckboxGroup(
                RESTRICTIONS_ID,
                filterRestrictionOptionsForMember(event),
                userSettings.getMatchmakingRestrictions(),
                DEFAULT_RESTRICTION_OPTIONS,
                !REQUIRE_SELECTION);

        return Modal.create(modalId, title)
                .addComponents(Label.of("Expansions", expansions))
                .addComponents(Label.of("Player Count", playerCounts))
                .addComponents(Label.of("Victory Point Goal", victoryPoints))
                .addComponents(Label.of("Pace", paces))
                .addComponents(Label.of("Restrictions", restrictions))
                .build();
    }

    @ButtonHandler(value = ADDITIONAL_SETTINGS_BUTTON_ID, save = false)
    public static void offerQueueAdditionalSettingsModal(ButtonInteractionEvent event) {
        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        List<String> selectedMaxQueueTime = userSettings.getMatchmakingMaxQueueTime() == null
                ? List.of()
                : List.of(userSettings.getMatchmakingMaxQueueTime());
        StringSelectMenu maxQueueTime = buildSingleSelect(
                MAX_QUEUE_TIME_ID,
                MAX_QUEUE_TIME_OPTIONS_TO_HOURS.keySet(),
                selectedMaxQueueTime,
                List.of(DEFAULT_MAX_QUEUE_TIME));
        EntitySelectMenu avoidPlayers = EntitySelectMenu.create(AVOID_PLAYERS_ID, SelectTarget.USER)
                .setRequired(false)
                .setMaxValues(MAX_AVOID_PLAYERS)
                .setDefaultValues(userSettings.getMatchmakingAvoidList().stream()
                        .map(EntitySelectMenu.DefaultValue::user)
                        .toList())
                .build();

        Modal modal = Modal.create(ADDITIONAL_SETTINGS_MODAL_ID, "Additional Queue Settings")
                .addComponents(Label.of("Max Queue Time", maxQueueTime))
                .addComponents(Label.of("Avoid List", avoidPlayers))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = LEAVE_QUEUE_BUTTON_ID, save = false)
    public static void leaveQueue(ButtonInteractionEvent event) {
        MatchmakerService matchmakerService = SpringContext.getBean(MatchmakerService.class);
        if (MatchmakerService.isQueueingDisabled()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Leaving queue is currently disabled. Try again later.");
            return;
        }
        matchmakerService.leaveQueue(event.getUser().getId());
        MessageHelper.sendEphemeralMessageToEventChannel(event, "You have left the matchmaking queue.");
    }

    @ButtonHandler(value = VIEW_QUEUE_BUTTON_ID, save = false)
    public static void viewQueue(ButtonInteractionEvent event) {
        ViewMatchmakingQueueService viewMatchmakingQueueService =
                SpringContext.getBean(ViewMatchmakingQueueService.class);
        List<MessageEmbed> embeds =
                viewMatchmakingQueueService.describeQueueFor(event.getUser().getId());
        for (MessageEmbed embed : embeds) {
            event.getHook()
                    .setEphemeral(true)
                    .sendMessageEmbeds(embed)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    @ModalHandler(QUEUE_FOR_GAME_MODAL_ID)
    public static void submitQueueForGameModal(ModalInteractionEvent event) {
        String userId = event.getUser().getId();
        UserSettings userSettings = UserSettingsManager.get(userId);

        if (isPlayerAtGameLimit(event, userId, userSettings)) return;

        saveMatchmakingPreferences(event, userSettings);

        SpringContext.getBean(MatchmakerService.class).queueUser(userId);

        event.getHook()
                .setEphemeral(true)
                .sendMessage("You have been added to the matchmaking queue.")
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(QUEUE_AS_GROUP_MODAL_ID)
    public static void submitQueueAsGroupModal(ModalInteractionEvent event) {
        String userId = event.getUser().getId();
        UserSettings userSettings = UserSettingsManager.get(userId);

        if (isPlayerAtGameLimit(event, userId, userSettings)) return;

        // Save the leader's preferences; the whole group will queue under them once members are picked.
        saveMatchmakingPreferences(event, userSettings);

        // A member picker can't live in the same modal (Discord caps modals at 5 components), so offer a
        // button that opens a second modal for choosing the group.
        event.getHook()
                .setEphemeral(true)
                .sendMessage("Your preferences are saved. Now pick the players to queue with.")
                .addComponents(ActionRow.of(Buttons.green(PICK_GROUP_MEMBERS_BUTTON_ID, "Pick Group Members")))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = PICK_GROUP_MEMBERS_BUTTON_ID, save = false)
    public static void offerGroupMembersModal(ButtonInteractionEvent event) {
        if (rejectIfCannotQueue(event)) return;

        EntitySelectMenu memberSelect = EntitySelectMenu.create(GROUP_MEMBERS_ID, SelectTarget.USER)
                .setRequiredRange(1, MAX_GROUP_MEMBERS)
                .build();
        Modal modal = Modal.create(GROUP_MEMBERS_MODAL_ID, "Pick Group Members")
                .addComponents(Label.of("Group Members (up to " + MAX_GROUP_MEMBERS + ")", memberSelect))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(GROUP_MEMBERS_MODAL_ID)
    public static void submitGroupMembersModal(ModalInteractionEvent event) {
        MatchmakerService matchmakerService = SpringContext.getBean(MatchmakerService.class);
        String leaderId = event.getUser().getId();

        List<String> memberIds = getSelectedUserIds(event, GROUP_MEMBERS_ID).stream()
                .filter(id -> !id.equals(leaderId))
                .distinct()
                .toList();
        if (memberIds.isEmpty()) {
            replyToGroupModal(event, "Select at least one other player to queue as a group.");
            return;
        }

        Optional<String> validationError = matchmakerService.validateParty(leaderId, memberIds);
        if (validationError.isPresent()) {
            replyToGroupModal(event, "Your group can't be queued: " + validationError.get());
            return;
        }

        Optional<String> queueError = matchmakerService.queueParty(leaderId, memberIds);
        if (queueError.isPresent()) {
            replyToGroupModal(event, queueError.get());
            return;
        }

        replyToGroupModal(
                event, "Your group of " + (memberIds.size() + 1) + " players has been added to the matchmaking queue.");
    }

    private static void replyToGroupModal(ModalInteractionEvent event, String message) {
        event.getHook().setEphemeral(true).sendMessage(message).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void saveMatchmakingPreferences(ModalInteractionEvent event, UserSettings userSettings) {
        userSettings.setMatchmakingExpansions(getSelectedValues(event, EXPANSIONS_ID));
        userSettings.setMatchmakingPlayerCounts(getSelectedValues(event, PLAYER_COUNTS_ID));
        userSettings.setMatchmakingVictoryPointGoals(getSelectedValues(event, VICTORY_POINTS_ID));
        userSettings.setMatchmakingPaces(getSelectedValues(event, PACE_RESTRICTIONS_ID));
        userSettings.setMatchmakingRestrictions(getSelectedValues(event, RESTRICTIONS_ID).stream()
                .filter(restriction -> !PACE_RESTRICTION_OPTIONS.contains(restriction))
                .toList());
        UserSettingsManager.save(userSettings);
    }

    @ModalHandler(ADDITIONAL_SETTINGS_MODAL_ID)
    public static void submitQueueForGameAdditionalSettingsModal(ModalInteractionEvent event) {
        List<String> selectedMaxQueueTime = getSelectedValues(event, MAX_QUEUE_TIME_ID);
        List<String> avoidedUserIds = getSelectedUserIds(event, AVOID_PLAYERS_ID);

        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        userSettings.setMatchmakingMaxQueueTime(
                selectedMaxQueueTime.isEmpty() ? DEFAULT_MAX_QUEUE_TIME : selectedMaxQueueTime.getFirst());
        userSettings.setMatchmakingAvoidList(avoidedUserIds);
        UserSettingsManager.save(userSettings);

        event.getHook()
                .setEphemeral(true)
                .sendMessage("Additional settings saved.")
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static boolean isPlayerAtGameLimit(ModalInteractionEvent event, String userId, UserSettings userSettings) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        if (managedPlayer == null) return false;

        int ongoingAmount = UserGameInfoService.countOngoingGamesThatAffectJoinLimit(managedPlayer);
        int completedGames = UserGameInfoService.countCompletedGamesThatAffectJoinLimit(managedPlayer);
        if (UserGameInfoService.isOverStandardGameLimit(managedPlayer)) {
            event.getHook()
                    .setEphemeral(true)
                    .sendMessage(
                            "You are at your game limit (# of ongoing games must be equal or less than # of completed games + 3) and so cannot queue for more games at the moment."
                                    + " Your number of ongoing games is " + ongoingAmount
                                    + " and your number of completed games is " + completedGames + ".")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return true;
        }
        if (userSettings.getGameLimit() > 0 && ongoingAmount >= userSettings.getGameLimit()) {
            event.getHook()
                    .setEphemeral(true)
                    .sendMessage("You are currently under a " + userSettings.getGameLimit()
                            + "-game limit and cannot join more games at this time.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return true;
        }
        return false;
    }

    private static List<String> filterRestrictionOptionsForMember(ButtonInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        if (member == null || guild == null) {
            return RESTRICTION_OPTIONS;
        }
        List<String> roleRestrictionOptions = MatchmakingOptions.getRoleRestrictionOptions(guild, member);
        if (roleRestrictionOptions.isEmpty()) {
            return RESTRICTION_OPTIONS;
        }
        List<String> options = new ArrayList<>(RESTRICTION_OPTIONS);
        options.addAll(roleRestrictionOptions);
        return options;
    }

    private static List<String> filterPaceRestrictionsByIfPlayerHasCompletedRequiredGame(String userId) {
        UserGameInfoService userGameInfoService = UserGameInfoService.get();
        List<String> restrictions = new ArrayList<>(PACE_RESTRICTION_OPTIONS);
        MatchmakingOptions.PACE_RESTRICTION_TO_GAME_DAYS_TO_COMPLETE_REQUIREMENT.forEach(
                (paceRestriction, gameCompletedInDaysRequirement) -> {
                    if (!userGameInfoService.hasCompletedGameInDays(userId, gameCompletedInDaysRequirement)) {
                        restrictions.remove(paceRestriction);
                    }
                });
        return restrictions;
    }

    private static CheckboxGroup buildCheckboxGroup(
            String id,
            List<String> options,
            List<String> selectedValues,
            List<String> defaultValues,
            boolean requireSelection) {
        CheckboxGroup.Builder builder = CheckboxGroup.create(id);
        for (String option : options) {
            builder.addOption(option, option);
        }
        return builder.setRequired(requireSelection)
                .setRequiredRange(requireSelection ? 1 : 0, options.size())
                .setSelectedValues(normalizeSelectedValues(selectedValues, options, defaultValues))
                .build();
    }

    private static StringSelectMenu buildSingleSelect(
            String id, Collection<String> options, List<String> selectedValues, List<String> defaultValues) {
        StringSelectMenu.Builder builder = StringSelectMenu.create(id);
        for (String option : options) {
            builder.addOptions(SelectOption.of(option, option));
        }
        return builder.setDefaultValues(normalizeSelectedValues(selectedValues, options, defaultValues))
                .setRequiredRange(1, 1)
                .build();
    }

    private static List<String> getSelectedValues(ModalInteraction event, String modalValueId) {
        ModalMapping modalMapping = event.getValue(modalValueId);
        return modalMapping == null ? List.of() : modalMapping.getAsStringList();
    }

    private static List<String> getSelectedUserIds(ModalInteraction event, String modalValueId) {
        ModalMapping modalMapping = event.getValue(modalValueId);
        if (modalMapping == null) return List.of();
        return modalMapping.getAsMentions().getUsers().stream().map(User::getId).toList();
    }

    private static List<String> normalizeSelectedValues(
            List<String> selectedValues, Collection<String> options, List<String> defaultValues) {
        List<String> selectedOrDefault =
                selectedValues == null || selectedValues.isEmpty() ? defaultValues : selectedValues;
        List<String> normalized =
                options.stream().filter(selectedOrDefault::contains).toList();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return options.stream().filter(defaultValues::contains).toList();
    }
}
