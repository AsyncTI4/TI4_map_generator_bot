package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.URLReaderHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;
import ti4.model.StrategyCardSetModel;
import ti4.service.game.DeckConfigImportService;
import ti4.service.game.SetDeckService;

/** DECKS step of the FoW setup wizard: pick decks (and the Strategy Card set), or import a Deck-editor config. */
final class FowSetupDecksService {

    private FowSetupDecksService() {}

    private record DeckSlot(String constantKey, String label, DeckModel.DeckType type) {}

    private static final List<DeckSlot> DECK_SLOTS = List.of(
            new DeckSlot(Constants.AC_DECK, "Action Card", DeckModel.DeckType.ACTION_CARD),
            new DeckSlot(Constants.SO_DECK, "Secret Objective", DeckModel.DeckType.SECRET_OBJECTIVE),
            new DeckSlot(Constants.STAGE_1_PUBLIC_DECK, "Stage 1 Public", DeckModel.DeckType.PUBLIC_STAGE_1_OBJECTIVE),
            new DeckSlot(Constants.STAGE_2_PUBLIC_DECK, "Stage 2 Public", DeckModel.DeckType.PUBLIC_STAGE_2_OBJECTIVE),
            new DeckSlot(Constants.RELIC_DECK, "Relic", DeckModel.DeckType.RELIC),
            new DeckSlot(Constants.AGENDA_DECK, "Agenda", DeckModel.DeckType.AGENDA),
            new DeckSlot(Constants.EVENT_DECK, "Event", DeckModel.DeckType.EVENT),
            new DeckSlot(Constants.EXPLORATION_DECKS, "Exploration", DeckModel.DeckType.EXPLORE),
            new DeckSlot(Constants.TECHNOLOGY_DECK, "Technology", DeckModel.DeckType.TECHNOLOGY));

    static void render(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("### Decks currently in use\n");
        for (DeckSlot slot : DECK_SLOTS) {
            String currentId = currentDeckId(game, slot.constantKey());
            DeckModel current =
                    StringUtils.isBlank(currentId) ? null : Mapper.getDecks().get(currentId);
            sb.append("> ")
                    .append(slot.label())
                    .append(": ")
                    .append(
                            current != null
                                    ? current.getName()
                                    : "_" + (StringUtils.isBlank(currentId) ? "unset" : currentId) + "_")
                    .append('\n');
        }
        StrategyCardSetModel scSet = game.getStrategyCardSet();
        sb.append("> Strategy Card Set: ")
                .append(scSet != null ? scSet.getName() : "_unset_")
                .append('\n');
        sb.append("\nClick a deck type below to pick from its available options.\n\n")
                .append("**Remove individual cards:** `/custom remove_ac_from_game`, `remove_agenda_from_game`, ")
                .append("`remove_relic_from_game`, `remove_so_from_game`, `remove_po_from_game`, ")
                .append("`remove_sc_from_game`, or `/explore remove` (Technology/Event decks aren't supported - ")
                .append("see the info thread for details).\n")
                .append("**Import a Deck-editor config:** the button below takes a URL. Discord buttons/modals ")
                .append("can't accept file uploads, so if you have a `.json` file instead, run ")
                .append("`/special2 import_deck_config` yourself with its `file` attachment option.\n");

        for (DeckSlot slot : DECK_SLOTS) {
            buttons.add(Buttons.gray("fowSetupDeckPick_" + slot.constantKey(), "Set " + slot.label() + " Deck"));
        }
        buttons.add(Buttons.gray("fowSetupDeckPickSC", "Set Strategy Card Set"));
        buttons.add(Buttons.blue("fowSetupDeckConfigImportExplain", "Import Deck-editor Config (URL)"));
    }

    private static String currentDeckId(Game game, String deckType) {
        return switch (deckType) {
            case Constants.AC_DECK -> game.getAcDeckID();
            case Constants.SO_DECK -> game.getSoDeckID();
            case Constants.STAGE_1_PUBLIC_DECK -> game.getStage1PublicDeckID();
            case Constants.STAGE_2_PUBLIC_DECK -> game.getStage2PublicDeckID();
            case Constants.RELIC_DECK -> game.getRelicDeckID();
            case Constants.AGENDA_DECK -> game.getAgendaDeckID();
            case Constants.EVENT_DECK -> game.getEventDeckID();
            case Constants.EXPLORATION_DECKS -> game.getExplorationDeckID();
            case Constants.TECHNOLOGY_DECK -> game.getTechnologyDeckID();
            default -> null;
        };
    }

    private static DeckSlot slotFor(String constantKey) {
        return DECK_SLOTS.stream()
                .filter(slot -> slot.constantKey().equals(constantKey))
                .findFirst()
                .orElse(null);
    }

    // --- Pick a deck for one slot ---

