package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.componentsV2.ComponentPanel;
import ti4.message.componentsV2.PanelView;
import ti4.service.fow.GMService;
import ti4.service.option.FOWOptionService;

public final class FowSetupPanel {

    static final String BACK = "fowWizBack";
    private static final String STEP_SELECT = "fowWizStep";
    private static final String GO_PREFIX = "fowWizGo_";
    private static final String REPOST = "fowWizRepost";

    private FowSetupPanel() {}

    public static void repost(Game game) {
        repost(game, null);
    }

    static void repost(Game game, String notice) {
        FowSetupWizardState state = prepare(game);
        TextChannel gmChannel = GMService.getGMChannel(game);
        if (gmChannel == null) return;
        FowSetupWizardService.postIntroOnce(game, gmChannel, state);
        ComponentPanel.repost(
                gmChannel,
                gmChannel,
                panelMessageId(state),
                build(game, state, notice),
                message -> rememberPanel(game, message));
        FowSetupWizardService.postStepInfoThreadOnce(game, state);
    }

    private static String panelMessageId(FowSetupWizardState state) {
        Long id = state.getPanelMessageId();
        return id == null ? null : Long.toString(id);
    }

    private static void rememberPanel(Game game, Message message) {
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.setPanelMessageId(message.getIdLong());
        FowSetupWizardService.saveState(game, state);
    }

    static void show(GenericInteractionCreateEvent event, Game game, String notice) {
        FowSetupWizardState state = prepare(game);
        ComponentPanel.showOrRepost(
                event, panelMessageId(state), () -> build(game, state, notice), () -> repost(game, notice));
    }

    static void show(GenericInteractionCreateEvent event, Game game) {
        show(event, game, null);
    }

    static void openSubView(GenericInteractionCreateEvent event, Game game, String subView, String notice) {
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.setSubView(subView);
        FowSetupWizardService.saveState(game, state);
        show(event, game, notice);
    }

    private static FowSetupWizardState prepare(Game game) {
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        if (state.getStep() == FowSetupStep.PLAYER_ROLES) {
            FowSetupPlayerRolesService.ensureInitialized(game, state);
        }
        return state;
    }

    public static List<MessageTopLevelComponent> build(Game game, FowSetupWizardState state, String notice) {
        List<ActionRow> navigation = List.of(ActionRow.of(stepSelect(game, state)), ActionRow.of(navButtons(state)));
        return ComponentPanel.compose(header(game, state), notice, viewFor(game, state), navigation);
    }

    private static PanelView viewFor(Game game, FowSetupWizardState state) {
        return switch (state.getStep()) {
            case GAME_TYPE -> FowSetupPanelBasics.gameType(game, state);
            case GAME_OPTIONS -> FowSetupPanelOptions.view(game, state);
            case FOG_TYPE -> FowSetupPanelFog.view(game, state);
            case MAP_LOAD -> FowSetupPanelBasics.mapLoad(game);
            case PLAYER_ROLES -> FowSetupPanelPlayers.roles(game, state);
            case FACTIONS -> FowSetupPanelFactions.view(game, state);
            case TABLE_ORDER -> FowSetupPanelPlayers.tableOrder(game, state);
            case DECKS -> FowSetupPanelDecks.view(game, state);
            case NEUTRAL_PLAYER -> FowSetupPanelPlayers.neutral(game, state);
            case DEAL_SO -> FowSetupPanelBasics.start(game, state);
            case DONE -> FowSetupPanelBasics.done();
        };
    }

    private static String header(Game game, FowSetupWizardState state) {
        FowSetupStep step = state.getStep();
        String title = step == FowSetupStep.DONE
                ? "### FoW Setup · " + FowSetupWizardService.stepTitle(step)
                : "### FoW Setup · Step " + (step.ordinal() + 1) + "/" + stepCount() + " · "
                        + FowSetupWizardService.stepTitle(step);
        String summary = expansionLabel(game) + " · " + playingCount(game, state) + " playing · "
                + FOWOptionService.status(game) + " · " + game.getVp() + " VP";
        return title + "\n-# " + summary;
    }

    private static int stepCount() {
        return FowSetupStep.values().length - 1;
    }

    static String expansionLabel(Game game) {
        String base;
        if (game.isBaseGameMode()) {
            base = "Base game";
        } else if (game.isThundersEdge()) {
            base = "Thunder's Edge + New PoK";
        } else if ("true".equals(game.getStoredValue("useOldPok"))) {
            base = "Old PoK";
        } else {
            base = "New PoK";
        }
        if (game.isTwilightsFallMode()) base += " · Twilight's Fall";
        if (game.isFrankenGame()) base += " · Franken";
        return base;
    }

    private static long playingCount(Game game, FowSetupWizardState state) {
        return game.getPlayers().values().stream()
                .filter(player -> FowSetupPlayerRolesService.isPlayerCandidate(player, state))
                .count();
    }

