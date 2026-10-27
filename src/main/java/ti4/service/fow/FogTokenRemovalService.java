package ti4.service.fow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.revenant.RevenantBreakthroughHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;

@UtilityClass
public class FogTokenRemovalService {

    private static final String REMOVE = "fogTokenRemove_";
    private static final String RANDOM = "fogTokenRandom_";
    private static final String NEGOTIATE = "fogTokenNegotiate_";
    private static final String NEGOTIATE_RESOLVE = "fogTokenNegotiateResolve_";
    private static final String POSITION = "position";
    private static final String MAHACT = "mahact";
    private static final String FLUX = "flux";

    public static void startMahactAgent(ButtonInteractionEvent event, Game game, Player mahact, int sc) {
        exhaustMahactAgent(game, mahact);
        offer(event, game, mahact, MAHACT, String.valueOf(sc));
    }

    public static void startFlux(ButtonInteractionEvent event, Game game, Player remover, Player owner) {
        offer(event, game, remover, FLUX, owner.getFaction());
    }

    private static void offer(ButtonInteractionEvent event, Game game, Player remover, String kind, String target) {
        Player owner = owner(game, kind, target);
        List<Tile> ownerTokens = owner == null ? List.of() : ButtonHelper.getTilesWithYourCC(owner, game, event);
        if (ownerTokens.isEmpty()) {
            fizzle(game, remover, kind, target, "there is no command token of that player on the board");
            return;
        }
        String checker = remover.factionButtonChecker();
        String ref = kind + "~" + target;
        Set<String> visible = FoWHelper.getTilePositionsToShow(game, remover);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ownerTokens) {
            if (visible.contains(tile.getPosition())) {
                buttons.add(Buttons.green(
                        checker + REMOVE + ref + "~" + tile.getPosition(),
                        "Remove from " + tile.getRepresentationForButtons(game, remover)));
            }
        }
        buttons.add(Buttons.blue(checker + RANDOM + ref, "Random token"));
        buttons.add(Buttons.gray(checker + NEGOTIATE + ref + "~MDL", "Negotiate (type a position)"));
        game.setStoredValue(offerKey(ref, remover), "open");
        MessageHelper.sendMessageToChannelWithButtons(
                remover.getCorrectChannel(),
                remover.getRepresentationUnfogged() + ", choose which command token of "
                        + ownerLabel(game, kind, target)
                        + " to remove: one you can see, a random one, or **Negotiate** and type a position blind."
                        + " You may agree on it through official communication first (`/fow whisper`), but they are"
                        + " free to lie. If there is no token at the position you type, " + effectName(kind)
                        + " fizzles.",
                buttons);
    }

    @ButtonHandler(REMOVE)
    public static void removeVisible(ButtonInteractionEvent event, Game game, Player remover, String buttonID) {
        String[] parts = buttonID.replace(REMOVE, "").split("~");
        resolve(event, game, remover, parts[0], parts[1], game.getTileByPosition(parts[2]), true);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RANDOM)
    public static void removeRandom(ButtonInteractionEvent event, Game game, Player remover, String buttonID) {
        String[] parts = buttonID.replace(RANDOM, "").split("~");
        Player owner = owner(game, parts[0], parts[1]);
        List<Tile> ownerTokens = owner == null ? List.of() : ButtonHelper.getTilesWithYourCC(owner, game, event);
        Tile tile = ownerTokens.isEmpty()
                ? null
                : ownerTokens.get(ThreadLocalRandom.current().nextInt(ownerTokens.size()));
        resolve(event, game, remover, parts[0], parts[1], tile, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = NEGOTIATE, save = false)
    public static void openNegotiation(ButtonInteractionEvent event, String buttonID) {
        String ref = buttonID.replace(NEGOTIATE, "").replace("~MDL", "");
        TextInput position = TextInput.create(POSITION, TextInputStyle.SHORT)
                .setPlaceholder("e.g. 305")
                .setRequiredRange(2, 6)
                .build();
        Modal modal = Modal.create(NEGOTIATE_RESOLVE + ref, "Remove command token at")
                .addComponents(Label.of("System position", position))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(NEGOTIATE_RESOLVE)
    public static void resolveNegotiation(ModalInteractionEvent event, Game game, Player remover) {
        String[] parts = event.getModalId().replace(NEGOTIATE_RESOLVE, "").split("~");
        String position = event.getValue(POSITION).getAsString().trim();
        resolve(event, game, remover, parts[0], parts[1], game.getTileByPosition(position), true);
        if (event.getMessage() != null) {
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static void resolve(
            GenericInteractionCreateEvent event,
            Game game,
            Player remover,
            String kind,
            String target,
            Tile tile,
            boolean nameTileToRemover) {
        String offerKey = offerKey(kind + "~" + target, remover);
        if (game.getStoredValue(offerKey).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    remover.getCorrectChannel(),
                    remover.getRepresentationUnfogged() + ", that choice was already resolved.");
            return;
        }
        game.removeStoredValue(offerKey);
        Player owner = owner(game, kind, target);
        if (owner == null || tile == null || !CommandCounterHelper.hasCC(owner, tile)) {
            fizzle(game, remover, kind, target, "there is no command token of that player there");
            return;
        }
        RemoveCommandCounterService.fromTile(owner.getColor(), tile, game);
        ButtonHelper.resolveAfterCommandTokenRemoved(game, owner, tile);
        String where = nameTileToRemover
                        || FoWHelper.getTilePositionsToShow(game, remover).contains(tile.getPosition())
                ? tile.getRepresentationForButtons(game, remover)
                : "a system you can't see";
        if (MAHACT.equals(kind)) {
            completeMahactFollow(event, game, remover, Integer.parseInt(target), where);
        } else {
            MessageHelper.sendMessageToChannel(
                    remover.getCorrectChannel(),
                    remover.getRepresentationUnfogged() + ", you removed a command token from " + where + ".");
        }
        MessageHelper.sendMessageToChannel(
                owner.getCorrectChannel(),
                owner.getRepresentationUnfogged() + ", this is a notice that someone removed your command token from "
                        + tile.getRepresentationForButtons(game, owner) + ".");
    }

    private static void completeMahactFollow(
            GenericInteractionCreateEvent event, Game game, Player mahact, int sc, String where) {
        if (!mahact.getFollowedSCs().contains(sc)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(mahact, sc, game, event);
        }
        mahact.addFollowedSC(sc, event);
        ButtonHelperSCs.reactToStrategyCardMessage(
                game,
                mahact,
                sc,
                "removed a command token from " + where + " with Jae Mir Kan and followed **"
                        + Helper.getSCName(sc, game) + "**.");
    }

    private static void fizzle(Game game, Player remover, String kind, String target, String reason) {
        String message = remover.getRepresentationUnfogged() + ", " + effectName(kind) + " fizzles: " + reason + ".";
        if (MAHACT.equals(kind)) {
            message += " The agent stays exhausted and you have **not** followed **"
                    + Helper.getSCName(Integer.parseInt(target), game) + "**.";
            if (remover.getStrategicCC() > 0) {
                message += " You can still follow normally by spending a strategy token.";
            }
        }
        MessageHelper.sendMessageToChannel(remover.getCorrectChannel(), message);
    }

    private static String offerKey(String ref, Player remover) {
        return "fogTokenOffer_" + ref + "_" + remover.getFaction();
    }

    private static Player owner(Game game, String kind, String target) {
        return MAHACT.equals(kind)
                ? game.getPlayerFromSC(Integer.parseInt(target))
                : game.getPlayerFromColorOrFaction(target);
    }

    private static String ownerLabel(Game game, String kind, String target) {
        if (MAHACT.equals(kind)) {
            return "the **" + Helper.getSCName(Integer.parseInt(target), game) + "** player";
        }
        Player owner = game.getPlayerFromColorOrFaction(target);
        return owner == null ? "that player" : owner.getFactionEmojiOrColor();
    }

    private static String effectName(String kind) {
        return MAHACT.equals(kind) ? "Jae Mir Kan" : "Flux";
    }

    private static void exhaustMahactAgent(Game game, Player mahact) {
        Leader agent = mahact.unsafeGetLeader("mahactagent");
        if (agent == null) return;
        agent.setExhausted(true);
        RevenantBreakthroughHandler.exhaustRevenantRisingForAttachedAgent(game, mahact, agent);
    }
}
