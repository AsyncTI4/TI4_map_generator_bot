package ti4.service.fow.setup;

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
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.HomebrewService.Homebrew;

/**
 * Step 1 of the FoW setup wizard: a categorized rundown of the same homebrew/scoring options
 * {@code getHomebrewButtons} (Step 0's "Supported Homebrew" button) offers as one flat list, plus a
 * TIGL toggle and custom scoring values that have no existing GM-facing control. Every homebrew button
 * here reuses {@code HomebrewService}'s existing {@code setupHomebrew_<ENUM>} button ids directly, so
 * clicking one is handled by its already-registered handler - nothing here duplicates that logic.
 */
final class FowSetupGameOptionsService {

    private FowSetupGameOptionsService() {}

    static void render(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("Same options as `/game weird_game_setup` and the \"Supported Homebrew\" button, just ")
                .append("grouped. Most of these are one-way switches - see the info thread for the ")
                .append("gotchas.\n\n");

        sb.append("### Homebrew\n");
        homebrewOption(sb, buttons, Homebrew.HBVOTC, game.isVotcMode());
        homebrewOption(sb, buttons, Homebrew.HBHBSC, game.isHomebrewSCMode());
        homebrewOption(sb, buttons, Homebrew.HBREMOVESFTT, "true".equals(game.getStoredValue("removeSupports")));
        sb.append("- **TIGL Game**: mark this game as a TIGL-tracked game.")
                .append(game.isCompetitiveTIGLGame() ? " _(ON)_" : "")
                .append('\n');
        buttons.add(
                game.isCompetitiveTIGLGame()
                        ? Buttons.red("fowSetupToggleTigl", "Disable TIGL Game")
                        : Buttons.green("fowSetupToggleTigl", "Enable TIGL Game"));

        sb.append("### Factions\n");
        homebrewOption(sb, buttons, Homebrew.HBDSFACTIONS, game.isDiscordantStarsMode());
        homebrewOption(sb, buttons, Homebrew.HBBRFACTIONS, game.isBlueReverieMode());

        sb.append("### Major card deck overhauls\n");
        homebrewOption(sb, buttons, Homebrew.HBABSOLRELICSAGENDAS, game.isAbsolMode());
        homebrewOption(sb, buttons, Homebrew.HBABSOLTECHSMECHS, game.isAbsolMode());
        homebrewOption(sb, buttons, Homebrew.HBDSEXPLORES, game.isUnchartedSpaceStuff());
        homebrewOption(sb, buttons, Homebrew.HBACDECK2, game.isAcd2());

        sb.append("### Scoring\n")
                .append("> Currently: ")
                .append(game.getVp())
                .append(" VP, ")
                .append(game.getMaxSOCountPerPlayer())
                .append(" SO/player, ")
                .append(game.getPublicObjectives1Peekable().size())
                .append(" peekable Stage 1, ")
                .append(game.getPublicObjectives2Peekable().size())
                .append(" peekable Stage 2.\n");
        homebrewOption(sb, buttons, Homebrew.HB444, false);
        homebrewOption(sb, buttons, Homebrew.HB456, false);
        buttons.add(Buttons.blue("fowSetupCustomScoring~MDL", "Set Custom Scoring (VP/SO/Stage 1/Stage 2)"));

        buttons.add(Buttons.red("setupHomebrewNone", "Remove All Homebrews"));
    }

    private static void homebrewOption(StringBuilder sb, List<Button> buttons, Homebrew hb, boolean active) {
        sb.append("- **")
                .append(hb.name)
                .append("**: ")
                .append(hb.description)
                .append(active ? " _(ON)_" : "")
                .append('\n');
        buttons.add(Buttons.green("setupHomebrew_" + hb, hb.name));
    }

    // --- TIGL toggle (simple flag, no existing dedicated control) ---

    @ButtonHandler("fowSetupToggleTigl")
    static void toggleTigl(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        game.setCompetitiveTIGLGame(!game.isCompetitiveTIGLGame());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "TIGL game flag set to **" + game.isCompetitiveTIGLGame() + "**.");
        FowSetupWizardService.openOrRefresh(game);
    }

    // --- Custom scoring: VP / max SO per player / peekable Stage 1 & 2 counts, any subset ---

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
        Modal modal = Modal.create("fowSetupCustomScoringResolve", "Custom Scoring")
                .addComponents(
                        Label.of("Victory Points (leave blank to skip)", vp),
                        Label.of("Max Secret Objectives / Player (blank to skip)", so),
                        Label.of("Max Peekable Stage 1 Objectives (blank to skip)", stage1),
                        Label.of("Max Peekable Stage 2 Objectives (blank to skip)", stage2))
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
