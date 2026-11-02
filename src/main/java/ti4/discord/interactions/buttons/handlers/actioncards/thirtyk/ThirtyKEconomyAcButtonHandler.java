package ti4.discord.interactions.buttons.handlers.actioncards.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.FoWHelper;
import ti4.helpers.thirtyk.ThirtyKAcState;
import ti4.helpers.thirtyk.ThirtyKCards;
import ti4.helpers.thirtyk.ThirtyKDistance;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.service.fow.PlanetTargetService;

@UtilityClass
public class ThirtyKEconomyAcButtonHandler {

    public static final String COMPOUND = "thkAcCompound_";
    public static final String SIPHON_START = "thkAcSiphonStart";
    public static final String FORGERIES = "thkAcForgeries_";
    public static final String PRICE = "thkAcPrice_";
    public static final String REPRIMAND = "thkAcReprimand_";
    public static final String WAR_PROFIT = "thkAcWarProfit_";
    private static final String WAR_PROFIT_TILE = "thkAcWarProfitTile_";
    public static final String ANTIDIS = "thkAcAntidisStart";
    public static final String PRESERVATION = "thkAcPreservation";
    private static final String SIPHON = "thkAcSiphon_";
    private static final String SIPHON_DONE = "thkAcSiphonDone";
    private static final String FORGERIES_PAY = "thkAcForgeriesPay_";
    private static final String FORGERIES_REFUSE = "thkAcForgeriesRefuse_";
    private static final String FORGERIES_TAKE = "thkAcForgeriesTake_";
    private static final String FORGERIES_COUNT = "thkForgeriesTaken";
    private static final String PRICE_SHOW = "thkAcPriceShow_";
    private static final String PRICE_DECLINE = "thkAcPriceDecline_";
    private static final String REPRIMAND_PAY = "thkAcReprimandPay_";
    private static final String REPRIMAND_LOSE = "thkAcReprimandLose_";
    private static final String ANTIDIS_REPLENISH = "thkAcAntidisReplenish";
    private static final String PRESERVE_GAIN = "thkAcPreserveGain_";
    private static final String PRESERVE_COUNT = "thkPreserveGained";
    private static final String RANDOM = "random";

