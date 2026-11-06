package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.special.SetupNeutralPlayer;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.componentsV2.ComponentPanel;
import ti4.model.ColorModel;
import ti4.service.game.GameColorsService;

final class FowSetupPanelPlayers {

    private static final String ROLES_PREFIX = "fowWizRoles_";
    private static final String SPEAKER = "fowWizSpeaker";
    private static final String MANUAL_ORDER = "fowWizOrderManual";
    private static final String SEAT = "fowWizSeat";
    private static final String DICE_MODAL = "fowWizDice~MDL";
    private static final String DICE_RESOLVE = "fowWizDiceResolve";
    private static final String RESOLVE_LOWEST = "fowWizResolveLow";
    private static final String RESOLVE_MIDDLE = "fowWizResolveMid";
    private static final String NEUTRAL_PREFIX = "fowWizNeutral_";
    private static final String NEUTRAL_PAGE_PREFIX = "fowWizNeutralPage_";
    private static final String NEUTRAL_RANDOM = "fowWizNeutralRandom";
    private static final String MANUAL_SUBVIEW = "order:manual";
    private static final int COLOUR_MENUS_PER_PAGE = 3;

    private FowSetupPanelPlayers() {}

    static FowSetupPanel.View roles(Game game, FowSetupWizardState state) {
        List<Player> roster = game.getPlayers().values().stream()
                .filter(player -> !player.isDummy())
                .toList();
        StringBuilder text = new StringBuilder("Pick everyone who is **playing**. Anyone left out (a GM, helper or")
                .append(" observer) is skipped for factions, positions and seat order. A GM who also plays should")
                .append(" stay picked.\n\n");
        for (Player player : roster) {
            boolean playing = !state.getNonPlayingUserIds().contains(player.getUserID());
            text.append("> ")
                    .append(player.getUserName())
                    .append(": ")
                    .append(playing ? "**Playing**" : "_GM / Observer_")
                    .append('\n');
        }
        if (roster.isEmpty()) {
            text.append("_Nobody has joined this game yet._");
        }
        List<ActionRow> rows = new ArrayList<>();
        List<List<Player>> pages = ListUtils.partition(roster, SelectMenu.OPTIONS_MAX_AMOUNT);
        for (int index = 0; index < pages.size() && index < 3; index++) {
            List<SelectOption> options = pages.get(index).stream()
                    .map(player -> SelectOption.of(StringUtils.left(player.getUserName(), 100), player.getUserID())
                            .withDefault(!state.getNonPlayingUserIds().contains(player.getUserID())))
                    .toList();
            rows.add(ActionRow.of(StringSelectMenu.create(ROLES_PREFIX + index)
                    .setPlaceholder("Nobody playing")
                    .addOptions(options)
                    .setRequiredRange(0, options.size())
                    .build()));
        }
        List<ContainerChildComponent> body = List.of(TextDisplay.of(text.toString()));
        return FowSetupPanel.View.of(body, rows);
    }

    @SelectionHandler(ROLES_PREFIX)
    static void setRoles(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        FowSetupPlayerRolesService.ensureInitialized(game, state);
        applyRoles(state, optionValues(event), event.getValues());
        FowSetupWizardService.saveState(game, state);
        FowSetupPanel.show(event, game);
    }

    static void applyRoles(FowSetupWizardState state, List<String> menuUserIds, List<String> playingUserIds) {
        for (String userId : menuUserIds) {
            if (playingUserIds.contains(userId)) {
                state.getNonPlayingUserIds().remove(userId);
            } else {
                state.getNonPlayingUserIds().add(userId);
            }
        }
    }

    static List<String> optionValues(StringSelectInteractionEvent event) {
        return event.getSelectMenu().getOptions().stream()
                .map(SelectOption::getValue)
                .toList();
    }

