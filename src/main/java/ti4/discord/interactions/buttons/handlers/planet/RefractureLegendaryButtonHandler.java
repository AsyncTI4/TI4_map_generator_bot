package ti4.discord.interactions.buttons.handlers.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.PublicObjectiveModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.map.RefractureRulesService;
import ti4.service.map.TokenPlanetService;
import ti4.service.objectives.ScorePublicObjectiveService;

@UtilityClass
public class RefractureLegendaryButtonHandler {

    @ButtonHandler("refractureMaahesStart")
    static void startMaahes(ButtonInteractionEvent event, Player player, Game game) {
        if (!RefractureRulesService.canUseMaahes(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Maahes_ can no longer be used.");
            return;
        }
        player.exhaustPlanetAbility("maahes");
        RefractureRulesService.startMaahes(game, player);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose fracture systems for _Radiant Energy Tap_, then select Done.",
                getMaahesButtons(game, player));
    }

    @ButtonHandler("refractureMaahesPlace_")
    static void placeMaahesFrontier(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.replace("refractureMaahesPlace_", ""));
        if (!RefractureRulesService.placeMaahesFrontier(game, player, tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That system is no longer eligible for _Radiant Energy Tap_.");
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a frontier token in "
                        + tile.getRepresentationForButtons(game, player) + " with _Radiant Energy Tap_.");
    }

    @ButtonHandler("refractureMaahesDone")
    static void finishMaahes(ButtonInteractionEvent event, Player player, Game game) {
        if (!RefractureRulesService.hasPendingMaahes(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That _Radiant Energy Tap_ selection is no longer active.");
            return;
        }
        RefractureRulesService.clearPendingMaahes(game, player);
        ButtonHelper.deleteAllButtons(event);
    }

    private static List<Button> getMaahesButtons(Game game, Player player) {
        List<Button> buttons = RefractureRulesService.getEligibleMaahesTiles(game).stream()
                .map(tile -> Buttons.blue(
                        player.factionButtonChecker() + "refractureMaahesPlace_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        tile.getTileEmoji(player)))
                .collect(Collectors.toCollection(ArrayList::new));
        buttons.add(Buttons.red(player.factionButtonChecker() + "refractureMaahesDone", "Done"));
        return buttons;
    }

    public static void offerLekythoi(Game game, Player player) {
        if (!RefractureRulesService.canUseLekythoi(game, player)) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + "refractureLekythoiStart",
                        "Use Override Clause",
                        PlanetEmojis.getPlanetEmojiOrNull("lekythoi")),
                Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", you may exhaust _Override Clause_ to immediately score 1 public objective.",
                buttons);
    }

    @ButtonHandler("refractureLekythoiStart")
    static void startLekythoi(ButtonInteractionEvent event, Player player, Game game) {
        if (!RefractureRulesService.canUseLekythoi(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Lekythoi_ can no longer be used.");
            return;
        }
        List<Button> buttons = getLekythoiObjectiveButtons(game, player, null);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You do not currently fulfill a public objective's requirements.");
            return;
        }
        player.exhaustPlanetAbility("lekythoi");
        String token = RefractureRulesService.startLekythoi(game, player);
        buttons = getLekythoiObjectiveButtons(game, player, token);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose the public objective to score with _Override Clause_.",
                buttons);
    }

