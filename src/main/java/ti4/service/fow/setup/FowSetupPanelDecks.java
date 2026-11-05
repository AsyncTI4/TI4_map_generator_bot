package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
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
import ti4.helpers.URLReaderHelper;
import ti4.logging.BotLogger;
import ti4.message.componentsV2.ComponentPanel;
import ti4.model.DeckModel;
import ti4.model.Mapper;
import ti4.model.StrategyCardSetModel;
import ti4.service.fow.setup.FowSetupDecksService.DeckSlot;
import ti4.service.game.DeckConfigImportService;
import ti4.service.game.SetDeckService;

final class FowSetupPanelDecks {

    private static final String SLOT_SELECT = "fowWizDeckSlot";
    private static final String PAGE_PREFIX = "fowWizDeckPage_";
    private static final String DECK_PREFIX = "fowWizDeck_";
    private static final String IMPORT_MODAL = "fowWizDeckImport~MDL";
    private static final String IMPORT_RESOLVE = "fowWizDeckImportResolve";
    private static final String STRATEGY_CARDS = "sc";
    private static final String SLOT_SUBVIEW = "decks:slot:";
    private static final int MENUS_PER_PAGE = 3;

    private FowSetupPanelDecks() {}

    static FowSetupPanel.View view(Game game, FowSetupWizardState state) {
        String subView = StringUtils.defaultString(state.getSubView());
        if (subView.startsWith(SLOT_SUBVIEW)) {
            String[] parts = subView.substring(SLOT_SUBVIEW.length()).split(":");
            Integer page = parts.length > 1 ? FowSetupPanel.parseInt(parts[1]) : null;
            return slotView(game, parts[0], page == null || page < 0 ? 0 : page);
        }
        return overview(game);
    }

    private static FowSetupPanel.View overview(Game game) {
        StringBuilder text = new StringBuilder("### Decks in use\n");
        for (DeckSlot slot : FowSetupDecksService.DECK_SLOTS) {
            text.append("> ")
                    .append(slot.label())
                    .append(": ")
                    .append(deckName(game, slot))
                    .append('\n');
        }
        StrategyCardSetModel scSet = game.getStrategyCardSet();
        text.append("> Strategy Card Set: ")
                .append(scSet != null ? scSet.getName() : "_unset_")
                .append("\n\n-# **Remove single cards** with `/custom remove_ac_from_game`, `remove_agenda_from_game`,")
                .append(" `remove_relic_from_game`, `remove_so_from_game`, `remove_po_from_game`,")
                .append(" `remove_sc_from_game` or `/explore remove`. Technology and event decks have none.");

        List<SelectOption> options = new ArrayList<>();
        for (DeckSlot slot : FowSetupDecksService.DECK_SLOTS) {
            options.add(SelectOption.of(slot.label(), slot.constantKey())
                    .withDescription(StringUtils.abbreviate(stripItalics(deckName(game, slot)), 100)));
        }
        options.add(SelectOption.of("Strategy Card Set", STRATEGY_CARDS)
                .withDescription(StringUtils.abbreviate(scSet != null ? scSet.getName() : "unset", 100)));
        StringSelectMenu slotSelect = StringSelectMenu.create(SLOT_SELECT)
                .setPlaceholder("Change a deck...")
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
        return FowSetupPanel.View.of(
                List.of(TextDisplay.of(text.toString())),
                List.of(
                        ActionRow.of(slotSelect),
                        ActionRow.of(Buttons.blue(IMPORT_MODAL, "Import Deck-editor config..."))));
    }

    private static String deckName(Game game, DeckSlot slot) {
        String currentId = FowSetupDecksService.currentDeckId(game, slot.constantKey());
        DeckModel current =
                StringUtils.isBlank(currentId) ? null : Mapper.getDecks().get(currentId);
        if (current != null) return current.getName();
        return "_" + (StringUtils.isBlank(currentId) ? "unset" : currentId) + "_";
    }

    private static String stripItalics(String text) {
        return StringUtils.strip(text, "_");
    }

