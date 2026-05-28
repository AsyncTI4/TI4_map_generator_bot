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
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroup;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
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

@UtilityClass
class MatchmakingButtonHandler {

    private static final String QUEUE_FOR_GAME_BUTTON_ID = "queueForGame~MDL";
    private static final String LEAVE_QUEUE_BUTTON_ID = "leaveQueueForGame";
    private static final String ADDITIONAL_SETTINGS_BUTTON_ID = "queueForGameAdditionalSettings~MDL";
    private static final String QUEUE_FOR_GAME_MODAL_ID = "queueForGameModal";
    private static final String ADDITIONAL_SETTINGS_MODAL_ID = "queueForGameAdditionalSettingsModal";

    private static final String EXPANSIONS_ID = "queue_expansions";
    private static final String PLAYER_COUNTS_ID = "queue_player_counts";
    private static final String VICTORY_POINTS_ID = "queue_victory_points";
    private static final String PACE_RESTRICTIONS_ID = "queue_pace_restrictions";
    private static final String RESTRICTIONS_ID = "queue_restrictions";
    private static final String MAX_QUEUE_TIME_ID = "queue_max_time";
    private static final String AVOID_PLAYERS_ID = "queue_avoid_players";

    private static final String DEFAULT_MAX_QUEUE_TIME = "8 hours";
    private static final List<String> DEFAULT_EXPANSIONS = List.of("Prophecy of Kings and Thunder's Edge");
    private static final List<String> DEFAULT_PLAYER_COUNTS = List.of("6");
    private static final List<String> DEFAULT_VICTORY_POINTS = List.of("10");
    private static final List<String> DEFAULT_PACE_RESTRICTIONS = List.of(NO_PACE_OPTION);
    private static final List<String> DEFAULT_RESTRICTIONS = List.of("Similar Active Hours");
    private static final Map<String, Integer> PACE_RESTRICTION_TO_GAME_DAYS_TO_COMPLETE_REQUIREMENT = Map.of(
            MatchmakingOptions.FASTER_PACE_OPTION, 19,
            MatchmakingOptions.FASTEST_PACE_OPTION, 10);

