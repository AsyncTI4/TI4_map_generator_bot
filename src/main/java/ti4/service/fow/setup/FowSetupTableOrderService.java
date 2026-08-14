package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.SetOrderService;

/** Step 3 of the FoW setup wizard: table / seat order, either picked manually or resolved from dice rolls. */
final class FowSetupTableOrderService {

    private FowSetupTableOrderService() {}

    static void render(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        sb.append("### Current player order\n");
        int i = 1;
        for (Player player : game.getRealPlayers()) {
            sb.append("> ").append(i++).append(". ").append(player.getUserName());
            if (player.isSpeaker()) sb.append(" (speaker)");
            sb.append('\n');
        }
        sb.append("\n**a. Manual** - pick players one at a time in seat order.\n");
        sb.append("**b. Dice** - configure dice count/sides; a roll button gets posted to each player's ")
                .append("own private channel so nobody sees anyone else's roll; then resolve.\n");

        buttons.add(Buttons.blue("fowSetupOrderManualStart", "Start Manual Order"));
        buttons.add(Buttons.blue("fowSetupDiceConfig~MDL", "Configure Dice"));

        if (!state.getDiceRolls().isEmpty()) {
            sb.append("\n### Rolls so far\n");
            for (Map.Entry<String, Integer> entry : state.getDiceRolls().entrySet()) {
                Player p = game.getPlayer(entry.getKey());
                sb.append("> ")
                        .append(p == null ? entry.getKey() : p.getUserName())
                        .append(": ")
                        .append(entry.getValue())
                        .append('\n');
            }
            buttons.add(Buttons.green("fowSetupOrderResolveAsc", "Resolve: Ascending (lowest = 1st)"));
            buttons.add(Buttons.green("fowSetupOrderResolveMid50", "Resolve: Closest to 50"));
            buttons.add(Buttons.gray("fowSetupOrderResolveManual", "Resolve: Manual"));
        }
    }

    // --- Manual order picking ---

    @ButtonHandler("fowSetupOrderManualStart")
    static void startManualOrder(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.getManualOrderPicks().clear();
        FowSetupWizardService.saveState(game, state);
        postRemainingPickButtons(event, game, state);
    }

    @ButtonHandler("fowSetupOrderResolveManual")
    static void resolveManual(ButtonInteractionEvent event, Game game) {
        startManualOrder(event, game);
    }