    @ButtonHandler("fowSetupDeckPick_")
    static void openDeckSelect(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String slotKey = buttonID.replace("fowSetupDeckPick_", "");
        DeckSlot slot = slotFor(slotKey);
        if (slot == null) return;

        List<DeckModel> options = Mapper.getDecks().values().stream()
                .filter(deck -> deck.getType() == slot.type())
                .sorted(Comparator.comparing(DeckModel::getName))
                .toList();
        if (options.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No decks of that type are defined.");
            return;
        }
        List<SelectOption> selectOptions = new ArrayList<>();
        for (DeckModel deck : options) {
            SelectOption option = SelectOption.of(StringUtils.left(deck.getName(), 100), deck.getAlias());
            if (StringUtils.isNotBlank(deck.getDescription())) {
                option = option.withDescription(
                        StringUtils.left(deck.getDescription().replace('\n', ' '), 100));
            }
            selectOptions.add(option);
        }
        MessageHelper.sendPagedSelectMenus(
                event.getMessageChannel(),
                "fowSetupDeckSelect_" + slotKey,
                selectOptions,
                "Pick a " + slot.label() + " deck:");
    }

    @SelectionHandler("fowSetupDeckSelect_")
    static void resolveDeckSelect(StringSelectInteractionEvent event, Game game, String menuID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String slotKey = menuID.replace("fowSetupDeckSelect_", "");
        DeckSlot slot = slotFor(slotKey);
        if (slot == null) return;

        String alias = event.getValues().getFirst();
        DeckModel deck = Mapper.getDecks().get(alias);
        boolean success = SetDeckService.setDeck(event, game, slotKey, deck, false);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                success
                        ? "Set " + slot.label() + " deck to **" + deck.getName() + "**."
                        : "Could not set that deck (validation failed).");
        FowSetupWizardService.openOrRefresh(game);
    }

    // --- Pick the Strategy Card set (not a DeckModel - a separate mapper table) ---

    @ButtonHandler("fowSetupDeckPickSC")
    static void openStrategyCardSetSelect(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        List<StrategyCardSetModel> options = Mapper.getStrategyCardSets().values().stream()
                .sorted(Comparator.comparing(StrategyCardSetModel::getName))
                .toList();
        List<SelectOption> selectOptions = new ArrayList<>();
        for (StrategyCardSetModel scSet : options) {
            SelectOption option = SelectOption.of(StringUtils.left(scSet.getName(), 100), scSet.getAlias());
            if (scSet.getDescription().filter(StringUtils::isNotBlank).isPresent()) {
                option = option.withDescription(
                        StringUtils.left(scSet.getDescription().get().replace('\n', ' '), 100));
            }
            selectOptions.add(option);
        }
        MessageHelper.sendPagedSelectMenus(
                event.getMessageChannel(), "fowSetupDeckSelectSC", selectOptions, "Pick a Strategy Card Set:");
    }

    @SelectionHandler("fowSetupDeckSelectSC")
    static void resolveStrategyCardSetSelect(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String alias = event.getValues().getFirst();
        try {
            game.setStrategyCardSet(alias);
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unknown strategy card set.");
            return;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Strategy Card Set set to **" + alias + "**.");
        FowSetupWizardService.openOrRefresh(game);
    }

    // --- Import a deck-set config from the companion Deck-editor tool ---

    /** Discord modals can only carry field labels/placeholders, not body text, so a button that opens
     * a modal directly can never show a paragraph at click time - post it as a plain message with a
     * "Continue" button first instead (same two-step pattern as {@code fowSetupBaseGameOnly}). */
    @ButtonHandler("fowSetupDeckConfigImportExplain")
    static void explainDeckConfigImport(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Import a Deck-editor config: the button below takes a URL. Discord buttons/modals can't "
                        + "accept file uploads, so if you have a `.json` file instead, run "
                        + "`/special2 import_deck_config` yourself with its `file` attachment option.",
                List.of(Buttons.blue("fowSetupDeckConfigImportModal~MDL", "Continue"), Buttons.CANCEL));
    }

    @ButtonHandler("fowSetupDeckConfigImportModal~MDL")
    static void openDeckConfigImportModal(ButtonInteractionEvent event) {
        TextInput url = TextInput.create("url", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("https://.../export.json")
                .build();
        Modal modal = Modal.create("fowSetupDeckConfigImportResolve", "Import Deck-editor Config")
                .addComponents(Label.of("Config JSON URL", url))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("fowSetupDeckConfigImportResolve")
    static void resolveDeckConfigImportModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String url = event.getValue("url").getAsString().trim();
        String json = URLReaderHelper.readFromURL(url, event.getMessageChannel());
        if (json == null) return;
        DeckConfigImportService.importDeckConfig(event, game, json);
        FowSetupWizardService.openOrRefresh(game);
    }
}
