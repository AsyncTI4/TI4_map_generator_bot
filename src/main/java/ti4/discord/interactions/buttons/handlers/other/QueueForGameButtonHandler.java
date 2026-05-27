package ti4.discord.interactions.buttons.handlers.other;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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

    static final String BUTTON_ID = "queueForGame~MDL";
    private static final String LEAVE_QUEUE_BUTTON_ID = "leaveQueueForGame";
    private static final String MODAL_ID = "queueForGameModal";

    private static final String EXPANSIONS_ID = "queue_expansions";
    private static final String PLAYER_COUNTS_ID = "queue_player_counts";
    private static final String VICTORY_POINTS_ID = "queue_victory_points";
    private static final String RESTRICTIONS_ID = "queue_restrictions";
    private static final String MAX_QUEUE_TIME_ID = "queue_max_time";

    private static final List<String> EXPANSION_OPTIONS = List.of("PoK", "TE", "PoK + TE");
    private static final List<String> PLAYER_COUNT_OPTIONS = List.of("3", "4", "5", "6", "7", "8");
    private static final List<String> VICTORY_POINT_OPTIONS = List.of("10", "12", "14");
    private static final List<String> RESTRICTION_OPTIONS = List.of(
            "Similar Active Hours",
            "Similar Player Skill",
            "Twilight Imperium Global League",
            "Fast Pace (30 days)",
            "Faster Pace (14 days)",
            "Fastest Pace (7 days)");
    private static final List<String> MAX_QUEUE_TIME_OPTIONS =
            List.of("1 hour", "4 hours", "8 hours", "24 hours", "48 hours", "1 week");

    private static final List<String> DEFAULT_EXPANSIONS = List.of("PoK + TE");
    private static final List<String> DEFAULT_PLAYER_COUNTS = List.of("6");
    private static final List<String> DEFAULT_VICTORY_POINTS = List.of("10");
    private static final List<String> DEFAULT_RESTRICTIONS = List.of("Similar Active Hours");
    private static final String DEFAULT_MAX_QUEUE_TIME = "8 hours";

    @ButtonHandler(value = BUTTON_ID, save = false)
    public static void offerQueueForGameModal(ButtonInteractionEvent event) {
        QueueForGameService queueForGameService = SpringContext.getBean(QueueForGameService.class);
        if (queueForGameService.isUserQueued(event.getUser().getId())) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "You are already queued for a game. To change your preferences, you must first leave the queue.");
            return;
        }
        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());

        StringSelectMenu expansions = buildMultiSelect(
                EXPANSIONS_ID, EXPANSION_OPTIONS, userSettings.getQueueForGameExpansions(), DEFAULT_EXPANSIONS);
        StringSelectMenu playerCounts = buildMultiSelect(
                PLAYER_COUNTS_ID,
                PLAYER_COUNT_OPTIONS,
                userSettings.getQueueForGamePlayerCounts(),
                DEFAULT_PLAYER_COUNTS);
        StringSelectMenu victoryPoints = buildMultiSelect(
                VICTORY_POINTS_ID,
                VICTORY_POINT_OPTIONS,
                userSettings.getQueueForGameVictoryPointGoals(),
                DEFAULT_VICTORY_POINTS);
        StringSelectMenu restrictions = buildSingleSelect(
                RESTRICTIONS_ID, RESTRICTION_OPTIONS, userSettings.getQueueForGameRestrictions(), DEFAULT_RESTRICTIONS);
        List<String> selectedMaxQueueTime = userSettings.getQueueForGameMaxQueueTime() == null
                ? List.of()
                : List.of(userSettings.getQueueForGameMaxQueueTime());
        StringSelectMenu maxQueueTime = buildSingleSelect(
                MAX_QUEUE_TIME_ID, MAX_QUEUE_TIME_OPTIONS, selectedMaxQueueTime, List.of(DEFAULT_MAX_QUEUE_TIME));

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
        SpringContext.getBean(QueueForGameService.class)
                .leaveQueue(event.getUser().getId());
        MessageHelper.sendEphemeralMessageToEventChannel(event, "You have left the matchmaking queue.");
    }

    @ModalHandler(MODAL_ID)
    public static void submitQueueForGameModal(ModalInteractionEvent event) {
        List<String> expansions = getSelectedValues(event, EXPANSIONS_ID, EXPANSION_OPTIONS, DEFAULT_EXPANSIONS, true);
        List<String> playerCounts =
                getSelectedValues(event, PLAYER_COUNTS_ID, PLAYER_COUNT_OPTIONS, DEFAULT_PLAYER_COUNTS, true);
        List<String> victoryPoints =
                getSelectedValues(event, VICTORY_POINTS_ID, VICTORY_POINT_OPTIONS, DEFAULT_VICTORY_POINTS, true);
        List<String> restrictions =
                getSelectedValues(event, RESTRICTIONS_ID, RESTRICTION_OPTIONS, DEFAULT_RESTRICTIONS, true);
        String maxQueueTime = getSelectedValues(
                        event, MAX_QUEUE_TIME_ID, MAX_QUEUE_TIME_OPTIONS, List.of(DEFAULT_MAX_QUEUE_TIME), true)
                .getFirst();

        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        userSettings.setQueueForGameExpansions(expansions);
        userSettings.setQueueForGamePlayerCounts(playerCounts);
        userSettings.setQueueForGameVictoryPointGoals(victoryPoints);
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

    private static StringSelectMenu buildMultiSelect(
            String id, List<String> options, List<String> selectedValues, List<String> defaultValues) {
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(id);
        for (String option : options) {
            menuBuilder.addOptions(SelectOption.of(option, option));
        }
        menuBuilder.setRequiredRange(1, options.size());
        menuBuilder.setDefaultValues(normalizeSelectedValues(selectedValues, options, defaultValues));
        return menuBuilder.build();
    }

    private static StringSelectMenu buildSingleSelect(
            String id, List<String> options, List<String> selectedValues, List<String> defaultValues) {
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(id);
        for (String option : options) {
            menuBuilder.addOptions(SelectOption.of(option, option));
        }
        menuBuilder.setRequiredRange(1, 1);
        menuBuilder.setDefaultValues(normalizeSelectedValues(selectedValues, options, defaultValues));
        return menuBuilder.build();
    }

    private static List<String> getSelectedValues(
            ModalInteractionEvent event,
            String modalValueId,
            List<String> options,
            List<String> defaultValues,
            boolean enforceNonEmpty) {
        ModalMapping modalMapping = event.getValue(modalValueId);
        List<String> selected = modalMapping == null ? List.of() : modalMapping.getAsStringList();
        List<String> normalized = normalizeSelectedValues(selected, options, defaultValues);
        if (!enforceNonEmpty || !normalized.isEmpty()) {
            return normalized;
        }
        return normalizeSelectedValues(defaultValues, options, defaultValues);
    }

    private static List<String> normalizeSelectedValues(
            List<String> selectedValues, List<String> options, List<String> defaultValues) {
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