    private static void postRemainingPickButtons(ButtonInteractionEvent event, Game game, FowSetupWizardState state) {
        List<Button> playerButtons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (state.getManualOrderPicks().contains(player.getUserID())) continue;
            playerButtons.add(Buttons.gray("fowSetupOrderPick_" + player.getUserID(), player.getUserName()));
        }
        if (playerButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No players left to pick.");
            return;
        }
        String msg = "Pick seat #" + (state.getManualOrderPicks().size() + 1) + ":";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, playerButtons);
    }

    @ButtonHandler("fowSetupOrderPick_")
    static void pickNextInOrder(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String userId = buttonID.replace("fowSetupOrderPick_", "");
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        if (!state.getManualOrderPicks().contains(userId)) {
            state.getManualOrderPicks().add(userId);
        }
        FowSetupWizardService.saveState(game, state);

        if (state.getManualOrderPicks().size() >= game.getRealPlayers().size()) {
            finalizeOrder(event, game, state, state.getManualOrderPicks());
            state.getManualOrderPicks().clear();
            FowSetupWizardService.saveState(game, state);
            FowSetupWizardService.openOrRefresh(game);
        } else {
            postRemainingPickButtons(event, game, state);
        }
    }

    // --- Dice-based order ---

    @ButtonHandler("fowSetupDiceConfig~MDL")
    static void openDiceConfigModal(ButtonInteractionEvent event) {
        TextInput count = TextInput.create("diceCount", TextInputStyle.SHORT)
                .setPlaceholder("e.g. 2")
                .setRequired(true)
                .build();
        TextInput sides = TextInput.create("diceSides", TextInputStyle.SHORT)
                .setPlaceholder("e.g. 6")
                .setRequired(true)
                .build();
        Modal modal = Modal.create("fowSetupDiceConfigResolve", "Configure Dice")
                .addComponents(Label.of("Number of dice", count), Label.of("Sides per die", sides))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("fowSetupDiceConfigResolve")
    static void resolveDiceConfigModal(ModalInteractionEvent event, Game game) {
        int count;
        int sides;
        try {
            count = Integer.parseInt(event.getValue("diceCount").getAsString().trim());
            sides = Integer.parseInt(event.getValue("diceSides").getAsString().trim());
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Dice count and sides must be numbers.");
            return;
        }
        if (count < 1 || sides < 2) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Need at least 1 die with at least 2 sides.");
            return;
        }

        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.setDiceCount(count);
        state.setDiceSides(sides);
        state.getDiceRolls().clear();
        FowSetupWizardService.saveState(game, state);

        // One roll button per player's own channel - nobody sees anyone else's roll. If a player's private
        // channel isn't linked, getCorrectChannel() falls back to the GM room instead of failing silently.
        for (Player player : game.getRealPlayers()) {
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCorrectChannel(),
                    "Roll for table order! Click below to roll " + count + "d" + sides
                            + ". Only you will see your result.",
                    Buttons.green("fowSetupDiceRoll", "Roll for Table Order"));
        }
        FowSetupWizardService.openOrRefresh(game);
    }

    @ButtonHandler("fowSetupDiceRoll")
    static void rollDice(ButtonInteractionEvent event, Game game) {
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        if (state.getDiceCount() == null || state.getDiceSides() == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Dice haven't been configured yet.");
            return;
        }
        Player player = game.getPlayer(event.getUser().getId());
        if (player == null || !player.isRealPlayer()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Only real players in this game can roll.");
            return;
        }
        if (state.getDiceRolls().containsKey(player.getUserID())) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "You already rolled a " + state.getDiceRolls().get(player.getUserID()) + ".");
            return;
        }

        int total = 0;
        for (int i = 0; i < state.getDiceCount(); i++) {
            total += ThreadLocalRandom.current().nextInt(state.getDiceSides()) + 1;
        }
        state.getDiceRolls().put(player.getUserID(), total);
        FowSetupWizardService.saveState(game, state);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You rolled a **" + total + "**.");
    }

    @ButtonHandler("fowSetupOrderResolveAsc")
    static void resolveAscending(ButtonInteractionEvent event, Game game) {
        resolveDiceOrder(event, game, Comparator.comparingInt(Map.Entry::getValue));
    }

    @ButtonHandler("fowSetupOrderResolveMid50")
    static void resolveClosestTo50(ButtonInteractionEvent event, Game game) {
        resolveDiceOrder(event, game, Comparator.comparingInt(e -> Math.abs(e.getValue() - 50)));
    }

    private static void resolveDiceOrder(
            ButtonInteractionEvent event, Game game, Comparator<Map.Entry<String, Integer>> comparator) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);

        List<String> ordered = new ArrayList<>(state.getDiceRolls().entrySet().stream()
                .sorted(comparator)
                .map(Map.Entry::getKey)
                .toList());
        for (Player player : game.getRealPlayers()) {
            if (!ordered.contains(player.getUserID())) {
                ordered.add(player.getUserID());
            }
        }

        finalizeOrder(event, game, state, ordered);
        state.getDiceRolls().clear();
        state.setDiceCount(null);
        state.setDiceSides(null);
        FowSetupWizardService.saveState(game, state);
        FowSetupWizardService.openOrRefresh(game);
    }

    private static void finalizeOrder(
            ButtonInteractionEvent event, Game game, FowSetupWizardState state, List<String> orderedUserIds) {
        List<User> users = new ArrayList<>();
        for (String userId : orderedUserIds) {
            Player player = game.getPlayer(userId);
            if (player != null && player.getUser() != null) {
                users.add(player.getUser());
            }
        }
        SetOrderService.setPlayerOrder(event, game, users);
    }
}
