package ti4.discord.interactions.buttons.handlers.relics.thirtyk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thirtyk.ThirtyKRelicHooks;
import ti4.helpers.thirtyk.ThirtyKRelics;
import ti4.helpers.thirtyk.ThirtyKTransactions;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.ExploreModel;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
class ThirtyKRelicButtonHandler {

    @ButtonHandler("thirtyKPlacebotron_")
    public static void placebotron(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasRelicReady(ThirtyKRelics.PLACEBOTRON)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (buttonID.endsWith("purge")) {
            ThirtyKRelics.purge(game, player, event, ThirtyKRelics.PLACEBOTRON);
            RelicHelper.drawRelicAndNotify(player, event, game);
        } else {
            ThirtyKRelics.exhaust(game, player, event, ThirtyKRelics.PLACEBOTRON);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKRediscoverPlanet_")
    public static void rediscoverPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String choice = buttonID.replace("thirtyKRediscoverPlanet_", "");
        if (choice.startsWith("page")) {
            int page = Integer.parseInt(choice.replace("page", ""));
            MessageHelper.editMessageButtons(
                    event,
                    withCancel(ThirtyKRelics.page(
                            ThirtyKRelics.rediscoveryPlanetButtons(game, player),
                            player.factionButtonChecker() + "thirtyKRediscoverPlanet_",
                            page)));
            return;
        }
        Planet planet = game.getPlanetsInfo().get(choice);
        if (planet == null) return;
        List<Button> buttons = new ArrayList<>();
        for (String card : ThirtyKRelics.rediscoveryOptions(game, planet)) {
            ExploreModel model = Mapper.getExplore(card);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "resolve_explore_" + card + "_" + choice, model.getName()));
            if (buttons.size() == 24) break;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the exploration card from the discard pile to resolve on "
                        + Helper.getPlanetRepresentation(choice, game) + ".",
                withCancel(buttons));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKRansomTarget_")
    public static void ransomTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKRansomTarget_", ""));
        if (target == null) return;
        List<Button> buttons = new ArrayList<>(ButtonHelper.getForcedPNSendButtons(game, player, target));
        buttons.add(Buttons.red(
                target.factionButtonChecker() + "thirtyKRansomDestroy_" + player.getFaction(),
                "Destroy Your Flagship"));
        String holder = FoWHelper.identityOrColorIfCanSeeStats(game, player, target, player.getRepresentationNoPing());
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + holder
                        + " fired _Ransom Cannon_ at your flagship. Give them a promissory note, or destroy your flagship.",
                buttons);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " targeted " + target.getColorIfCanSeeStats(player)
                        + " with _Ransom Cannon_. They choose to give a promissory note or destroy their flagship.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKRansomDestroy_")
    public static void ransomDestroy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player holder = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKRansomDestroy_", ""));
        Tile flagshipTile = game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player) > 0)
                .findFirst()
                .orElse(null);
        if (flagshipTile != null) {
            DestroyUnitService.destroyUnits(event, flagshipTile, game, player.getColor(), "fs", false);
        }
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + " destroyed their flagship rather than pay the _Ransom Cannon_.");
        if (holder != null && holder.hasRelic(ThirtyKRelics.RANSOM_CANNON)) {
            holder.removeRelic(ThirtyKRelics.RANSOM_CANNON);
            holder.removeExhaustedRelic(ThirtyKRelics.RANSOM_CANNON);
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    holder.getRepresentationUnfogged()
                            + ", the target destroyed its flagship, so _Ransom Cannon_ was purged.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKDeadlineTarget_")
    public static void deadlineTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKDeadlineTarget_", ""));
        if (target == null) return;
        if (target.getTacticalCC() < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    target.getColorIfCanSeeStats(player)
                            + " has no command token in their tactic pool, so _Deadline Documentation_ has no effect.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String prefix = target.factionButtonChecker() + "thirtyKDeadlineMove_";
        String actor = FoWHelper.identityOrColorIfCanSeeStats(game, player, target, player.getRepresentationNoPing());
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + actor
                        + " used _Deadline Documentation_ on you: move 1 command token from your tactic pool.",
                List.of(
                        Buttons.blue(prefix + "fleet", "Tactic to Fleet Pool"),
                        Buttons.blue(prefix + "strategy", "Tactic to Strategy Pool")));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Deadline Documentation_ on "
                        + target.getColorIfCanSeeStats(player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKDeadlineMove_")
    public static void deadlineMove(ButtonInteractionEvent event, Player player, String buttonID) {
        if (player.getTacticalCC() < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Your tactic pool is empty.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String before = player.getCCRepresentation();
        player.setTacticalCC(player.getTacticalCC() - 1);
        if (buttonID.endsWith("fleet")) {
            player.setFleetCC(player.getFleetCC() + 1);
        } else {
            player.setStrategicCC(player.getStrategicCC() + 1);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " moved 1 command token for _Deadline Documentation_: " + before
                        + " -> " + player.getCCRepresentation() + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKTechSupportTarget_")
    public static void techSupportTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKTechSupportTarget_", ""));
        if (target == null) return;
        ThirtyKTransactions.allowForThisTurn(game, player, target);
        ThirtyKRelicHooks.rememberTechSupport(game, player, target);
        String actor = FoWHelper.identityOrColorIfCanSeeStats(game, player, target, player.getRepresentationNoPing());
        MessageHelper.sendMessageToChannel(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + actor
                        + " called the _Tech Support Hotline_: you may transact with them this turn, and once a deal is made you may research 1 technology with no prerequisites.");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " may transact with " + target.getColorIfCanSeeStats(player)
                        + " this turn through _Tech Support Hotline_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKSpyTarget_")
    public static void spyTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKSpyTarget_", ""));
        if (target == null) return;
        List<String> cards = new ArrayList<>(target.getActionCards().keySet());
        Collections.shuffle(cards);
        List<String> shown = cards.subList(0, Math.min(2, cards.size()));
        StringBuilder report = new StringBuilder(player.getRepresentationUnfogged())
                .append(", _Spy Reports_ shows you these action cards of ")
                .append(target.getColorIfCanSeeStats(player))
                .append(":");
        List<String> names = new ArrayList<>();
        for (String card : shown) {
            ActionCardModel model = Mapper.getActionCard(card);
            if (model == null) continue;
            report.append("\n").append(model.getRepresentation());
            names.add(model.getName());
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), report.toString());
        String actor = FoWHelper.identityOrColorIfCanSeeStats(game, player, target, player.getRepresentationNoPing());
        MessageHelper.sendMessageToChannel(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + actor + " used _Spy Reports_ and saw: "
                        + String.join(", ", names) + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKSovereignTarget_")
    public static void sovereignTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player eliminated = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKSovereignTarget_", ""));
        if (eliminated == null || ThirtyKRelics.sovereignRefugeesUsed(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(ThirtyKRelics.SOVEREIGN_REFUGEES_USED_KEY + player.getFaction(), eliminated.getFaction());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Sovereign Refugees_ to gain access to the flagship of "
                        + eliminated.getColorIfCanSeeStats(player)
                        + ". Track and use that flagship manually.");
        ButtonHelper.deleteMessage(event);
    }

    static List<Button> withCancel(List<Button> buttons) {
        List<Button> result = new ArrayList<>(buttons);
        result.add(Buttons.red("deleteButtons", "Cancel"));
        return result;
    }
}
