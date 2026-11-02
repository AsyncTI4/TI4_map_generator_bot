package ti4.service.actioncard;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;

@UtilityClass
public class OracleDrawService {

    private static final String STATE_KEY = "oracleDrawState";
    private static final String CARD = "_Oracle_";

    private record DrawState(int total, int remaining, boolean scheming, boolean silent) {

        static DrawState parse(String stored) {
            String[] parts = stored.split("_");
            return new DrawState(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Boolean.parseBoolean(parts[2]),
                    Boolean.parseBoolean(parts[3]));
        }

        String serialize() {
            return total + "_" + remaining + "_" + scheming + "_" + silent;
        }
    }

    public static boolean shouldSplitDraw(Player player, int count) {
        return count > 1 && holdsOracle(player);
    }

    public static void drawOneAtATime(Game game, Player player, int total, boolean scheming, boolean silent) {
        drawNext(game, player, new DrawState(total, total, scheming, silent));
    }

    @ButtonHandler("oracleDrawContinue")
    public static void continueDrawing(Game game, Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        String stored = game.getStoredValue(stateKey(player));
        if (stored.isEmpty()) {
            return;
        }
        drawNext(game, player, DrawState.parse(stored));
    }

    private static void drawNext(Game game, Player player, DrawState state) {
        game.drawActionCard(player.getUserID(), 1);
        DrawState next = new DrawState(state.total(), state.remaining() - 1, state.scheming(), state.silent());
        if (next.remaining() == 0) {
            game.removeStoredValue(stateKey(player));
            ActionCardHelper.finishActionCardDraw(game, player, next.total(), next.scheming(), next.silent());
            return;
        }
        game.setStoredValue(stateKey(player), next.serialize());
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you drew action card " + (next.total() - next.remaining())
                        + " of " + next.total() + ". If you want to play " + CARD
                        + " on this draw, play it from your hand and resolve it now, then continue drawing.",
                List.of(Buttons.green(
                        player.factionButtonChecker() + "oracleDrawContinue",
                        "Continue Drawing (" + next.remaining() + " Left)")));
    }

    private static boolean holdsOracle(Player player) {
        return player.getActionCards().keySet().stream().anyMatch(OracleDrawService::isOracle);
    }

    private static boolean isOracle(String actionCardId) {
        ActionCardModel model = Mapper.getActionCard(actionCardId);
        return model != null && "oracle".equals(model.getAutomationID());
    }

    private static String stateKey(Player player) {
        return STATE_KEY + player.getFaction();
    }
}