    @ButtonHandler("refractureLekythoiScore_")
    static void scoreLekythoiObjective(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String value = buttonID.replace("refractureLekythoiScore_", "");
        int separator = value.lastIndexOf('_');
        if (separator < 1 || !RefractureRulesService.hasPendingLekythoi(game, player, value.substring(0, separator))) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That Override Clause choice is no longer available.");
            return;
        }
        int objectiveId;
        try {
            objectiveId = Integer.parseInt(value.substring(separator + 1));
        } catch (NumberFormatException e) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Override Clause choice is invalid.");
            return;
        }
        String objectiveAlias = game.getRevealedPublicObjectives().entrySet().stream()
                .filter(entry -> entry.getValue() == objectiveId)
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (objectiveAlias == null || !canScoreLekythoiObjective(game, player, objectiveAlias)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You no longer fulfill that objective's requirements.");
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        RefractureRulesService.clearPendingLekythoi(game, player);
        ScorePublicObjectiveService.scorePO(event, game, player, objectiveId);
        if (game.getScoredPublicObjectives()
                .getOrDefault(objectiveAlias, List.of())
                .contains(player.getUserID())) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " scored _"
                            + Mapper.getPublicObjective(objectiveAlias).getName()
                            + "_ by exhausting _Override Clause_ on Lekythoi.");
        }
    }

    private static List<Button> getLekythoiObjectiveButtons(Game game, Player player, String token) {
        List<Button> buttons = new ArrayList<>();
        for (var entry : game.getRevealedPublicObjectives().entrySet()) {
            String objectiveAlias = entry.getKey();
            PublicObjectiveModel objective = Mapper.getPublicObjective(objectiveAlias);
            if (objective == null
                    || game.getScoredPublicObjectives()
                            .getOrDefault(objectiveAlias, List.of())
                            .contains(player.getUserID())
                    || !canScoreLekythoiObjective(game, player, objectiveAlias)) {
                continue;
            }
            var emoji = Mapper.getPublicObjectivesStage1().containsKey(objectiveAlias)
                    ? CardEmojis.Public1
                    : CardEmojis.Public2;
            buttons.add(Buttons.blue(
                    player.factionButtonChecker()
                            + "refractureLekythoiScore_"
                            + (token == null ? "" : token + "_")
                            + entry.getValue(),
                    objective.getName(),
                    emoji));
        }
        return buttons;
    }

    private static boolean canScoreLekythoiObjective(Game game, Player player, String objectiveAlias) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objectiveAlias, game);
        return threshold > 0
                && ListPlayerInfoService.getPlayerProgressOnObjective(objectiveAlias, game, player) >= threshold;
    }

    @ButtonHandler("refractureExperimentStart")
    static void startExperiment(ButtonInteractionEvent event, Player player, Game game) {
        if (!RefractureRulesService.canUseExperiment(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Experiment_ can no longer be used.");
            return;
        }
        if (!hasUnrevealedPublicObjective(game)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are no unrevealed public objectives to view.");
            return;
        }
        player.exhaustPlanetAbility("experiment");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        paymentButtons.add(Buttons.DONE_EXHAUSTING_PLANETS_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " exhausted _Experiment_. Spend 3 influence.",
                paymentButtons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose an unrevealed public objective to look at.",
                getExperimentObjectiveButtons(game, player));
    }

    @ButtonHandler("refractureExperimentObjective_")
    static void resolveExperimentObjective(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String value = buttonID.replace("refractureExperimentObjective_", "");
        int stageSeparator = value.lastIndexOf('_');
        if (stageSeparator < 1 || stageSeparator == value.length() - 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That _Experiment_ choice is invalid.");
            return;
        }
        int stage;
        int objectivePosition;
        try {
            stage = Integer.parseInt(value.substring(0, stageSeparator));
            objectivePosition = Integer.parseInt(value.substring(stageSeparator + 1));
        } catch (NumberFormatException e) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That _Experiment_ choice is invalid.");
            return;
        }
        List<String> deck = stage == 1 ? game.getPublicObjectives1Peekable() : game.getPublicObjectives2Peekable();
        if ((stage != 1 && stage != 2)
                || !game.isRefractureMode()
                || !player.getPlanets().contains("experiment")
                || !player.getExhaustedPlanetsAbilities().contains("experiment")
                || objectivePosition < 0
                || objectivePosition >= deck.size()
                || game.getRevealedPublicObjectives().containsKey(deck.get(objectivePosition))) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That public objective is no longer available to view.");
            return;
        }
        PublicObjectiveModel objective = Mapper.getPublicObjective(deck.get(objectivePosition));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player,
                player.getRepresentationUnfogged() + " looked at an unrevealed public objective with _Experiment_:",
                List.of(objective.getRepresentationEmbed(true)));
    }

    private static List<Button> getExperimentObjectiveButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        addExperimentObjectiveButtons(
                buttons, game, game.getPublicObjectives1Peekable(), player, 1, CardEmojis.Public1);
        addExperimentObjectiveButtons(
                buttons, game, game.getPublicObjectives2Peekable(), player, 2, CardEmojis.Public2);
        return buttons;
    }

    private static boolean hasUnrevealedPublicObjective(Game game) {
        return game.getPublicObjectives1Peekable().stream()
                        .anyMatch(
                                objective -> !game.getRevealedPublicObjectives().containsKey(objective))
                || game.getPublicObjectives2Peekable().stream()
                        .anyMatch(
                                objective -> !game.getRevealedPublicObjectives().containsKey(objective));
    }

    private static void addExperimentObjectiveButtons(
            List<Button> buttons, Game game, List<String> deck, Player player, int stage, TI4Emoji emoji) {
        for (int position = 0; position < deck.size(); position++) {
            if (game.getRevealedPublicObjectives().containsKey(deck.get(position))) continue;
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + "refractureExperimentObjective_" + stage + "_" + position,
                    "Stage " + stage + " Objective " + (position + 1),
                    emoji));
        }
    }

    @ButtonHandler("refractureDemeterDoubleCapacity")
    static void doubleDemeterCapacity(ButtonInteractionEvent event, Player player, Game game) {
        if (!RefractureRulesService.canDoubleDemeterCapacity(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Demeter II_ can no longer double your capacity.");
            return;
        }
        RefractureRulesService.doubleDemeterCapacity(game, player);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentationNoPing()
                        + " is doubling the CAPACITY value of all their units for this turn's tactical actions. They must skip the Production step in each such tactical action.");
    }

    @ButtonHandler("refractureCharonAssignHit_")
    static void assignHitToCharon(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String position = buttonID.replace("refractureCharonAssignHit_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || !"spacecombat".equals(game.getStoredValue(player.getFaction() + "latestAssignHits"))
                || !RefractureRulesService.charonCanParticipateInCombat(game, player, tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Charon_ can no longer be assigned this hit.");
            return;
        }
        player.exhaustPlanet("charon");
        List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, "spacecombat");
        MessageHelper.editMessageButtons(event, buttons);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted Charon to assign 1 hit to it.");
    }

    @ButtonHandler("moveCharon_")
    static void moveCharon(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String position = buttonID.replace("moveCharon_", "");
        Tile destination = game.getTileByPosition(position);
        Tile origin = game.getTileFromPlanet("charon");
        if (!RefractureRulesService.canMoveCharonTo(game, player, destination)
                || origin == null
                || origin == destination) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Charon_ can no longer move there.");
            return;
        }
        TokenPlanetService.moveTokenPlanet(game, player, destination, "charon");
        RefractureRulesService.consumeCharonMovement(game, player);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
