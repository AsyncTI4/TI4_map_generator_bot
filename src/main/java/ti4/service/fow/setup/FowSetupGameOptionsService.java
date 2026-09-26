package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.helpers.TIGLHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.HomebrewService;
import ti4.service.game.HomebrewService.Homebrew;

/**
 * Step "Game Options" of the FoW setup wizard: 3 category submenus of real on/off toggle buttons
 * (mirroring {@code FOWOptionService}'s single-button-per-flag style), plus a Scoring section that
 * stays on the main panel since its buttons apply values rather than flip a switch.
 *
 * <p>The ON transition for every homebrew-backed toggle reuses {@code HomebrewService.applyHomebrew}
 * directly - nothing here duplicates that logic. Most of those options have no clean reverse (they
 * rebuild decks, swap variant techs, etc.), so the OFF transition only flips the underlying boolean
 * flag back and is explicit in its response about what it did *not* undo, mirroring the existing
 * "Remove All Homebrews" button's own accepted limitation.
 */
final class FowSetupGameOptionsService {

    private FowSetupGameOptionsService() {}

    private enum Category {
        HOMEBREW("Homebrew Options", List.of(Homebrew.HBVOTC, Homebrew.HBHBSC, Homebrew.HBREMOVESFTT)),
        FACTIONS("Faction Options", List.of(Homebrew.HBDSFACTIONS, Homebrew.HBBRFACTIONS)),
        DECKS(
                "Major Card Deck Overhauls",
                List.of(
                        Homebrew.HBABSOLRELICSAGENDAS,
                        Homebrew.HBABSOLTECHSMECHS,
                        Homebrew.HBDSEXPLORES,
                        Homebrew.HBACDECK2)),
        // Not Homebrew-enum-driven like the others - special-cased in postCategoryButtons, same as
        // how HOMEBREW already special-cases its TIGL/Alliance/Remove-All extras.
        SCORING("Scoring Options", List.of());

        final String title;
        final List<Homebrew> items;

        Category(String title, List<Homebrew> items) {
            this.title = title;
            this.items = items;
        }
    }

    static void render(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Same options as `/game weird_game_setup` and the \"Supported Homebrew\" button, just ")
                .append("grouped into toggle switches. Pick a category to see and flip its options.\n\n");

