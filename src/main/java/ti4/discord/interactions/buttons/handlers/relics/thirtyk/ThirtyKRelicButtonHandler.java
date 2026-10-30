package ti4.discord.interactions.buttons.handlers.relics.thirtyk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thirtyk.ThirtyKRelicHooks;
import ti4.helpers.thirtyk.ThirtyKRelics;
import ti4.helpers.thirtyk.ThirtyKTransactions;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.model.RelicModel;
import ti4.service.explore.ExploreService;
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
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                ThirtyKRelics.rediscoveryMessage(player),
                () -> ThirtyKRelics.rediscoveryPlanetButtons(game, player),
                ThirtyKRelics.cancel(),
                player.factionButtonChecker() + "thirtyKRediscoverPlanet_")) {
            return;
        }
        String choice = buttonID.replace("thirtyKRediscoverPlanet_", "");
        Planet planet = game.getPlanetsInfo().get(choice);
        if (planet == null) return;
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                rediscoveryCardMessage(game, player, choice),
                rediscoveryCardButtons(game, player, planet),
                ThirtyKRelics.cancel(),
                rediscoveryCardNav(player, choice));
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> rediscoveryCardButtons(Game game, Player player, Planet planet) {
        List<Button> buttons = new ArrayList<>();
        for (String card : ThirtyKRelics.rediscoveryOptions(game, planet)) {
            ExploreModel model = Mapper.getExplore(card);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "thirtyKRediscoverCard_" + card + "_" + planet.getName(),
                    model.getName()));
        }
        return buttons;
    }

    private static String rediscoveryCardMessage(Game game, Player player, String planet) {
        return player.getRepresentationUnfogged()
                + ", choose the exploration card from the discard pile to resolve on "
                + Helper.getPlanetRepresentation(planet, game) + ".";
    }

    private static String rediscoveryCardNav(Player player, String planet) {
        return player.factionButtonChecker() + "thirtyKRediscoverCard_" + planet + "_";
    }

    @ButtonHandler("thirtyKRediscoverCard_")
    public static void rediscoverCard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String cardAndPlanet = buttonID.replace("thirtyKRediscoverCard_", "");
        String pagedPlanet = StringUtils.substringBeforeLast(cardAndPlanet, "_");
        Planet pagedHolder = game.getPlanetsInfo().get(pagedPlanet);
        if (pagedHolder != null
                && NewStuffHelper.turnPage(
                        event,
                        buttonID,
                        rediscoveryCardMessage(game, player, pagedPlanet),
                        () -> rediscoveryCardButtons(game, player, pagedHolder),
                        ThirtyKRelics.cancel(),
                        rediscoveryCardNav(player, pagedPlanet))) {
            return;
        }
        String card = StringUtils.substringBefore(cardAndPlanet, "_");
        Planet planet = game.getPlanetsInfo().get(StringUtils.substringAfter(cardAndPlanet, "_"));
        if (planet == null || !ThirtyKRelics.rediscoveryOptions(game, planet).contains(card)) {
            noLongerValid(event, ThirtyKRelics.REDISCOVERY);
            return;
        }
        if (!ThirtyKRelics.exhaustIfReady(game, player, event, ThirtyKRelics.REDISCOVERY)) return;
        ExploreService.resolveExplore(event, player, "resolve_explore_" + cardAndPlanet, game);
    }

    @ButtonHandler("thirtyKRansomTarget_")
    public static void ransomTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKRansomTarget_", ""));
        if (target == null) return;
        if (!ThirtyKRelics.ransomCannonTargets(game, player).contains(target)) {
            noLongerValid(event, ThirtyKRelics.RANSOM_CANNON);
            return;
        }
        if (!ThirtyKRelics.exhaustIfReady(game, player, event, ThirtyKRelics.RANSOM_CANNON)) return;
        List<Button> buttons = new ArrayList<>(ButtonHelper.getForcedPNSendButtons(game, player, target));
        buttons.add(Buttons.red(
                target.factionButtonChecker() + "thirtyKRansomDestroy_" + player.getFaction(),
                "Destroy Your Flagship"));
        String holder = FoWHelper.identityOrColorIfCanSeeStats(game, player, target, player.getRepresentationNoPing());
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + holder
                        + " has your flagship in the sights of the _Ransom Cannon_. Give them 1 promissory note, or destroy your flagship.",
                buttons);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " has the flagship of "
                        + FoWHelper.identityOrColorIfCanSeeStats(game, target, player, target.getRepresentationNoPing())
                        + " in the sights of the _Ransom Cannon_: they must pay 1 promissory note or lose the ship.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKRansomDestroy_")
    public static void ransomDestroy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player holder = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKRansomDestroy_", ""));
        Tile flagshipTile = game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player) > 0)
                .findFirst()
                .orElse(null);
        if (flagshipTile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationNoPing()
                            + ", the bot found no flagship of yours on the game board, so nothing was destroyed and"
                            + " _Ransom Cannon_ stays. Give 1 promissory note instead, or fix the board first.");
            return;
        }
        DestroyUnitService.destroyUnits(event, flagshipTile, game, player.getColor(), "fs", false);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + " scuttled their flagship rather than pay the ransom; it is destroyed and _Ransom Cannon_ is purged.");
        if (holder != null && holder.hasRelic(ThirtyKRelics.RANSOM_CANNON)) {
            holder.removeRelic(ThirtyKRelics.RANSOM_CANNON);
            holder.removeExhaustedRelic(ThirtyKRelics.RANSOM_CANNON);
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    holder.getRepresentationUnfogged()
                            + ", your target scuttled its flagship rather than pay, and the spent _Ransom Cannon_ is purged.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKDeadlineTarget_")
    public static void deadlineTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKDeadlineTarget_", ""));
        if (target == null) return;
        if (!game.getRealPlayers().contains(target)) {
            noLongerValid(event, ThirtyKRelics.DEADLINE_DOCUMENTATION);
            return;
        }
        if (!ThirtyKRelics.exhaustIfReady(game, player, event, ThirtyKRelics.DEADLINE_DOCUMENTATION)) return;
        if (target.getTacticalCC() < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    FoWHelper.identityOrColorIfCanSeeStats(game, target, player, target.getRepresentationNoPing())
                            + " has no command tokens in their tactic pool to hand over, so the _Deadline Documentation_ has no effect.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String prefix = target.factionButtonChecker() + "thirtyKDeadlineMove_";
        String actor = FoWHelper.identityOrColorIfCanSeeStats(game, player, target, player.getRepresentationNoPing());
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + actor
                        + " served you _Deadline Documentation_. Move 1 command token from your tactic pool.",
                List.of(
                        Buttons.blue(prefix + "fleet", "Tactic to Fleet Pool"),
                        Buttons.blue(prefix + "strategy", "Tactic to Strategy Pool")));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " buried "
                        + FoWHelper.identityOrColorIfCanSeeStats(game, target, player, target.getRepresentationNoPing())
                        + " in _Deadline Documentation_: they must move 1 command token out of their tactic pool.");
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
                player.getRepresentationNoPing()
                        + " met the deadline by moving 1 command token out of their tactic pool for _Deadline Documentation_: "
                        + before + " -> " + player.getCCRepresentation() + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKTechSupportTarget_")
    public static void techSupportTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKTechSupportTarget_", ""));
        if (target == null) return;
        if (!ThirtyKRelics.techSupportTargets(game, player).contains(target)) {
            noLongerValid(event, ThirtyKRelics.TECH_SUPPORT_HOTLINE);
            return;
        }
        if (!ThirtyKRelics.exhaustIfReady(game, player, event, ThirtyKRelics.TECH_SUPPORT_HOTLINE)) return;
        ThirtyKTransactions.allowForThisTurn(game, player, target);
        ThirtyKRelicHooks.rememberTechSupport(game, player, target);
        String actor = FoWHelper.identityOrColorIfCanSeeStats(game, player, target, player.getRepresentationNoPing());
        MessageHelper.sendMessageToChannel(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + actor
                        + " is on the line from the _Tech Support Hotline_: you may transact with them this turn, and once a deal is made you may research 1 technology with no prerequisites.");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " patched a call through to "
                        + FoWHelper.identityOrColorIfCanSeeStats(game, target, player, target.getRepresentationNoPing())
                        + " on the _Tech Support Hotline_: the two may transact this turn.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thirtyKSpyTarget_")
    public static void spyTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("thirtyKSpyTarget_", ""));
        if (target == null) return;
        if (!ThirtyKRelics.spyReportTargets(game, player).contains(target)) {
            noLongerValid(event, ThirtyKRelics.SPY_REPORTS);
            return;
        }
        if (!ThirtyKRelics.exhaustIfReady(game, player, event, ThirtyKRelics.SPY_REPORTS)) return;
        List<String> cards = new ArrayList<>(target.getActionCards().keySet());
        Collections.shuffle(cards, RandomHelper.current());
        List<String> shown = cards.subList(0, Math.min(2, cards.size()));
        String targetName =
                FoWHelper.identityOrColorIfCanSeeStats(game, target, player, target.getRepresentationNoPing());
        String count = spyCount(shown.size());
        StringBuilder report = new StringBuilder(player.getRepresentationUnfogged())
                .append(", your agents return with stolen dispatches. _Spy Reports_ shows you ")
                .append(count)
                .append(" of ")
                .append(targetName)
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
                target.getRepresentationUnfogged() + ", there is a leak in your court: " + actor
                        + " used _Spy Reports_ and saw " + count
                        + (names.isEmpty() ? "." : ": " + String.join(", ", names) + "."));
        FoWHelper.announcePublicOrLocal(
                game,
                event,
                player.getRepresentationNoPing() + " used _Spy Reports_ on " + target.getRepresentationNoPing()
                        + ", who showed them " + count + ".",
                player.getRepresentationNoPing() + " used _Spy Reports_ on " + targetName + ", who showed " + count
                        + "; see your cards info thread.");
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
        ThirtyKRelics.addActionSummary(game, player, ThirtyKRelics.SOVEREIGN_REFUGEES);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " welcomed the refugees of "
                        + FoWHelper.identityOrColorIfCanSeeStats(
                                game, eliminated, player, eliminated.getRepresentationNoPing())
                        + " aboard: _Sovereign Refugees_ grants access to their flagship. Track and use it manually.");
        ButtonHelper.deleteMessage(event);
    }

    private static String spyCount(int shown) {
        String cards = shown + " random action card" + (shown == 1 ? "" : "s");
        return shown < 2 ? cards + " (their whole hand)" : cards;
    }

    private static void noLongerValid(ButtonInteractionEvent event, String relicID) {
        RelicModel relic = Mapper.getRelic(relicID);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "That target is no longer valid for _" + (relic == null ? relicID : relic.getName())
                        + "_, so it was not exhausted.");
        ButtonHelper.deleteMessage(event);
    }

    static List<Button> withCancel(List<Button> buttons) {
        List<Button> result = new ArrayList<>(buttons);
        result.add(Buttons.CANCEL_DELETE_BUTTONS);
        return result;
    }
}