    static FowSetupPanel.View tableOrder(Game game, FowSetupWizardState state) {
        if (MANUAL_SUBVIEW.equals(state.getSubView())) {
            return manualOrder(game, state);
        }
        List<Player> seats = FowSetupTableOrderService.seatCandidates(game, state);
        StringBuilder text =
                new StringBuilder("### Seat order\n").append(FowSetupTableOrderService.orderLines(game, state));
        if (state.getDiceCount() != null && state.getDiceSides() != null) {
            text.append("\n**Dice:** ")
                    .append(state.getDiceCount())
                    .append('d')
                    .append(state.getDiceSides())
                    .append(", roll buttons are in each player's private channel. ")
                    .append(state.getDiceRolls().size())
                    .append('/')
                    .append(seats.size())
                    .append(" rolled.\n");
        }
        if (!state.getDiceRolls().isEmpty()) {
            text.append("### Rolls so far\n").append(FowSetupTableOrderService.rollLines(game, state));
        }
        text.append("\n-# **Manual** picks seats one at a time. **Dice** sends each player a private roll button;")
                .append(" resolve once the rolls are in. The speaker is set on its own, below.");

        List<ActionRow> rows = new ArrayList<>();
        if (!seats.isEmpty()) {
            rows.add(ActionRow.of(playerSelect(SPEAKER, seats, "Pick the speaker", game.getSpeaker())));
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(MANUAL_ORDER, "Manual order"));
        buttons.add(Buttons.blue(DICE_MODAL, "Configure dice..."));
        if (!state.getDiceRolls().isEmpty()) {
            buttons.add(Buttons.green(RESOLVE_LOWEST, "Resolve: lowest first"));
            buttons.add(Buttons.green(RESOLVE_MIDDLE, "Resolve: closest to 50"));
        }
        rows.add(ActionRow.of(buttons));
        return FowSetupPanel.View.of(List.of(TextDisplay.of(text.toString())), rows);
    }

    private static FowSetupPanel.View manualOrder(Game game, FowSetupWizardState state) {
        List<String> picks = state.getManualOrderPicks();
        StringBuilder text = new StringBuilder("### Manual seat order\n");
        for (int index = 0; index < picks.size(); index++) {
            Player player = game.getPlayer(picks.get(index));
            text.append("> ")
                    .append(index + 1)
                    .append(". ")
                    .append(player == null ? picks.get(index) : player.getUserName())
                    .append('\n');
        }
        List<Player> remaining = FowSetupTableOrderService.seatCandidates(game, state).stream()
                .filter(player -> !picks.contains(player.getUserID()))
                .toList();
        text.append(remaining.isEmpty() ? "Everyone has a seat." : "Pick seat #" + (picks.size() + 1) + '.');
        List<ActionRow> rows = new ArrayList<>();
        if (!remaining.isEmpty()) {
            rows.add(ActionRow.of(playerSelect(SEAT, remaining, "Seat #" + (picks.size() + 1), null)));
        }
        rows.add(ActionRow.of(Buttons.gray(MANUAL_ORDER, "Start over"), FowSetupPanel.backButton()));
        return FowSetupPanel.View.sub(List.of(TextDisplay.of(text.toString())), rows, false);
    }

    private static StringSelectMenu playerSelect(String id, List<Player> players, String placeholder, Player chosen) {
        List<SelectOption> options = players.stream()
                .limit(SelectMenu.OPTIONS_MAX_AMOUNT)
                .map(player -> SelectOption.of(StringUtils.left(player.getUserName(), 100), player.getUserID())
                        .withDefault(chosen != null && chosen.getUserID().equals(player.getUserID())))
                .toList();
        return StringSelectMenu.create(id)
                .setPlaceholder(placeholder)
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }

    @SelectionHandler(SPEAKER)
    static void pickSpeaker(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Player player = game.getPlayer(event.getValues().getFirst());
        if (player == null) return;
        game.setSpeaker(player);
        FowSetupPanel.show(event, game, player.getUserName() + " is now speaker.");
    }

    @ButtonHandler(MANUAL_ORDER)
    static void startManualOrder(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.getManualOrderPicks().clear();
        FowSetupWizardService.saveState(game, state);
        FowSetupPanel.openSubView(event, game, MANUAL_SUBVIEW, null);
    }

    @SelectionHandler(SEAT)
    static void pickSeat(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        String userId = event.getValues().getFirst();
        if (!state.getManualOrderPicks().contains(userId)) {
            state.getManualOrderPicks().add(userId);
        }
        if (state.getManualOrderPicks().size()
                < FowSetupTableOrderService.seatCandidates(game, state).size()) {
            FowSetupWizardService.saveState(game, state);
            FowSetupPanel.show(event, game);
            return;
        }
        FowSetupTableOrderService.finalizeOrder(event, game, state, new ArrayList<>(state.getManualOrderPicks()));
        state.getManualOrderPicks().clear();
        state.setSubView(null);
        FowSetupWizardService.saveState(game, state);
        FowSetupPanel.show(event, game, "Seat order set.");
    }

    static Modal diceModal(FowSetupWizardState state) {
        return Modal.create(DICE_RESOLVE, "Configure Dice")
                .addComponents(
                        TextDisplay.of("Each player gets a roll button in their own private channel, so nobody"
                                + " sees anyone else's roll. Saving clears earlier rolls."),
                        Label.of("Number of dice", diceInput("diceCount", state.getDiceCount(), "2")),
                        Label.of("Sides per die", diceInput("diceSides", state.getDiceSides(), "6")))
                .build();
    }

