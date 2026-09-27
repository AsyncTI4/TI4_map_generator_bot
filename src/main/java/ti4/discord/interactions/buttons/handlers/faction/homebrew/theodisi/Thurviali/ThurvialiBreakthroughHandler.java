package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thurviali;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class ThurvialiBreakthroughHandler {
    private static final String NEUROGRAFTING = "thurvialibt";
    private static final String SELECT_NEUROGRAFTING_TARGET = "selectNeurograftingTarget_";
    private static final String SELECT_NEUROGRAFTING_TECH = "selectNeurograftingTech_";
    private static final String READY_NEUROGRAFTING = "readyNeurografting";
    private static final String DECLINE_NEUROGRAFTING_READY = "declineNeurograftingReady";

    public static void offerNeurografting(
            ButtonInteractionEvent event, Game game, Player player, UnitHolder unitHolder) {
        if (!player.hasReadyBreakthrough(NEUROGRAFTING)) {
            return;
        }

        List<Button> buttons = getNeurograftingTargetButtons(game, player, unitHolder);
        if (buttons.isEmpty()) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", your units entered coexistence. Choose the player whose technology you wish to research with _Neurografting_. It must be the player that you enter into coexistence with.",
                buttons);
    }

    @ButtonHandler(SELECT_NEUROGRAFTING_TARGET)
    public static void selectNeurograftingTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_NEUROGRAFTING_TARGET.length()));
        if (target == null || target == player || !player.hasReadyBreakthrough(NEUROGRAFTING)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getNeurograftingTechButtons(game, player, target);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose a technology owned by "
                        + target.getRepresentationNoPing()
                        + " to research with _Neurografting_.",
                buttons);
    }

    @ButtonHandler(SELECT_NEUROGRAFTING_TECH)
    public static void selectNeurograftingTech(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_NEUROGRAFTING_TECH.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        TechnologyModel technology = payload.length == 2 ? Mapper.getTech(payload[1]) : null;

        if (target == null
                || technology == null
                || !player.hasReadyBreakthrough(NEUROGRAFTING)
                || !isEligibleNeurograftingTech(player, target, technology)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        BreakthroughCommandHelper.exhaustBreakthrough(player, NEUROGRAFTING);
        PlayerTechService.getTech(game, player, event, "getTech_" + technology.getAlias() + "__noPay");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted _Neurografting_ to research "
                        + technology.getNameRepresentation()
                        + " from "
                        + target.getRepresentationNoPing()
                        + ".");

        ButtonHelper.deleteMessage(event);
    }

    public static void addNeurograftingReadyButtons(List<Button> buttons, Player player) {
        if (!player.hasUnlockedBreakthrough(NEUROGRAFTING) || !player.isBreakthroughExhausted(NEUROGRAFTING)) {
            return;
        }

        buttons.add(Buttons.green(
                player.factionButtonChecker() + READY_NEUROGRAFTING, "Ready Neurografting", FactionEmojis.thurviali));
    }

    @ButtonHandler(READY_NEUROGRAFTING)
    public static void readyNeurografting(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnlockedBreakthrough(NEUROGRAFTING) || !player.isBreakthroughExhausted(NEUROGRAFTING)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        BreakthroughCommandHelper.readyBreakthrough(player, NEUROGRAFTING);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " readied _Neurografting_ after another player ended coexistence with their units.");

        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(DECLINE_NEUROGRAFTING_READY)
    public static void declineNeurograftingReady(ButtonInteractionEvent event, Game game, Player player) {
        if (player.hasUnlockedBreakthrough(NEUROGRAFTING) && player.isBreakthroughExhausted(NEUROGRAFTING)) {
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCardsInfoThread(),
                    player.getRepresentationNoPing()
                            + ", if another player ended coexistence with your units, you may ready _Neurografting_.",
                    Buttons.green(
                            player.factionButtonChecker() + READY_NEUROGRAFTING,
                            "Ready Neurografting",
                            FactionEmojis.thurviali));
        }

        ButtonHelper.deleteTheOneButton(event);
    }

    private static List<Button> getNeurograftingTargetButtons(Game game, Player player, UnitHolder unitHolder) {
        List<Button> buttons = new ArrayList<>();

        for (Player target : ButtonHelper.getPlayersWithUnitsOnAPlanet(game, unitHolder)) {
            if (target == player
                    || getNeurograftingTechButtons(game, player, target).isEmpty()) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_NEUROGRAFTING_TARGET + target.getFaction(),
                    "Research from " + target.getColor(),
                    target.getFactionEmojiOrColor()));
        }

        return buttons;
    }

    private static List<Button> getNeurograftingTechButtons(Game game, Player player, Player target) {
        List<Button> buttons = new ArrayList<>();

        for (String techId : target.getTechs()) {
            TechnologyModel technology = Mapper.getTech(techId);
            if (!isEligibleNeurograftingTech(player, target, technology)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker()
                            + SELECT_NEUROGRAFTING_TECH
                            + target.getFaction()
                            + "|"
                            + technology.getAlias(),
                    technology.getName(),
                    technology.getSingleTechEmoji()));
        }

        return buttons;
    }

    private static boolean isEligibleNeurograftingTech(Player player, Player target, TechnologyModel technology) {
        return technology != null
                && target.hasTech(technology.getAlias())
                && !player.hasTech(technology.getAlias())
                && ListTechService.isTechResearchable(technology, player);
    }
}