        for (Category category : Category.values()) {
            buttons.add(Buttons.blue("fowSetupOptionsCategory_" + category, category.title));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Category submenus of real toggle buttons
    // ---------------------------------------------------------------------------------------

    @ButtonHandler("fowSetupOptionsCategory_")
    static void openCategory(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Category category = Category.valueOf(buttonID.replace("fowSetupOptionsCategory_", ""));
        postCategoryButtons(event, game, category, false, null);
    }

    /**
     * Renders a category submenu. {@code editInPlace} controls whether this updates the message the
     * clicked button lives on (every re-render triggered from within an already-open submenu) or
     * sends a fresh message (only the initial "open category" click, which lives on a different
     * message - the main Game Options panel). {@code leadNote}, when given, is folded into the top of
     * the rendered text instead of being sent as a separate message - keeps toggling several options
     * in a row from spamming the channel with one extra confirmation message per click.
     */
    private static void postCategoryButtons(
            ButtonInteractionEvent event, Game game, Category category, boolean editInPlace, String leadNote) {
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        StringBuilder sb = new StringBuilder("### " + category.title + "\n\n");
        if (leadNote != null) {
            sb.append(leadNote).append("\n\n");
        }
        List<Button> buttons = new ArrayList<>();

        if (category == Category.SCORING) {
            sb.append("> Currently: ")
                    .append(game.getVp())
                    .append(" VP, ")
                    .append(game.getMaxSOCountPerPlayer())
                    .append(" SO/player, ")
                    .append(game.getPublicObjectives1Peekable().size())
                    .append(" peekable Stage 1, ")
                    .append(game.getPublicObjectives2Peekable().size())
                    .append(" peekable Stage 2.\n")
                    .append("These apply a value rather than toggle on/off.\n");
            buttons.add(Buttons.green("fowSetupScoringPreset_" + Homebrew.HB444, "Apply 4/4/4"));
            buttons.add(Buttons.green("fowSetupScoringPreset_" + Homebrew.HB456, "Apply 4/5/6"));
            buttons.add(Buttons.blue("fowSetupCustomScoring~MDL", "Set Custom Scoring"));
            buttons.add(Buttons.DONE_DELETE_BUTTONS);
            MessageHelper.postOrEditWithButtons(event, sb.toString(), buttons, editInPlace);
            return;
        }

        if (category == Category.DECKS
                && game.isAbsolMode()
                && !state.getEnabledGameOptions().contains(Homebrew.HBABSOLRELICSAGENDAS.name())
                && !state.getEnabledGameOptions().contains(Homebrew.HBABSOLTECHSMECHS.name())) {
            sb.append("⚠ Absol Mode is active in the game, but this wizard didn't apply it (probably set via ")
                    .append("the old Homebrew button on Step 1 or a slash command) - the two switches below ")
                    .append("can't tell which one is responsible and may not reflect reality.\n\n");
        }

        for (Homebrew hb : category.items) {
            boolean on = isOptionOn(game, state, hb);
            sb.append(on ? "✅ " : "🚫 ")
                    .append("**")
                    .append(hb.name)
                    .append("**\n-# ")
                    .append(hb.description)
                    .append('\n');
            buttons.add(
                    on
                            ? Buttons.red("fowSetupOptionToggle_" + category + "_" + hb, "Disable " + hb.name)
                            : Buttons.green("fowSetupOptionToggle_" + category + "_" + hb, "Enable " + hb.name));
        }

        if (category == Category.HOMEBREW) {
            boolean tiglOn = TIGLHelper.isFracturedTIGLGame(game) && game.isCompetitiveTIGLGame();
            sb.append(tiglOn ? "✅ " : "🚫 ")
                    .append("**Fractured TIGL**\n-# Standard TIGL can't combine with FoW - this flags the game ")
                    .append("as Fractured-ladder TIGL instead, the only variant compatible with FoW mode.\n");
            buttons.add(
                    tiglOn
                            ? Buttons.red("fowSetupToggleTigl", "Disable Fractured TIGL")
                            : Buttons.green("fowSetupToggleTigl", "Enable Fractured TIGL"));

            boolean allianceOn = game.isAllianceMode();
            sb.append(allianceOn ? "✅ " : "🚫 ")
                    .append("**Alliance Mode**\n-# Team-based variant. Turning this on disables TIGL (the two ")
                    .append("are mutually exclusive). Pair specific players together on the Factions step.\n");
            buttons.add(
                    allianceOn
                            ? Buttons.red("fowSetupToggleAlliance", "Disable Alliance Mode")
                            : Buttons.green("fowSetupToggleAlliance", "Enable Alliance Mode"));

            buttons.add(Buttons.red("fowSetupOptionsRemoveAllHomebrews_" + category, "Remove All Homebrews"));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);

        MessageHelper.postOrEditWithButtons(event, sb.toString(), buttons, editInPlace);
    }

    private static boolean isOptionOn(Game game, FowSetupWizardState state, Homebrew hb) {
        return switch (hb) {
            case HBVOTC -> game.isVotcMode();
            case HBHBSC -> game.isHomebrewSCMode();
            case HBREMOVESFTT -> "true".equals(game.getStoredValue("removeSupports"));
            case HBDSFACTIONS -> game.isDiscordantStarsMode();
            case HBBRFACTIONS -> game.isBlueReverieMode();
            case HBDSEXPLORES -> game.isUnchartedSpaceStuff();
            case HBACDECK2 -> game.isAcd2();
            // Both toggles share Game.absolMode - no single flag can tell them apart, so this is
            // tracked in wizard state instead. Can drift if absolMode is set from outside this
            // submenu (e.g. the old flat "Supported Homebrew" button) - see the DECKS category note.
            case HBABSOLRELICSAGENDAS, HBABSOLTECHSMECHS ->
                state.getEnabledGameOptions().contains(hb.name());
            default -> false;
        };
    }

    @ButtonHandler("fowSetupOptionToggle_")
    static void toggleOption(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String[] parts = buttonID.replace("fowSetupOptionToggle_", "").split("_", 2);
        Category category = Category.valueOf(parts[0]);
        Homebrew hb = Homebrew.valueOf(parts[1]);

        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        boolean turningOn = !isOptionOn(game, state, hb);

        String leadNote = null;
        if (turningOn) {
            // Reuses the exact same apply logic as the flat "Supported Homebrew" button - nothing duplicated.
            // This still sends its own separate confirmation message internally (shared production code).
            HomebrewService.applyHomebrew(game, event, hb);
            state.getEnabledGameOptions().add(hb.name());
        } else {
            String caveat = applyOff(game, state, hb);
            state.getEnabledGameOptions().remove(hb.name());
            leadNote = hb.name + " turned **off**." + (caveat == null ? "" : " " + caveat);
        }
        FowSetupWizardService.saveState(game, state);
        postCategoryButtons(event, game, category, true, leadNote);
    }

    /** Flips the flag back off where possible. Returns a caveat about what was NOT reverted, or null if clean. */
    private static String applyOff(Game game, FowSetupWizardState state, Homebrew hb) {
        return switch (hb) {
            case HBHBSC -> {
                game.setHomebrewSCMode(false);
                yield null;
            }
            case HBBRFACTIONS -> {
                game.setBlueReverieMode(false);
                yield null;
            }
            case HBVOTC -> {
                game.setVotcMode(false);
                yield "The agenda/tech/strategy-card/action-card decks it swapped in were not reverted - "
                        + "check the Decks step manually.";
            }
            case HBREMOVESFTT -> {
                game.setStoredValue("removeSupports", "");
                yield "Support for the Throne promissory notes already removed were not restored.";
            }
            case HBDSFACTIONS -> {
                game.setDiscordantStarsMode(false);
                yield "The technology deck it may have changed was not reverted.";
            }
            case HBDSEXPLORES -> {
                game.setUnchartedSpaceStuff(false);
                yield "The explore/action-card/relic decks it changed were not reverted.";
            }
            case HBABSOLRELICSAGENDAS, HBABSOLTECHSMECHS -> {
                Homebrew other = hb == Homebrew.HBABSOLRELICSAGENDAS
                        ? Homebrew.HBABSOLTECHSMECHS
                        : Homebrew.HBABSOLRELICSAGENDAS;
                // Both toggles share Game.absolMode - only clear it once neither is tracked as on.
                game.setAbsolMode(state.getEnabledGameOptions().contains(other.name()));
                yield "The decks it changed were not reverted.";
            }
            case HBACDECK2 ->
                "No deck change was made, so this will still show as enabled - pick a different Action "
                        + "Card deck in the Decks step if you want to actually move off Action Card Deck 2.";
            default -> null;
        };
    }

    @ButtonHandler("fowSetupOptionsRemoveAllHomebrews_")
    static void removeAllHomebrews(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Category category = Category.valueOf(buttonID.replace("fowSetupOptionsRemoveAllHomebrews_", ""));
        HomebrewService.removeHomebrew(game, event);
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.getEnabledGameOptions().clear();
        FowSetupWizardService.saveState(game, state);
        postCategoryButtons(event, game, category, true, null);
    }

    // ---------------------------------------------------------------------------------------
    // Fractured TIGL / Alliance Mode toggles (lives in the Homebrew category)
    // ---------------------------------------------------------------------------------------

    /**
     * Standard TIGL is unconditionally forced off by {@code Game.setCompetitiveTIGLGame} whenever
     * {@code isFowMode()} is true - only Fractured-ladder TIGL is allowed to combine with FoW. This
     * toggle uses {@code TIGLHelper.initializeTIGLGame}, the real enable path (tags Fractured, sets
     * the flag, posts the TIGL setup text, snapshots player ranks) rather than the plain setter.
     */
    @ButtonHandler("fowSetupToggleTigl")
    static void toggleTigl(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        boolean tiglOn = TIGLHelper.isFracturedTIGLGame(game) && game.isCompetitiveTIGLGame();
        String leadNote;
        if (tiglOn) {
            game.setCompetitiveTIGLGame(false);
            TIGLHelper.removeFracturedTag(game);
            leadNote = "Fractured TIGL turned **off**.";
        } else {
            TIGLHelper.initializeTIGLGame(game, true);
            leadNote = game.isCompetitiveTIGLGame()
                    ? "Fractured TIGL turned **on**."
                    : "Could not enable TIGL - see the message above for why (likely a missing TIGL admin "
                            + "thread/role, or a player not in the TIGL hub server).";
        }
        postCategoryButtons(event, game, Category.HOMEBREW, true, leadNote);
    }

    /** Simple flag - {@code Game.setAllianceMode} already forces TIGL off internally when enabling. */
    @ButtonHandler("fowSetupToggleAlliance")
    static void toggleAlliance(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        boolean allianceOn = game.isAllianceMode();
        game.setAllianceMode(!allianceOn);
        String leadNote = "Alliance Mode turned **" + (!allianceOn ? "on" : "off") + "**."
                + (!allianceOn ? " Pair specific players together on the Factions step." : "");
        postCategoryButtons(event, game, Category.HOMEBREW, true, leadNote);
    }

    /** Own button id (not the raw {@code setupHomebrew_} one) to avoid its delete-clicked-button side
     * effect, which would strip this button from the submenu instead of refreshing the shown values. */
    @ButtonHandler("fowSetupScoringPreset_")
    static void applyScoringPreset(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Homebrew hb = Homebrew.valueOf(buttonID.replace("fowSetupScoringPreset_", ""));
        HomebrewService.applyHomebrew(game, event, hb);
        postCategoryButtons(event, game, Category.SCORING, true, null);
    }

    // ---------------------------------------------------------------------------------------
    // Custom scoring: VP / max SO per player / peekable Stage 1 & 2 counts, any subset
    // ---------------------------------------------------------------------------------------

    @ButtonHandler("fowSetupCustomScoring~MDL")
    static void openCustomScoringModal(ButtonInteractionEvent event, Game game) {
        TextInput vp = TextInput.create("vp", TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder("Current: " + game.getVp())
                .build();
        TextInput so = TextInput.create("so", TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder("Current: " + game.getMaxSOCountPerPlayer())
                .build();
        TextInput stage1 = TextInput.create("stage1", TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder(
                        "Current: " + game.getPublicObjectives1Peekable().size())
                .build();
        TextInput stage2 = TextInput.create("stage2", TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder(
                        "Current: " + game.getPublicObjectives2Peekable().size())
                .build();
        // Discord's modal Label text caps at 45 chars (Label.LABEL_MAX_LENGTH) - keep these short or
        // Modal.create(...) throws synchronously and the button just shows as "failed" with no detail.
        Modal modal = Modal.create("fowSetupCustomScoringResolve", "Custom Scoring")
                .addComponents(
                        Label.of("Victory Points (blank to skip)", vp),
                        Label.of("Max Secret Objs/Player (blank=skip)", so),
                        Label.of("Max Peekable Stage 1 (blank to skip)", stage1),
                        Label.of("Max Peekable Stage 2 (blank to skip)", stage2))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("fowSetupCustomScoringResolve")
    static void resolveCustomScoringModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        StringBuilder applied = new StringBuilder();
        StringBuilder problems = new StringBuilder();
        applyIntIfPresent(event, "vp", "VP", game::setVp, applied, problems);
        applyIntIfPresent(event, "so", "Max Secret Objectives/Player", game::setMaxSOCountPerPlayer, applied, problems);
        applyIntIfPresent(
                event, "stage1", "Max Peekable Stage 1", v -> game.setUpPeekableObjectives(v, 1), applied, problems);
        applyIntIfPresent(
                event, "stage2", "Max Peekable Stage 2", v -> game.setUpPeekableObjectives(v, 2), applied, problems);

        StringBuilder summary = new StringBuilder();
        if (applied.isEmpty() && problems.isEmpty()) {
            summary.append("No values were changed (every field was left blank).");
        } else {
            if (!applied.isEmpty()) summary.append("Updated:\n").append(applied);
            if (!problems.isEmpty()) summary.append("Skipped:\n").append(problems);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), summary.toString());
        FowSetupWizardService.openOrRefresh(game);
    }

    private static void applyIntIfPresent(
            ModalInteractionEvent event,
            String field,
            String label,
            IntConsumer setter,
            StringBuilder applied,
            StringBuilder problems) {
        String raw = event.getValue(field).getAsString().trim();
        if (raw.isEmpty()) return;
        try {
            int value = Integer.parseInt(raw);
            setter.accept(value);
            applied.append("- ").append(label).append(" -> ").append(value).append('\n');
        } catch (NumberFormatException e) {
            problems.append("- ").append(label).append(": '").append(raw).append("' is not a whole number.\n");
        }
    }
}