    private static StringSelectMenu stepSelect(Game game, FowSetupWizardState state) {
        List<SelectOption> options = new ArrayList<>();
        for (FowSetupStep step : FowSetupStep.values()) {
            if (step == FowSetupStep.DONE) continue;
            StepStatus status = stepStatus(game, state, step);
            SelectOption option = SelectOption.of(
                            (step.ordinal() + 1) + ". " + FowSetupWizardService.stepTitle(step), step.name())
                    .withDescription(StringUtils.abbreviate(status.summary(), 100))
                    .withDefault(step == state.getStep());
            if (status.done()) {
                option = option.withEmoji(Emoji.fromUnicode("✅"));
            }
            options.add(option);
        }
        return StringSelectMenu.create(STEP_SELECT)
                .setPlaceholder("Jump to a step")
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }

    private record StepStatus(String summary, boolean done) {}

    private static StepStatus stepStatus(Game game, FowSetupWizardState state, FowSetupStep step) {
        return switch (step) {
            case GAME_TYPE -> new StepStatus(expansionLabel(game), false);
            case GAME_OPTIONS ->
                new StepStatus(game.getVp() + " VP · " + game.getMaxSOCountPerPlayer() + " secrets each", false);
            case FOG_TYPE -> new StepStatus(FOWOptionService.status(game), false);
            case MAP_LOAD -> {
                int tiles = game.getTileMap().size();
                yield new StepStatus(tiles == 0 ? "No map yet" : tiles + " tiles", tiles > 0);
            }
            case PLAYER_ROLES ->
                new StepStatus(
                        playingCount(game, state) + " playing, "
                                + state.getNonPlayingUserIds().size() + " GM/observer",
                        state.isPlayerRolesInitialized());
            case FACTIONS -> {
                List<Player> candidates = FowSetupFactionService.setupCandidates(game, state);
                long placed = candidates.stream()
                        .filter(player -> FowSetupFactionService.isPlacedOnMap(game, player))
                        .count();
                yield new StepStatus(
                        placed + "/" + candidates.size() + " placed",
                        !candidates.isEmpty() && placed == candidates.size());
            }
            case TABLE_ORDER -> {
                Player speaker = game.getSpeaker();
                yield new StepStatus(
                        speaker == null ? "No speaker yet" : "Speaker: " + speaker.getUserName(), speaker != null);
            }
            case DECKS -> new StepStatus("Pick decks, or import a Deck-editor config", false);
            case NEUTRAL_PLAYER -> {
                Player neutral = game.getPlayer(Constants.dicecordId);
                boolean hasColor = neutral != null && StringUtils.isNotBlank(neutral.getColor());
                yield new StepStatus(hasColor ? "Colour: " + neutral.getColor() : "Optional · no colour yet", hasColor);
            }
            case DEAL_SO -> new StepStatus("Deal secret objectives, then start the game", false);
            case DONE -> new StepStatus("", false);
        };
    }

    private static List<Button> navButtons(FowSetupWizardState state) {
        List<Button> buttons = new ArrayList<>();
        FowSetupStep[] steps = FowSetupStep.values();
        int index = state.getStep().ordinal();
        if (index > 0) {
            buttons.add(Buttons.gray(GO_PREFIX + steps[index - 1], "⬅ Previous"));
        }
        if (index < steps.length - 1) {
            buttons.add(Buttons.blue(GO_PREFIX + steps[index + 1], "Next ➡"));
        }
        if (FowSetupWizardService.hasStepInfo(state.getStep())) {
            buttons.add(Buttons.gray("fowSetupShowInfo", "ℹ Info"));
        }
        buttons.add(Buttons.gray(REPOST, "Repost"));
        if (state.getStep() == FowSetupStep.DONE) {
            buttons.add(Buttons.red("fowSetupCloseWizard", "Close Wizard"));
        }
        return buttons;
    }

    static Button backButton() {
        return Buttons.gray(BACK, "Back");
    }

    @SelectionHandler(STEP_SELECT)
    static void jumpToStep(StringSelectInteractionEvent event, Game game) {
        goTo(event, game, FowSetupStep.valueOf(event.getValues().getFirst()));
    }

    @ButtonHandler(GO_PREFIX)
    static void stepButton(ButtonInteractionEvent event, Game game, String buttonID) {
        goTo(event, game, FowSetupStep.valueOf(buttonID.substring(GO_PREFIX.length())));
    }

    private static void goTo(GenericInteractionCreateEvent event, Game game, FowSetupStep step) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.setStep(step);
        state.setSubView(null);
        state.setStepTab(null);
        FowSetupWizardService.saveState(game, state);
        FowSetupWizardService.postStepInfoThreadOnce(game, state);
        show(event, game);
    }

    @ButtonHandler(BACK)
    static void back(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        openSubView(event, game, null, null);
    }

    @ButtonHandler(REPOST)
    static void repostButton(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        repost(game);
    }
}
