package ti4.discord.interactions.buttons.handlers.matchmaking;

import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.MAX_QUEUE_TIME_OPTIONS_TO_HOURS;
import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.PLAYER_COUNT_OPTIONS;
import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.RESTRICTION_OPTIONS;
import static ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions.VICTORY_POINT_OPTIONS;

import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroup;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;
import ti4.spring.service.statistics.matchmaking.QueueForGameService;

@UtilityClass
class QueueForGameButtonHandler {

    private static final String BUTTON_ID = "queueForGame~MDL";
    private static final String LEAVE_QUEUE_BUTTON_ID = "leaveQueueForGame";
    private static final String MODAL_ID = "queueForGameModal";

    private static final String EXPANSIONS_ID = "queue_expansions";
    private static final String PLAYER_COUNTS_ID = "queue_player_counts";
    private static final String VICTORY_POINTS_ID = "queue_victory_points";
    private static final String RESTRICTIONS_ID = "queue_restrictions";
    private static final String MAX_QUEUE_TIME_ID = "queue_max_time";

    private static final String DEFAULT_MAX_QUEUE_TIME = "8 hours";
    private static final List<String> DEFAULT_EXPANSIONS = List.of("PoK + TE");
    private static final List<String> DEFAULT_PLAYER_COUNTS = List.of("6");
    private static final List<String> DEFAULT_VICTORY_POINTS = List.of("10");
    private static final List<String> DEFAULT_RESTRICTIONS = List.of("Similar Active Hours");

    @ButtonHandler(value = BUTTON_ID, save = false)
    public static void offerQueueForGameModal(ButtonInteractionEvent event) {
        QueueForGameService queueForGameService = SpringContext.getBean(QueueForGameService.class);
        if (queueForGameService.isQueueingDisabled()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Queueing is currently disabled.");
            return;
        }
        if (queueForGameService.isUserQueued(event.getUser().getId())) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "You are already queued for a game. To change your preferences, you must first leave the queue.");
            return;
        }

        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());

        final boolean REQUIRE_SELECTION = true;
        CheckboxGroup expansions = buildCheckboxGroup(
                EXPANSIONS_ID,
                MatchmakingOptions.EXPANSION_OPTIONS,
                userSettings.getQueueForGameExpansions(),
                DEFAULT_EXPANSIONS,
                !REQUIRE_SELECTION);
        CheckboxGroup playerCounts = buildCheckboxGroup(
                PLAYER_COUNTS_ID,
                PLAYER_COUNT_OPTIONS,
                userSettings.getQueueForGamePlayerCounts(),
                DEFAULT_PLAYER_COUNTS,
                REQUIRE_SELECTION);
        CheckboxGroup victoryPoints = buildCheckboxGroup(
                VICTORY_POINTS_ID,
                VICTORY_POINT_OPTIONS,
                userSettings.getQueueForGameVictoryPointGoals(),
                DEFAULT_VICTORY_POINTS,
                REQUIRE_SELECTION);
        CheckboxGroup restrictions = buildCheckboxGroup(
                RESTRICTIONS_ID,
                RESTRICTION_OPTIONS,
                userSettings.getQueueForGameRestrictions(),
                DEFAULT_RESTRICTIONS,
                !REQUIRE_SELECTION);
        List<String> selectedMaxQueueTime = userSettings.getQueueForGameMaxQueueTime() == null
                ? List.of()
                : List.of(userSettings.getQueueForGameMaxQueueTime());
        StringSelectMenu maxQueueTime = buildSingleSelect(
                MAX_QUEUE_TIME_ID,
                MAX_QUEUE_TIME_OPTIONS_TO_HOURS.keySet(),
                selectedMaxQueueTime,
                List.of(DEFAULT_MAX_QUEUE_TIME),
                REQUIRE_SELECTION);

        Modal modal = Modal.create(MODAL_ID, "Queue for Game")
                .addComponents(Label.of("Expansions", expansions))
                .addComponents(Label.of("Player Count", playerCounts))
                .addComponents(Label.of("Victory Point Goal", victoryPoints))
                .addComponents(Label.of("Restrictions", restrictions))
                .addComponents(Label.of("Max Queue Time", maxQueueTime))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = LEAVE_QUEUE_BUTTON_ID, save = false)
    public static void leaveQueue(ButtonInteractionEvent event) {
        QueueForGameService queueForGameService = SpringContext.getBean(QueueForGameService.class);
        if (queueForGameService.isQueueingDisabled()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Leaving queue is currently disabled. Try again later.");
            return;
        }
        queueForGameService.leaveQueue(event.getUser().getId());
        MessageHelper.sendEphemeralMessageToEventChannel(event, "You have left the matchmaking queue.");
    }

    @ModalHandler(MODAL_ID)
    public static void submitQueueForGameModal(ModalInteractionEvent event) {
        List<String> expansions = getSelectedValues(event, EXPANSIONS_ID);
        List<String> playerCounts = getSelectedValues(event, PLAYER_COUNTS_ID);
        List<String> victoryPoints = getSelectedValues(event, VICTORY_POINTS_ID);
        List<String> restrictions = getSelectedValues(event, RESTRICTIONS_ID);
        String maxQueueTime = getSelectedValues(event, MAX_QUEUE_TIME_ID).getFirst();

        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        userSettings.setQueueForGameExpansions(expansions);
        userSettings.setQueueForGamePlayerCounts(playerCounts);
        userSettings.setQueueForGameVictoryPointGoals(victoryPoints);
        // TODO: Make sure the player has completed at least 1 game near the restricted pace
        userSettings.setQueueForGameRestrictions(restrictions);
        userSettings.setQueueForGameMaxQueueTime(maxQueueTime);
        UserSettingsManager.save(userSettings);

        SpringContext.getBean(QueueForGameService.class)
                .queueUser(
                        event.getUser().getId(),
                        event.getUser().getName(),
                        expansions,
                        playerCounts,
                        victoryPoints,
                        restrictions,
                        maxQueueTime);

        MessageHelper.sendEphemeralMessageToEventChannel(event, "You have been added to the matchmaking queue.");
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
        if (requireSelection) {
            builder.setRequiredRange(1, options.size());
        }
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
