package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperTechs;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.TechnologyModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.VeiledHeartService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.map.FractureService;
import ti4.service.turn.EndTurnService;
import ti4.service.unit.DestroyUnitService;

public final class ButtonHelperTwilightsFallActionCards {

    @ButtonHandler("resolveEngineer")
    public static void resolveEngineer(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue("engineerACSplice", "take_remove_remove");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " added 2 more cards to the splice. You should be prompted to discard 2 cards from the splice after choosing yours.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveLocust")
    public static void resolveLocust(Game game, Player player, ButtonInteractionEvent event) {
        List<String> tilesSeen = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                for (String tilePos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                    if (tilesSeen.contains(tilePos)) {
                        continue;
                    } else {
                        tilesSeen.add(tilePos);
                    }
                    Tile tile2 = game.getTileByPosition(tilePos);
                    for (UnitHolder uH : tile2.getUnitHolders().values()) {
                        String label;
                        for (Player p2 : game.getRealAndEliminatedAndDummyPlayers()) {
                            if ("space".equals(uH.getName())) {
                                label = "(" + StringUtils.capitalize(p2.getColor()) + ") Space Area of "
                                        + tile2.getRepresentationForButtons();
                            } else {
                                label = "(" + StringUtils.capitalize(p2.getColor()) + ") "
                                        + Helper.getPlanetRepresentation(uH.getName(), game);
                            }
                            if (uH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0) {
                                buttons.add(Buttons.gray(
                                        player.factionButtonChecker() + "locustOn_" + tilePos + "_" + uH.getName() + "_"
                                                + p2.getColor(),
                                        label));
                            }
                        }
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose the infantry to start the _Locust_ swarm.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("locustOn")
    public static void locustOn(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String tileP = buttonID.split("_")[1];
        String uHName = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        Tile oG = game.getTileByPosition(tileP);

        Die d1 = new Die(3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " chose to hit the " + color + " " + UnitEmojis.infantry + " in the "
                        + tileP + " system on " + uHName + ", and rolled " + d1.getResult() + ".");
        if (d1.isSuccess()) {
            UnitKey key = Units.getUnitKey(UnitType.Infantry, color);
            DestroyUnitService.destroyUnit(
                    event, oG, game, key, 1, oG.getUnitHolders().get(uHName), false);
            List<Button> buttons = new ArrayList<>();
            for (String tilePos : FoWHelper.getAdjacentTiles(game, tileP, player, false, true)) {
                Tile tile2 = game.getTileByPosition(tilePos);
                for (UnitHolder uH : tile2.getUnitHolders().values()) {
                    String label;

                    for (Player p2 : game.getRealAndEliminatedAndDummyPlayers()) {
                        if ("space".equals(uH.getName())) {
                            label = "(" + StringUtils.capitalize(p2.getColor()) + ") Space Area of "
                                    + tile2.getRepresentationForButtons();
                        } else {
                            label = "(" + StringUtils.capitalize(p2.getColor()) + ") "
                                    + Helper.getPlanetRepresentation(uH.getName(), game);
                        }
                        if (uH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0) {
                            buttons.add(Buttons.gray(
                                    player.factionButtonChecker() + "locustOn_" + tilePos + "_" + uH.getName() + "_"
                                            + p2.getColor(),
                                    label));
                        }
                    }
                }
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", please choose the next infantry to locust. You must choose a different player from last time, if possible.",
                    buttons);
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveReverseTF")
    public static void resolveReverseTF(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue("reverseSpliceOrder", "True");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentation() + " has reversed the order of the ɘɔilqƨ.");
        if (game.getStoredValue("savedParticipants").split("_").length > 2) {
            ButtonHelperTwilightsFall.reverseSpliceOrder(game);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveManipulateTF")
    public static void resolveManipulateTF(Game game, Player player, ButtonInteractionEvent event) {
        List<String> cards = ButtonHelperTwilightsFall.getSpliceCards(game);
        String type = game.getStoredValue("spliceType");
        List<Button> buttons =
                ButtonHelperTwilightsFall.getSpliceButtons(game, type, cards, player, "manipulateStep1_");
        String msg = player.getRepresentation() + ", please assign players cards with these buttons.";
        if (game.isVeiledHeartMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    player.getRepresentation() + " is choosing which card each participating player will take.");
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("manipulateStep1_")
    public static void manipulateStep1_(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String cardID = buttonID.split("_")[1];

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !game.getStoredValue("savedParticipants").contains(p2.getFaction())) {
                continue;
            }
            buttons.add(Buttons.green(
                    "manipulateStep2_" + cardID + "_" + p2.getFaction(), p2.getUserName(), p2.fogSafeEmoji()));
        }
        MessageHelper.sendMessageToChannel(
                game.isVeiledHeartMode() ? player.getCardsInfoThread() : player.getCorrectChannel(),
                player.getRepresentation() + ", please choose the player you wish to give the card to.",
                buttons);

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("manipulateStep2_")
    public static void manipulateStep2(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String cardID = buttonID.split("_")[1];
        String type = game.getStoredValue("spliceType");
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        ButtonHelperTwilightsFall.triggerYellowUnits(game, p2);

        if (game.getStoredValue("savedSpliceCards").contains(cardID + "_")) {
            game.setStoredValue(
                    "savedSpliceCards", game.getStoredValue("savedSpliceCards").replace(cardID + "_", ""));
        } else {
            if (game.getStoredValue("savedSpliceCards").contains("_" + cardID)) {
                game.setStoredValue(
                        "savedSpliceCards",
                        game.getStoredValue("savedSpliceCards").replace("_" + cardID, ""));
            } else {
                game.setStoredValue(
                        "savedSpliceCards",
                        game.getStoredValue("savedSpliceCards").replace(cardID, ""));
            }
        }

        if (game.isVeiledHeartMode()) {
            VeiledHeartService.doManipulate(type, player, cardID, p2);
        } else {
            if ("ability".equalsIgnoreCase(type)) {
                p2.addTech(cardID);
                MessageHelper.sendMessageToChannelWithEmbed(
                        p2.getCorrectChannel(),
                        p2.getRepresentation() + " has spliced in the _"
                                + Mapper.getTech(cardID).getName() + "_ ability.",
                        Mapper.getTech(cardID).getRepresentationEmbed());
            }
            if ("genome".equalsIgnoreCase(type)) {
                p2.addLeader(cardID);
                MessageHelper.sendMessageToChannelWithEmbed(
                        p2.getCorrectChannel(),
                        p2.getRepresentation() + " has spliced in the "
                                + Mapper.getLeader(cardID).getTFNameIfAble() + " genome.",
                        Mapper.getLeader(cardID).getRepresentationEmbed(false, true, false, false, true));
            }
            if ("units".equalsIgnoreCase(type)) {
                UnitModel unitModel = Mapper.getUnit(cardID);
                String asyncId = unitModel.getAsyncId();
                if (!"fs".equalsIgnoreCase(asyncId) && !"mf".equalsIgnoreCase(asyncId)) {
                    List<UnitModel> unitsToRemove = p2.getUnitsByAsyncID(asyncId).stream()
                            .filter(unit -> unit.getFaction().isEmpty()
                                    || unit.getUpgradesFromUnitId().isEmpty())
                            .toList();
                    for (UnitModel u : unitsToRemove) {
                        p2.removeOwnedUnitByID(u.getId());
                    }
                }
                p2.addOwnedUnitByID(cardID);
                MessageHelper.sendMessageToChannelWithEmbed(
                        p2.getCorrectChannel(),
                        p2.getRepresentation() + " has spliced in the "
                                + Mapper.getUnit(cardID).getName() + " unit upgrade.",
                        Mapper.getUnit(cardID).getRepresentationEmbed());
            }
        }

        List<Player> participants = ButtonHelperTwilightsFall.getParticipantsList(game);
        participants.remove(p2);
        game.removeStoredValue("savedParticipants");
        if (!participants.isEmpty()) {
            for (Player p : participants) {
                if (game.getStoredValue("savedParticipants").isEmpty()) {
                    game.setStoredValue("savedParticipants", p.getFaction());
                } else {
                    game.setStoredValue(
                            "savedParticipants", game.getStoredValue("savedParticipants") + "_" + p.getFaction());
                }
            }
        } else {
            if (game.isVeiledHeartMode()) {
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), game.getPing() + ", the splice is complete.");
            } else {
                List<String> cards = ButtonHelperTwilightsFall.getSpliceCards(game);
                List<MessageEmbed> embeds = ButtonHelperTwilightsFall.getSpliceEmbeds(game, type, cards, null);
                MessageHelper.sendMessageToChannelWithEmbeds(
                        game.getMainGameChannel(),
                        game.getPing() + ", the splice is complete. The remaining splice cards were as follows",
                        embeds);
            }
            if (!game.getStoredValue("endTurnWhenSpliceEnds").isEmpty()) {
                Player p3 = game.getActivePlayer();
                if (game.getStoredValue("endTurnWhenSpliceEnds").contains(p3.getFaction())) {
                    EndTurnService.endTurnAndUpdateMap(event, game, p3);
                }
                game.setStoredValue("endTurnWhenSpliceEnds", "");
            }
            game.removeStoredValue("willParticipateInSplice");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveThieve")
    public static void resolveThieve(Game game, Player player, ButtonInteractionEvent event) {
        // ButtonHelperTwilightsFall.sendPlayerSpliceOptions(game, player);
        List<String> cards = ButtonHelperTwilightsFall.getSpliceCards(game);
        for (String card : cards) {
            ButtonHelperTwilightsFall.selectASpliceCard(game, player, "spoof_" + card, event);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveCreate")
    public static void resolveCreate(Game game, Player player, ButtonInteractionEvent event) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveUnravel")
    public static void resolveUnravel(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getRelics().isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray("unravelStep2_" + p2.getFaction(), p2.getFactionNameOrColor(), p2.fogSafeEmoji()));
        }
        String msg =
                player.getRepresentation() + ", please choose the player who owns the relic you wish to _Unravel_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("unravelStep2")
    public static void unravelStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String relic : p2.getRelics()) {
            if (Mapper.getRelic(relic).isFakeRelic()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    "unravelStep3_" + p2.getFaction() + "_" + relic,
                    Mapper.getRelic(relic).getName()));
        }
        String msg = player.getRepresentation() + ", please choose the relic you wish to _Unravel_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("unravelStep3")
    public static void unravelStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String relic = buttonID.split("_")[2];
        p2.removeRelic(relic);
        RelicHelper.resolveRelicLossEffects(game, p2, relic);
        String msg = player.getRepresentation() + " has chosen for _"
                + Mapper.getRelic(relic).getName() + "_, owned by " + p2.getRepresentation() + ", to be _Unravel_'d.";
        if (p2 == player) {
            if (!FractureService.isFractureInPlay(game)) {
                FractureService.spawnFracture(event, game);
                FractureService.spawnIngressTokens(event, game, player, null);
            }
            TeHelperTechs.initializePlanesplitterStep1(game, player);
        } else {
            Integer poIndex =
                    game.addCustomPO("Unravel " + Mapper.getRelic(relic).getName(), 1);
            game.scorePublicObjective(p2.getUserID(), poIndex);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    p2.getRepresentation() + " has scored a victory point for the relic they lost.");
            Helper.checkEndGame(game, p2);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveCoerce")
    public static void resolveCoerce(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(player)) {
            buttons.add(Buttons.gray("coerceStep2_" + p2.getFaction(), p2.getFactionNameOrColor(), p2.fogSafeEmoji()));
        }
        String msg = player.getRepresentation() + ", please choose the player you wish to _Coerce_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("coerceStep2")
    private static void coerceStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String ability : p2.getTechs()) {
            TechnologyModel tech = Mapper.getTech(ability);
            if (tech.getFaction().isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    p2.factionButtonChecker() + "coerceStep3_" + player.getFaction() + "_" + ability, tech.getName()));
        }
        String msg = p2.getRepresentationUnfogged() + ", please choose the ability you wish to give to "
                + (game.isFowMode() ? player.getColorIfCanSeeStats(p2) : player.getRepresentation()) + ".";
        MessageChannel channel = p2.getCorrectChannel();
        if (game.isVeiledHeartMode()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    p2.getRepresentation() + " is choosing which ability to give to " + player.getRepresentation());
            msg += " (The red buttons are for veiled abilities.)";
            channel = p2.getCardsInfoThread();
            buttons.addAll(VeiledHeartService.getVeiledGiveButtonsForCoerce(p2, player));
        }
        MessageHelper.sendMessageToChannel(channel, msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("coerceStep3")
    public static void coerceStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);

        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String ability1 = buttonID.split("_")[2];

        if (game.isVeiledHeartMode()) {
            VeiledHeartService.doCoerce(player, p2, ability1);
            return;
        }

        TechnologyModel tech1 = Mapper.getTech(ability1);
        player.removeTech(ability1);
        p2.addTech(ability1);
        String msg = player.getRepresentation() + " has lost _" + tech1.getName() + "_ to "
                + (game.isFowMode() ? p2.getColorIfCanSeeStats(player) : p2.getFactionNameOrColor()) + ".";
        String msg2 = p2.getRepresentation() + ", you gained _" + tech1.getName() + "_ from "
                + player.getFactionNameOrColor() + ".";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void resolvePoison(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(player)) {
            buttons.add(
                    Buttons.gray("poisonHeroStep2_" + p2.getFaction(), p2.getFactionNameOrColor(), p2.fogSafeEmoji()));
        }
        String msg = player.getRepresentation() + ", please choose the player you wish to poison.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("poisonHeroStep2")
    public static void poisonHeroStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String ability : p2.getTechs()) {
            TechnologyModel tech = Mapper.getTech(ability);
            if (tech.getFaction().isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray("poisonHeroStep3_" + p2.getFaction() + "_" + ability, tech.getName()));
        }
        String msg = player.getRepresentation() + ", please choose the ability you wish to try to steal.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("poisonHeroStep3")
    public static void poisonHeroStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String ability1 = buttonID.split("_")[2];
        TechnologyModel tech1 = Mapper.getTech(ability1);
        buttons.add(Buttons.gray(
                p2.factionButtonChecker() + "coerceStep3_" + player.getFaction() + "_" + ability1,
                "Give " + tech1.getName()));
        buttons.add(
                Buttons.gray(p2.factionButtonChecker() + "poisonHeroStep4_" + player.getFaction(), "Give 2 Abilities"));
        String msg = p2.getRepresentation()
                + ", you have been hit with _Poison of the Nefishh_, and now much choose to either give them the ability they named, or give them 2 of the abilities of you choice.";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("poisonHeroStep4")
    public static void poisonHeroStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        coerceStep2(game, p2, null, "spoof_" + player.getFaction());
        coerceStep2(game, p2, null, "spoof_" + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveTranspose")
    public static void resolveTranspose(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers(false)) {
            buttons.add(
                    Buttons.gray("transposeStep2_" + p2.getFaction(), p2.getFactionNameOrColor(), p2.fogSafeEmoji()));
        }
        String msg = player.getRepresentation() + ", please choose the player you wish to _Transpose_ with.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("transposeStep2")
    public static void transposeStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String ability : player.getTechs()) {
            TechnologyModel tech = Mapper.getTech(ability);
            if (tech.getFaction().isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray("transposeStep3_" + p2.getFaction() + "_" + ability, tech.getName()));
        }
        String msg = player.getRepresentation() + ", please choose the ability you wish to give to "
                + p2.getRepresentation();
        MessageChannel channel = player.getCorrectChannel();
        if (game.isVeiledHeartMode()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentation() + " is choosing which ability to give to " + p2.getRepresentation());
            msg += " (The red buttons are for veiled abilities.)";
            channel = player.getCardsInfoThread();
            buttons.addAll(VeiledHeartService.getVeiledGiveButtonsForTranspose(player, p2));
        }
        MessageHelper.sendMessageToChannel(channel, msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("transposeStep3")
    public static void transposeStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String ability1 = buttonID.split("_")[2];
        for (String ability : p2.getTechs()) {
            if (p2.hasTech("tf-biosyntheticsynergy")
                    && !"tf-biosyntheticsynergy".equals(ability)
                    && !ability.contains("singularity")) {
                continue;
            }
            TechnologyModel tech = Mapper.getTech(ability);
            if (tech.getFaction().isEmpty()) {
                continue;
            }
            if (p2.getSingularityTechs().contains(ability)) {
                continue;
            }
            buttons.add(
                    Buttons.gray("transposeStep4_" + p2.getFaction() + "_" + ability1 + "_" + ability, tech.getName()));
        }
        if (game.isVeiledHeartMode() && !p2.hasTech("tf-biosyntheticsynergy")) {
            buttons.addAll(VeiledHeartService.getVeiledTakeButtonsForTranspose(player, p2, ability1));
        }
        String msg = player.getRepresentation() + ", please choose the ability you wish to steal.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("transposeStep4")
    public static void transposeStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);

        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String ability1 = buttonID.split("_")[2];
        String ability2 = buttonID.split("_")[3];
        TechnologyModel tech1 = Mapper.getTech(ability1);
        TechnologyModel tech2 = Mapper.getTech(ability2);

