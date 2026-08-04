package ti4.service.game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;

/**
 * Imports a deck-set configuration exported by the external Ti-Async-Deckcard-tool "Deck-editor".
 *
 * <p>The config is a partial override: it carries only the deck slots the GM changed away from the
 * game-creation defaults, plus per-deck card exclusions. Slots that are absent or null are left
 * untouched. Setting a slot reuses {@link SetDeckService#setDeck}; exclusions reuse the existing
 * per-card removal methods on {@link Game}. Nothing else in the game is modified.
 */
@UtilityClass
public class DeckConfigImportService {

    private static final String EXPECTED_SCHEMA = "ti4-deck-editor-config";

    /**
     * Applies {@code json} (a Deck-editor export) to {@code game}. Fault-tolerant: a bad slot or
     * exclusion is reported and skipped rather than aborting the whole import. The caller's command
     * carries {@code saveGame = true}, so the mutated game is persisted after this returns.
     */
    public static void importDeckConfig(SlashCommandInteractionEvent event, Game game, String json) {
        DeckConfigIO config;
        try {
            config = JsonMapperManager.basic().readValue(json, DeckConfigIO.class);
        } catch (Exception e) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Failed to parse deck config JSON: " + e.getMessage());
            return;
        }

        StringBuilder applied = new StringBuilder();
        StringBuilder problems = new StringBuilder();

        if (config.getSchema() != null && !EXPECTED_SCHEMA.equals(config.getSchema())) {
            problems.append("- Unexpected schema '")
                    .append(config.getSchema())
                    .append("' (expected '")
                    .append(EXPECTED_SCHEMA)
                    .append("') — attempting import anyway.\n");
        }

        try {
            applySlots(event, game, config, applied, problems);
            applyExclusions(game, config, applied, problems);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to import deck config " + Constants.solaxPing(), e);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Failed to import deck config: " + e.getMessage() + "\n-# Solax has been pinged");
            return;
        }

        StringBuilder summary = new StringBuilder("Deck config imported.\n");
        summary.append(applied.isEmpty() ? "- No deck slots were changed.\n" : applied);
        if (!problems.isEmpty()) {
            summary.append("\nWarnings / skipped:\n").append(problems);
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), summary.toString());
    }

    private static void applySlots(
            SlashCommandInteractionEvent event,
            Game game,
            DeckConfigIO config,
            StringBuilder applied,
            StringBuilder problems) {
        Map<String, String> slots = config.getSlots();
        if (slots == null) return;

        for (Map.Entry<String, String> entry : slots.entrySet()) {
            String slotKey = entry.getKey();
            String alias = entry.getValue();
            // Partial override: an omitted/null/blank slot is left exactly as the game has it.
            if (StringUtils.isBlank(alias)) continue;

            DeckModel deck = Mapper.getDeck(alias);
            if (deck == null || !deck.isValid()) {
                problems.append("- Slot '")
                        .append(slotKey)
                        .append("': unknown or invalid deck '")
                        .append(alias)
                        .append("' — skipped.\n");
                continue;
            }

            boolean success = SetDeckService.setDeck(event, game, slotKey, deck);
            if (success) {
                applied.append("- ").append(slotKey).append(" → ").append(alias).append('\n');
            } else {
                problems.append("- Slot '")
                        .append(slotKey)
                        .append("': not a settable deck slot or validation failed — skipped.\n");
            }
        }
    }

    private static void applyExclusions(Game game, DeckConfigIO config, StringBuilder applied, StringBuilder problems) {
        Map<String, List<String>> exclusions = config.getExclusions();
        if (exclusions == null) return;

        for (Map.Entry<String, List<String>> entry : exclusions.entrySet()) {
            String alias = entry.getKey();
            List<String> cardIds = entry.getValue();
            if (cardIds == null || cardIds.isEmpty()) continue;

            DeckModel deck = Mapper.getDeck(alias);
            if (deck == null || deck.getType() == null) {
                problems.append("- Exclusions for '")
                        .append(alias)
                        .append("': unknown deck — ")
                        .append(cardIds.size())
                        .append(" exclusion(s) skipped.\n");
                continue;
            }

            int removed = 0;
            for (String cardId : cardIds) {
                if (StringUtils.isBlank(cardId)) continue;
                Boolean result = removeCardFromGame(game, deck.getType(), cardId);
                if (result == null) {
                    problems.append("- Exclusion '")
                            .append(cardId)
                            .append("' (")
                            .append(deck.getType())
                            .append("): per-card exclusion not supported for this deck type — skipped.\n");
                } else if (result) {
                    removed++;
                } else {
                    problems.append("- Exclusion '")
                            .append(cardId)
                            .append("': not present in ")
                            .append(alias)
                            .append(" — nothing removed.\n");
                }
            }
            if (removed > 0) {
                applied.append("- Excluded ")
                        .append(removed)
                        .append(" card(s) from ")
                        .append(alias)
                        .append('\n');
            }
        }
    }

    /**
     * Removes a single card from its live in-game deck. Returns {@code true} if removed,
     * {@code false} if the id was not present, or {@code null} if per-card exclusion is unsupported
     * for that deck type (technology decks are rebuilt on demand; events have no removal method).
     */
    private static Boolean removeCardFromGame(Game game, DeckModel.DeckType type, String cardId) {
        return switch (type) {
            case ACTION_CARD -> game.removeACFromGame(cardId);
            case AGENDA -> game.removeAgendaFromGame(cardId);
            case RELIC -> game.removeRelicFromGame(cardId);
            case SECRET_OBJECTIVE -> game.removeSOFromGame(cardId);
            case PUBLIC_STAGE_1_OBJECTIVE, PUBLIC_STAGE_2_OBJECTIVE -> game.removePOFromGame(cardId);
            case EXPLORE -> {
                game.purgeExplore(cardId);
                yield true;
            }
            case TECHNOLOGY, EVENT, OTHER -> null;
        };
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeckConfigIO {
        private String schema;
        private Integer version;
        private String savedAt;
        // Keyed by deck-slot name (Constants.AC_DECK, AGENDA_DECK, ...); value is a deck alias.
        private Map<String, String> slots;
        // Keyed by deck alias; value is the list of card ids to remove from that deck.
        private Map<String, List<String>> exclusions;
    }
}
