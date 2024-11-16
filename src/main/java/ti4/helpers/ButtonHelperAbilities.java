package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.cardspn.PlayPN;
import ti4.commands.combat.StartCombat;
import ti4.commands.custom.PeekAtStage1;
import ti4.commands.custom.PeekAtStage2;
import ti4.commands.ds.TrapReveal;
import ti4.commands.ds.TrapToken;
import ti4.commands.explore.ExplorePlanet;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.player.ClearDebt;
import ti4.commands.player.TurnStart;
import ti4.commands.special.SleeperToken;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

public class ButtonHelperAbilities {

    public static void autoneticMemoryStep1(Game game, Player player, int count) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("autoneticMemoryStep2_" + count, "Use Autonetic Memory"));
        buttons.add(Buttons.red("autoneticMemoryDecline_" + count, "Decline"));
        String msg = player.getRepresentationUnfogged()
            + " you have the ability to draw 1 less action card and utilize your autonetic memory ability. Please use or decline to use.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("startFacsimile_")
    public static void startFacsimile(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        String msg = player.getRepresentation() + " choose the opponent ship you wish to build using " + Emojis.influence + " influence";
        if (player.getPromissoryNotes().containsKey("dspnmort") && !player.getPromissoryNotesOwned().contains("dspnmort")) {
            PlayPN.resolvePNPlay("dspnmort", player, game, event);
        }
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if ((player.unitBelongsToPlayer(unitEntry.getKey())))
                continue;
            UnitKey unitKey = unitEntry.getKey();
            String unitName = unitKey.unitName();
            int totalUnits = unitEntry.getValue();
            if (totalUnits > 0) {
                String buttonID2 = "facsimileStep2_" + unitName;
                String buttonText = unitKey.unitName();
                Button validTile2 = Buttons.red(buttonID2, buttonText, unitKey.unitEmoji());
                buttons.add(validTile2);
            }
        }
        if (event.getMessageChannel().getName().contains("cards-info")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("facsimileStep2_")
    public static void resolveFacsimileStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String unit = buttonID.split("_")[1];
        String unitEmoji = Emojis.getEmojiFromDiscord(AliasHandler.resolveUnit(unit));
        String msg = player.getFactionEmoji() + " chose to produce a " + unitEmoji + unit
            + " with " + Emojis.mortheus + "**Facsimile** or " + Emojis.mortheus + Emojis.PN + "**Secrets of the  Weave** and will now spend influence to build it.";
        event.getMessage().delete().queue();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        new AddUnits().unitParsing(event, player.getColor(), tile, unit, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        String message2 = player.getRepresentation() + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
    }

    @ButtonHandler("startRallyToTheCause")
    public static void startRallyToTheCause(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Choose the tile to produce up to 2 ships in", getTilesToRallyToTheCause(game, player));
    }

    @ButtonHandler("rallyToTheCauseStep2_")
    public static void rallyToTheCauseStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        String type = "sling";
        buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to produce the first of potentially 2 ships. "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        String message2 = player.getRepresentation() + " Use the buttons to produce the second of potentially 2 ships. "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getTilesToRallyToTheCause(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game) || tile.isHomeSystem()
                || ButtonHelper.isTileLegendary(tile, game) || tile.isMecatol()) {
                continue;
            }
            buttons.add(Buttons.green("rallyToTheCauseStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));

        }
        return buttons;
    }

    @ButtonHandler("mercenariesStep1_")
    public static void mercenariesStep1(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos2);
        List<Button> buttons = new ArrayList<>();
        for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
            Tile tile2 = game.getTileByPosition(pos);
            UnitHolder unitHolder = tile2.getUnitHolders().get(Constants.SPACE);
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                buttons.add(Buttons.green("mercenariesStep2_" + pos2 + "_" + pos,
                    tile2.getRepresentationForButtons(game, player)));
            }
        }
        String msg = player.getRepresentation() + " choose the tile you wish to pull fighters from";
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("mercenariesStep2_")
    public static void mercenariesStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Tile tile2 = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("mercenariesStep3_" + pos2 + "_" + pos + "_1", "1 fighter"));
        if (tile2.getUnitHolders().get("space").getUnitCount(UnitType.Fighter, player.getColor()) > 1) {
            buttons.add(Buttons.green("mercenariesStep3_" + pos2 + "_" + pos + "_2", "2 fighters"));
        }
        String msg = player.getRepresentation() + " choose whether to pull 1 or 2 fighters";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("mercenariesStep3_")
    public static void mercenariesStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos2);
        String pos = buttonID.split("_")[2];
        String fighters = buttonID.split("_")[3];
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                String id = "mercenariesStep4_" + pos2 + "_" + pos + "_" + fighters + "_" + p2.getFaction();
                if (game.isFowMode()) {
                    buttons.add(Buttons.green(id, StringUtils.capitalize(p2.getColor())));
                } else {
                    buttons.add(Buttons.green(id, p2.getFactionModel().getFactionName()));
                }
            }
        }
        String msg = player.getRepresentation() + " choose which player to give the fighters too";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("mercenariesStep4_")
    public static void mercenariesStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos2);
        String pos = buttonID.split("_")[2];
        Tile tile2 = game.getTileByPosition(pos);
        String fighters = buttonID.split("_")[3];
        String faction = buttonID.split("_")[4];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        new RemoveUnits().unitParsing(event, player.getColor(), tile2, fighters + " fighters", game);
        new AddUnits().unitParsing(event, p2.getColor(), tile, fighters + " fighters", game);
        String msg = player.getRepresentation() + " used the mercenaries ability and transferred " + fighters
            + " fighter" + (fighters.equals("1") ? "" : "s") + " from " + tile2.getRepresentationForButtons(game, player) + " to "
            + tile.getRepresentationForButtons(game, player) + " and gave them to "
            + p2.getFactionEmojiOrColor();
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    @ButtonHandler("bindingDebtsRes_")
    public static void bindingDebtRes(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        Player vaden = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);

        vaden.setTg(vaden.getTg() + 1);
        pillageCheck(vaden, game);
        player.setTg(player.getTg() - 1);
        int amount = Math.min(2, vaden.getDebtTokenCount(player.getColor()));
        ClearDebt.clearDebt(vaden, player, amount);
        String msg = player.getFactionEmojiOrColor() + " paid 1TG to "
            + vaden.getFactionEmojiOrColor()
            + "to get 2 debt tokens cleared via the binding debts ability";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(vaden.getCorrectChannel(), msg);
        }
    }

    @ButtonHandler("autoneticMemoryDecline_")
    public static void autoneticMemoryDecline(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        event.getMessage().delete().queue();
        int count = Integer.parseInt(buttonID.split("_")[1]);
        game.drawActionCard(player.getUserID(), count);
        ActionCardHelper.sendActionCardInfo(game, player, event);
        ButtonHelper.checkACLimit(game, event, player);
    }

    public static List<String> getTrapNames() {
        List<String> trapNames = new ArrayList<>();
        trapNames.add("Minefields");
        trapNames.add("Interference Grid");
        trapNames.add("Feint");
        trapNames.add("Gravitic Inhibitor");
        trapNames.add("Account Siphon");
        trapNames.add("Saboteurs");
        return trapNames;
    }

    @ButtonHandler("resolveGrace_")
    public static void resolveGrace(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String msg = player.getFactionEmoji() + " is resolving the Grace ability";
        int scPlayed = Integer.parseInt(buttonID.split("_")[1]);
        if (!player.hasAbility("grace")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "To " + player.getFactionEmoji() + ": This button ain't for you ");
            return;
        }
        player.addExhaustedAbility("grace");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged()
                + " use buttons to resolve Grace, reminder you have to spend a strat CC if applicable, and that you may only do one of these.",
            getGraceButtons(game, player, scPlayed));
    }

    public static List<Button> getGraceButtons(Game game, Player edyn, int scPlayed) {
        List<Button> scButtons = new ArrayList<>();
        scButtons.add(Buttons.gray("spendAStratCC", "Spend a Strategy CC"));
        if (scPlayed > 1 && (game.getScPlayed().get(1) == null || !game.getScPlayed().get(1))) {
            scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain CCs"));
            // scButtons.add(Buttons.red("leadershipExhaust", "Exhaust Planets"));
        }
        if (scPlayed > 2 && (game.getScPlayed().get(2) == null || !game.getScPlayed().get(2))) {
            scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
        }
        if (scPlayed > 3 && (game.getScPlayed().get(3) == null || !game.getScPlayed().get(3))) {
            scButtons.add(Buttons.gray("sc_ac_draw", "Draw 2 Action Cards", Emojis.ActionCard));
        }
        if (scPlayed > 4 && (game.getScPlayed().get(4) == null || !game.getScPlayed().get(4))) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 space dock", Emojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", Emojis.pds));
        }
        if (scPlayed > 5 && (game.getScPlayed().get(5) == null || !game.getScPlayed().get(5))) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", Emojis.comm));
        }
        if (scPlayed > 6 && (game.getScPlayed().get(6) == null || !game.getScPlayed().get(6))) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (scPlayed > 7 && (game.getScPlayed().get(7) == null || !game.getScPlayed().get(7))) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (scPlayed > 8 && (game.getScPlayed().get(8) == null || !game.getScPlayed().get(8))) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", Emojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));

        return scButtons;
    }

    @ButtonHandler("setTrapStep1")
    public static void setTrapStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("setTrapStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("setTrapStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged() + " tell the bot whose planet you want to put a trap on", buttons);
    }

    @ButtonHandler("setTrapStep2_")
    public static void setTrapStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Buttons.gray("setTrapStep3_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " select the planet you want to put a trap on", buttons);
    }

    @ButtonHandler("setTrapStep3_")
    public static void setTrapStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : getUnusedTraps(game, player)) {
            availableTraps.add(Buttons.green("setTrapStep4_" + planet + "_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentationUnfogged() + " choose the trap you want to set on the planet "
            + Helper.getPlanetRepresentation(planet, game);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    @ButtonHandler("removeTrapStep1")
    public static void removeTrapStep1(Game game, Player player) {
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : player.getTrapCardsPlanets().keySet()) {
            availableTrap = translateNameIntoTrapIDOrReverse(availableTrap);
            availableTraps.add(Buttons.green("removeTrapStep2_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentationUnfogged() + " choose the trap you want to remove";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    @ButtonHandler("revealTrapStep1")
    public static void revealTrapStep1(Game game, Player player) {
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : player.getTrapCardsPlanets().keySet()) {
            availableTrap = translateNameIntoTrapIDOrReverse(availableTrap);
            availableTraps.add(Buttons.green("revealTrapStep2_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentationUnfogged() + " choose the trap you want to reveal";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    @ButtonHandler("revealTrapStep2_")
    public static void revealTrapStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String trap = buttonID.split("_")[1];
        trap = translateNameIntoTrapIDOrReverse(trap);
        String planet = player.getTrapCardsPlanets().get(trap);
        TrapReveal.revealTrapForPlanet(event, game, planet, trap, player, true);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("removeTrapStep2_")
    public static void removeTrapStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String trap = buttonID.split("_")[1];
        trap = translateNameIntoTrapIDOrReverse(trap);
        String planet = player.getTrapCardsPlanets().get(trap);
        TrapReveal.revealTrapForPlanet(event, game, planet, trap, player, false);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("setTrapStep4_")
    public static void setTrapStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String trap = translateNameIntoTrapIDOrReverse(buttonID.split("_")[2]);
        event.getMessage().delete().queue();
        new TrapToken().setTrapForPlanet(event, game, planet, trap, player);
    }

    public static List<String> getPlanetsWithTraps(Game game) {
        List<String> trappedPlanets = new ArrayList<>();
        for (String trapName : getTrapNames()) {
            if (!game.getStoredValue(trapName).isEmpty()) {
                trappedPlanets.add(game.getStoredValue(trapName));
            }
        }
        return trappedPlanets;
    }

    public static List<String> getUnusedTraps(Game game, Player player) {
        List<String> unusedTraps = new ArrayList<>();
        for (String trapName : getTrapNames()) {
            if (!player.getTrapCardsPlanets().containsKey(translateNameIntoTrapIDOrReverse(trapName))) {
                unusedTraps.add(trapName);
            }
        }
        return unusedTraps;
    }

    public static String translateNameIntoTrapIDOrReverse(String nameOrTrapID) {
        switch (nameOrTrapID) {
            case "Minefields" -> {
                return "dshc_3_lizho";
            }
            case "Interference Grid" -> {
                return "dshc_1_lizho";
            }
            case "Feint" -> {
                return "dshc_6_lizho";
            }
            case "Gravitic Inhibitor" -> {
                return "dshc_4_lizho";
            }
            case "Account Siphon" -> {
                return "dshc_2_lizho";
            }
            case "Saboteurs" -> {
                return "dshc_5_lizho";
            }
            case "dshc_3_lizho" -> {
                return "Minefields";
            }
            case "dshc_1_lizho" -> {
                return "Interference Grid";
            }
            case "dshc_6_lizho" -> {
                return "Feint";
            }
            case "dshc_4_lizho" -> {
                return "Gravitic Inhibitor";
            }
            case "dshc_2_lizho" -> {
                return "Account Siphon";
            }
            case "dshc_5_lizho" -> {
                return "Saboteurs";
            }
        }
        return "nope";
    }

    public static void addATrapToken(Game game, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        boolean addedYet = false;
        for (int x = 1; x < 7 && !addedYet; x++) {
            String tokenName = "attachment_lizhotrap" + x + ".png";
            if (!uH.getTokenList().contains(tokenName)) {
                addedYet = true;
                tile.addToken(tokenName, uH.getName());
            }
        }
    }

    public static void removeATrapToken(Game game, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            return;
        }
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        boolean removedYet = false;
        for (int x = 1; x < 7 && !removedYet; x++) {
            String tokenName = "attachment_lizhotrap" + x + ".png";
            if (uH.getTokenList().contains(tokenName)) {
                removedYet = true;
                tile.removeToken(tokenName, uH.getName());
            }
        }
        if (!removedYet) {
            String tokenName = "attachment_lizhotrap.png";
            if (uH.getTokenList().contains(tokenName)) {
                tile.removeToken(tokenName, uH.getName());
            }
        }
    }

    @ButtonHandler("autoneticMemoryStep2_")
    public static void autoneticMemoryStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        int count = Integer.parseInt(buttonID.split("_")[1]);
        game.drawActionCard(player.getUserID(), count - 1);
        ActionCardHelper.sendActionCardInfo(game, player, event);
        String msg2 = player.getFactionEmoji() + " is choosing to resolve their Autonetic Memory ability";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("autoneticMemoryStep3a", "Pick A Card From the Discard"));
        buttons.add(Buttons.blue("autoneticMemoryStep3b", "Drop 1 infantry"));
        String msg = player.getRepresentationUnfogged()
            + " you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void autoneticMemoryStep3b(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "gf", "placeOneNDone_skipbuild");
        String message = player.getRepresentationUnfogged() + " Use buttons to drop 1 infantry on a planet";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
    }

    public static void autoneticMemoryStep3a(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        ActionCardHelper.pickACardFromDiscardStep1(event, game, player);
    }

    public static void addOmenDie(Game game, int omenDie) {
        String omenDice;
        if (!game.getStoredValue("OmenDice").isEmpty()) {
            omenDice = game.getStoredValue("OmenDice");
            omenDice = omenDice + "_" + omenDie;
            game.setStoredValue("OmenDice", omenDice);
        } else {
            game.setStoredValue("OmenDice", "" + omenDie);
        }
    }

    public static void removeOmenDie(Game game, int omenDie) {
        String omenDice;
        if (!game.getStoredValue("OmenDice").isEmpty()) {
            omenDice = game.getStoredValue("OmenDice");
            omenDice = omenDice.replaceFirst("" + omenDie, "");
            omenDice = omenDice.replace("__", "_");
            game.setStoredValue("OmenDice", omenDice);
        }
    }

    public static List<Integer> getAllOmenDie(Game game) {
        List<Integer> dice = new ArrayList<>();
        for (String dieResult : game.getStoredValue("OmenDice").split("_")) {
            if (!dieResult.isEmpty() && !dieResult.contains("_")) {
                int die = Integer.parseInt(dieResult);
                dice.add(die);
            }
        }
        return dice;
    }

    @ButtonHandler("getOmenDice")
    public static void offerOmenDiceButtons(Game game, Player player) {
        String msg = player.getRepresentationUnfogged() + " you may play an Omen die with the following buttons. Duplicate dice are not shown.";
        List<Button> buttons = new ArrayList<>();
        List<Integer> dice = new ArrayList<>();
        for (int die : getAllOmenDie(game)) {
            if (!dice.contains(die)) {
                buttons.add(Buttons.green("useOmenDie_" + die, "Use Result: " + die));
                dice.add(die);
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete these"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("useOmenDie_")
    public static void useOmenDie(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        int die = Integer.parseInt(buttonID.split("_")[1]);
        removeOmenDie(game, die);
        String msg = player.getRepresentationUnfogged() + " used an Omen die with the number " + die;
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    public static void rollOmenDiceAtStartOfStrat(Game game, Player myko) {
        game.setStoredValue("OmenDice", "");
        StringBuilder msg = new StringBuilder(
            myko.getRepresentationUnfogged() + " rolled 4 omen dice and rolled the following numbers: ");
        for (int x = 0; x < 4; x++) {
            Die d1 = new Die(6);
            msg.append(d1.getResult()).append(" ");
            addOmenDie(game, d1.getResult());
        }
        MessageHelper.sendMessageToChannel(myko.getCorrectChannel(), msg.toString());
    }

    @ButtonHandler("pillage_")
    public static void pillage(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("pillage_", "");
        String colorPlayer = buttonID.split("_")[0];
        String checkedStatus = buttonID.split("_")[1];
        Player pillaged = game.getPlayerFromColorOrFaction(colorPlayer);
        if (pillaged == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Could not find player, please resolve manually.");
            return;
        }
        if (checkedStatus.contains("unchecked")) {
            List<Button> buttons = new ArrayList<>();
            String message2 = "Please confirm this is a valid pillage opportunity and that you wish to pillage.";
            buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "pillage_" + pillaged.getColor() + "_checked", "Pillage 1TG"));
            if (pillaged.getCommodities() > 0) {
                buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "pillage_" + pillaged.getColor() + "_checkedcomm", "Pillage a Commodity"));
            }
            buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        } else {
            MessageChannel channel1 = pillaged.getCorrectChannel();
            MessageChannel channel2 = player.getCorrectChannel();
            String pillagerMessage = player.getRepresentationUnfogged() + " you pillaged, your TGs have gone from "
                + player.getTg() + " to "
                + (player.getTg() + 1) + ".";
            String pillagedMessage = pillaged.getRepresentationUnfogged() + " you have been pillaged";

            if (pillaged.getCommodities() > 0 && checkedStatus.contains("checkedcomm")) {
                pillagedMessage = pillagedMessage + ", your comms have gone from " + pillaged.getCommodities() + " to "
                    + (pillaged.getCommodities() - 1) + ".";
                pillaged.setCommodities(pillaged.getCommodities() - 1);
            } else {
                pillagedMessage = pillagedMessage + ", your TGs have gone from " + pillaged.getTg() + " to "
                    + (pillaged.getTg() - 1) + ".";
                pillaged.setTg(pillaged.getTg() - 1);
            }
            player.setTg(player.getTg() + 1);
            MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
            MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
            if (player.hasUnexhaustedLeader("mentakagent")) {
                List<Button> buttons = new ArrayList<>();
                Button winnuButton = Buttons.green("FFCC_" + player.getFaction() + "_" + "exhaustAgent_mentakagent_" + pillaged.getFaction(), "Use Mentak Agent", Emojis.Mentak);
                buttons.add(winnuButton);
                buttons.add(Buttons.red("deleteButtons", "Done"));
                MessageHelper.sendMessageToChannelWithButtons(channel2,
                    "Wanna use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Suffi An, the Mentak" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " Agent?", buttons);
            }
            for (Player p2 : game.getRealPlayers()) {
                if (p2 != pillaged && p2 != player && p2.hasUnexhaustedLeader("yssarilagent")
                    && player.hasLeader("mentakagent")) {
                    List<Button> buttons = new ArrayList<>();
                    Button winnuButton = Buttons.green("FFCC_" + p2.getFaction() + "_" + "exhaustAgent_mentakagent_" + pillaged.getFaction(), "Use Mentak Agent", Emojis.Mentak);
                    buttons.add(winnuButton);
                    buttons.add(Buttons.red("deleteButtons", "Done"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(),
                        p2.getRepresentation() + "Wanna use " + (p2.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Suffi An, the Mentak" + (p2.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent?",
                        buttons);
                }
            }
        }
        event.getMessage().delete().queue();
    }

    public static List<Button> getMitosisOptions(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("mitosisInf", "Place 1 infantry"));
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
            buttons.add(Buttons.blue("mitosisMech", "Remove 1 infantry to DEPLOY 1 mech"));
        }
        return buttons;
    }

    @ButtonHandler("getDiplomatsButtons")
    public static void resolveGetDiplomatButtons(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteTheOneButton(event);
        String message = player.getRepresentation() + " select the planet you would like to exhaust";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, getDiplomatButtons(game, player));
    }

    public static List<Button> getDiplomatButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getTokenList().contains("token_freepeople.png")) {
                    buttons.add(Buttons.green("exhaustViaDiplomats_" + unitHolder.getName(),
                        Helper.getPlanetRepresentation(unitHolder.getName(), game)));
                }
            }
        }
        return buttons;
    }

    public static void resolveFreePeopleAbility(Game game) {
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet) {
                    String planet = unitHolder.getName();
                    boolean alreadyOwned = false;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_.getPlanets().contains(planet)) {
                            alreadyOwned = true;
                            break;
                        }
                    }
                    if (!alreadyOwned && !Constants.MECATOLS.contains(planet)) {
                        unitHolder.addToken("token_freepeople.png");
                    }
                }

            }
        }
    }

    @ButtonHandler("exhaustViaDiplomats_")
    public static void resolveDiplomatExhaust(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String message = player.getFactionEmoji() + " exhausted the unowned planet "
            + Helper.getPlanetRepresentation(planet, game) + " using the diplomats ability";
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        unitHolder.removeToken("token_freepeople.png");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        event.getMessage().delete().queue();

    }

    @ButtonHandler("mitosisInf")
    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "infantry", "placeOneNDone_skipbuild"));
        String message = player.getRepresentationUnfogged() + " Use buttons to put 1 infantry on a planet";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("mitosisMech")
    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>(getPlanetPlaceUnitButtonsForMechMitosis(player, game));
        String message = player.getRepresentationUnfogged() + " Use buttons to replace 1 infantry with 1 mech";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game game) {
        List<Button> planetButtons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                String colorID = Mapper.getColorID(player.getColor());
                int numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);

                if (numInf > 0) {
                    String buttonID = player.getFinsFactionCheckerPrefix() + "mitoMechPlacement_" + tile.getPosition() + "_"
                        + unitHolder.getName();
                    if ("space".equalsIgnoreCase(unitHolder.getName())) {
                        planetButtons.add(Buttons.green(buttonID,
                            "Space Area of " + tile.getRepresentationForButtons(game, player)));
                    } else {
                        planetButtons.add(Buttons.green(buttonID,
                            Helper.getPlanetRepresentation(unitHolder.getName(), game)));
                    }
                }
            }
        }
        return planetButtons;
    }

    @ButtonHandler("putSleeperOnPlanet_")
    public static void putSleeperOn(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("putSleeperOnPlanet_", "");
        String planet = buttonID;
        String message = player.getFactionEmojiOrColor() + " put a Sleeper on " + Helper.getPlanetRepresentation(planet, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, game, planet, player);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("orbitalMechDrop_")
    public static void orbitalMechDrop(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet), "1 mech " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, false) + " dropped a mech on " + Helper.getPlanetRepresentation(planet, game) + " for the cost of " + Emojis.Resources_3);
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button DoneExhausting = Buttons.red("finishComponentAction_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Use Buttons to Pay For The Mech", buttons);
        event.getMessage().delete().queue();

    }

    public static List<Button> getMantleCrackingButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        int coreCount = 0;
        for (String planetName : player.getPlanetsAllianceMode()) {
            if (planetName.contains("custodia") || planetName.contains("ghoti")) {
                continue;
            }
            Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
            if (planet == null) {
                continue;
            }
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null) {
                continue;
            }
            if (!planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG) && !Constants.MECATOLS.contains(planetName)
                && !tile.isHomeSystem()) {
                buttons.add(Buttons.gray("mantleCrack_" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
            } else {
                if (planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
                    coreCount = coreCount + 1;
                }
            }
        }
        if (coreCount > 2) {
            return new ArrayList<>();
        }
        return buttons;
    }

    @ButtonHandler("resolveShipOrder_")
    public static void resolveAxisOrderExhaust(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String order = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " Chose to Use " + Mapper.getRelic(order).getName());
        List<Button> buttons = new ArrayList<>();
        String message = "";
        String techName = "";
        if (Mapper.getRelic(order).getName().contains("Dreadnought")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "dreadnought", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 dreadnought in a system with your ships and CC";
            techName = "dn2";
        }
        if (Mapper.getRelic(order).getName().contains("Carrier")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "carrier", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 carrier in a system with your ships and CC";
            techName = "cv2";
        }
        if (Mapper.getRelic(order).getName().contains("Cruiser")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 cruiser in a system with your ships and CC";
            techName = "cr2";
        }
        if (Mapper.getRelic(order).getName().contains("Destroyer")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "2destroyer", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 2 destroyers in a system with your ships and CC";
            techName = "dd2";
        }
        message = player.getRepresentationUnfogged() + " " + message;
        ButtonHelper.deleteTheOneButton(event);
        player.addExhaustedRelic(order);
        for (Player p2 : game.getRealPlayers()) {
            if (game.playerHasLeaderUnlockedOrAlliance(p2, "axiscommander") && !p2.hasTech(techName)) {
                List<Button> buttons2 = new ArrayList<>();
                buttons2.add(Buttons.GET_A_TECH);
                buttons2.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2
                    .getRepresentationUnfogged()
                    + " a player has resolved an Axis Order (" + Mapper.getRelic(order).getName()
                    + ") and you may use the button to gain the corresponding unit upgrade tech if you pay 6 resources.",
                    buttons2);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
    }

    public static int getNumberOfDifferentAxisOrdersBought(Player player, Game game) {
        int num = 0;
        String relicName = "axisorderdd";
        String extra = "duplicate";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)
            || ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName + extra)) {
            num++;
        }
        relicName = "axisordercr";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)
            || ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName + extra)) {
            num++;
        }
        relicName = "axisordercv";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)
            || ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName + extra)) {
            num++;
        }
        relicName = "axisorderdn";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)
            || ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName + extra)) {
            num++;
        }
        return num;
    }

    public static List<Button> getBuyableAxisOrders(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        int maxCost = player.getCommodities();

        String relicName = "axisorderdd";
        String extra = "duplicate";
        int orderCost = 1;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)) {
                buttons.add(Buttons.gray("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)) {
                buttons.add(Buttons.gray("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisordercr";
        orderCost = 1;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)) {
                buttons.add(Buttons.gray("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)) {
                buttons.add(Buttons.gray("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisordercv";
        orderCost = 2;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)) {
                buttons.add(Buttons.gray("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)) {
                buttons.add(Buttons.gray("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisorderdn";
        orderCost = 3;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)) {
                buttons.add(Buttons.gray("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)) {
                buttons.add(Buttons.gray("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete these buttons"));

        return buttons;
    }

    @ButtonHandler("buyAxisOrder_")
    public static void resolveAxisOrderBuy(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String relicName = buttonID.split("_")[1];
        String cost = buttonID.split("_")[2];
        int lostComms = Integer.parseInt(cost);
        int oldComms = player.getCommodities();
        if (lostComms > oldComms) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " you don't have that many comms");
            return;
        }
        player.addRelic(relicName);
        player.setCommodities(oldComms - lostComms);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " acquired " + Mapper.getRelic(relicName).getName()
                + " and paid " + lostComms + " commodities (" + oldComms + "->" + player.getCommodities()
                + ")");
        CommanderUnlockCheck.checkPlayer(player, "axis");
        ButtonHelper.deleteTheOneButton(event);
    }

    public static List<Button> offerOlradinConnectButtons(Player player, Game game, String planetName) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.equalsIgnoreCase(planetName)) {
                continue;
            }
            buttons.add(Buttons.green("olradinConnectStep2_" + planetName + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        return buttons;
    }

    @ButtonHandler("olradinConnectStep2_")
    public static void resolveOlradinConnectStep2(Player player, Game game, String buttonID,
        ButtonInteractionEvent event) {
        String planet1 = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        player.setHasUsedPeopleConnectAbility(true);
        new RemoveUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet1), "inf " + planet1,
            game);
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet2), "inf " + planet2,
            game);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmoji() + " moved 1 infantry from "
                + Helper.getPlanetRepresentation(planet1, game) + " to "
                + Helper.getPlanetRepresentation(planet2, game) + " using their connect policy");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("olradinPreserveStep2_")
    public static void resolveOlradinPreserveStep2(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String type = buttonID.split("_")[1];
        player.setHasUsedEnvironmentPreserveAbility(true);
        StringBuilder sb = new StringBuilder();
        String cardID = game.drawExplore(type);
        ExploreModel card = Mapper.getExplore(cardID);
        sb.append(card.textRepresentation()).append(System.lineSeparator());
        String cardType = card.getResolution();
        if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
            sb.append(player.getRepresentationUnfogged()).append(" Gained relic fragment\n");
            player.addFragment(cardID);
            game.purgeExplore(cardID);
        }

        CommanderUnlockCheck.checkPlayer(player, "kollecc");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        event.getMessage().delete().queue();
    }

    public static List<Button> getOlradinPreserveButtons(Game game, Player player, String planet) {
        List<Button> buttons = new ArrayList<>();
        Set<String> types = ButtonHelper.getTypeOfPlanet(game, planet);
        for (String type : types) {
            if ("industrial".equals(type)) {
                buttons.add(Buttons.green("olradinPreserveStep2_industrial", "Explore Industrial"));
            }
            if ("cultural".equals(type)) {
                buttons.add(Buttons.blue("olradinPreserveStep2_cultural", "Explore Cultural"));
            }
            if ("hazardous".equals(type)) {
                buttons.add(Buttons.red("olradinPreserveStep2_hazardous", "Explore Hazardous"));
            }
        }
        return buttons;
    }

    public static void offerOrladinPlunderButtons(Player player, Game game, String planet) {
        if (!player.getHasUsedEnvironmentPlunderAbility() && player.hasAbility("policy_the_environment_plunder")
            && ButtonHelper.getTypeOfPlanet(game, planet).contains("hazardous")) {
            UnitHolder planetUnit = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            Planet planetReal = (Planet) planetUnit;
            List<Button> buttons = new ArrayList<>();
            if (planetReal.getOriginalPlanetType() != null && player.getPlanetsAllianceMode().contains(planet)
                && FoWHelper.playerHasUnitsOnPlanet(player, game.getTileFromPlanet(planet), planet)) {
                List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);

                String msg = player.getRepresentation() + " Due to your exhausting of "
                    + Helper.getPlanetRepresentation(planet, game)
                    + " you may resolve the following ability: **The Environment - Plunder (-)**: Once per action, after you explore a hazardous planet, you may remove 1 unit from that planet to explore that planet.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                Button remove = Buttons.red("getDamageButtons_" + game.getTileFromPlanet(planet).getPosition() + "_remove",
                    "Remove units in "
                        + game.getTileFromPlanet(planet).getRepresentationForButtons(game, player));
                buttons.add(remove);
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                planetButtons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Resolve remove",
                    buttons);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Resolve explore", planetButtons);
                player.setHasUsedEnvironmentPlunderAbility(true);
            }
        }
    }

    public static boolean canBePillaged(Player player, Game game, int tg) {
        if (player.getPromissoryNotesInPlayArea().contains("pop")) {
            return false;
        }
        if (Helper.getPlayerFromAbility(game, "pillage") != null && !Helper
            .getPlayerFromAbility(game, "pillage").getFaction().equalsIgnoreCase(player.getFaction())) {
            Player pillager = Helper.getPlayerFromAbility(game, "pillage");
            return tg > 2 && player.getNeighbouringPlayers().contains(pillager);
        }
        return false;
    }

    @ButtonHandler("meteorSlings_")
    public static void meteorSlings(Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String msg = player.getRepresentation() + " cancelled one bombardment hit to place one infantry on " + Helper.getPlanetRepresentation(planet, game);
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet), "1 inf " + planet, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    public static void pillageCheck(Player player, Game game) {
        if (canBePillaged(player, game, player.getTg())) {
            Player pillager = Helper.getPlayerFromAbility(game, "pillage");
            String finChecker = "FFCC_" + pillager.getFaction() + "_";
            List<Button> buttons = new ArrayList<>();
            String playerIdent = player.getFlexibleDisplayName();
            player.getDisplayName();
            MessageChannel channel = game.getMainGameChannel();
            if (game.isFowMode()) {
                playerIdent = StringUtils.capitalize(player.getColor());
                channel = pillager.getPrivateChannel();
            }
            String message = pillager.getRepresentationUnfogged() + " you may have the opportunity to pillage "
                + playerIdent
                + ". Please check this is a valid pillage opportunity, and use buttons to resolve.";
            buttons.add(Buttons.red(finChecker + "pillage_" + player.getColor() + "_unchecked",
                "Pillage " + playerIdent));
            buttons.add(Buttons.green(finChecker + "deleteButtons", "Decline Pillage Window"));
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
    }

    @ButtonHandler("mantleCrack_")
    public static void mantleCracking(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        int oldTg = player.getTg();
        player.setTg(oldTg + 4);
        Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        planet.addToken(Constants.GLEDGE_CORE_PNG);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmojiOrColor() + "cracked the mantle of "
                + Helper.getPlanetRepresentation(planetName, game) + " and gained 4TGs (" + oldTg + "->"
                + player.getTg() + "). This is technically an optional gain");
        pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 4);
        List<Button> buttons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getButtonsForPossibleTechForNekro(Player nekro, List<String> currentList, Game game) {
        List<Button> techToGain = new ArrayList<>();
        for (String tech : currentList) {
            techToGain.add(Buttons.green("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                Mapper.getTech(tech).getName()));
        }
        return techToGain;
    }

    public static List<String> getPossibleTechForNekroToGainFromPlayer(Player nekro, Player victim, List<String> currentList, Game game) {
        List<String> techToGain = new ArrayList<>(currentList);
        for (String tech : victim.getTechs()) {
            if (!nekro.getTechs().contains(tech) && !techToGain.contains(tech) && !"iihq".equalsIgnoreCase(tech)) {
                techToGain.add(tech);
            }
        }
        return techToGain;
    }

    @ButtonHandler("removeSleeperFromPlanet_")
    public static void removeSleeper(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
        String planet = buttonID;
        String message = player.getFactionEmojiOrColor() + " removed a Sleeper from " + planet;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, game, planet, player);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("replaceSleeperWith_")
    public static void replaceSleeperWith(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("replaceSleeperWith_", "");
        String planetName = buttonID.split("_")[1];
        String unit = buttonID.split("_")[0];
        String message;
        new SleeperToken().addOrRemoveSleeper(event, game, planetName, player);
        if ("mech".equalsIgnoreCase(unit)) {
            new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName + ", inf " + planetName, game);
            message = player.getFactionEmojiOrColor() + " replaced a Sleeper on " + Helper.getPlanetRepresentation(planetName, game) + " with a " + Emojis.mech + " and " + Emojis.infantry;
        } else {
            new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planetName)), "pds " + planetName, game);
            message = player.getFactionEmojiOrColor() + " replaced a Sleeper on " + Helper.getPlanetRepresentation(planetName, game) + " with a " + Emojis.pds;
            CommanderUnlockCheck.checkPlayer(player, "titans");
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

    public static void giveKeleresCommsNTg(Game game, GenericInteractionCreateEvent event) {
        for (Player player : game.getRealPlayers()) {
            if (player.hasAbility("divination")) {
                rollOmenDiceAtStartOfStrat(game, player);
            }
            if (!player.hasAbility("council_patronage"))
                continue;
            ButtonHelperStats.gainTGs(event, game, player, 1, true);
            ButtonHelperStats.replenishComms(event, game, player, true);
            String sb = player.getRepresentationUnfogged() + " your **Council Patronage** ability was triggered. Your " + Emojis.comm +
                    " commodities have been replenished and you have gained 1 " + Emojis.getTGorNomadCoinEmoji(game) +
                    " trade good (" + (player.getTg() - 1) + " -> " + player.getTg() + ")";

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
        }
    }

    @ButtonHandler("starforgeTile_")
    public static void starforgeTile(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String pos = buttonID.replace("starforgeTile_", "");

        String prefix = player.getFinsFactionCheckerPrefix() + "starforge_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(prefix + "destroyer_" + pos, "Starforge Destroyer", Emojis.destroyer));
        buttons.add(Buttons.red(prefix + "fighters_" + pos, "Starforge 2 Fighters", Emojis.fighter));
        String message = "Use the buttons to select what you would like to starforge.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("starforge_")
    public static void starforge(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unitNPlace = buttonID.replace("starforge_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String successMessage;
        if (player.getStrategicCC() > 0) {
            successMessage = player.getFactionEmoji() + " Spent 1 strategy token (" + (player.getStrategicCC()) + " -> " + (player.getStrategicCC() - 1) + ")";
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, Emojis.Muaat + "Starforge");
        } else {
            player.addExhaustedRelic("emelpar");
            successMessage = "Exhausted Scepter of Emelpar";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);

        List<Button> buttons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        if ("destroyer".equals(unit)) {
            new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", game);
            successMessage = "Produced 1 " + Emojis.destroyer + " in tile "
                + tile.getRepresentationForButtons(game, player) + ".";

        } else {
            new AddUnits().unitParsing(event, player.getColor(), tile, "2 ff", game);
            successMessage = "Produced 2 " + Emojis.fighter + " in tile "
                + tile.getRepresentationForButtons(game, player) + ".";
        }
        if (player.ownsUnit("muaat_mech") && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            successMessage = ButtonHelper.putInfWithMechsForStarforge(pos, successMessage, game, player, event);
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("mitoMechPlacement_")
    public static void resolveMitosisMechPlacement(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        String uH = buttonID.split("_")[2];
        String successMessage = "";
        if ("space".equalsIgnoreCase(uH)) {
            successMessage = player.getFactionEmojiOrColor() + " Replaced 1 infantry with 1 mech in the space area of " + tile.getRepresentationForButtons(game, player) + ".";
        } else {
            successMessage = player.getFactionEmojiOrColor() + " Replaced 1 infantry with 1 mech on " + Helper.getPlanetRepresentation(uH, game) + ".";
        }
        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), tile, "mech " + uH.replace("space", ""), game);
        new RemoveUnits().removeStuff(event, tile, 1, uH, key, player.getColor(), false, game);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
        event.getMessage().delete().queue();
    }

    public static List<Button> getXxchaPeaceAccordsButtons(Game game, Player player, GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Tile tile = game.getTileFromPlanet(planet);
            for (String pos2 : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false)) {
                Tile tile2 = game.getTileByPosition(pos2);
                for (Planet planetUnit2 : tile2.getPlanetUnitHolders()) {
                    Planet planetReal2 = planetUnit2;
                    String planet2 = planetReal2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                    if (!player.getPlanetsAllianceMode().contains(planet2) && !planetRepresentation2.contains("Mecatol")
                        && (planetReal2.getUnits() == null || planetReal2.getUnits().isEmpty())
                        && !planetsChecked.contains(planet2)) {
                        buttons.add(Buttons.green(finChecker + "peaceAccords_" + planet2, planetRepresentation2, Emojis.Xxcha));
                        planetsChecked.add(planet2);
                    }
                }
            }
        }
        return buttons;
    }

    public static List<Button> getKyroContagionButtons(Game game, Player player, GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Tile tile = game.getTileFromPlanet(planet);
            for (String pos2 : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player,
                false)) {
                Tile tile2 = game.getTileByPosition(pos2);
                for (Planet planetUnit2 : tile2.getPlanetUnitHolders()) {
                    Planet planetReal2 = planetUnit2;
                    String planet2 = planetReal2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                    if (!planetsChecked.contains(planet2)) {
                        buttons.add(Buttons.green(finChecker + "contagion_" + planet2, planetRepresentation2, Emojis.Xxcha));
                        planetsChecked.add(planet2);
                    }
                }
            }
            for (Planet planetUnit2 : tile.getPlanetUnitHolders()) {
                Planet planetReal2 = planetUnit2;
                String planet2 = planetReal2.getName();
                String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                if (!planetsChecked.contains(planet2)) {
                    buttons.add(Buttons.green(finChecker + "contagion_" + planet2, planetRepresentation2, Emojis.Xxcha));
                    planetsChecked.add(planet2);
                }
            }
        }
        return buttons;
    }

    @ButtonHandler("moult_")
    public static void resolveMoult(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteTheOneButton(event);
        String pos = buttonID.split("_")[1];
        String generalMsg = player.getFactionEmoji()
            + " is resolving moult (after winning the space combat) to build 1 ship, reducing the cost by 1 for each of their non-fighter ships destroyed in the combat";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), generalMsg);
        String type = "sling";
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos),
            type,
            "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to produce a ship. "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("munitionsReserves")
    public static void munitionsReserves(ButtonInteractionEvent event, Game game, Player player) {
        if (player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " you only have " + player.getTg() + Emojis.tg + " and thus can't use Munitions Reserve");
            return;
        }
        String msg = player.getFactionEmoji() + " spent " + Emojis.tg(2) + " " + player.gainTG(-2) + " to use Munitions Reserves.\nTheir next roll will automatically reroll misses. If they wish to instead reroll hits as a part of a deal, they should just ignore the rerolls.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        game.setStoredValue("munitionsReserves", player.getFaction());
    }

    @ButtonHandler("contagion_")
    public static void lastStepOfContagion(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String amount = "1";
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));

        new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planet)), amount + " inf " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " used contagion ability to land " + amount
                + " infantry on " + Helper.getPlanetRepresentation(planet, game));
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName());
        if (players.size() > 1) {
            StartCombat.startGroundCombat(players.get(0), players.get(1), game, event, unitHolder, tile);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("peaceAccords_")
    public static void resolvePeaceAccords(String buttonID, Player player, Game game, ButtonInteractionEvent event) {
        String planetID = buttonID.split("_")[1];
        if ("lockedmallice".equalsIgnoreCase(planetID)) {
            planetID = "mallice";
            Tile tile = game.getTileFromPlanet("lockedmallice");
            MoveUnits.flipMallice(event, tile, game);
        } else if ("hexlockedmallice".equalsIgnoreCase(planetID)) {
            planetID = "hexmallice";
            Tile tile = game.getTileFromPlanet("hexlockedmallice");
            MoveUnits.flipMallice(event, tile, game);
        }
        PlanetAdd.doAction(player, planetID, game, event, false);
        String planetRep = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetID, game);
        String msg = player.getFactionEmojiOrColor() + " claimed the planet " + planetRep + " using the " + Emojis.Xxcha + "**Peace Accords** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("initialIndoctrination_")
    public static void resolveInitialIndoctrinationQuestion(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        List<Button> options = new ArrayList<>();
        options.add(Buttons.green("indoctrinate_" + planet + "_infantry", "Indoctrinate to place 1 infantry"));
        options.add(Buttons.green("indoctrinate_" + planet + "_mech", "Indoctrinate to place 1 mech"));
        options.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " use buttons to resolve indoctrination", options);
    }

    @ButtonHandler("indoctrinate_")
    public static void resolveFollowUpIndoctrinationQuestion(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 " + unit + " " + planet, game);
        for (Player p2 : game.getPlayers().values()) {
            if (p2.getColor() == null || p2 == player) {
                continue; // fix indoctrinate vs neutral
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet) && !player.getAllianceMembers().contains(p2.getFaction())) {
                new RemoveUnits().unitParsing(event, p2.getColor(), tile, "1 infantry " + planet, game);
                break;
            }
        }
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        CommanderUnlockCheck.checkPlayer(player, "yin");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " replaced 1 of their opponent's infantry with 1 " + unit + " on "
                + Helper.getPlanetRepresentation(planet, game) + " using indoctrination");
        options.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " pay for indoctrination.", options);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("distant_suns_")
    public static void distantSuns(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String bID = buttonID.replace("distant_suns_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = player.getFactionEmoji() + " declined to use their Distant Suns ability";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            new ExplorePlanet().explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, game, 1, false);
        } else {
            new ExplorePlanet().explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, game, 2, false);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("deep_mining_")
    public static void deepMining(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String bID = buttonID.replace("deep_mining_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = player.getFactionEmoji() + " declined to use their Deep Mining ability";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            new ExplorePlanet().explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, game, 1, false);
        } else {
            message = player.getFactionEmoji() + " used their Deep Mining ability to gain 1TG " + player.gainTG(1);
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("augersPeak_")
    public static void handleAugursPeak(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if ("1".equalsIgnoreCase(buttonID.split("_")[1])) {
            new PeekAtStage1().secondHalfOfPeak(event, game, player, 1);
        } else {
            new PeekAtStage2().secondHalfOfPeak(event, game, player, 1);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("initialPeak")
    public static void handleAugursPeakInitial(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("augersPeak_1", "Peek At Next Stage 1"));
        buttons.add(Buttons.green("augersPeak_2", "Peek At Next Stage 2"));
        String msg = player.getRepresentationUnfogged()
            + " the bot doesn't know if the next objective is a stage 1 or a stage 2. Please help it out and click the right button.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }
}
