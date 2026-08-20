package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class RetrofittingLLButtonHandler {
    private static final String RESOLVE = "resolveRetrofitting";
    private static final String SELECT = "retrofittingTech_";
    private static final String STATE = "retrofitting_";

    @ButtonHandler(RESOLVE)
    public static void resolveRetrofitting(ButtonInteractionEvent event, Game game, Player player) {
        if (game.getActiveSystem() == null || game.getActiveSystem().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "_Retrofitting_ must be resolved during a tactical action.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getUnitUpgradeButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible unit upgrade to gain for _Retrofitting_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = player.getRepresentationNoPing()
                + ", choose a unit upgrade to gain until the end of this tactical action.";
        MessageHelper.editMessageWithButtons(
                event, message, NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SELECT, 0));
    }

    @ButtonHandler(SELECT)
    public static void selectRetrofittingTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getUnitUpgradeButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a unit upgrade to gain until the end of this tactical action.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + SELECT, buttonID)) {
            return;
        }

        String techId = buttonID.substring(SELECT.length());
        TechnologyModel tech = Mapper.getTech(techId);
        if (tech == null
                || !tech.isUnitUpgrade()
                || player.hasTech(techId)
                || !ListTechService.getAllTechOfAType(
                                game, TechnologyModel.TechnologyType.UNITUPGRADE.toString(), player, false, false)
                        .contains(tech)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit upgrade is no longer eligible.");
            return;
        }

        game.setStoredValue(STATE + player.getFaction(), game.getActiveSystem() + "|" + techId);
        PlayerTechService.getTech(game, player, event, "getTech_" + techId + "__noPay__comp");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " gained " + tech.getNameRepresentation()
                        + " with _Retrofitting_. It will return to the technology deck at the end of this tactical action.");
        ButtonHelper.deleteMessage(event);
    }

    public static void returnRetrofittedTechs(Game game) {
        for (Player player : game.getRealPlayers()) {
            String key = STATE + player.getFaction();
            String[] state = game.getStoredValue(key).split("\\|", 2);
            if (state.length != 2 || !state[0].equals(game.getActiveSystem())) continue;
            game.removeStoredValue(key);
            if (!player.hasTech(state[1])) continue;
            TechnologyModel tech = Mapper.getTech(state[1]);
            player.removeTech(state[1]);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " returned "
                            + (tech == null ? state[1] : tech.getNameRepresentation())
                            + " to the technology deck after _Retrofitting_.");
        }
    }

    private static List<Button> getUnitUpgradeButtons(Game game, Player player) {
        return new ArrayList<>(
                ListTechService.getAllTechOfAType(
                                game, TechnologyModel.TechnologyType.UNITUPGRADE.toString(), player, false, false)
                        .stream()
                        .map(tech ->
                                Buttons.gray(player.factionButtonChecker() + SELECT + tech.getAlias(), tech.getName()))
                        .toList());
    }
}
