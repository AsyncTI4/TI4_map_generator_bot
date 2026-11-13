package ti4.service.tournament.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentSetupOption;
import ti4.service.tournament.TournamentSetupOptions;
import ti4.service.tournament.panel.TournamentPanel.Context;
import ti4.service.tournament.panel.TournamentPanel.Tab;
import ti4.service.tournament.panel.TournamentPanel.View;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentService;

final class TournamentPanelSetup {

    private static final String CYCLE = "trnSetup_";
    private static final int MAX_OPTIONS_SHOWN = 6;

    private TournamentPanelSetup() {}

    static View view(Context context) {
        Tournament tournament = context.tournament();
        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(TextDisplay.of(
                "-# Applied to every game of this round right after it is created. Use **Apply game setup to all games**"
                        + " on the Create tab to apply changes to games that already exist."));
        List<TournamentSetupOption> options = TournamentSetupOptions.all();
        if (options.isEmpty()) {
            body.add(TextDisplay.of("No game setup options are available yet. Deck choices, Milty draft types and"
                    + " similar settings will show up here once they are added."));
            return View.of(body, List.of());
        }
        Map<String, String> values = TournamentService.gameSetup(tournament);
        boolean editable = tournament.getStatus().allowsRosterChanges();
        options.stream().limit(MAX_OPTIONS_SHOWN).forEach(option -> {
            String current = TournamentSetupOptions.currentChoice(option, values);
            body.add(Section.of(
                    Buttons.blue(CYCLE + context.id() + "_" + option.id(), "Change")
                            .withDisabled(!editable),
                    TextDisplay.of("**" + option.label() + ":** " + current + "\n-# " + option.description())));
        });
        return View.of(body, List.of());
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
