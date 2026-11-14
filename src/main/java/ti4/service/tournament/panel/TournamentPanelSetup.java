package ti4.service.tournament.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentSetupOption;
import ti4.service.tournament.TournamentSetupOptions;
import ti4.service.tournament.panel.TournamentPanel.Context;
import ti4.service.tournament.panel.TournamentPanel.Tab;
import ti4.service.tournament.panel.TournamentPanel.View;
import ti4.service.tournament.setup.TournamentSetupApplier;
import ti4.service.tournament.setup.TournamentSetupGame;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentService;

final class TournamentPanelSetup {

    private static final String CYCLE = "trnSetup_";
    private static final String CREATE_SETUP_GAME = "trnSetupGame_";
    private static final String USE_GAME_MODAL = "trnSetupUse~MDL_";
    private static final String USE_GAME_SAVE = "trnSetupUseSave_";
    private static final String CLEAR_SOURCE = "trnSetupClear_";
    private static final int MAX_OPTIONS_SHOWN = 4;

    private TournamentPanelSetup() {}

    static View view(Context context) {
        Tournament tournament = context.tournament();
        List<ContainerChildComponent> body = new ArrayList<>();
        List<ActionRow> controls = new ArrayList<>();
        boolean warning = false;
        if (tournament.isFogMode()) {
            body.add(TextDisplay.of("Copying a setup into Fog of War games comes later."));
        } else {
            warning = sourceSection(tournament, body, controls);
        }
        registrySection(context, body);
        return View.of(body, controls).withWarning(warning);
    }

    private static boolean sourceSection(
            Tournament tournament, List<ContainerChildComponent> body, List<ActionRow> controls) {
        long id = tournament.getId();
        List<Button> buttons = new ArrayList<>();
        String sourceName = tournament.getSetupSourceGame();
        boolean warning = false;
        if (StringUtils.isBlank(sourceName)) {
            body.add(TextDisplay.of("**Setup game:** none yet.\n-# Set up one game the normal way (expansion, homebrew,"
                    + " decks, removed cards, Milty or draft settings). Every game of this round copies it when it is"
                    + " created. Seating, slices and factions are still drawn in each game."));
            buttons.add(Buttons.green(CREATE_SETUP_GAME + id, "Create setup game"));
        } else {
            Game source = TournamentSetupApplier.loadSource(tournament);
            if (source == null) {
                body.add(TextDisplay.of("⚠️ **Setup game " + sourceName + "** no longer exists."));
                warning = true;
            } else {
                String link = source.getActionsChannel() == null
                        ? sourceName
                        : source.getActionsChannel().getAsMention();
                String kind =
                        TournamentSetupGame.isOwnSetupGame(source, tournament) ? "Setup game" : "Setup copied from";
                body.add(TextDisplay.of("**" + kind + ":** " + link + "\n"
                        + String.join("\n", TournamentSetupApplier.summary(source))));
                List<String> warnings = TournamentSetupApplier.warnings(source, tournament.getGroupSize());
                if (!warnings.isEmpty()) {
                    body.add(TextDisplay.of("⚠️ " + String.join("\n⚠️ ", warnings)));
                    warning = true;
                }
            }
            buttons.add(Buttons.gray(CLEAR_SOURCE + id, "Stop copying"));
        }
        buttons.add(Buttons.blue(USE_GAME_MODAL + id, "Use setup from game"));
        controls.add(ActionRow.of(buttons));
        return warning;
    }

    private static void registrySection(Context context, List<ContainerChildComponent> body) {
        List<TournamentSetupOption> options = TournamentSetupOptions.all();
        if (options.isEmpty()) return;
        Tournament tournament = context.tournament();
        Map<String, String> values = TournamentService.gameSetup(tournament);
        boolean editable = tournament.getStatus().allowsRosterChanges();
        options.stream().limit(MAX_OPTIONS_SHOWN).forEach(option -> {
            String current = TournamentSetupOptions.currentChoice(option, values);
            body.add(Section.of(
                    Buttons.blue(CYCLE + context.id() + "_" + option.id(), "Change")
                            .withDisabled(!editable),
                    TextDisplay.of("**" + option.label() + ":** " + current + "\n-# " + option.description())));
        });
    }

    @ButtonHandler(value = CREATE_SETUP_GAME, save = false)
    static void createSetupGame(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, CREATE_SETUP_GAME);
        Tournament tournament = TournamentService.getBean().find(id).orElseThrow();
        if (tournament.isFogMode()) {
            TournamentPanel.show(event, id, Tab.SETUP, "Copying a setup into Fog of War games comes later.");
            return;
        }
        String notice = TournamentSetupGame.create(tournament, event.getMember(), event.getMessageChannel());
        TournamentPanel.show(event, id, Tab.SETUP, notice);
    }

    @ButtonHandler(value = USE_GAME_MODAL, save = false)
    static void openUseGame(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, USE_GAME_MODAL);
        Modal modal = Modal.create(USE_GAME_SAVE + id, "Use setup from game")
                .addComponents(
                        TextDisplay.of("Every new game of this round copies that game's settings. Only settings are"
                                + " copied; seating, slices and factions are drawn in each game."),
                        Label.of(
                                "Game name, for example pbd9123",
                                TournamentPanel.input("game", TextInputStyle.SHORT, null, true)))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(value = USE_GAME_SAVE, save = false)
    static void useGame(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), USE_GAME_SAVE);
        String gameName = TournamentPanel.value(event, "game").toLowerCase();
        if (!GameManager.isValid(gameName)) {
            TournamentPanel.show(event, id, Tab.SETUP, "There is no game called " + gameName + ".");
            return;
        }
        TournamentService.getBean().update(id, pool -> pool.setSetupSourceGame(gameName));
        TournamentPanel.show(event, id, Tab.SETUP, "New games will copy the setup of **" + gameName + "**.");
    }

    @ButtonHandler(value = CLEAR_SOURCE, save = false)
    static void clearSource(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, CLEAR_SOURCE);
        TournamentService.getBean().update(id, pool -> pool.setSetupSourceGame(null));
        TournamentPanel.show(
                event,
                id,
                Tab.SETUP,
                "New games no longer copy a setup. The setup game itself is kept; end it by hand when you no longer"
                        + " need it.");
    }

    @ButtonHandler(value = CYCLE, save = false)
    static void cycle(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, CYCLE);
        String optionId = TournamentPanel.argOf(buttonID, CYCLE);
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElseThrow();
        TournamentSetupOptions.find(optionId).ifPresent(option -> {
            String current = TournamentSetupOptions.currentChoice(option, TournamentService.gameSetup(tournament));
            service.setGameSetupValue(id, option.id(), TournamentSetupOptions.nextChoice(option, current));
        });
        TournamentPanel.show(event, id, Tab.SETUP, null);
    }
}
