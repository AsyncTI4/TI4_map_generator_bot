package ti4.helpers;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.AddPlanetService;
import ti4.service.planet.FlipTileService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

public class ButtonHelperAbilities {

    public static void autoneticMemoryStep1(Game game, Player player, int count) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("autoneticMemoryStep2_" + count, "Use Autonetic Memory"));
        buttons.add(Buttons.red("autoneticMemoryDecline_" + count, "Decline"));
        String msg = player.getRepresentationUnfogged()
            + ", you may draw 1 less action card and utilize your **Autonetic Memory** ability. Please use or decline to use.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("startFacsimile_")
    public static void startFacsimile(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        String msg = player.getRepresentation() + " choose the opponent ship you wish to build using " + MiscEmojis.influence + " influence";
        if (player.getPromissoryNotes().containsKey("dspnmort") && !player.getPromissoryNotesOwned().contains("dspnmort")) {
            PromissoryNoteHelper.resolvePNPlay("dspnmort", player, game, event);
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
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("facsimileStep2_")
    public static void resolveFacsimileStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String unit = buttonID.split("_")[1];
        UnitType unitType = Units.findUnitType(AliasHandler.resolveUnit(unit));
        TI4Emoji unitEmoji = unitType.getUnitTypeEmoji();
        String msg = player.getFactionEmoji() + " chose to produce a " + unitEmoji + unit
            + " with **Facsimile** or _Secrets of the Weave_ and will now spend influence to build it.";
        event.getMessage().delete().queue();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit);
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
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Choose the tile to produce up to 2 ships in",
            getTilesToRallyToTheCause(game, player));
    }

    @ButtonHandler("rallyToTheCauseStep2_")
    public static void rallyToTheCauseStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        String type = "sling";
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
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
                || ButtonHelper.isTileLegendary(tile) || tile.isMecatol()) {
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
        String msg = player.getRepresentation() + " choose the tile you wish to pull fighters from.";
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
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
        String msg = player.getRepresentation() + " choose whether to pull 1 or 2 fighters.";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
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
        String msg = player.getRepresentation() + " choose which player to give the fighters too.";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
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
        RemoveUnitService.removeUnits(event, tile2, game, player.getColor(), fighters + " fighters");
        AddUnitService.addUnits(event, tile, game, p2.getColor(), fighters + " fighters");
        String msg = player.getRepresentation() + " used their **Mercenaries** ability and transferred " + fighters
            + " fighter" + (fighters.equals("1") ? "" : "s") + " from " + tile2.getRepresentationForButtons(game, player) + " to "
            + tile.getRepresentationForButtons(game, player) + " and gave them to "
            + p2.getFactionEmojiOrColor() + ".";
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
        vaden.clearDebt(player, amount);
        String msg = player.getFactionEmojiOrColor() + " paid 1 trade good to "
            + vaden.getFactionEmojiOrColor()
            + "to clear 2 debt tokens via the **Binding Debts** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(vaden.getCorrectChannel(), msg);
        }
    }

    @ButtonHandler("autoneticMemoryDecline_")
    public static void autoneticMemoryDecline(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        int count = Integer.parseInt(buttonID.split("_")[1]);
        game.drawActionCard(player.getUserID(), count);
        ActionCardHelper.sendActionCardInfo(game, player, event);
        ButtonHelper.checkACLimit(game, player);
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
        String msg = player.getFactionEmoji() + " is resolving the **Grace** ability.";
        int scPlayed = Integer.parseInt(buttonID.split("_")[1]);
        if (!player.hasAbility("grace")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "To " + player.getFactionEmoji() + ": This button ain't for you.");
            return;
        }
        player.addExhaustedAbility("grace");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged()
                + " use buttons to resolve **Grace**.\n"
                + "-# Reminder that you have to spend a command token from your strategy pool if applicable, and that you may only do one of these.",
            getGraceButtons(game, player, scPlayed));
    }

    public static List<Button> getGraceButtons(Game game, Player edyn, int scPlayed) {
        List<Button> scButtons = new ArrayList<>();
        scButtons.add(Buttons.gray("spendAStratCC", "Spend a Strategy Token"));
        if (scPlayed > 1 && (game.getScPlayed().get(1) == null || !game.getScPlayed().get(1))) {
            scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens"));
            // scButtons.add(Buttons.red("leadershipExhaust", "Exhaust Planets"));
        }
        if (scPlayed > 2 && (game.getScPlayed().get(2) == null || !game.getScPlayed().get(2))) {
            scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
        }
        if (scPlayed > 3 && (game.getScPlayed().get(3) == null || !game.getScPlayed().get(3))) {
            scButtons.add(Buttons.gray("sc_ac_draw", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (scPlayed > 4 && (game.getScPlayed().get(4) == null || !game.getScPlayed().get(4))) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 Space Dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (scPlayed > 5 && (game.getScPlayed().get(5) == null || !game.getScPlayed().get(5))) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (scPlayed > 6 && (game.getScPlayed().get(6) == null || !game.getScPlayed().get(6))) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (scPlayed > 7 && (game.getScPlayed().get(7) == null || !game.getScPlayed().get(7))) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (scPlayed > 8 && (game.getScPlayed().get(8) == null || !game.getScPlayed().get(8))) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done Resolving"));

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
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged() + " tell the bot whose planet you wish to put a trap on.", buttons);
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
            player.getRepresentationUnfogged() + " select the planet you wish to put a trap on.", buttons);
    }

    @ButtonHandler("setTrapStep3_")
    public static void setTrapStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : getUnusedTraps(game, player)) {
            availableTraps.add(Buttons.green("setTrapStep4_" + planet + "_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentationUnfogged() + " choose the trap you wish to set on the planet "
            + Helper.getPlanetRepresentation(planet, game) + ".";
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
        String msg = player.getRepresentationUnfogged() + " choose the trap you wish to remove.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    @ButtonHandler("revealTrapStep1")
    public static void revealTrapStep1(Game game, Player player) {
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : player.getTrapCardsPlanets().keySet()) {
            availableTrap = translateNameIntoTrapIDOrReverse(availableTrap);
            availableTraps.add(Buttons.green("revealTrapStep2_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentationUnfogged() + " choose the trap you wish to reveal.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    @ButtonHandler("revealTrapStep2_")
    public static void revealTrapStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String trap = buttonID.split("_")[1];
        trap = translateNameIntoTrapIDOrReverse(trap);
        String planet = player.getTrapCardsPlanets().get(trap);
        DiscordantStarsHelper.revealTrapForPlanet(event, game, planet, trap, player, true);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("removeTrapStep2_")
    public static void removeTrapStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String trap = buttonID.split("_")[1];
        trap = translateNameIntoTrapIDOrReverse(trap);
        String planet = player.getTrapCardsPlanets().get(trap);
        DiscordantStarsHelper.revealTrapForPlanet(event, game, planet, trap, player, false);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("setTrapStep4_")
    public static void setTrapStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String trap = translateNameIntoTrapIDOrReverse(buttonID.split("_")[2]);
        event.getMessage().delete().queue();
        DiscordantStarsHelper.setTrapForPlanet(event, game, planet, trap, player);
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
        String msg2 = player.getRepresentationNoPing() + " is choosing to resolve their **Autonetic Memory** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("autoneticMemoryStep3a", "Pick A Card From the Discard"));
        buttons.add(Buttons.blue("autoneticMemoryStep3b", "Drop 1 infantry"));
        String msg = player.getRepresentationUnfogged()
            + " you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control.";
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
        ActionCardHelper.pickACardFromDiscardStep1(game, player);
    }

    public static void addOmenDie(Game game, int omenDie) {
        String omenDice;
        if (!game.getStoredValue("OmenDice").isEmpty()) {
            omenDice = game.getStoredValue("OmenDice");
            omenDice += "_" + omenDie;
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
        offerOmenDiceButtons2(game, player, "no");
    }

    @ButtonHandler("getOmenDice2")
    public static void offerOmenDiceButtons2(Game game, Player player, String agent) {
        String msg = player.getRepresentationUnfogged() + " you may play an Omen die with the following buttons. Duplicate dice are not shown.";
        List<Button> buttons = new ArrayList<>();
        List<Integer> dice = new ArrayList<>();
        for (int die : getAllOmenDie(game)) {
            if (!dice.contains(die)) {
                buttons.add(Buttons.green("useOmenDie_" + die + "_" + agent, "Use Result: " + die));
                dice.add(die);
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete these"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("useOmenDie_")
    public static void useOmenDie(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        int die = Integer.parseInt(buttonID.split("_")[1]);
        if (buttonID.split("_")[2].equalsIgnoreCase("no")) {
            removeOmenDie(game, die);
        }
        String msg = player.getRepresentationUnfogged() + " used an **Omen** die with the number " + die + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    public static void rollOmenDiceAtStartOfStrat(Game game, Player myko) {
        game.setStoredValue("OmenDice", "");
        StringBuilder msg = new StringBuilder(
            myko.getRepresentationUnfogged() + " rolled 4 **Omen** dice and rolled the following numbers:");
        for (int x = 0; x < 4; x++) {
            Die d1 = new Die(6);
            msg.append(" ").append(d1.getResult());
            addOmenDie(game, d1.getResult());
        }
        MessageHelper.sendMessageToChannel(myko.getCorrectChannel(), msg.append(".").toString());
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
            String message2 = "Please confirm this is a valid **Pillage** opportunity and that you wish to **Pillage**.";
            buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "pillage_" + pillaged.getColor() + "_checked", "Pillage 1 Trade Good"));
            if (pillaged.getCommodities() > 0) {
                buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "pillage_" + pillaged.getColor() + "_checkedcomm", "Pillage 1 Commodity"));
            }
            buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Delete These Buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        } else {

            player.setPillageCounter(player.getPillageCounter() + 1);
            pillaged.setPillageCounter(pillaged.getPillageCounter() + 1);
            String pillagerMessage = player.getRepresentationUnfogged() + " you succesfully **Pillage**'d, so your ~~doubloons~~ trade goods have gone from "
                + player.getTg() + " to "
                + (player.getTg() + 1) + ". The number of innocent merchant ships you have looted this game is " + player.getPillageCounter();
            if (player.getPillageCounter() < 5) {
                pillagerMessage += ", which is not enough to be well-known yet, keep trying young matey.";
            } else {
                if (player.getPillageCounter() < 10) {
                    pillagerMessage += ", which is a small but significant amount, you'll be an infamous pirate yet, just keep at it for 10, maybe 20, more rounds.";
                } else {
                    if (player.getPillageCounter() < 15) {
                        pillagerMessage += ", which is quite the treasure hoard of ill-gotten goods. Hope you didn't spend it all in one place.";
                    } else {
                        if (player.getPillageCounter() < 20) {
                            pillagerMessage += ", which is starting to be a bit legendary. Sort of surprised the table hasn't united against you yet";
                        } else {
                            pillagerMessage += ", which is enough to ensure your name goes down in async history as one of the most successfull pirates ever to sail the intergalactic seas.";
                        }

                    }
                }
            }
            String pillagedMessage = "Arrr " + pillaged.getRepresentationUnfogged() + " it do seem ye have been **Pillage**'d 🏴‍☠️🏴‍☠️🏴‍☠️";

            if (pillaged.getCommodities() > 0 && checkedStatus.contains("checkedcomm")) {
                pillagedMessage += ", so your worthless commodities went from " + pillaged.getCommodities() + " to "
                    + (pillaged.getCommodities() - 1) + ".";
                pillaged.setCommodities(pillaged.getCommodities() - 1);
            } else {
                pillagedMessage += ", so your trade goods went from " + pillaged.getTg() + " to "
                    + (pillaged.getTg() - 1) + ".";
                pillaged.setTg(pillaged.getTg() - 1);
            }
            pillagedMessage += " This number of times your gold has been forcefully liberated from your grasping hands for the benefit of the needy this game is " + pillaged.getPillageCounter();
            player.setTg(player.getTg() + 1);
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pillagerMessage);
                MessageHelper.sendMessageToChannel(pillaged.getCorrectChannel(), pillagedMessage);
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), pillagerMessage + "\n" + pillagedMessage);
            }
            if (player.hasUnexhaustedLeader("mentakagent")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("FFCC_" + player.getFaction() + "_" + "exhaustAgent_mentakagent_" + pillaged.getFaction(), "Use Mentak Agent", FactionEmojis.Mentak));
                buttons.add(Buttons.red("deleteButtons", "Done"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    "Wanna use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Suffi An, the Mentak" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " Agent?", buttons);
            }
            for (Player p2 : game.getRealPlayers()) {
                if (p2 != pillaged && p2 != player && p2.hasUnexhaustedLeader("yssarilagent")
                    && player.hasLeader("mentakagent")) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("FFCC_" + p2.getFaction() + "_" + "exhaustAgent_mentakagent_" + pillaged.getFaction(), "Use Mentak Agent", FactionEmojis.Mentak));
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
            buttons.add(Buttons.blue("mitosisMech", "Remove 1 Infantry to Deploy 1 Mech"));
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
            + Helper.getPlanetRepresentation(planet, game) + " using their **Diplomats** ability.";
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        unitHolder.removeToken("token_freepeople.png");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("addTombToken_")
    public static void addTombToken(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String message = player.getFactionEmoji() + " added a tomb token to " + Helper.getPlanetRepresentation(planet, game) + ".";
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        unitHolder.addToken("token_tomb.png");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("startAncientEmpire")
    public static void startAncientEmpire(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String message = player.getRepresentation() + " chose a planet to add a tomb token to.";
        List<Button> buttons = new ArrayList<>();
        for (String planet : game.getPlanets()) {
            if (player.getPlanets().contains(planet)) {
                continue;
            }
            UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (unitHolder != null) {
                if (unitHolder.getTokenList().contains("token_tomb.png")) {
                    continue;
                }
                buttons.add(Buttons.green("addTombToken_" + planet, Helper.getPlanetRepresentation(planet, game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    @ButtonHandler("mitosisInf")
    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "infantry", "placeOneNDone_skipbuild"));
        String message = player.getRepresentationUnfogged() + ", please choose which planet to place an infantry on.";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " is resolving **Mitosis**.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("mitosisMech")
    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>(getPlanetPlaceUnitButtonsForMechMitosis(player, game));
        String message = player.getRepresentationUnfogged() + ", please choose where you wish to replace an infantry with a mech.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " is resolving **Mitosis**.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game game) {
        return getPlanetPlaceUnitButtonsForMechMitosis(player, game, "mitosis");
    }

    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game game, String reason) {
        List<Button> planetButtons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                String colorID = Mapper.getColorID(player.getColor());
                int numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);

                if (numInf > 0) {
                    String buttonID = player.getFinsFactionCheckerPrefix() + "mitoMechPlacement_" + tile.getPosition() + "_"
                        + unitHolder.getName() + "_" + reason;
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
        SleeperTokenHelper.addOrRemoveSleeper(event, game, planet, player);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("orbitalMechDrop_")
    public static void orbitalMechDrop(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 mech " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, false) + " dropped a mech on " + Helper.getPlanetRepresentation(planet, game) + " for the cost of " + MiscEmojis.Resources_3);
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
                    coreCount += 1;
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
            player.getRepresentationNoPing() + " is using _" + Mapper.getRelic(order).getName() + "_.");
        List<Button> buttons = new ArrayList<>();
        String message = "";
        String techName = "";
        if (Mapper.getRelic(order).getName().contains("Dreadnought")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "dreadnought", "placeOneNDone_skipbuild", event));
            message = "Use buttons to place 1 dreadnought in a system your command token and your other ships.";
            techName = "dn2";
        }
        if (Mapper.getRelic(order).getName().contains("Carrier")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "carrier", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 carrier in a system your command token and your other ships.";
            techName = "cv2";
        }
        if (Mapper.getRelic(order).getName().contains("Cruiser")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 cruiser in a system your command token and your other ships.";
            techName = "cr2";
        }
        if (Mapper.getRelic(order).getName().contains("Destroyer")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "2destroyer", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 2 destroyers in a system your command token and your other ships.";
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
                    + " a player has resolved an _Axis Order_ (" + Mapper.getRelic(order).getName()
                    + ") and you may use the button to gain the corresponding unit upgrade technology if you pay 6 resources.",
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

        Map<String, Integer> orderDeck = Map.of(
            "axisorderdd", 1,
            "axisorderddduplicate", 1,
            "axisordercr", 1,
            "axisordercrduplicate", 1,
            "axisordercv", 2,
            "axisordercvduplicate", 2,
            "axisorderdn", 3,
            "axisorderdnduplicate", 3);
        for (Map.Entry<String, Integer> order : orderDeck.entrySet()) {
            String orderName = order.getKey();
            int orderCost = order.getValue();
            if (orderCost <= maxCost) {
                if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, orderName)) {
                    buttons.add(Buttons.gray("buyAxisOrder_" + orderName + "_" + orderCost,
                        "Buy an " + Mapper.getRelic(orderName).getName() + " for " + orderCost + " commodit" + (orderCost == 1 ? "y" : "ies")));
                }
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
                player.getRepresentationUnfogged() + " you don't have " + lostComms + " commodit" + (lostComms == 1 ? "y" : "ies")
                    + " (you only have " + oldComms + " commodit" + (oldComms == 1 ? "y" : "ies") + ".");
            return;
        }
        player.addRelic(relicName);
        player.setCommodities(oldComms - lostComms);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " acquired " + Mapper.getRelic(relicName).getName()
                + " and paid " + lostComms + " commodit" + (lostComms == 1 ? "y" : "ies") + " (" + oldComms + "->" + player.getCommodities()
                + ").");
        CommanderUnlockCheckService.checkPlayer(player, "axis");
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
    public static void resolveOlradinConnectStep2(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet1 = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        player.setHasUsedPeopleConnectAbility(true);
        RemoveUnitService.removeUnits(event, game.getTileFromPlanet(planet1), game, player.getColor(), "inf " + planet1);
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet2), game, player.getColor(), "inf " + planet2);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmoji() + " moved 1 infantry from "
                + Helper.getPlanetRepresentation(planet1, game) + " to "
                + Helper.getPlanetRepresentation(planet2, game) + " using _Policy - The People: Connect ➕_.");
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

        CommanderUnlockCheckService.checkPlayer(player, "kollecc");
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
        if (!player.isHasUsedEnvironmentPlunderAbility() && player.hasAbility("policy_the_environment_plunder")
            && ButtonHelper.getTypeOfPlanet(game, planet).contains("hazardous")) {
            UnitHolder planetUnit = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            Planet planetReal = (Planet) planetUnit;
            List<Button> buttons = new ArrayList<>();
            if (planetReal != null && isNotBlank(planetReal.getOriginalPlanetType()) && player.getPlanetsAllianceMode().contains(planet)
                && FoWHelper.playerHasUnitsOnPlanet(player, game.getTileFromPlanet(planet), planet)) {
                List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);

                String msg = player.getRepresentation() + ", due to your exhausting of "
                    + Helper.getPlanetRepresentation(planet, game) + ", you may resolve your _Policy - The Environment: Plunder ➖_ ability."
                    + " Once per action, after you explore a hazardous planet, you may remove 1 unit from that planet to explore that planet.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                Button remove = Buttons.red("getDamageButtons_" + game.getTileFromPlanet(planet).getPosition() + "_remove",
                    "Remove units in "
                        + game.getTileFromPlanet(planet).getRepresentationForButtons(game, player));
                buttons.add(remove);
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                planetButtons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Resolve Remove", buttons);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Resolve Explore", planetButtons);
                player.setHasUsedEnvironmentPlunderAbility(true);
            }
        }
    }

    public static boolean canBePillaged(Player player, Game game, int tg) {
        if (player.getPromissoryNotesInPlayArea().contains("pop")) {
            return false;
        }
        if (player.getPromissoryNotesInPlayArea().contains("sigma_promise_of_protection")) {
            return false;
        }
        if (Helper.getPlayerFromAbility(game, "pillage") != null && !Helper
            .getPlayerFromAbility(game, "pillage").getFaction().equalsIgnoreCase(player.getFaction())) {
            Player pillager = Helper.getPlayerFromAbility(game, "pillage");
            return tg > 2 && player.getNeighbouringPlayers(true).contains(pillager);
        }
        return false;
    }

    @ButtonHandler("meteorSlings_")
    public static void meteorSlings(Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String msg = player.getRepresentation() + " cancelled one BOMBARDMENT hit to place one infantry on " + Helper.getPlanetRepresentation(planet, game);
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 inf " + planet);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    public static void pillageCheck(Player player) {
        if (player != null) pillageCheck(player, player.getGame());
    }

    public static void pillageCheck(Player player, Game game) {
        if (canBePillaged(player, game, player.getTg())) {
            Player pillager = Helper.getPlayerFromAbility(game, "pillage");
            String finChecker = "FFCC_" + pillager.getFaction() + "_";
            List<Button> buttons = new ArrayList<>();
            String playerIdent = player.getRepresentationNoPing();
            player.getDisplayName();
            MessageChannel channel = game.getMainGameChannel();
            if (game.isFowMode()) {
                playerIdent = StringUtils.capitalize(player.getColor());
                channel = pillager.getPrivateChannel();
            }
            String message = pillager.getRepresentationUnfogged() + " you may have the opportunity to **Pillage** "
                + playerIdent
                + ". Please check this is a valid **Pillage** opportunity, and use buttons to resolve.";
            buttons.add(Buttons.red(finChecker + "pillage_" + player.getColor() + "_unchecked",
                "Pillage " + (game.isFowMode() ? playerIdent : player.getFlexibleDisplayName())));
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
            player.getFactionEmojiOrColor() + " Cracked the Mantle of "
                + Helper.getPlanetRepresentation(planetName, game) + " and gained 4 trade goods (" + oldTg + "->"
                + player.getTg() + ").\n-# This is technically an optional gain.");
        pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 4);
        List<Button> buttons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
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
        SleeperTokenHelper.addOrRemoveSleeper(event, game, planet, player);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("replaceSleeperWith_")
    public static void replaceSleeperWith(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("replaceSleeperWith_", "");
        String planetName = buttonID.split("_")[1];
        String unit = buttonID.split("_")[0];
        String message;
        SleeperTokenHelper.addOrRemoveSleeper(event, game, planetName, player);

        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        if ("mech".equalsIgnoreCase(unit)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "mech " + planetName + ", inf " + planetName);
            message = player.getFactionEmojiOrColor() + " replaced a Sleeper on " + Helper.getPlanetRepresentation(planetName, game) + " with a " + UnitEmojis.mech + " and " + UnitEmojis.infantry;
        } else {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "pds " + planetName);
            message = player.getFactionEmojiOrColor() + " replaced a Sleeper on " + Helper.getPlanetRepresentation(planetName, game) + " with a " + UnitEmojis.pds;
            CommanderUnlockCheckService.checkPlayer(player, "titans");
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
            String sb = player.getRepresentationUnfogged() + " your **Council Patronage** ability was triggered. Your " + MiscEmojis.comm +
                " commodities have been replenished and you have gained 1 " + MiscEmojis.getTGorNomadCoinEmoji(game) +
                " trade good (" + (player.getTg() - 1) + " -> " + player.getTg() + ")";

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
        }
    }

    @ButtonHandler("starforgeTile_")
    public static void starforgeTile(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String pos = buttonID.replace("starforgeTile_", "");

        String prefix = player.getFinsFactionCheckerPrefix() + "starforge_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(prefix + "destroyer_" + pos, "Starforge Destroyer", UnitEmojis.destroyer));
        buttons.add(Buttons.red(prefix + "fighters_" + pos, "Starforge 2 Fighters", UnitEmojis.fighter));
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
            successMessage = player.getRepresentationUnfogged() + " spent 1 strategy token (" + (player.getStrategicCC()) + " -> " + (player.getStrategicCC() - 1) + ")";
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, FactionEmojis.Muaat + "Starforge");
        } else {
            player.addExhaustedRelic("emelpar");
            successMessage = player.getRepresentationUnfogged() + " exhausted the _" + RelicHelper.sillySpelling() + "_.";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);

        List<Button> buttons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        if ("destroyer".equals(unit)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 destroyer");
            successMessage = "Produced 1 " + UnitEmojis.destroyer + " in tile "
                + tile.getRepresentationForButtons(game, player) + ".";

        } else {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "2 ff");
            successMessage = "Produced 2 " + UnitEmojis.fighter + " in tile "
                + tile.getRepresentationForButtons(game, player) + ".";
        }
        if ((player.ownsUnit("muaat_mech") || player.ownsUnit("sigma_muaat_mech") || player.ownsUnit("absol_muaat_mech")) && !ButtonHelper.isLawInPlay(game, "articles_war")) {
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
        String reason = buttonID.split("_")[3];
        switch (reason) {
            case "mitosis" -> reason = "**Mitosis**";
            case "refit" -> reason = "_Refit Troops_";
        }
        String successMessage;
        if ("space".equalsIgnoreCase(uH)) {
            successMessage = player.getFactionEmojiOrColor() + " replaced 1 infantry with 1 mech in the space area of "
                + tile.getRepresentationForButtons(game, player) + " with " + reason + ".";
        } else {
            successMessage = player.getFactionEmojiOrColor() + " replaced 1 infantry with 1 mech on "
                + Helper.getPlanetRepresentation(uH, game) + " with " + reason + ".";
        }
        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), player.getColor());
        AddUnitService.addUnits(event, tile, game, player.getColor(), "mech " + uH.replace("space", ""));
        var parsedUnit = new ParsedUnit(key, 1, uH);
        RemoveUnitService.removeUnit(event, tile, game, parsedUnit);

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
                    String planet2 = planetUnit2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                    if (!player.getPlanetsAllianceMode().contains(planet2) && !planetRepresentation2.contains("Mecatol")
                        && (planetUnit2.getUnits() == null || planetUnit2.getUnits().isEmpty())
                        && !planetsChecked.contains(planet2)) {
                        buttons.add(Buttons.green(finChecker + "peaceAccords_" + planet2, planetRepresentation2, FactionEmojis.Xxcha));
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
            for (String pos2 : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
                Tile tile2 = game.getTileByPosition(pos2);
                for (Planet planetUnit2 : tile2.getPlanetUnitHolders()) {
                    String planet2 = planetUnit2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                    if (!planetsChecked.contains(planet2)) {
                        buttons.add(Buttons.green(finChecker + "contagion_" + planet2, planetRepresentation2, FactionEmojis.Xxcha));
                        planetsChecked.add(planet2);
                    }
                }
            }
            for (Planet planetUnit2 : tile.getPlanetUnitHolders()) {
                String planet2 = planetUnit2.getName();
                String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                if (!planetsChecked.contains(planet2)) {
                    buttons.add(Buttons.green(finChecker + "contagion_" + planet2, planetRepresentation2, FactionEmojis.Xxcha));
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
            + " is resolving **Moult** (after winning the space combat) to build 1 ship, reducing the cost by 1 for each of their non-fighter ships destroyed in the combat.";
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
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + ", you only have "
                + player.getTg() + " trade goods and thus can't use **Munitions Reserve**.");
            return;
        }
        String msg = player.getFactionEmoji() + " spent 2 trade goods " + player.gainTG(-2) + " to use **Munitions Reserves**."
            + "\nTheir next roll will automatically reroll misses. If they wish to instead reroll hits as a part of a deal, they should just ignore the rerolls.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        game.setStoredValue("munitionsReserves", player.getFaction());
    }

    @ButtonHandler("contagion_")
    public static void lastStepOfContagion(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String amount = "1";
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));

        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " inf " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " used their **Contagion** ability to land " + amount
                + " infantry on " + Helper.getPlanetRepresentation(planet, game) + ".");
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName());
        if (players.size() > 1) {
            StartCombatService.startGroundCombat(players.get(0), players.get(1), game, event, unitHolder, tile);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("peaceAccords_")
    public static void resolvePeaceAccords(String buttonID, Player player, Game game, ButtonInteractionEvent event) {
        String planetID = buttonID.split("_")[1];
        if ("lockedmallice".equalsIgnoreCase(planetID)) {
            planetID = "mallice";
            Tile tile = game.getTileFromPlanet("lockedmallice");
            FlipTileService.flipTileIfNeeded(event, tile, game);
        } else if ("hexlockedmallice".equalsIgnoreCase(planetID)) {
            planetID = "hexmallice";
            Tile tile = game.getTileFromPlanet("hexlockedmallice");
            FlipTileService.flipTileIfNeeded(event, tile, game);
        }
        AddPlanetService.addPlanet(player, planetID, game, event, false);
        String planetRep = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetID, game);
        String msg = player.getFactionEmojiOrColor() + " claimed the planet " + planetRep + " using the " + FactionEmojis.Xxcha + "**Peace Accords** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("initialIndoctrination_")
    public static void resolveInitialIndoctrinationQuestion(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        List<Button> options = new ArrayList<>();
        options.add(Buttons.green("indoctrinate_" + planet + "_infantry", "Indoctrinate 1 Infantry into 1 Infantry").withEmoji(UnitEmojis.infantry.asEmoji()));
        if (player.hasUnit("yin_mech")) {
            options.add(Buttons.green("indoctrinate_" + planet + "_mech", "Indoctrinate 1 Infantry into 1 Mech").withEmoji(UnitEmojis.mech.asEmoji()));
        }
        options.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " use buttons to resolve **Indoctrination**.", options);
    }

    @ButtonHandler("indoctrinate_")
    public static void resolveFollowUpIndoctrinationQuestion(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit + " " + planet);
        String opponent = "their opponent's";
        for (Player p2 : game.getPlayers().values()) {
            if (p2.getColor() == null || p2 == player || player.getAllianceMembers().contains(p2.getFaction())) {
                continue; // fix indoctrinate vs neutral
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet) && !player.getAllianceMembers().contains(p2.getFaction())) {
                RemoveUnitService.removeUnits(event, tile, game, p2.getColor(), "1 infantry " + planet);
                if (p2.getUnitsOwned().contains("pharadn_infantry") || p2.getUnitsOwned().contains("pharadn_infantry2")) {
                    ButtonHelper.resolveInfantryDeath(p2, 1);
                }
                opponent = p2.getRepresentationNoPing();
                break;
            }
        }
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        CommanderUnlockCheckService.checkPlayer(player, "yin");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getFactionEmoji() + " replaced 1 of their opponent's infantry with 1 " + unit + " on "
                    + Helper.getPlanetRepresentation(planet, game) + " using **Indoctrination**.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getRepresentationNoPing() + " replaced 1 of " + opponent + " infantry with 1 " + unit + " on "
                    + Helper.getPlanetRepresentation(planet, game) + " using **Indoctrination**.");
        }
        options.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + ", please pay for **Indoctrination**.", options);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("distant_suns_")
    public static void distantSuns(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String bID = buttonID.replace("distant_suns_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = player.getFactionEmoji() + " declined to use their **Distant Suns** ability.";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            ExploreService.explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, game, 1, false);
        } else {
            ExploreService.explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2],
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
            message = player.getFactionEmoji() + " declined to use their **Deep Mining** ability.";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            ExploreService.explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, game, 1, false);
        } else {
            message = player.getFactionEmoji() + " used their **Deep Mining** ability to gain 1 trade good " + player.gainTG(1) + ".";
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("augersPeak_")
    public static void handleAugursPeak(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if ("1".equalsIgnoreCase(buttonID.split("_")[1])) {
            ObjectiveHelper.secondHalfOfPeakStage1(game, player, 1);
        } else {
            ObjectiveHelper.secondHalfOfPeakStage2(game, player, 1);
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