    private static FowSetupPanel.View slotView(Game game, String slotKey, int requestedPage) {
        boolean strategyCards = STRATEGY_CARDS.equals(slotKey);
        DeckSlot slot = FowSetupDecksService.slotFor(slotKey);
        String label = strategyCards ? "Strategy Card Set" : slot == null ? slotKey : slot.label() + " deck";
        List<SelectOption> options = strategyCards
                ? FowSetupDecksService.strategyCardSetOptions()
                : slot == null ? List.of() : FowSetupDecksService.deckOptions(slot);
        String current = strategyCards
                ? (game.getStrategyCardSet() != null ? game.getStrategyCardSet().getName() : "_unset_")
                : slot == null ? "_unknown_" : deckName(game, slot);

        int pages = ComponentPanel.pageCount(options.size(), MENUS_PER_PAGE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        List<ActionRow> rows = new ArrayList<>(
                ComponentPanel.selectPages(DECK_PREFIX + slotKey + "_", options, page, MENUS_PER_PAGE, "Pick one"));
        List<Button> buttons = new ArrayList<>();
        if (page > 0) buttons.add(Buttons.gray(PAGE_PREFIX + slotKey + "_" + (page - 1), "◀ More"));
        if (page < pages - 1) buttons.add(Buttons.gray(PAGE_PREFIX + slotKey + "_" + (page + 1), "More ▶"));
        buttons.add(FowSetupPanel.backButton());
        rows.add(ActionRow.of(buttons));
        String text = "### Pick a " + label + "\n**Current:** " + current
                + (options.isEmpty() ? "\nNo options of this type are defined." : "");
        return FowSetupPanel.View.sub(List.of(TextDisplay.of(text)), rows, false);
    }

    @SelectionHandler(SLOT_SELECT)
    static void pickSlot(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupPanel.openSubView(event, game, SLOT_SUBVIEW + event.getValues().getFirst() + ":0", null);
    }

    @ButtonHandler(PAGE_PREFIX)
    static void pageSlot(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String tail = buttonID.substring(PAGE_PREFIX.length());
        int split = tail.lastIndexOf('_');
        FowSetupPanel.openSubView(
                event, game, SLOT_SUBVIEW + tail.substring(0, split) + ":" + tail.substring(split + 1), null);
    }

    @SelectionHandler(DECK_PREFIX)
    static void pickDeck(StringSelectInteractionEvent event, Game game, String menuID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String tail = menuID.substring(DECK_PREFIX.length());
        String slotKey = tail.substring(0, tail.lastIndexOf('_'));
        String alias = event.getValues().getFirst();
        FowSetupPanel.openSubView(event, game, null, setDeck(event, game, slotKey, alias));
    }

    private static String setDeck(StringSelectInteractionEvent event, Game game, String slotKey, String alias) {
        if (STRATEGY_CARDS.equals(slotKey)) {
            try {
                game.setStrategyCardSet(alias);
            } catch (IllegalArgumentException e) {
                return "Unknown strategy card set.";
            }
            return "Strategy Card Set set to **" + alias + "**.";
        }
        DeckSlot slot = FowSetupDecksService.slotFor(slotKey);
        DeckModel deck = Mapper.getDecks().get(alias);
        if (slot == null || deck == null) return "Unknown deck. Nothing was changed.";
        boolean success = SetDeckService.setDeck(event, game, slotKey, deck, false);
        return success
                ? "Set " + slot.label() + " deck to **" + deck.getName() + "**."
                : "Could not set that deck (validation failed).";
    }

    static Modal importModal() {
        return FowSetupPanel.urlOrFileModal(
                IMPORT_RESOLVE,
                "Import Deck-editor Config",
                "Applies a deck-set config exported from the Deck-editor"
                        + " (https://stabar-ti.github.io/Ti-Async-Deckcard-tool/). It can set several decks and"
                        + " exclude single cards in one go. Paste the export URL or upload the `.json` file.",
                "https://.../export.json");
    }

    @ButtonHandler(IMPORT_MODAL)
    static void openImportModal(ButtonInteractionEvent event) {
        event.replyModal(importModal()).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(IMPORT_RESOLVE)
    static void resolveImportModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String source = FowSetupPanel.urlOrFile(event);
        if (source.isEmpty()) {
            FowSetupPanel.show(event, game, "Give a URL or upload a file. Nothing was imported.");
            return;
        }
        String json = URLReaderHelper.readFromURL(source, event.getMessageChannel());
        if (json == null) {
            FowSetupPanel.show(event, game, "Could not read the config. Nothing was imported.");
            return;
        }
        boolean imported = DeckConfigImportService.importDeckConfig(event, game, json);
        FowSetupPanel.show(
                event,
                game,
                imported
                        ? "Deck-editor config imported. The summary is posted in this channel."
                        : "The Deck-editor config import failed. The error is posted in this channel.");
    }
}