        if (game.isVeiledHeartMode()) {
            VeiledHeartService.doTranspose(player, p2, ability1, ability2);
            return;
        }

        player.removeTech(ability1);
        p2.removeTech(ability2);
        player.addTech(ability2);
        p2.addTech(ability1);

        String msg = player.getRepresentationUnfogged() + " choose to exchange _" + tech1.getName() + "_ with _"
                + tech2.getName() + "_ via a _Transpose_ with " + p2.getFactionNameOrColor() + ".";
        String msg2 = p2.getRepresentationUnfogged() + ", you exchanged _" + tech2.getName() + "_ for _"
                + tech1.getName() + "_ via a _Transpose_ with "
                + (game.isFowMode() ? player.getColorIfCanSeeStats(p2) : player.getFactionNameOrColor()) + ".";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        checkForSingularityTransfer(player, p2, ability1);
        checkForSingularityTransfer(p2, player, ability2);
    }

    public static void checkForSingularityTransfer(Player sender, Player recipient, String ability) {
        if (ability.contains("tf-singularity")) {
            List<Button> transferButtons = getTransferSingularityButtons(sender, recipient);
            if (!transferButtons.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        sender.getCorrectChannel(),
                        sender.getRepresentation()
                                + ", since you lost a singularity ability, you may also have to transfer whatever it was copying to "
                                + recipient.getFactionNameOrColor() + ".",
                        transferButtons);
            }
        }
    }

    private static List<Button> getTransferSingularityButtons(Player target, Player recipient) {
        List<Button> buttons = new ArrayList<>();
        for (String ability : target.getTechs()) {
            TechnologyModel tech = Mapper.getTech(ability);
            if (tech.getFaction().isEmpty()) {
                continue;
            }
            if (!target.getSingularityTechs().contains(ability)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    target.factionButtonChecker() + "transferSingularity_" + recipient.getFaction() + "_" + ability,
                    tech.getName(),
                    tech.getSingleTechEmoji()));
        }
        buttons.add(Buttons.red("deleteButtons", "It wasn't copying anything"));
        return buttons;
    }

    @ButtonHandler("transferSingularity")
    public static void transferSingularity(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String ability1 = buttonID.split("_")[2];
        TechnologyModel tech1 = Mapper.getTech(ability1);
        player.removeTech(ability1);
        player.removeSingularityTech(ability1);
        p2.addTech(ability1);
        p2.addSingularityTech(ability1);
        String msg = player.getRepresentationUnfogged() + " lose the copied ability _" + tech1.getName() + "_ to "
                + p2.getFactionNameOrColor() + " when their singularity was transposed.";
        String msg2 = p2.getRepresentationUnfogged() + ", you gained _" + tech1.getName() + "_ from "
                + player.getFactionNameOrColor() + ".";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveScarab")
    public static void resolveScarab(Game game, Player player, ButtonInteractionEvent event) {
        Map<String, Integer> factions = new HashMap<>();
        for (String ability : player.getTechs()) {
            TechnologyModel tech = Mapper.getTech(ability);
            if (tech.getFaction().isEmpty()) {
                continue;
            }
            if (player.getSingularityTechs().contains(ability)) {
                continue;
            }
            String faction = tech.getFaction().get();
            if (faction.contains("keleres")) {
                faction = "keleres";
            }
            factions.merge(faction, 1, Integer::sum);
        }
        for (String leaderID : player.getLeaderIDs()) {
            LeaderModel lead = Mapper.getLeader(leaderID);
            String faction = lead.getFaction();
            if (faction.contains("keleres")) {
                faction = "keleres";
            }
            factions.merge(faction, 1, Integer::sum);
        }
        for (String unit : player.getUnitsOwned()) {
            UnitModel unitM = Mapper.getUnit(unit);
            if (unitM.getFaction().isEmpty()) {
                continue;
            }
            String faction = unitM.getFaction().get();
            if (faction.equalsIgnoreCase(player.getFaction())) {
                continue;
            }
            if (faction.contains("keleres")) {
                faction = "keleres";
            }
            factions.merge(faction, 1, Integer::sum);
        }
        int max = 0;
        String bestFaction = "";
        for (Map.Entry<String, Integer> entry : factions.entrySet()) {
            Integer v = entry.getValue();
            if (v > max) {
                max = v;
                bestFaction = entry.getKey();
            }
        }

        String gainMsg = player.gainTG(max * 2, true);
        ButtonHelperAgents.resolveArtunoCheck(player, max * 2);
        String msg = player.getRepresentation() + " gained " + (max * 2) + " trade goods " + gainMsg + " from having "
                + StringHelper.pluralize(max, "faction symbol") + " from " + bestFaction + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveLawsHero(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            buttons.add(
                    Buttons.gray("lawsHeroStep2_" + p2.getFaction(), p2.getFactionNameOrColor(), p2.fogSafeEmoji()));
        }
        String msg = player.getRepresentation() + ", please choose the player you wish to purge an ability from.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("lawsHeroStep2")
    public static void lawsHeroStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String tech : p2.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if ("wavelength".equalsIgnoreCase(tech) || "antimatter".equalsIgnoreCase(tech)) {
                continue;
            }
            buttons.add(Buttons.gray("lawsHeroStep3_" + p2.getFaction() + "_" + tech, techM.getName()));
        }
        String msg = player.getRepresentation() + ", please choose the ability that you wish to purge.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("lawsHeroStep3")
    public static void lawsHeroStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String agent = buttonID.split("_")[2];
        game.setStoredValue("purgedAbilities", game.getStoredValue("purgedAbilities") + "_" + agent);
        TechnologyModel lead = Mapper.getTech(agent);
        p2.removeTech(agent);
        String leaderRepresentation = lead.getNameRepresentation();
        String msg = player.getRepresentation() + " has chosen to purge " + leaderRepresentation + " from "
                + p2.getFactionNameOrColor() + ".";
        String msg2 = p2.getRepresentation() + ", you lost " + leaderRepresentation + " to _The Laws Unwritten_ by "
                + player.getFactionNameOrColor() + "; it has been purged.";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveGenophage")
    public static void resolveGenophage(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers(false)) {
            buttons.add(
                    Buttons.gray("genophageStep2_" + p2.getFaction(), p2.getFactionNameOrColor(), p2.fogSafeEmoji()));
        }
        String msg = player.getRepresentation() + ", please choose the neighbor you wish to _Genophage_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genophageStep2")
    public static void genophageStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String agent : p2.getLeaderIDs()) {

            if (!agent.contains("agent")) {
                continue;
            }
            LeaderModel lead = Mapper.getLeader(agent);
            buttons.add(Buttons.gray(
                    "genophageStep3_" + p2.getFaction() + "_" + agent,
                    lead.getTFNameIfAble(),
                    FactionEmojis.getFactionIcon(lead.getFaction())));
        }
        if (game.isVeiledHeartMode()) {
            buttons.addAll(VeiledHeartService.getVeiledDiscardButtonsForGenophage(player, p2));
        }
        String msg =
                player.getRepresentation() + ", please choose the genome of your neighbor that you wish to remove.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genophageStep3")
    public static void genophageStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String agent = buttonID.split("_")[2];
        LeaderModel lead = Mapper.getLeader(agent);
        p2.removeLeader(agent);
        String msg = player.getRepresentation() + " has chosen to remove _" + lead.getNameRepresentation() + "_ from "
                + p2.getFactionNameOrColor() + ".";
        String msg2 = p2.getRepresentation() + ", you lost _" + lead.getNameRepresentation() + "_ to a _Genophage_ by "
                + player.getFactionNameOrColor() + ".";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveMagnificence")
    public static void resolveMagnificence(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelperHeroes.getBenediction1stTileOptions(player, game);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose which system you wish to force ships to move from.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveIrradiate")
    public static void resolveIrradiate(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        List<String> units = new ArrayList<>(Arrays.asList(
                "mech",
                "warsun",
                "dreadnought",
                "carrier",
                "fighter",
                "infantry",
                "cruiser",
                "spacedock",
                "destroyer",
                "pds"));
        for (String unit : units) {
            buttons.add(Buttons.gray("irradiateStep2_" + unit, StringUtils.capitalize(unit)));
        }
        String msg = player.getRepresentation() + ", please choose the unit type you wish to search for.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveIgnis")
    public static void resolveIgnis(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        if (player.getTacticalCC() <= 0) {
            String msg = player.getRepresentation()
                    + " does not have enough command tokens in their tactics pool to place a command token.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
            ButtonHelper.deleteMessage(event);
            return;
        }
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.isHomeSystem(game) && FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                buttons.add(Buttons.gray("ignisStep2_" + tile.getPosition(), tile.getRepresentationForButtons()));
            }
        }
        String msg = player.getRepresentation() + ", please choose the tile you wish to place a command token in.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ignisStep2_")
    public static void ignisStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        player.setTacticalCC(player.getTacticalCC() - 1);
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        CommandCounterHelper.addCC(event, player, tile);
        List<Button> buttons = ButtonHelperModifyUnits.getOpposingUnitsToHit(player, game, tile, true);

        String msg = player.getRepresentation() + ", please choose the enemy unit you wish to destroy.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("irradiateStep2")
    public static void irradiateStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<MessageEmbed> embeds = new ArrayList<>();
        List<String> unitSpliceDeck = game.getUnitSpliceDeck(false);
        String unitT = buttonID.split("_")[1];

        String found = "nothing applicable";
        for (String card : unitSpliceDeck) {
            embeds.add(Mapper.getUnit(card).getRepresentationEmbed());
            if (Mapper.getUnit(card).getBaseType().equalsIgnoreCase(unitT)) {
                UnitModel unitModel = Mapper.getUnit(card);
                String asyncId = unitModel.getAsyncId();
                if (!"fs".equalsIgnoreCase(asyncId) && !"mf".equalsIgnoreCase(asyncId)) {
                    List<UnitModel> unitsToRemove = player.getUnitsByAsyncID(asyncId).stream()
                            .filter(unit -> unit.getFaction().isEmpty()
                                    || unit.getUpgradesFromUnitId().isEmpty())
                            .toList();
                    for (UnitModel u : unitsToRemove) {
                        player.removeOwnedUnitByID(u.getId());
                    }
                }
                player.addOwnedUnitByID(card);
                found = Mapper.getUnit(card).getNameRepresentation() + ". It has been automatically gained.";
                break;
            }
        }
        String msg = player.getRepresentation() + " searched through the following cards and found " + found;
        MessageHelper.sendMessageToChannelWithEmbeds(player.getCorrectChannel(), msg, embeds);
        ButtonHelper.deleteMessage(event);
    }

    public static void sendDestroyButtonsForSpecificTileAndSurrounding(Game game, Tile tile) {
        for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), null, false, true)) {
            Tile tile2 = game.getTileByPosition(pos);
            for (Player player : game.getRealPlayers()) {
                if (FoWHelper.playerHasUnitsInSystem(player, tile2)) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.red(
                            player.factionButtonChecker() + "getDamageButtons_" + pos + "_" + "deleteThis_combat",
                            "Destroy Units In " + tile2.getRepresentationForButtons()));
                    String msg = player.getRepresentation() + ", please use this button to destroy units in "
                            + tile2.getRepresentationForButtons() + ".";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
                }
            }
        }
    }

    @ButtonHandler("resolveStarFlare")
    public static void resolveStarFlare(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        String msg = player.getRepresentation() + ", please choose the supernova you wish to have erupt.";
        if (game.isTwilightKart()) {
            for (Tile tile : game.getTileMap().values()) {
                if (tile.getPlanetUnitHolders().isEmpty()
                        && FoWHelper.playerHasActualShipsInSystem(player, tile)
                        && !tile.getTileModel().hasWormhole()
                        && !tile.getPosition().contains("frac")
                        && tile.getSpaceStations().isEmpty()) {
                    buttons.add(
                            Buttons.gray("starFlareTKStep2_" + tile.getPosition(), tile.getRepresentationForButtons()));
                }
            }
        } else {
            for (Tile tile : game.getTileMap().values()) {
                if (tile.isSupernova()) {
                    buttons.add(
                            Buttons.gray("starFlareStep2_" + tile.getPosition(), tile.getRepresentationForButtons()));
                }
            }
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveConverge")
    public static void resolveConverge(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue(player.getFaction() + "graviton", "true");
        String msg = "Hits that " + player.getRepresentation()
                + " produces will auto target non-fighter ships and/or mechs in the auto hit assignment.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveAtomize")
    public static void resolveAtomize(Game game, Player player, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile != null) {
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                for (Player player_ : game.getPlayers().values()) {
                    DestroyUnitService.destroyAllPlayerUnits(event, game, player_, tile, uh, true);
                }
            }
            String msg = player.getRepresentation() + " purged their flagship to destroy all units in "
                    + tile.getRepresentationForButtons() + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteMessage(event);
            player.setUnitCap("fs", 0);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Couldn't find the active system.");
        }
    }

    @ButtonHandler("starFlareStep2_")
    public static void starFlareStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        sendDestroyButtonsForSpecificTileAndSurrounding(game, tile);
        String msg =
                player.getRepresentation() + ", everyone should now destroy 3 units in each tile in and adjacent to "
                        + tile.getRepresentationForButtons() + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("starFlareTKStep2_")
    public static void starFlareTKStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tileOg = game.getTileByPosition(buttonID.split("_")[1]);
        List<String> redTilesToPullFrom = new ArrayList<>(List.of(
                // Source:  https://discord.com/channels/943410040369479690/1009507056606249020/1140518249088434217
                //
                // https://cdn.discordapp.com/attachments/1009507056606249020/1140518248794820628/Starmap_Roll_Helper.xlsx

                "41",
                "42",
                "43",
                "44",
                "45",
                "67",
                "68",
                "79",
                "80",
                "113",
                "114",
                "115",
                "116",
                "117",
                "d117",
                "d118",
                "d119",
                "d120",
                "d121",
                "d122",
                "d123"));

        redTilesToPullFrom.removeAll(
                game.getTileMap().values().stream().map(Tile::getTileID).toList());
        if (!game.isDiscordantStarsMode() && !game.isUnchartedSpaceStuff()) {
            redTilesToPullFrom.removeAll(redTilesToPullFrom.stream()
                    .filter(tileID -> tileID.contains("d"))
                    .toList());
        }
        List<String> tileToPullFromUnshuffled = new ArrayList<>(redTilesToPullFrom);
        Collections.shuffle(redTilesToPullFrom);

        if (redTilesToPullFrom.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Not enough tiles to draw from.");
            return;
        }

        List<MessageEmbed> tileEmbeds = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        String tileID = redTilesToPullFrom.getFirst();
        ids.add(tileID);
        TileModel tile = TileHelper.getTileById(tileID);
        tileEmbeds.add(tile.getRepresentationEmbed(false));

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " drew 1 red back tile from this list:\n> " + tileToPullFromUnshuffled);

        event.getMessageChannel().sendMessageEmbeds(tileEmbeds).queue(Consumers.nop(), BotLogger::catchRestError);
        UnitHolder space = tileOg.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        boolean frontier = space.getTokenList().contains(frontierFilename);
        Tile novaTile = new Tile(tileID, tileOg.getPosition(), space);

        game.removeTile(tileOg.getPosition());
        game.setTile(novaTile);
        if (frontier) {
            novaTile.getSpaceUnitHolder().addToken(frontierFilename);
        }
        String msg = player.getRepresentation() + " has replaced the tile in " + tileOg.getPosition() + " with "
                + novaTile.getRepresentationForButtons();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    // timestop

    // linkship is ralnel FS ability

    // Ral nel flagship

}