    @ButtonHandler(value = QUEUE_FOR_GAME_BUTTON_ID, save = false)
    public static void offerQueueForGameModal(ButtonInteractionEvent event) {
        MatchmakerService matchmakerService = SpringContext.getBean(MatchmakerService.class);
        if (matchmakerService.isQueueingDisabled()) {
            event.reply("Queueing is currently disabled.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }
        String userId = event.getUser().getId();
        if (matchmakerService.isUserQueued(userId)) {
            event.reply(
                            "You are already queued for a game. To change your preferences, you must first leave the queue.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        UserSettings userSettings = UserSettingsManager.get(userId);

        final boolean REQUIRE_SELECTION = true;
        CheckboxGroup expansions = buildCheckboxGroup(
                EXPANSIONS_ID,
                MatchmakingOptions.EXPANSION_OPTIONS,
                userSettings.getMatchmakingExpansions(),
                DEFAULT_EXPANSIONS,
                REQUIRE_SELECTION);
        CheckboxGroup playerCounts = buildCheckboxGroup(
                PLAYER_COUNTS_ID,
                PLAYER_COUNT_OPTIONS,
                userSettings.getMatchmakingPlayerCounts(),
                DEFAULT_PLAYER_COUNTS,
                REQUIRE_SELECTION);
        CheckboxGroup victoryPoints = buildCheckboxGroup(
                VICTORY_POINTS_ID,
                VICTORY_POINT_OPTIONS,
                userSettings.getMatchmakingVictoryPointGoals(),
                DEFAULT_VICTORY_POINTS,
                REQUIRE_SELECTION);
        CheckboxGroup paceRestrictions = buildCheckboxGroup(
                PACE_RESTRICTIONS_ID,
                filterPaceRestrictionsByIfPlayerHasCompletedRequiredGame(userId),
                userSettings.getMatchmakingRestrictions(),
                DEFAULT_PACE_RESTRICTIONS,
                REQUIRE_SELECTION);
        CheckboxGroup restrictions = buildCheckboxGroup(
                RESTRICTIONS_ID,
                RESTRICTION_OPTIONS,
                userSettings.getMatchmakingRestrictions(),
                DEFAULT_RESTRICTIONS,
                !REQUIRE_SELECTION);

        Modal modal = Modal.create(QUEUE_FOR_GAME_MODAL_ID, "Queue for Game")
                .addComponents(Label.of("Expansions", expansions))
                .addComponents(Label.of("Player Count", playerCounts))
                .addComponents(Label.of("Victory Point Goal", victoryPoints))
                .addComponents(Label.of("Pace Restrictions", paceRestrictions))
                .addComponents(Label.of("Restrictions", restrictions))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = ADDITIONAL_SETTINGS_BUTTON_ID, save = false)
    public static void offerQueueAdditionalSettingsModal(ButtonInteractionEvent event) {
        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        final boolean REQUIRE_SELECTION = true;
        List<String> selectedMaxQueueTime = userSettings.getMatchmakingMaxQueueTime() == null
                ? List.of()
                : List.of(userSettings.getMatchmakingMaxQueueTime());
        StringSelectMenu maxQueueTime = buildSingleSelect(
                MAX_QUEUE_TIME_ID,
                MAX_QUEUE_TIME_OPTIONS_TO_HOURS.keySet(),
                selectedMaxQueueTime,
                List.of(DEFAULT_MAX_QUEUE_TIME),
                REQUIRE_SELECTION);
        EntitySelectMenu avoidPlayers = EntitySelectMenu.create(AVOID_PLAYERS_ID, SelectTarget.USER)
                .setRequired(false)
                .setMaxValues(25)
                .setDefaultValues(userSettings.getMatchmakingAvoidList().stream()
                        .map(EntitySelectMenu.DefaultValue::user)
                        .toList())
                .build();

        Modal modal = Modal.create(ADDITIONAL_SETTINGS_MODAL_ID, "Queue Additional Settings")
                .addComponents(Label.of("Max Queue Time", maxQueueTime))
                .addComponents(Label.of("Avoid List", avoidPlayers))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = LEAVE_QUEUE_BUTTON_ID, save = false)
    public static void leaveQueue(ButtonInteractionEvent event) {
        MatchmakerService matchmakerService = SpringContext.getBean(MatchmakerService.class);
        if (matchmakerService.isQueueingDisabled()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Leaving queue is currently disabled. Try again later.");
            return;
        }
        matchmakerService.leaveQueue(event.getUser().getId());
        MessageHelper.sendEphemeralMessageToEventChannel(event, "You have left the matchmaking queue.");
    }

    @ModalHandler(QUEUE_FOR_GAME_MODAL_ID)
    public static void submitQueueForGameModal(ModalInteractionEvent event) {
        List<String> expansions = getSelectedValues(event, EXPANSIONS_ID);
        List<String> playerCounts = getSelectedValues(event, PLAYER_COUNTS_ID);
        List<String> victoryPoints = getSelectedValues(event, VICTORY_POINTS_ID);
        List<String> paceRestrictions = getSelectedValues(event, PACE_RESTRICTIONS_ID);
        List<String> restrictions = getSelectedValues(event, RESTRICTIONS_ID);

        String userId = event.getUser().getId();
        UserSettings userSettings = UserSettingsManager.get(userId);

        if (isPlayerAtGameLimit(event, userId, userSettings)) return;

        userSettings.setMatchmakingExpansions(expansions);
        userSettings.setMatchmakingPlayerCounts(playerCounts);
        userSettings.setMatchmakingVictoryPointGoals(victoryPoints);
        List<String> allRestrictions = new ArrayList<>(restrictions);
        allRestrictions.addAll(paceRestrictions);
        userSettings.setMatchmakingRestrictions(allRestrictions);
        UserSettingsManager.save(userSettings);

        SpringContext.getBean(MatchmakerService.class).queueUser(userId);

        event.getHook()
                .setEphemeral(true)
                .sendMessage("You have been added to the matchmaking queue.")
                .queue(Consumers.nop(), BotLogger::catchRestError);
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

    private static List<String> filterPaceRestrictionsByIfPlayerHasCompletedRequiredGame(String userId) {
        UserGameInfoService userGameInfoService = UserGameInfoService.get();
        List<String> restrictions = new ArrayList<>(PACE_RESTRICTION_OPTIONS);
        PACE_RESTRICTION_TO_GAME_DAYS_TO_COMPLETE_REQUIREMENT.forEach(
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
        builder.setRequired(requireSelection);
        builder.setSelectedValues(normalizeSelectedValues(selectedValues, options, defaultValues));
        return builder.build();
    }

    private static StringSelectMenu buildSingleSelect(
            String id,
            Collection<String> options,
            List<String> selectedValues,
            List<String> defaultValues,
            boolean requireSelection) {
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(id);
        for (String option : options) {
            menuBuilder.addOptions(SelectOption.of(option, option));
        }
        if (requireSelection) {
            menuBuilder.setRequiredRange(1, 1);
        }
        menuBuilder.setDefaultValues(normalizeSelectedValues(selectedValues, options, defaultValues));
        return menuBuilder.build();
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
