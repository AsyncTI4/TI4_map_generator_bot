package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.combat.CancelGroundHitsButtonId;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ToldarTechHandler {
    private static final String VIR_TRAINING = "dstoldr";
    private static final String USE_VIR_TRAINING = "useVirTraining";
    private static final String EXHAUST_VIR_TRAINING = "exhaustVirTraining_";
    private static final String HITS = "virTrainingHits_";
    private static final String ASSIGNMENT = "virTrainingAssignment_";
    private static final String USED = "virTrainingUsed_";

    public static void addVirTrainingButton(List<Button> buttons, Player player) {
        if (player.hasTech(VIR_TRAINING)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + USE_VIR_TRAINING, "Use V.I.R. Training", FactionEmojis.toldar));
        }
    }

    public static void recordCombatRoundHits(
            Game game, Player player, Tile tile, UnitHolder unitHolder, int round, int hits) {
        game.setStoredValue(hitKey(player, tile, unitHolder), round + "|" + hits);
    }

    public static void recordAssignmentMessage(
            Game game,
            Player target,
            Player source,
            Tile tile,
            UnitHolder unitHolder,
            int round,
            int hits,
            Message message) {
        game.setStoredValue(
                assignmentKey(target, tile, unitHolder),
                round + "|" + source.getFaction() + "|" + hits + "|" + message.getId());
    }

    public static void addExhaustVirTrainingButton(
            List<Button> buttons, Player defender, Player attacker, Tile tile, UnitHolder unitHolder, int hits) {
        if (!defender.hasTechReady(VIR_TRAINING) || hits < 1) {
            return;
        }
        buttons.add(Buttons.gray(
                defender.factionButtonChecker() + EXHAUST_VIR_TRAINING + attacker.getFaction() + "|"
                        + tile.getPosition() + "|" + unitHolder.getName() + "|" + hits,
                "Exhaust V.I.R. Training: Cancel 1 Hit",
                FactionEmojis.toldar));
    }

    @ButtonHandler(USE_VIR_TRAINING)
    public static void useVirTraining(ButtonInteractionEvent event, Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (!player.hasTech(VIR_TRAINING)
                || combat == null
                || combat.tilePosition() == null
                || combat.unitHolderName() == null
                || combat.factions().size() != 2
                || !combat.factions().contains(player.getFaction())) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "V.I.R. Training can only be used during a two-player combat.");
            return;
        }
        Tile tile = game.getTileByPosition(combat.tilePosition());
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(combat.unitHolderName());
        Player opponent = combat.factions().stream()
                .filter(faction -> !faction.equals(player.getFaction()))
                .map(game::getPlayerFromColorOrFaction)
                .findFirst()
                .orElse(null);
        HitRecord playerHits = readHitRecord(game, player, tile, unitHolder);
        HitRecord opponentHits = readHitRecord(game, opponent, tile, unitHolder);
        if (tile == null
                || unitHolder == null
                || opponent == null
                || playerHits == null
                || opponentHits == null
                || playerHits.round() != opponentHits.round()
                || playerHits.hits() < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "Wait until both players have rolled this combat round and you have produced at least 1 hit.");
            return;
        }
        String usedKey = usedKey(player, tile, unitHolder, playerHits.round());
        if (!game.getStoredValue(usedKey).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "V.I.R. Training has already been used this combat round.");
            return;
        }
        AssignmentRecord outgoing = readAssignmentRecord(game, opponent, tile, unitHolder);
        AssignmentRecord incoming = readAssignmentRecord(game, player, tile, unitHolder);
        game.setStoredValue(usedKey, "used");
        updateAssignment(
                event,
                game,
                opponent,
                player,
                tile,
                unitHolder,
                outgoing,
                playerHits.round(),
                Math.max(0, playerHits.hits() - 1));
        updateAssignment(
                event,
                game,
                player,
                opponent,
                tile,
                unitHolder,
                incoming,
                opponentHits.round(),
                Math.max(0, opponentHits.hits() - 1));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " used _V.I.R. Training_ to cancel 1 hit their units produced and up to 1 hit their opponent's units produced.");
    }

    @ButtonHandler(EXHAUST_VIR_TRAINING)
    public static void exhaustVirTraining(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(EXHAUST_VIR_TRAINING.length()).split("\\|", 4);
        if (values.length != 4 || !player.hasTechReady(VIR_TRAINING)) {
            return;
        }
        Tile tile = game.getTileByPosition(values[1]);
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(values[2]);
        Player attacker = game.getPlayerFromColorOrFaction(values[0]);
        int hits;
        try {
            hits = Integer.parseInt(values[3]);
        } catch (NumberFormatException e) {
            return;
        }
        if (tile == null || unitHolder == null || attacker == null || hits < 1) {
            return;
        }
        player.exhaustTech(VIR_TRAINING);
        replaceAssignmentMessage(event, game, player, attacker, tile, unitHolder, Math.max(0, hits - 1));
        AssignmentRecord assignment = readAssignmentRecord(game, player, tile, unitHolder);
        if (assignment != null) {
            recordAssignmentMessage(
                    game,
                    player,
                    attacker,
                    tile,
                    unitHolder,
                    assignment.round(),
                    Math.max(0, hits - 1),
                    event.getMessage());
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _V.I.R. Training_ to cancel 1 hit.");
    }

    private static void updateAssignment(
            ButtonInteractionEvent event,
            Game game,
            Player target,
            Player source,
            Tile tile,
            UnitHolder unitHolder,
            AssignmentRecord assignment,
            int round,
            int hits) {
        event.getMessageChannel()
                .getHistory()
                .retrievePast(25)
                .queue(messages -> messages.stream()
                        .filter(message -> isAssignmentMessageFor(message, target))
                        .findFirst()
                        .ifPresentOrElse(
                                message -> {
                                    replaceAssignmentMessage(message, game, target, source, tile, unitHolder, hits);
                                    recordAssignmentMessage(
                                            game, target, source, tile, unitHolder, round, hits, message);
                                },
                                () -> updateStoredAssignment(
                                        event, game, target, source, tile, unitHolder, assignment, hits)));
    }

    private static void updateStoredAssignment(
            ButtonInteractionEvent event,
            Game game,
            Player target,
            Player source,
            Tile tile,
            UnitHolder unitHolder,
            AssignmentRecord assignment,
            int hits) {
        if (!isCurrentAssignment(assignment, source)) {
            return;
        }
        event.getMessageChannel().retrieveMessageById(assignment.messageId()).queue(message -> {
            replaceAssignmentMessage(message, game, target, source, tile, unitHolder, hits);
            recordAssignmentMessage(game, target, source, tile, unitHolder, assignment.round(), hits, message);
        });
    }

    private static void replaceAssignmentMessage(
            ButtonInteractionEvent event,
            Game game,
            Player target,
            Player source,
            Tile tile,
            UnitHolder unitHolder,
            int hits) {
        replaceAssignmentMessage(event.getMessage(), game, target, source, tile, unitHolder, hits);
    }

    private static void replaceAssignmentMessage(
            Message message, Game game, Player target, Player source, Tile tile, UnitHolder unitHolder, int hits) {
        String action = hits == 1 ? "hit" : "hits";
        String messageText =
                target.getRepresentationNoPing() + ", you have " + hits + " combat " + action + " to assign.";
        if (hits < 1) {
            message.editMessage(messageText).setComponents(List.of()).queue();
            return;
        }
        List<Button> buttons = getAssignmentButtons(game, target, source, tile, unitHolder, hits);
        message.editMessage(messageText)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
    }

    private static List<Button> getAssignmentButtons(
            Game game, Player target, Player source, Tile tile, UnitHolder unitHolder, int hits) {
        List<Button> buttons = new ArrayList<>();
        String factionChecker =
                target.isDummy() || target.isNpc() ? target.dummyPlayerSpoof() : target.factionButtonChecker();
        if (unitHolder instanceof Planet) {
            buttons.add(Buttons.green(
                    factionChecker + AutoAssignGroundHitsButtonIds.format(unitHolder.getName(), hits),
                    "Auto-assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat",
                    "Manually Assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    factionChecker + CancelGroundHitsButtonId.of(tile.getPosition(), hits, unitHolder.getName()),
                    "Cancel a Hit"));
        } else {
            buttons.add(Buttons.green(
                    factionChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits,
                    "Auto-assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                    "Manually Assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    factionChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + hits, "Cancel a Hit"));
        }
        addExhaustVirTrainingButton(buttons, target, source, tile, unitHolder, hits);
        return buttons;
    }

    private static HitRecord readHitRecord(Game game, Player player, Tile tile, UnitHolder unitHolder) {
        if (player == null || tile == null || unitHolder == null) {
            return null;
        }
        String[] values = game.getStoredValue(hitKey(player, tile, unitHolder)).split("\\|", 2);
        if (values.length != 2) {
            return null;
        }
        try {
            return new HitRecord(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static AssignmentRecord readAssignmentRecord(Game game, Player target, Tile tile, UnitHolder unitHolder) {
        String[] values =
                game.getStoredValue(assignmentKey(target, tile, unitHolder)).split("\\|", 4);
        if (values.length != 4) {
            return null;
        }
        try {
            return new AssignmentRecord(Integer.parseInt(values[0]), values[1], Integer.parseInt(values[2]), values[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isCurrentAssignment(AssignmentRecord assignment, Player source) {
        return assignment != null && assignment.sourceFaction().equals(source.getFaction());
    }

    private static boolean isAssignmentMessageFor(Message message, Player target) {
        return message.getComponentTree().findAll(Button.class).stream().anyMatch(button -> {
                    String id = button.getCustomId();
                    return id != null
                            && (id.startsWith(target.factionButtonChecker() + "autoAssign")
                                    || id.startsWith(target.dummyPlayerSpoof() + "autoAssign"));
                })
                || message.getContentRaw().contains(target.getRepresentationNoPing())
                        && message.getComponentTree().findAll(Button.class).stream()
                                .anyMatch(button -> {
                                    String label = button.getLabel();
                                    return label != null
                                            && (label.startsWith("Auto-assign") || label.startsWith("Manually Assign"));
                                });
    }

    private static String hitKey(Player player, Tile tile, UnitHolder unitHolder) {
        return HITS + player.getFaction() + "_" + tile.getPosition() + "_" + unitHolder.getName();
    }

    private static String assignmentKey(Player target, Tile tile, UnitHolder unitHolder) {
        return ASSIGNMENT + target.getFaction() + "_" + tile.getPosition() + "_" + unitHolder.getName();
    }

    private static String usedKey(Player player, Tile tile, UnitHolder unitHolder, int round) {
        return USED + player.getFaction() + "_" + tile.getPosition() + "_" + unitHolder.getName() + "_" + round;
    }

    private record HitRecord(int round, int hits) {}

    private record AssignmentRecord(int round, String sourceFaction, int hits, String messageId) {}
}