    public static List<Button> compoundInterestButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        for (int amount = 0; amount <= Math.min(4, player.getTg()); amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + COMPOUND + amount, "Place " + amount + " Trade Goods"));
        }
        return buttons;
    }

    @ButtonHandler(COMPOUND)
    public static void compoundInterest(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int amount = Math.min(Math.min(4, player.getTg()), NumberUtils.toInt(buttonID.substring(COMPOUND.length()), 0));
        player.setTg(player.getTg() - amount);
        ThirtyKAcState.addCompoundInterest(game, player, amount);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", your brokers lock " + amount
                        + " trade goods into _Compound Interest_ (" + player.getTg() + " left); it pays out "
                        + ThirtyKAcState.compoundInterest(game, player) * 2
                        + " trade goods at the start of the next strategy phase.");
    }

    @ButtonHandler(SIPHON_START)
    public static void siphonStart(ButtonInteractionEvent event, Game game, Player player) {
        ThirtyKAcState.clearSiphonTargets(game, player);
        List<Button> buttons = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            if (other == player) continue;
            if (!game.isFowMode() && !ThirtyKDistance.homesMoreThanFiveApart(game, player, other)) continue;
            String label = game.isFowMode()
                    ? other.getFactionNameOrColor()
                    : other.getFactionModel().getShortName();
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + SIPHON + other.getColor() + "_tg",
                    "Trade Good: " + label,
                    other.fogSafeEmoji()));
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + SIPHON + other.getColor() + "_comm",
                    "Commodity: " + label,
                    other.fogSafeEmoji()));
        }
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Siphon Funds");
            return;
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + SIPHON_DONE, "Done"));
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged() + ", take up to 1 commodity or trade good from each of 2 players"
                        + " whose home systems are more than 5 hexes from yours.",
                buttons);
    }

    @ButtonHandler(SIPHON)
    public static void siphon(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, SIPHON);
        boolean tradeGood = "tg".equals(ThirtyKAcTargets.afterTarget(buttonID, SIPHON));
        List<String> taken = ThirtyKAcState.siphonTargets(game, player);
        if (target == null || target == player || taken.contains(target.getFaction()) || taken.size() >= 2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You have already siphoned from that player, or from two players.");
            return;
        }
        if (!ThirtyKDistance.homesMoreThanFiveApart(game, player, target)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        String what = tradeGood ? "trade good" : "commodity";
        if (!takeOne(target, tradeGood)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", those coffers were already empty; nothing was taken and this pick is not used up.");
            return;
        }
        ThirtyKAcState.addSiphonTarget(game, player, target);
        player.gainTG(1, true);
        String actorMessage = player.getRepresentationUnfogged() + ", unmarked transports slip out of "
                + ThirtyKAcTargets.name(game, target, player) + "'s docks with 1 " + what
                + " (_Siphon Funds_); you now have " + player.getTg() + " trade goods.";
        FoWHelper.notifyActorAndAffectedElsePublic(
                game,
                player,
                actorMessage,
                target,
                target.getRepresentationUnfogged() + ", your ledgers come up 1 " + what + " short (_Siphon Funds_).",
                actorMessage);
        if (ThirtyKAcState.siphonTargets(game, player).size() >= 2) {
            ThirtyKAcState.clearSiphonTargets(game, player);
            ButtonHelper.deleteMessage(event);
        }
    }

    private static boolean takeOne(Player target, boolean tradeGood) {
        if (tradeGood && target.getTg() > 0) {
            target.setTg(target.getTg() - 1);
            return true;
        }
        if (!tradeGood && target.getCommodities() > 0) {
            target.setCommodities(target.getCommodities() - 1);
            return true;
        }
        return false;
    }

    @ButtonHandler(SIPHON_DONE)
    public static void siphonDone(ButtonInteractionEvent event, Game game, Player player) {
        ThirtyKAcState.clearSiphonTargets(game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> forgeriesButtons(Game game, Player player) {
        return ThirtyKAcTargets.otherPlayerButtons(
                game, player, FORGERIES, other -> ThirtyKDistance.homesMoreThanFiveApart(game, player, other));
    }

    @ButtonHandler(FORGERIES)
    public static void forgeries(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, FORGERIES);
        if (target == null || target == player || !ThirtyKDistance.homesMoreThanFiveApart(game, player, target)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        if (target.getTg() < 2) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", "
                            + (game.isFowMode()
                                    ? PlanetTargetService.fizzleMessage()
                                    : target.getRepresentationNoPing()
                                            + " cannot spend 2 trade goods, so the forged deeds go nowhere;"
                                            + " _Unverified Forgeries_ has no effect."));
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationUnfogged()
                            + ", forged deeds bearing your seal surfaced, but you cannot spend 2 trade goods, so"
                            + " _Unverified Forgeries_ has no effect.");
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(target.factionButtonChecker() + FORGERIES_PAY + player.getColor(), "Spend 2 Trade Goods"),
                Buttons.red(
                        target.factionButtonChecker() + FORGERIES_REFUSE + player.getColor(),
                        "Refuse (They May Take 2 Fragments)"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", "
                        + FoWHelper.factionEmojiOrAnon(game, player, "a player")
                        + " played _Unverified Forgeries_ on you. Spend 2 trade goods, or they may take up to 2 of your"
                        + " relic fragments.",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", waiting for " + ThirtyKAcTargets.name(game, target, player)
                        + " to answer _Unverified Forgeries_.");
    }

    @ButtonHandler(FORGERIES_PAY)
    public static void forgeriesPay(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player actor = ThirtyKAcTargets.target(game, buttonID, FORGERIES_PAY);
        if (actor == null || player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You cannot spend 2 trade goods.");
            return;
        }
        player.setTg(player.getTg() - 2);
        ButtonHelper.deleteMessage(event);
        FoWHelper.notifyActorAndAffectedElsePublic(
                game,
                player,
                player.getRepresentationUnfogged()
                        + ", you paid 2 trade goods to have the forged deeds burned (_Unverified Forgeries_).",
                actor,
                actor.getRepresentationUnfogged()
                        + ", your target paid 2 trade goods to discredit the forgeries; _Unverified Forgeries_ ends.",
                player.getRepresentationNoPing()
                        + " paid 2 trade goods to have the forged deeds burned (_Unverified Forgeries_).");
    }

    @ButtonHandler(FORGERIES_REFUSE)
    public static void forgeriesRefuse(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player actor = ThirtyKAcTargets.target(game, buttonID, FORGERIES_REFUSE);
        if (actor == null) return;
        ButtonHelper.deleteMessage(event);
        game.setStoredValue(FORGERIES_COUNT + actor.getFaction(), "0");
        List<Button> buttons = new ArrayList<>();
        String prefix = actor.factionButtonChecker() + FORGERIES_TAKE + player.getColor() + "_";
        if (game.isFowMode()) {
            buttons.add(Buttons.green(prefix + RANDOM + "1", "Take A Random Fragment"));
            buttons.add(Buttons.green(prefix + RANDOM + "2", "Take Another Random Fragment"));
        } else {
            for (String fragment : player.getFragments()) {
                ExploreModel model = Mapper.getExplore(fragment);
                buttons.add(Buttons.green(prefix + fragment, "Take " + (model == null ? fragment : model.getName())));
            }
        }
        buttons.add(Buttons.gray(actor.factionButtonChecker() + "deleteButtons", "Done"));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you refused to pay; the forgers now move on your relic fragments"
                        + " (_Unverified Forgeries_).");
        MessageHelper.sendMessageToChannelWithButtons(
                actor.getCorrectChannel(),
                actor.getRepresentationUnfogged()
                        + ", your target refused to pay. Take up to 2 of their relic fragments.",
                buttons);
    }

    @ButtonHandler(FORGERIES_TAKE)
    public static void forgeriesTake(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, FORGERIES_TAKE);
        String choice = ThirtyKAcTargets.afterTarget(buttonID, FORGERIES_TAKE);
        int taken = NumberUtils.toInt(game.getStoredValue(FORGERIES_COUNT + player.getFaction()), 2);
        if (target == null || taken >= 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String fragment = choice.startsWith(RANDOM) ? randomFragment(target) : choice;
        ButtonHelper.deleteTheOneButton(event);
        if (fragment == null || !target.getFragments().contains(fragment)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", there was no such fragment to take.");
            return;
        }
        target.removeFragment(fragment);
        player.addFragment(fragment);
        game.setStoredValue(FORGERIES_COUNT + player.getFaction(), String.valueOf(taken + 1));
        String fragmentName = Mapper.getExplore(fragment) == null
                ? fragment
                : Mapper.getExplore(fragment).getName();
        FoWHelper.notifyActorAndAffectedElsePublic(
                game,
                player,
                player.getRepresentationUnfogged() + ", forged deed in hand, you claim _" + fragmentName
                        + "_ (_Unverified Forgeries_).",
                target,
                target.getRepresentationUnfogged() + ", a forged deed of transfer strips you of _" + fragmentName
                        + "_ (_Unverified Forgeries_).",
                player.getRepresentationNoPing() + " claimed _" + fragmentName + "_ from "
                        + target.getRepresentationNoPing() + " with a forged deed (_Unverified Forgeries_).");
        if (taken + 1 >= 2) ButtonHelper.deleteMessage(event);
    }

    private static String randomFragment(Player target) {
        List<String> fragments = target.getFragments();
        if (fragments.isEmpty()) return null;
        return fragments.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(fragments.size()));
    }

    public static List<Button> priceOfInformationButtons(Game game, Player player) {
        return ThirtyKAcTargets.otherPlayerButtons(game, player, PRICE, other -> isPriceTarget(game, player, other));
    }

    private static boolean isPriceTarget(Game game, Player player, Player other) {
        return ThirtyKDistance.homesMoreThanFiveApart(game, player, other) && !player.isNeighboursWith(other);
    }

    @ButtonHandler(PRICE)
    public static void priceOfInformation(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, PRICE);
        if (target == null || target == player || !isPriceTarget(game, player, target)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        for (String secret : target.getSecretsUnscored().keySet()) {
            SecretObjectiveModel model = Mapper.getSecretObjective(secret);
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + PRICE_SHOW + player.getColor() + "_" + secret,
                    "Show " + (model == null ? secret : model.getName())));
        }
        buttons.add(Buttons.red(target.factionButtonChecker() + PRICE_DECLINE + player.getColor(), "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + FoWHelper.factionEmojiOrAnon(game, player, "a player")
                        + " played _Price of Information_ on you. Show them one unscored secret objective to gain 2"
                        + " commodities, or decline.",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", waiting for " + ThirtyKAcTargets.name(game, target, player)
                        + " to answer _Price of Information_.");
    }

    @ButtonHandler(PRICE_SHOW)
    public static void priceShow(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player actor = ThirtyKAcTargets.target(game, buttonID, PRICE_SHOW);
        String secret = ThirtyKAcTargets.afterTarget(buttonID, PRICE_SHOW);
        SecretObjectiveModel model = Mapper.getSecretObjective(secret);
        if (actor == null || model == null || !player.getSecretsUnscored().containsKey(secret)) return;
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithEmbed(
                actor.getCardsInfoThread(),
                actor.getRepresentationUnfogged() + ", the information you paid for arrives from "
                        + ThirtyKAcTargets.name(game, player, actor) + " (_Price of Information_):",
                model.getRepresentationEmbed());
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", you let _" + model.getName()
                        + "_ slip to your buyer for 2 commodities (_Price of Information_).");
        FoWHelper.notifyActorAndAffectedElsePublic(
                game,
                player,
                player.getRepresentationNoPing() + " showed " + ThirtyKAcTargets.name(game, actor, player)
                        + " 1 unscored secret objective and gained 2 commodities (_Price of Information_).",
                actor,
                ThirtyKAcTargets.name(game, player, actor) + " showed you 1 unscored secret objective; it is in"
                        + " your cards info thread (_Price of Information_).",
                player.getRepresentationNoPing() + " showed " + actor.getRepresentationNoPing()
                        + " 1 unscored secret objective and gained 2 commodities (_Price of Information_).");
        ButtonHelperStats.gainComms(event, game, player, 2, false);
    }

    @ButtonHandler(PRICE_DECLINE)
    public static void priceDecline(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player actor = ThirtyKAcTargets.target(game, buttonID, PRICE_DECLINE);
        ButtonHelper.deleteMessage(event);
        if (actor == null) return;
        MessageHelper.sendMessageToChannel(
                actor.getCorrectChannel(),
                actor.getRepresentationUnfogged()
                        + ", your target kept their secrets and declined _Price of Information_.");
    }

    public static List<Button> reprimandButtons(Game game, Player player) {
        return ThirtyKAcTargets.otherPlayerButtons(game, player, REPRIMAND, null);
    }

    @ButtonHandler(REPRIMAND)
    public static void reprimand(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, REPRIMAND);
        if (target == null || target == player) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = List.of(
                Buttons.blue(target.factionButtonChecker() + REPRIMAND_PAY + player.getColor(), "Pay 3 Influence"),
                Buttons.red(
                        target.factionButtonChecker() + REPRIMAND_LOSE + player.getColor(), "Lose All Trade Goods"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", " + FoWHelper.factionEmojiOrAnon(game, player, "a player")
                        + " played _Public Reprimand_ on you. Pay 3 influence, or lose all of your trade goods.",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", waiting for " + ThirtyKAcTargets.name(game, target, player)
                        + " to answer _Public Reprimand_.");
    }

    @ButtonHandler(REPRIMAND_PAY)
    public static void reprimandPay(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player actor = ThirtyKAcTargets.target(game, buttonID, REPRIMAND_PAY);
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        buttons.add(Buttons.DONE_EXHAUSTING_PLANETS_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", exhaust planets to pay 3 influence for _Public Reprimand_.",
                buttons);
        if (actor != null) {
            MessageHelper.sendMessageToChannel(
                    actor.getCorrectChannel(),
                    actor.getRepresentationUnfogged()
                            + ", your target pays 3 influence to quiet the scandal (_Public Reprimand_).");
        }
    }

    @ButtonHandler(REPRIMAND_LOSE)
    public static void reprimandLose(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player actor = ThirtyKAcTargets.target(game, buttonID, REPRIMAND_LOSE);
        ButtonHelper.deleteMessage(event);
        int lost = player.getTg();
        player.setTg(0);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", the Council's censure costs you all " + lost
                        + " of your trade goods (_Public Reprimand_).");
        if (actor != null) {
            MessageHelper.sendMessageToChannel(
                    actor.getCorrectChannel(),
                    actor.getRepresentationUnfogged()
                            + ", your target would not pay and must lose all of their trade goods"
                            + " (_Public Reprimand_).");
        }
    }

    public static List<Button> warProfiteeringButtons(Game game, Player player) {
        Tile active = activeSystem(game);
        if (active != null
                && ButtonHelper.getPlayersWithShipsInTheSystem(game, active).size() > 1) {
            return warProfiteeringTargets(game, player, active);
        }
        List<Tile> battles = ThirtyKCards.visibleTiles(game, player).stream()
                .filter(tile ->
                        ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).size() > 1)
                .toList();
        if (battles.size() == 1) return warProfiteeringTargets(game, player, battles.getFirst());
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : battles) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + WAR_PROFIT_TILE + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    private static List<Button> warProfiteeringTargets(Game game, Player player, Tile tile) {
        boolean hidden = game.isFowMode()
                && !FoWHelper.getTilePositionsToShow(game, player).contains(tile.getPosition());
        List<Player> combatants =
                hidden ? game.getRealPlayers() : ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        List<Button> buttons = new ArrayList<>();
        for (Player combatant : combatants) {
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + WAR_PROFIT + combatant.getColor() + "_" + tile.getPosition(),
                    "gray",
                    combatant));
        }
        return buttons;
    }

    @ButtonHandler(WAR_PROFIT_TILE)
    public static void warProfiteeringTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(WAR_PROFIT_TILE.length()));
        List<Button> buttons = tile == null ? List.of() : warProfiteeringTargets(game, player, tile);
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "War Profiteering");
            return;
        }
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged()
                        + ", choose the player you back with _War Profiteering_. You may back yourself.",
                buttons);
    }

    private static Tile activeSystem(Game game) {
        String position = game.getActiveSystem();
        return position == null || position.isBlank() ? null : game.getTileByPosition(position);
    }

    @ButtonHandler(WAR_PROFIT)
    public static void warProfiteering(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, WAR_PROFIT);
        String position = ThirtyKAcTargets.afterTarget(buttonID, WAR_PROFIT);
        Tile tile = position.isBlank() ? activeSystem(game) : game.getTileByPosition(position);
        if (target == null || tile == null) {
            ThirtyKAcTargets.noTarget(event, player, "War Profiteering");
            return;
        }
        if (!FoWHelper.playerHasShipsInSystem(target, tile)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ThirtyKAcState.setWarProfiteering(game, player, target, tile.getPosition());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", your financiers quietly bankroll "
                        + ThirtyKAcTargets.name(game, target, player)
                        + " (_War Profiteering_); if they win this space combat, you gain 4 trade goods.");
    }

    @ButtonHandler(ANTIDIS)
    public static void antidisestablishmentarianism(ButtonInteractionEvent event, Game game, Player player) {
        ThirtyKAcState.StructureLoss loss = ThirtyKAcState.structureLoss(game);
        Player loser = loss == null ? null : game.getPlayerFromColorOrFaction(loss.loserFaction());
        if (loser == null) {
            ThirtyKAcTargets.noTarget(event, player, "Antidisestablishmentarianism");
            return;
        }
        if (!ThirtyKDistance.isMoreThanFiveFromHome(game, player, loss.position())) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ThirtyKAcState.clearStructureLost(game);
        ButtonHelper.deleteMessage(event);
        ButtonHelperStats.replenishComms(event, game, player, false);
        if (loser == player) return;
        MessageHelper.sendMessageToChannelWithButtons(
                loser.getCorrectChannel(),
                loser.getRepresentationUnfogged() + ", reconstruction funds arrive after your loss; you may"
                        + " replenish your commodities (_Antidisestablishmentarianism_).",
                List.of(
                        Buttons.green(loser.factionButtonChecker() + ANTIDIS_REPLENISH, "Replenish Commodities"),
                        Buttons.red(loser.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    @ButtonHandler(ANTIDIS_REPLENISH)
    public static void antidisReplenish(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperStats.replenishComms(event, game, player, false);
    }

    @ButtonHandler(PRESERVATION)
    public static void preservationAct(ButtonInteractionEvent event, Game game, Player player) {
        ThirtyKAcState.PurgedFragments purged = ThirtyKAcState.purgedFragments(game);
        Player purger = purged == null ? null : game.getPlayerFromColorOrFaction(purged.purgerFaction());
        if (purger == null
                || purger == player
                || player.isNeighboursWith(purger)
                || purged.fragments().isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Preservation Act");
            return;
        }
        game.setStoredValue(PRESERVE_COUNT + player.getFaction(), "0");
        List<Button> buttons = new ArrayList<>();
        for (String fragment : purged.fragments()) {
            ExploreModel model = Mapper.getExplore(fragment);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PRESERVE_GAIN + fragment,
                    "Gain " + (model == null ? fragment : model.getName())));
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Done"));
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged() + ", gain up to 2 of the relic fragments that were purged.",
                buttons);
    }

    @ButtonHandler(PRESERVE_GAIN)
    public static void preserveGain(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String fragment = buttonID.substring(PRESERVE_GAIN.length());
        int gained = NumberUtils.toInt(game.getStoredValue(PRESERVE_COUNT + player.getFaction()), 2);
        ThirtyKAcState.PurgedFragments purged = ThirtyKAcState.purgedFragments(game);
        ButtonHelper.deleteTheOneButton(event);
        if (gained >= 2 || purged == null || !purged.fragments().contains(fragment) || isHeldByAnyone(game, fragment)) {
            return;
        }
        player.addFragment(fragment);
        game.setNumberOfPurgedFragments(Math.max(0, game.getNumberOfPurgedFragments() - 1));
        ThirtyKAcState.removePurgedFragment(game, fragment);
        game.setStoredValue(PRESERVE_COUNT + player.getFaction(), String.valueOf(gained + 1));
        String name = Mapper.getExplore(fragment) == null
                ? fragment
                : Mapper.getExplore(fragment).getName();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", your archivists rescue _" + name
                        + "_ from destruction (_Preservation Act_).");
        if (gained + 1 >= 2) ButtonHelper.deleteMessage(event);
    }

    private static boolean isHeldByAnyone(Game game, String fragment) {
        for (Map.Entry<String, Player> entry : game.getPlayers().entrySet()) {
            if (entry.getValue().getFragments().contains(fragment)) return true;
        }
        return false;
    }
}
