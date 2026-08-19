package ti4.service.relic;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class QuantumEntanglerService {
    private static final String CHOOSE_TARGET = "quantumEntanglerTarget_";
    private static final String CHOOSE_FOR_TARGET = "quantumEntanglerGiveTarget_";
    private static final String CHOOSE_FOR_OWNER = "quantumEntanglerGiveOwner_";
    private static final String DRAWN_RELICS = "quantumEntanglerDrawn_";
    private static final String TARGET = "quantumEntanglerTarget_";

    public static boolean offerQuantumEntanglerTargets(ButtonInteractionEvent event, Game game, Player player) {
        if (game.getAllRelics().size() < 3) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " cannot use _Quantum Entangler_ because fewer than 3 relics remain in the deck.");
            return false;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> target != player)
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_TARGET + target.getFaction(),
                        "Select " + target.getFactionEmoji() + " " + target.getFactionNameOrColor()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no other player to select for _Quantum Entangler_.");
            return false;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", select another player for _Quantum Entangler_.",
                buttons);
        return true;
    }

    @ButtonHandler(CHOOSE_TARGET)
    public static void chooseQuantumEntanglerTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(CHOOSE_TARGET.length()));
        if (target == null || target == player || game.getAllRelics().size() < 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> relics = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            relics.add(game.drawRelic());
        }
        game.setStoredValue(DRAWN_RELICS + player.getFaction(), String.join(",", relics));
        game.setStoredValue(TARGET + player.getFaction(), target.getFaction());

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " drew 3 relics with _Quantum Entangler_. Choose 1 to give to "
                        + target.getRepresentationNoPing() + ".",
                getRelicEmbeds(relics),
                getRelicButtons(player, relics, CHOOSE_FOR_TARGET + target.getFaction() + "|"));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_FOR_TARGET)
    public static void chooseRelicForQuantumEntanglerTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CHOOSE_FOR_TARGET.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        int relicIndex = payload.length == 2 ? getRelicIndex(payload[1]) : -1;
        List<String> drawn = getDrawnRelics(game, player);
        if (target == null
                || player == target
                || !target.getFaction().equals(game.getStoredValue(TARGET + player.getFaction()))
                || relicIndex < 0
                || relicIndex >= drawn.size()) {
            clearPendingState(game, player, true);
            ButtonHelper.deleteMessage(event);
            return;
        }

        String relic = normalizeRelicID(drawn.get(relicIndex));
        target.addRelic(relic);
        RelicHelper.resolveRelicEffects(event, game, target, relic);
        drawn.remove(relicIndex);
        game.setStoredValue(DRAWN_RELICS + player.getFaction(), String.join(",", drawn));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " gave _"
                        + Mapper.getRelic(relic).getName() + "_ to " + target.getRepresentationNoPing()
                        + " with _Quantum Entangler_.");
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + ", choose 1 of the remaining relics to give to "
                        + player.getRepresentationNoPing() + " with _Quantum Entangler_.",
                getRelicEmbeds(drawn),
                getRelicButtons(target, drawn, CHOOSE_FOR_OWNER + player.getFaction() + "|"));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_FOR_OWNER)
    public static void chooseRelicForQuantumEntanglerOwner(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CHOOSE_FOR_OWNER.length()).split("\\|", 2);
        Player owner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        int relicIndex = payload.length == 2 ? getRelicIndex(payload[1]) : -1;
        List<String> drawn = owner == null ? List.of() : getDrawnRelics(game, owner);
        if (owner == null
                || !player.getFaction().equals(game.getStoredValue(TARGET + owner.getFaction()))
                || relicIndex < 0
                || relicIndex >= drawn.size()) {
            if (owner != null) {
                clearPendingState(game, owner, true);
            }
            ButtonHelper.deleteMessage(event);
            return;
        }

        String relic = normalizeRelicID(drawn.get(relicIndex));
        owner.addRelic(relic);
        RelicHelper.resolveRelicEffects(event, game, owner, relic);
        drawn.remove(relicIndex);
        String purgedRelic = drawn.isEmpty() ? null : normalizeRelicID(drawn.getFirst());

        String message = owner.getRepresentationNoPing() + " gained _"
                + Mapper.getRelic(relic).getName()
                + "_, and gave " + player.getRepresentationNoPing() + " a relic"
                + (purgedRelic == null
                        ? "."
                        : ". _" + Mapper.getRelic(purgedRelic).getName() + "_ was purged.");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        clearPendingState(game, owner, false);
        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, owner);
    }

    private static List<Button> getRelicButtons(Player player, List<String> relics, String buttonPrefix) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < relics.size(); i++) {
            var relic = Mapper.getRelic(normalizeRelicID(relics.get(i)));
            if (relic != null) {
                buttons.add(
                        Buttons.green(player.factionButtonChecker() + buttonPrefix + i, "Choose " + relic.getName()));
            }
        }
        return buttons;
    }

    private static List<MessageEmbed> getRelicEmbeds(List<String> relics) {
        return relics.stream()
                .map(QuantumEntanglerService::normalizeRelicID)
                .map(Mapper::getRelic)
                .filter(java.util.Objects::nonNull)
                .map(relic -> relic.getRepresentationEmbed(false, true))
                .toList();
    }

    private static String normalizeRelicID(String relicID) {
        return relicID.replaceFirst("extra\\d+$", "");
    }

    private static int getRelicIndex(String indexText) {
        try {
            return Integer.parseInt(indexText);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static List<String> getDrawnRelics(Game game, Player player) {
        String drawn = game.getStoredValue(DRAWN_RELICS + player.getFaction());
        return drawn.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(drawn.split(",")));
    }

    public static void clearPendingQuantumEntanglers(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!game.getStoredValue(DRAWN_RELICS + player.getFaction()).isEmpty()) {
                clearPendingState(game, player, true);
            }
        }
    }

    private static void clearPendingState(Game game, Player player, boolean returnUndistributedRelics) {
        if (returnUndistributedRelics) {
            game.getAllRelics().addAll(getDrawnRelics(game, player));
            game.shuffleRelics();
        }
        game.setStoredValue(DRAWN_RELICS + player.getFaction(), "");
        game.setStoredValue(TARGET + player.getFaction(), "");
    }
}
