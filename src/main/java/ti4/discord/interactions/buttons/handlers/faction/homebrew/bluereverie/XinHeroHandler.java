package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

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
import ti4.helpers.SecretObjectiveHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.strategycard.StrategyCardSecondaryButtonService;

@UtilityClass
public class XinHeroHandler {
    private static final String RESOLVED_TARGETS = "xinHeroResolvedTargets";
    private static final String SELECT_TARGET = "xinHeroSelectTarget_";
    private static final String SELECT_EFFECT = "xinHeroSelectEffect_";
    private static final String SELECT_SECONDARY = "xinHeroSelectSecondary_";
    private static final String COMPLETE_SECONDARY = "xinHeroCompleteSecondary";

    public void startXinHero(Game game, Player player) {
        player.removeStoredValue(RESOLVED_TARGETS);
        offerTargetButtons(game, player);
    }

    @ButtonHandler(SELECT_TARGET)
    public void selectTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_TARGET.length()));
        if (target == null || !getEligiblePlayers(game, player).contains(target) || isResolved(player, target)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        if (getSecondaryStrategyCards(game, target).size() > 0) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT_EFFECT + target.getFaction() + "_secondary",
                    "Resolve a Strategy Card Secondary"));
        }
        if (!XinTechHandler.hasAstromanticCloakSteel(target)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT_EFFECT + target.getFaction() + "_secrets",
                    "Look at Secret Objectives"));
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + SELECT_EFFECT + target.getFaction() + "_skip",
                "Skip " + target.getColor()));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose how to resolve **Tsao** for "
                        + target.getRepresentationNoPing() + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_EFFECT)
    public void selectEffect(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_EFFECT.length()).split("_", 2);
        Player target = values.length == 2 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (target == null || isResolved(player, target)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        switch (values[1]) {
            case "secondary" -> offerSecondaryButtons(event, game, player, target);
            case "secrets" -> {
                if (XinTechHandler.hasAstromanticCloakSteel(target)) {
                    ButtonHelper.deleteMessage(event);
                    return;
                }
                resolveTarget(player, target);
                SecretObjectiveHelper.showAll(target, player, game);
                offerTargetButtons(game, player);
            }
            case "skip" -> {
                resolveTarget(player, target);
                offerTargetButtons(game, player);
            }
            default -> {
                return;
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SECONDARY)
    public void selectSecondary(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_SECONDARY.length()).split("_", 2);
        Player target = values.length == 2 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (target == null || isResolved(player, target)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int strategyCard;
        try {
            strategyCard = Integer.parseInt(values[1]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!getSecondaryStrategyCards(game, target).contains(strategyCard)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        resolveTarget(player, target);
        List<Button> buttons =
                new ArrayList<>(StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, strategyCard));
        buttons.add(Buttons.red(player.factionButtonChecker() + COMPLETE_SECONDARY, "Done Resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " is resolving the secondary ability of **"
                        + game.getSCName(strategyCard) + "** without spending a command token due to **Tsao**.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(COMPLETE_SECONDARY)
    public void completeSecondary(ButtonInteractionEvent event, Game game, Player player) {
        offerTargetButtons(game, player);
        ButtonHelper.deleteMessage(event);
    }

    private void offerSecondaryButtons(ButtonInteractionEvent event, Game game, Player player, Player target) {
        List<Button> buttons = getSecondaryStrategyCards(game, target).stream()
                .map(sc -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_SECONDARY + target.getFaction() + "_" + sc,
                        "Secondary of " + game.getSCName(sc),
                        FactionEmojis.xin))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That player has no strategy card secondary to resolve.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose one of " + target.getRepresentationNoPing()
                        + "'s strategy cards.",
                buttons);
    }

    private void offerTargetButtons(Game game, Player player) {
        List<Button> buttons = getEligiblePlayers(game, player).stream()
                .filter(target -> !isResolved(player, target))
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_TARGET + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.getFactionEmojiOrColor()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "**Tsao** has finished resolving.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a player who controls a planet containing your units.",
                buttons);
    }

    private List<Player> getEligiblePlayers(Game game, Player player) {
        List<Player> eligiblePlayers = new ArrayList<>();
        for (String planet : game.getPlanetsPlayerIsCoexistingOn(player)) {
            Player planetOwner = game.getPlanetOwner(planet);
            if (planetOwner != null && !eligiblePlayers.contains(planetOwner)) {
                eligiblePlayers.add(planetOwner);
            }
        }
        return eligiblePlayers;
    }

    private List<Integer> getSecondaryStrategyCards(Game game, Player player) {
        return player.getSCs().stream()
                .filter(sc -> !StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, sc)
                        .isEmpty())
                .toList();
    }

    private boolean isResolved(Player player, Player target) {
        return player.getStoredList(RESOLVED_TARGETS).contains(target.getFaction());
    }

    private void resolveTarget(Player player, Player target) {
        player.addToStoredList(RESOLVED_TARGETS, target.getFaction());
    }
}