    private static TextInput diceInput(String id, Integer current, String example) {
        TextInput.Builder input =
                TextInput.create(id, TextInputStyle.SHORT).setRequired(true).setPlaceholder("e.g. " + example);
        if (current != null) {
            input.setValue(String.valueOf(current));
        }
        return input.build();
    }

    @ButtonHandler(DICE_MODAL)
    static void openDiceModal(ButtonInteractionEvent event, Game game) {
        event.replyModal(diceModal(FowSetupWizardService.loadState(game)))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(DICE_RESOLVE)
    static void resolveDiceModal(ModalInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        Integer count = FowSetupPanel.parseInt(FowSetupPanel.value(event, "diceCount"));
        Integer sides = FowSetupPanel.parseInt(FowSetupPanel.value(event, "diceSides"));
        if (count == null || sides == null) {
            FowSetupPanel.show(event, game, "Dice count and sides must be whole numbers. Nothing was sent.");
            return;
        }
        FowSetupPanel.show(event, game, FowSetupTableOrderService.configureDice(game, count, sides));
    }

    @ButtonHandler(RESOLVE_LOWEST)
    static void resolveLowestFirst(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupTableOrderService.applyDiceOrder(event, game, FowSetupTableOrderService.LOWEST_FIRST);
        FowSetupPanel.show(event, game, "Seat order set from the rolls, lowest first.");
    }

    @ButtonHandler(RESOLVE_MIDDLE)
    static void resolveClosestToFifty(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupTableOrderService.applyDiceOrder(event, game, FowSetupTableOrderService.CLOSEST_TO_50);
        FowSetupPanel.show(event, game, "Seat order set from the rolls, closest to 50 first.");
    }

    static FowSetupPanel.View neutral(Game game, FowSetupWizardState state) {
        Player neutral = game.getPlayer(Constants.dicecordId);
        String color = neutral != null && StringUtils.isNotBlank(neutral.getColor()) ? neutral.getColor() : null;
        String text = "Give the neutral player a colour, then add their units with the normal unit commands"
                + " targeting that colour (see the info thread).\n> Currently: "
                + (color == null ? "_no colour yet_" : "**" + color + "**");

        List<SelectOption> options = FowSetupNeutralPlayerService.colorSelectOptions(neutralColors(game));
        int pages = ComponentPanel.pageCount(options.size(), COLOUR_MENUS_PER_PAGE);
        int page = Math.min(pageOf(state), pages - 1);
        List<ActionRow> rows = new ArrayList<>(
                ComponentPanel.selectPages(NEUTRAL_PREFIX, options, page, COLOUR_MENUS_PER_PAGE, "Neutral colour"));
        List<Button> buttons = new ArrayList<>();
        if (page > 0) buttons.add(Buttons.gray(NEUTRAL_PAGE_PREFIX + (page - 1), "◀ More colours"));
        if (page < pages - 1) buttons.add(Buttons.gray(NEUTRAL_PAGE_PREFIX + (page + 1), "More colours ▶"));
        buttons.add(Buttons.gray(NEUTRAL_RANDOM, "Random colour"));
        rows.add(ActionRow.of(buttons));
        return FowSetupPanel.View.of(List.of(TextDisplay.of(text)), rows);
    }

    private static int pageOf(FowSetupWizardState state) {
        Integer page = state.getStepTab() == null ? null : FowSetupPanel.parseInt(state.getStepTab());
        return page == null || page < 0 ? 0 : page;
    }

    private static List<ColorModel> neutralColors(Game game) {
        return GameColorsService.getUnusedColors(game).stream()
                .filter(color -> ColorChangeHelper.isColorAllowedForPlayer(color.getName(), Constants.dicecordId, game))
                .sorted(Comparator.comparing(ColorModel::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @SelectionHandler(NEUTRAL_PREFIX)
    static void pickNeutralColor(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String color = event.getValues().getFirst();
        game.setupNeutralPlayer(color);
        FowSetupPanel.show(event, game, "Neutral player set to **" + color + "**.");
    }

    @ButtonHandler(NEUTRAL_PAGE_PREFIX)
    static void pageNeutralColors(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.setStepTab(buttonID.substring(NEUTRAL_PAGE_PREFIX.length()));
        FowSetupWizardService.saveState(game, state);
        FowSetupPanel.show(event, game);
    }

    @ButtonHandler(NEUTRAL_RANDOM)
    static void randomNeutralColor(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String color = SetupNeutralPlayer.pickNeutralColor(game);
        game.setupNeutralPlayer(color);
        FowSetupPanel.show(event, game, "Neutral player set to **" + color + "**.");
    }
}
