package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.combat.StartCombat;
import ti4.commands.ds.TrapReveal;
import ti4.commands.ds.TrapToken;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.explore.ExploreAndDiscard;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.player.ClearDebt;
import ti4.commands.player.TurnStart;
import ti4.commands.special.SleeperToken;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

public class ButtonHelperAbilities {

    public static void autoneticMemoryStep1(Game activeGame, Player player, int count) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("autoneticMemoryStep2_" + count, "Use Autonetic Memory"));
        buttons.add(Button.danger("autoneticMemoryDecline_" + count, "Decline"));
        String msg = player.getRepresentation(true, true)
            + " you have the ability to draw 1 less action card and utilize your autonetic memory ability. Please use or decline to use.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void startFacsimile(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        String msg = player.getRepresentation() + " choose the opponent ship you wish to build using influence";
        if (player.getPromissoryNotes().containsKey("dspnmort")
            && !player.getPromissoryNotesOwned().contains("dspnmort")) {
            ButtonHelper.resolvePNPlay("dspnmort", player, activeGame, event);
        }
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if ((player.unitBelongsToPlayer(unitEntry.getKey())))
                continue;
            UnitKey unitKey = unitEntry.getKey();
            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
            // System.out.println(unitKey.asyncID());
            int totalUnits = unitEntry.getValue();
            EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
            if (totalUnits > 0) {
                String buttonID2 = "facsimileStep2_" + unitName;
                String buttonText = unitKey.unitName();
                Button validTile2 = Button.danger(buttonID2, buttonText);
                validTile2 = validTile2.withEmoji(emoji);
                buttons.add(validTile2);
            }
        }
        if (event.getMessageChannel().getName().contains("cards-info")) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveFacsimileStep2(Game activeGame, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String unit = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " choose to produce a " + unit
            + " with facsimile ability or morpheus promissory note, and will now spend influence to build it.";
        event.getMessage().delete().queue();
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        new AddUnits().unitParsing(event, player.getColor(), tile, unit, activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        String message2 = player.getRepresentation() + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "inf");
        Button DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);

    }

    public static void startRallyToTheCause(Game activeGame, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            "Choose the tile to produce up to 2 ships in", getTilesToRallyToTheCause(activeGame, player));
    }

    public static void rallyToTheCauseStep2(Game activeGame, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        String type = "sling";
        buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos), type,
            "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to produce the first of potentially 2 ships. "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
        String message2 = player.getRepresentation() + " Use the buttons to produce the second of potentially 2 ships. "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getTilesToRallyToTheCause(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, activeGame) || tile.isHomeSystem()
                || ButtonHelper.isTileLegendary(tile, activeGame) || "18".equals(tile.getTileID())) {
                continue;
            }
            buttons.add(Button.success("rallyToTheCauseStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(activeGame, player)));

        }
        return buttons;
    }

    public static void mercenariesStep1(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos2);
        List<Button> buttons = new ArrayList<>();
        for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tile.getPosition(), player, false)) {
            Tile tile2 = activeGame.getTileByPosition(pos);
            UnitHolder unitHolder = tile2.getUnitHolders().get(Constants.SPACE);
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                buttons.add(Button.success("mercenariesStep2_" + pos2 + "_" + pos,
                    tile2.getRepresentationForButtons(activeGame, player)));
            }
        }
        String msg = player.getRepresentation() + " choose the tile you wish to pull fighters from";
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    public static void mercenariesStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos2);
        String pos = buttonID.split("_")[2];
        Tile tile2 = activeGame.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("mercenariesStep3_" + pos2 + "_" + pos + "_1", "1 fighter"));
        if (tile2.getUnitHolders().get("space").getUnitCount(UnitType.Fighter, player.getColor()) > 1) {
            buttons.add(Button.success("mercenariesStep3_" + pos2 + "_" + pos + "_2", "2 fighters"));
        }
        String msg = player.getRepresentation() + " choose whether to pull 1 or 2 fighters";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    public static void mercenariesStep3(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos2);
        String pos = buttonID.split("_")[2];
        Tile tile2 = activeGame.getTileByPosition(pos);
        String fighters = buttonID.split("_")[3];
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                if (activeGame.isFoWMode()) {
                    buttons.add(Button.success(
                        "mercenariesStep4_" + pos2 + "_" + pos + "_" + fighters + "_" + p2.getFaction(),
                        StringUtils.capitalize(p2.getColor())));
                } else {
                    buttons.add(Button.success(
                        "mercenariesStep4_" + pos2 + "_" + pos + "_" + fighters + "_" + p2.getFaction(),
                        p2.getFactionModel().getFactionName()));
                }
            }
        }
        String msg = player.getRepresentation() + " choose which player to give the fighters too";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    public static void mercenariesStep4(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos2);
        String pos = buttonID.split("_")[2];
        Tile tile2 = activeGame.getTileByPosition(pos);
        String fighters = buttonID.split("_")[3];
        String faction = buttonID.split("_")[4];
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        new RemoveUnits().unitParsing(event, player.getColor(), tile2, fighters + " fighters", activeGame);
        new AddUnits().unitParsing(event, p2.getColor(), tile, fighters + " fighters", activeGame);
        String msg = player.getRepresentation() + " used the mercenaries ability and transferred " + fighters
            + " fighter(s) from " + tile2.getRepresentationForButtons(activeGame, player) + " to "
            + tile.getRepresentationForButtons(activeGame, player) + " and gave them to "
            + ButtonHelper.getIdentOrColor(p2, activeGame);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    public static void bindingDebtRes(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        Player vaden = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);

        vaden.setTg(vaden.getTg() + 1);
        pillageCheck(vaden, activeGame);
        player.setTg(player.getTg() - 1);
        int amount = Math.min(2, vaden.getDebtTokenCount(player.getColor()));
        ClearDebt.clearDebt(vaden, player, amount);
        String msg = ButtonHelper.getIdentOrColor(player, activeGame) + " paid 1tg to "
            + ButtonHelper.getIdentOrColor(vaden, activeGame)
            + "to get 2 debt tokens cleared via the binding debts ability";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(vaden, activeGame), msg);
        }
    }

    public static void autoneticMemoryDecline(Game activeGame, Player player, ButtonInteractionEvent event,
        String buttonID) {
        event.getMessage().delete().queue();
        int count = Integer.parseInt(buttonID.split("_")[1]);
        activeGame.drawActionCard(player.getUserID(), count);
        ACInfo.sendActionCardInfo(activeGame, player, event);
        ButtonHelper.checkACLimit(activeGame, event, player);
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

    public static void resolveGrace(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {
        String msg = ButtonHelper.getIdent(player) + " is resolving the grace ability";
        int scPlayed = Integer.parseInt(buttonID.split("_")[1]);
        if (!player.hasAbility("grace")) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                "To " + ButtonHelper.getIdent(player) + ": This button aint for you ");
            return;
        }
        player.addExhaustedAbility("grace");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true)
                + " use buttons to resolve grace, reminder you have to spend a strat CC if applicable, and that you can only do one of these.",
            getGraceButtons(activeGame, player, scPlayed));
    }

    public static List<Button> getGraceButtons(Game activeGame, Player edyn, int scPlayed) {
        List<Button> scButtons = new ArrayList<>();
        scButtons.add(Button.secondary("spendAStratCC", "Spend a Strategy CC"));
        if (scPlayed > 1 && (activeGame.getScPlayed().get(1) == null || !activeGame.getScPlayed().get(1))) {
            scButtons.add(Button.success("leadershipGenerateCCButtons", "Spend & Gain CCs"));
            // scButtons.add(Button.danger("leadershipExhaust", "Exhaust Planets"));
        }
        if (scPlayed > 2 && (activeGame.getScPlayed().get(2) == null || !activeGame.getScPlayed().get(2))) {
            scButtons.add(Button.success("diploRefresh2", "Ready 2 Planets"));
        }
        if (scPlayed > 3 && (activeGame.getScPlayed().get(3) == null || !activeGame.getScPlayed().get(3))) {
            scButtons.add(Button.secondary("sc_ac_draw", "Draw 2 Action Cards")
                .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
        }
        if (scPlayed > 4 && (activeGame.getScPlayed().get(4) == null || !activeGame.getScPlayed().get(4))) {
            scButtons.add(
                Button.success("construction_spacedock", "Place A SD").withEmoji(Emoji.fromFormatted(Emojis.spacedock)));
            scButtons.add(Button.success("construction_pds", "Place a PDS").withEmoji(Emoji.fromFormatted(Emojis.pds)));
        }
        if (scPlayed > 5 && (activeGame.getScPlayed().get(5) == null || !activeGame.getScPlayed().get(5))) {
            scButtons.add(Button.secondary("sc_refresh", "Replenish Commodities")
                .withEmoji(Emoji.fromFormatted(Emojis.comm)));
        }
        if (scPlayed > 6 && (activeGame.getScPlayed().get(6) == null || !activeGame.getScPlayed().get(6))) {
            scButtons.add(Button.success("warfareBuild", "Build At Home"));
        }
        if (scPlayed > 7 && (activeGame.getScPlayed().get(7) == null || !activeGame.getScPlayed().get(7))) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (scPlayed > 8 && (activeGame.getScPlayed().get(8) == null || !activeGame.getScPlayed().get(8))) {
            scButtons.add(Button.secondary("non_sc_draw_so", "Draw Secret Objective")
                .withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
        }
        scButtons.add(Button.danger("deleteButtons", "Done resolving"));

        return scButtons;
    }

    public static void setTrapStep1(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("setTrapStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("setTrapStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentation(true, true) + " tell the bot who's planet you want to put a trap on", buttons);
    }

    public static void setTrapStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Button.secondary("setTrapStep3_" + planet, Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentation(true, true) + " select the planet you want to put a trap on", buttons);
    }

    public static void setTrapStep3(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : getUnusedTraps(activeGame, player)) {
            availableTraps.add(Button.success("setTrapStep4_" + planet + "_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentation(true, true) + " choose the trap you want to set on the planet "
            + Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    public static void removeTrapStep1(Game activeGame, Player player) {
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : player.getTrapCardsPlanets().keySet()) {
            availableTrap = translateNameIntoTrapIDOrReverse(availableTrap);
            availableTraps.add(Button.success("removeTrapStep2_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentation(true, true) + " choose the trap you want to remove";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    public static void revealTrapStep1(Game activeGame, Player player) {
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : player.getTrapCardsPlanets().keySet()) {
            availableTrap = translateNameIntoTrapIDOrReverse(availableTrap);
            availableTraps.add(Button.success("revealTrapStep2_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentation(true, true) + " choose the trap you want to reveal";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    public static void revealTrapStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String trap = buttonID.split("_")[1];
        trap = translateNameIntoTrapIDOrReverse(trap);
        String planet = player.getTrapCardsPlanets().get(trap);
        new TrapReveal().revealTrapForPlanet(event, activeGame, planet, trap, player, true);
        event.getMessage().delete().queue();
    }

    public static void removeTrapStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String trap = buttonID.split("_")[1];
        trap = translateNameIntoTrapIDOrReverse(trap);
        String planet = player.getTrapCardsPlanets().get(trap);
        new TrapReveal().revealTrapForPlanet(event, activeGame, planet, trap, player, false);
        event.getMessage().delete().queue();
    }

    public static void setTrapStep4(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String trap = translateNameIntoTrapIDOrReverse(buttonID.split("_")[2]);
        event.getMessage().delete().queue();
        new TrapToken().setTrapForPlanet(event, activeGame, planet, trap, player);
    }

    public static List<String> getPlanetsWithTraps(Game activeGame) {
        List<String> trappedPlanets = new ArrayList<>();
        for (String trapName : getTrapNames()) {
            if (!activeGame.getStoredValue(trapName).isEmpty()) {
                trappedPlanets.add(activeGame.getStoredValue(trapName));
            }
        }
        return trappedPlanets;
    }

    public static List<String> getUnusedTraps(Game activeGame, Player player) {
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

    public static void addATrapToken(Game activeGame, String planetName) {
        Tile tile = activeGame.getTileFromPlanet(planetName);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
        boolean addedYet = false;
        for (int x = 1; x < 7 && !addedYet; x++) {
            String tokenName = "attachment_lizhotrap" + x + ".png";
            if (!uH.getTokenList().contains(tokenName)) {
                addedYet = true;
                tile.addToken(tokenName, uH.getName());
            }
        }
    }

    public static void removeATrapToken(Game activeGame, String planetName) {
        Tile tile = activeGame.getTileFromPlanet(planetName);
        if (tile == null) {
            return;
        }
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
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

    public static void autoneticMemoryStep2(Game activeGame, Player player, ButtonInteractionEvent event,
        String buttonID) {
        event.getMessage().delete().queue();
        int count = Integer.parseInt(buttonID.split("_")[1]);
        activeGame.drawActionCard(player.getUserID(), count - 1);
        ACInfo.sendActionCardInfo(activeGame, player, event);
        String msg2 = ButtonHelper.getIdent(player) + " is choosing to resolve their Autonetic Memory ability";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("autoneticMemoryStep3a", "Pick A Card From the Discard"));
        buttons.add(Button.primary("autoneticMemoryStep3b", "Drop an infantry"));
        String msg = player.getRepresentation(true, true)
            + " you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void autoneticMemoryStep3b(Game activeGame, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, "gf", "placeOneNDone_skipbuild");
        String message = player.getRepresentation(true, true) + " Use buttons to drop 1 infantry on a planet";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
            buttons);
    }

    public static void autoneticMemoryStep3a(Game activeGame, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        ButtonHelper.pickACardFromDiscardStep1(activeGame, player);
    }

    public static void addOmenDie(Game activeGame, int omenDie) {
        String omenDice;
        if (!activeGame.getStoredValue("OmenDice").isEmpty()) {
            omenDice = activeGame.getStoredValue("OmenDice");
            omenDice = omenDice + "_" + omenDie;
            activeGame.setStoredValue("OmenDice", "" + omenDice);
        } else {
            activeGame.setStoredValue("OmenDice", "" + omenDie);
        }
    }

    public static void removeOmenDie(Game activeGame, int omenDie) {
        String omenDice;
        if (!activeGame.getStoredValue("OmenDice").isEmpty()) {
            omenDice = activeGame.getStoredValue("OmenDice");
            omenDice = omenDice.replaceFirst("" + omenDie, "");
            omenDice = omenDice.replace("__", "_");
            activeGame.setStoredValue("OmenDice", "" + omenDice);
        }
    }

    public static List<Integer> getAllOmenDie(Game activeGame) {
        List<Integer> dice = new ArrayList<>();
        for (String dieResult : activeGame.getStoredValue("OmenDice").split("_")) {
            if (!dieResult.isEmpty() && !dieResult.contains("_")) {
                int die = Integer.parseInt(dieResult);
                dice.add(die);
            }
        }
        return dice;
    }

    public static void offerOmenDiceButtons(Game activeGame, Player player) {
        String msg = player.getRepresentation(true, true)
            + " you can play an omen die with the following buttons. Duplicate dice are not shown.";
        List<Button> buttons = new ArrayList<>();
        List<Integer> dice = new ArrayList<>();
        for (int die : getAllOmenDie(activeGame)) {
            if (!dice.contains(die)) {
                buttons.add(Button.success("useOmenDie_" + die, "Use Result: " + die));
                dice.add(die);
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete these"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void useOmenDie(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {

        int die = Integer.parseInt(buttonID.split("_")[1]);
        removeOmenDie(activeGame, die);
        String msg = player.getRepresentation(true, true) + " used an omen die with the number " + die;
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }

    public static void rollOmenDiceAtStartOfStrat(Game activeGame, Player myko) {
        activeGame.setStoredValue("OmenDice", "");
        StringBuilder msg = new StringBuilder(
            myko.getRepresentation(true, true) + " rolled 4 omen dice and rolled the following numbers: ");
        for (int x = 0; x < 4; x++) {
            Die d1 = new Die(6);
            msg.append(d1.getResult()).append(" ");
            addOmenDie(activeGame, d1.getResult());
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(myko, activeGame), msg.toString());
    }

    public static void pillage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident, String finsFactionCheckerPrefix) {
        buttonID = buttonID.replace("pillage_", "");
        String colorPlayer = buttonID.split("_")[0];
        String checkedStatus = buttonID.split("_")[1];
        Player pillaged = activeGame.getPlayerFromColorOrFaction(colorPlayer);
        if (pillaged == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                "Could not find player, please resolve manually.");
            return;
        }
        if (checkedStatus.contains("unchecked")) {
            List<Button> buttons = new ArrayList<>();
            String message2 = "Please confirm this is a valid pillage opportunity and that you wish to pillage.";
            buttons.add(Button.danger(finsFactionCheckerPrefix + "pillage_" + pillaged.getColor() + "_checked",
                "Pillage a TG"));
            if (pillaged.getCommodities() > 0) {
                buttons.add(Button.danger(finsFactionCheckerPrefix + "pillage_" + pillaged.getColor() + "_checkedcomm",
                    "Pillage a Commodity"));
            }
            buttons.add(Button.success(finsFactionCheckerPrefix + "deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        } else {
            MessageChannel channel1 = ButtonHelper.getCorrectChannel(pillaged, activeGame);
            MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeGame);
            String pillagerMessage = player.getRepresentation(true, true) + " you pillaged, your tgs have gone from "
                + player.getTg() + " to "
                + (player.getTg() + 1) + ".";
            String pillagedMessage = pillaged.getRepresentation(true, true) + " you have been pillaged";

            if (pillaged.getCommodities() > 0 && checkedStatus.contains("checkedcomm")) {
                pillagedMessage = pillagedMessage + ", your comms have gone from " + pillaged.getCommodities() + " to "
                    + (pillaged.getCommodities() - 1) + ".";
                pillaged.setCommodities(pillaged.getCommodities() - 1);
            } else {
                pillagedMessage = pillagedMessage + ", your tgs have gone from " + pillaged.getTg() + " to "
                    + (pillaged.getTg() - 1) + ".";
                pillaged.setTg(pillaged.getTg() - 1);
            }
            player.setTg(player.getTg() + 1);
            MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
            MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
            if (player.hasUnexhaustedLeader("mentakagent")) {
                List<Button> buttons = new ArrayList<>();
                Button winnuButton = Button
                    .success(
                        "FFCC_" + player.getFaction() + "_" + "exhaustAgent_mentakagent_"
                            + pillaged.getFaction(),
                        "Use Mentak Agent")
                    .withEmoji(Emoji.fromFormatted(Emojis.Mentak));
                buttons.add(winnuButton);
                buttons.add(Button.danger("deleteButtons", "Done"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, "Wanna use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Suffi An (Mentak Agent)?", buttons);
            }
            for (Player p2 : activeGame.getRealPlayers()) {
                if (p2 != pillaged && p2 != player && p2.hasUnexhaustedLeader("yssarilagent")
                    && player.hasLeader("mentakagent")) {
                    List<Button> buttons = new ArrayList<>();
                    Button winnuButton = Button
                        .success(
                            "FFCC_" + p2.getFaction() + "_" + "exhaustAgent_mentakagent_"
                                + pillaged.getFaction(),
                            "Use Mentak Agent")
                        .withEmoji(Emoji.fromFormatted(Emojis.Mentak));
                    buttons.add(winnuButton);
                    buttons.add(Button.danger("deleteButtons", "Done"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame),
                        p2.getRepresentation() + "Wanna use " + (p2.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Suffi An (Mentak Agent)?", buttons);
                }
            }
        }
        event.getMessage().delete().queue();
    }

    public static List<Button> getMitosisOptions(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("mitosisInf", "Place an Infantry"));
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "mech") < 4) {
            buttons.add(Button.primary("mitosisMech", "Replace an Infantry With a Mech (DEPLOY)"));
        }
        return buttons;
    }

    public static void resolveGetDiplomatButtons(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player) {
        ButtonHelper.deleteTheOneButton(event);
        String message = player.getRepresentation() + " select the planet you would like to exhaust";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
            getDiplomatButtons(activeGame, player));
    }

    public static List<Button> getDiplomatButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getTokenList().contains("token_freepeople.png")) {
                    buttons.add(Button.success("exhaustViaDiplomats_" + unitHolder.getName(),
                        Helper.getPlanetRepresentation(unitHolder.getName(), activeGame)));
                }
            }
        }
        return buttons;
    }

    public static void resolveFreePeopleAbility(Game activeGame) {
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet) {
                    String planet = unitHolder.getName();
                    boolean alreadyOwned = false;
                    for (Player player_ : activeGame.getPlayers().values()) {
                        if (player_.getPlanets().contains(planet)) {
                            alreadyOwned = true;
                            break;
                        }
                    }
                    if (!alreadyOwned && !"mr".equalsIgnoreCase(planet)) {
                        unitHolder.addToken("token_freepeople.png");
                    }
                }

            }
        }
    }

    public static void resolveDiplomatExhaust(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player) {
        String planet = buttonID.split("_")[1];
        String message = ButtonHelper.getIdent(player) + " exhausted the unowned planet "
            + Helper.getPlanetRepresentation(planet, activeGame) + " using the diplomats ability";
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
        unitHolder.removeToken("token_freepeople.png");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
        event.getMessage().delete().queue();

    }

    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        List<Button> buttons = new ArrayList<>(
            Helper.getPlanetPlaceUnitButtons(player, activeGame, "infantry", "placeOneNDone_skipbuild"));
        String message = player.getRepresentation(true, true) + " Use buttons to put 1 infantry on a planet";

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
            buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident, String finChecker) {
        List<Button> buttons = new ArrayList<>(getPlanetPlaceUnitButtonsForMechMitosis(player, activeGame, finChecker));
        String message = player.getRepresentation(true, true) + " Use buttons to replace 1 infantry with a mech";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
            buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game activeGame,
        String finChecker) {
        List<Button> planetButtons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                String colorID = Mapper.getColorID(player.getColor());
                int numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);

                if (numInf > 0) {
                    String buttonID = finChecker + "mitoMechPlacement_" + tile.getPosition() + "_"
                        + unitHolder.getName();
                    if ("space".equalsIgnoreCase(unitHolder.getName())) {
                        planetButtons.add(Button.success(buttonID,
                            "Space Area of " + tile.getRepresentationForButtons(activeGame, player)));
                    } else {
                        planetButtons.add(Button.success(buttonID,
                            Helper.getPlanetRepresentation(unitHolder.getName(), activeGame)));
                    }
                }
            }
        }
        return planetButtons;
    }

    public static void putSleeperOn(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        buttonID = buttonID.replace("putSleeperOnPlanet_", "");
        String planet = buttonID;
        String message = ident + " put a sleeper on " + Helper.getPlanetRepresentation(planet, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planet, player);
        event.getMessage().delete().queue();
    }

    public static void oribtalDropFollowUp(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player, String ident) {
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            event.getMessage().getContentRaw());
        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success("dropAMechToo", "Spend 3 resource to Drop a Mech Too");
        startButtons.add(tacticalAction);
        Button componentAction = Button.danger("finishComponentAction_spitItOut", "Decline Mech");
        startButtons.add(componentAction);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            "Decide whether to drop mech",
            startButtons);
        event.getMessage().delete().queue();
    }

    public static void oribtalDropExhaust(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            event.getMessage().getContentRaw());
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "res");
        Button DoneExhausting = Button.danger("finishComponentAction_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            "Use Buttons to Pay For The Mech", buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getMantleCrackingButtons(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        int coreCount = 0;
        for (String planetName : player.getPlanetsAllianceMode()) {
            if (planetName.contains("custodia") || planetName.contains("ghoti")) {
                continue;
            }
            Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
            if (planet == null) {
                continue;
            }
            Tile tile = activeGame.getTileFromPlanet(planetName);
            if (tile == null) {
                continue;
            }
            if (!planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG) && !"mr".equalsIgnoreCase(planetName)
                && !tile.isHomeSystem()) {
                buttons.add(Button.secondary("mantleCrack_" + planetName,
                    Helper.getPlanetRepresentation(planetName, activeGame)));
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

    public static void resolveAxisOrderExhaust(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        String order = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " Chose to Use " + Mapper.getRelic(order).getName());
        List<Button> buttons = new ArrayList<>();
        String message = "";
        String techName = "";
        boolean hasTech = false;
        if (Mapper.getRelic(order).getName().contains("Dreadnought")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "dreadnought",
                "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 dreadnought in a system with your ships and CC";
            techName = "dn2";
        }
        if (Mapper.getRelic(order).getName().contains("Carrier")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "carrier",
                "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 carrier in a system with your ships and CC";
            techName = "cv2";
        }
        if (Mapper.getRelic(order).getName().contains("Cruiser")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "cruiser",
                "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 cruiser in a system with your ships and CC";
            techName = "cr2";
        }
        if (Mapper.getRelic(order).getName().contains("Destroyer")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "2destroyer",
                "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 2 destroyers in a system with your ships and CC";
            techName = "dd2";
        }
        message = player.getRepresentation(true, true) + " " + message;
        ButtonHelper.deleteTheOneButton(event);
        player.addExhaustedRelic(order);
        for (Player p2 : activeGame.getRealPlayers()) {
            if (activeGame.playerHasLeaderUnlockedOrAlliance(p2, "axiscommander") && !p2.hasTech(techName)) {
                List<Button> buttons2 = new ArrayList<>();
                buttons2.add(Buttons.GET_A_TECH);
                buttons2.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), p2
                    .getRepresentation(true, true)
                    + " a player has resolved an Axis Order (" + Mapper.getRelic(order).getName()
                    + ") and you can use the button to gain the corresponding unit upgrade tech if you pay 6r",
                    buttons2);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
            buttons);
    }

    public static int getNumberOfDifferentAxisOrdersBought(Player player, Game activeGame) {
        int num = 0;
        String relicName = "axisorderdd";
        String extra = "duplicate";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)
            || ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName + extra)) {
            num++;
        }
        relicName = "axisordercr";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)
            || ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName + extra)) {
            num++;
        }
        relicName = "axisordercv";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)
            || ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName + extra)) {
            num++;
        }
        relicName = "axisorderdn";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)
            || ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName + extra)) {
            num++;
        }
        return num;
    }

    public static List<Button> getBuyableAxisOrders(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        int maxCost = player.getCommodities();

        String relicName = "axisorderdd";
        String extra = "duplicate";
        int orderCost = 1;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisordercr";
        orderCost = 1;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisordercv";
        orderCost = 2;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisorderdn";
        orderCost = 3;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost,
                    "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete these buttons"));

        return buttons;
    }

    public static void resolveAxisOrderBuy(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        String relicName = buttonID.split("_")[1];
        String cost = buttonID.split("_")[2];
        int lostComms = Integer.parseInt(cost);
        int oldComms = player.getCommodities();
        if (lostComms > oldComms) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you don't have that many comms");
            return;
        }
        player.addRelic(relicName);
        player.setCommodities(oldComms - lostComms);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " acquired " + Mapper.getRelic(relicName).getName()
                + " and paid " + lostComms + " commodities (" + oldComms + "->" + player.getCommodities()
                + ")");
        if (player.getLeaderIDs().contains("axiscommander") && !player.hasLeaderUnlocked("axiscommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "axis", event);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    public static List<Button> offerOlradinConnectButtons(Player player, Game activeGame, String planetName) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.equalsIgnoreCase(planetName)) {
                continue;
            }
            buttons.add(Button.success("olradinConnectStep2_" + planetName + "_" + planet,
                Helper.getPlanetRepresentation(planet, activeGame)));
        }
        buttons.add(Button.danger("deleteButtons", "Decline"));
        return buttons;
    }

    public static void resolveOlradinConnectStep2(Player player, Game activeGame, String buttonID,
        ButtonInteractionEvent event) {
        String planet1 = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        player.setHasUsedPeopleConnectAbility(true);
        new RemoveUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planet1), "inf " + planet1,
            activeGame);
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planet2), "inf " + planet2,
            activeGame);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmoji() + " moved 1 infantry from "
                + Helper.getPlanetRepresentation(planet1, activeGame) + " to "
                + Helper.getPlanetRepresentation(planet2, activeGame) + " using their connect policy");
        event.getMessage().delete().queue();
    }

    public static void resolveOlradinPreserveStep2(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player) {
        String type = buttonID.split("_")[1];
        player.setHasUsedEnvironmentPreserveAbility(true);
        StringBuilder sb = new StringBuilder();
        String cardID = activeGame.drawExplore(type);
        sb.append(new ExploreAndDiscard().displayExplore(cardID)).append(System.lineSeparator());
        ExploreModel card = Mapper.getExplore(cardID);
        String cardType = card.getResolution();
        if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
            sb.append(player.getRepresentation(true, true)).append(" Gained relic fragment\n");
            player.addFragment(cardID);
            activeGame.purgeExplore(cardID);
        }

        if (player.getLeaderIDs().contains("kollecccommander") && !player.hasLeaderUnlocked("kollecccommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "kollecc", event);
        }
        MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        event.getMessage().delete().queue();
    }

    public static List<Button> getOlradinPreserveButtons(Game activeGame, Player player, String planet) {
        List<Button> buttons = new ArrayList<>();
        Set<String> types = ButtonHelper.getTypeOfPlanet(activeGame, planet);
        for (String type : types) {
            if ("industrial".equals(type)) {
                buttons.add(Button.success("olradinPreserveStep2_industrial", "Explore Industrial"));
            }
            if ("cultural".equals(type)) {
                buttons.add(Button.primary("olradinPreserveStep2_cultural", "Explore Cultural"));
            }
            if ("hazardous".equals(type)) {
                buttons.add(Button.danger("olradinPreserveStep2_hazardous", "Explore Hazardous"));
            }
        }
        return buttons;
    }

    public static void offerOrladinPlunderButtons(Player player, Game activeGame, String planet) {
        if (!player.getHasUsedEnvironmentPlunderAbility() && player.hasAbility("policy_the_environment_plunder")
            && ButtonHelper.getTypeOfPlanet(activeGame, planet).contains("hazardous")) {
            UnitHolder planetUnit = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            Planet planetReal = (Planet) planetUnit;
            List<Button> buttons = new ArrayList<>();
            if (planetReal.getOriginalPlanetType() != null && player.getPlanetsAllianceMode().contains(planet)
                && FoWHelper.playerHasUnitsOnPlanet(player, activeGame.getTileFromPlanet(planet), planet)) {
                List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(activeGame, planetReal, player);

                String msg = player.getRepresentation() + " Due to your exhausting of "
                    + Helper.getPlanetRepresentation(planet, activeGame)
                    + " you can resolve the following ability: **The Environment - Plunder (-)**: Once per action, after you explore a hazardous planet, you may remove 1 unit from that planet to explore that planet.";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                Button remove = Button.danger("getDamageButtons_" + activeGame.getTileFromPlanet(planet).getPosition() + "_remove",
                    "Remove units in "
                        + activeGame.getTileFromPlanet(planet).getRepresentationForButtons(activeGame, player));
                buttons.add(remove);
                buttons.add(Button.danger("deleteButtons", "Decline"));
                planetButtons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Resolve remove",
                    buttons);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Resolve explore", planetButtons);
                player.setHasUsedEnvironmentPlunderAbility(true);
            }
        }
    }

    public static boolean canBePillaged(Player player, Game activeGame, int tg) {
        if (player.getPromissoryNotesInPlayArea().contains("pop")) {
            return false;
        }
        if (Helper.getPlayerFromAbility(activeGame, "pillage") != null && !Helper
            .getPlayerFromAbility(activeGame, "pillage").getFaction().equalsIgnoreCase(player.getFaction())) {
            Player pillager = Helper.getPlayerFromAbility(activeGame, "pillage");
            if (tg > 2 && player.getNeighbouringPlayers().contains(pillager)) {
                return true;
            }
        }
        return false;
    }

    public static void pillageCheck(Player player, Game activeGame) {
        if (canBePillaged(player, activeGame, player.getTg())) {
            Player pillager = Helper.getPlayerFromAbility(activeGame, "pillage");
            String finChecker = "FFCC_" + pillager.getFaction() + "_";
            List<Button> buttons = new ArrayList<>();
            String playerIdent = StringUtils.capitalize(player.getFaction());
            MessageChannel channel = activeGame.getMainGameChannel();
            if (activeGame.isFoWMode()) {
                playerIdent = StringUtils.capitalize(player.getColor());
                channel = pillager.getPrivateChannel();
            }
            String message = pillager.getRepresentation(true, true) + " you may have the opportunity to pillage "
                + playerIdent
                + ". Please check this is a valid pillage opportunity, and use buttons to resolve.";
            buttons.add(Button.danger(finChecker + "pillage_" + player.getColor() + "_unchecked",
                "Pillage " + playerIdent));
            buttons.add(Button.success(finChecker + "deleteButtons", "Decline Pillage Window"));
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
    }

    public static void mantleCracking(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        int oldTg = player.getTg();
        player.setTg(oldTg + 4);
        Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
        planet.addToken(Constants.GLEDGE_CORE_PNG);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdentOrColor(player, activeGame) + "cracked the mantle of "
                + Helper.getPlanetRepresentation(planetName, activeGame) + " and gained 4tg (" + oldTg + "->"
                + player.getTg() + "). This is technically an optional gain");
        pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 4);
        List<Button> buttons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getButtonsForPossibleTechForNekro(Player nekro, List<String> currentList,
        Game activeGame) {
        List<Button> techToGain = new ArrayList<>();
        for (String tech : currentList) {
            techToGain.add(Button.success("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                Mapper.getTech(tech).getName()));
        }
        return techToGain;
    }

    public static List<String> getPossibleTechForNekroToGainFromPlayer(Player nekro, Player victim,
        List<String> currentList, Game activeGame) {
        List<String> techToGain = new ArrayList<>(currentList);
        for (String tech : victim.getTechs()) {
            if (!nekro.getTechs().contains(tech) && !techToGain.contains(tech) && !"iihq".equalsIgnoreCase(tech)) {
                techToGain.add(tech);
            }
        }
        return techToGain;
    }

    public static void removeSleeper(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
        String planet = buttonID;
        String message = ident + " removed a sleeper from " + planet;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planet, player);
        event.getMessage().delete().queue();
    }

    public static void replaceSleeperWith(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        buttonID = buttonID.replace("replaceSleeperWith_", "");
        String planetName = buttonID.split("_")[1];
        String unit = buttonID.split("_")[0];
        String message;
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planetName, player);
        if ("mech".equalsIgnoreCase(unit)) {
            new AddUnits().unitParsing(event, player.getColor(),
                activeGame.getTile(AliasHandler.resolveTile(planetName)),
                "mech " + planetName + ", inf " + planetName, activeGame);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName, activeGame)
                + " with a " + Emojis.mech + " and "
                + Emojis.infantry;
        } else {
            new AddUnits().unitParsing(event, player.getColor(),
                activeGame.getTile(AliasHandler.resolveTile(planetName)), "pds " + planetName, activeGame);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName, activeGame)
                + " with a " + Emojis.pds;
            if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

    public static void giveKeleresCommsNTg(Game activeGame, GenericInteractionCreateEvent event) {
        for (Player player : activeGame.getRealPlayers()) {
            if (player.hasAbility("divination")) {
                rollOmenDiceAtStartOfStrat(activeGame, player);
            }
            if (!player.hasAbility("council_patronage"))
                continue;
            MessageChannel channel = activeGame.getActionsChannel();
            if (activeGame.isFoWMode()) {
                channel = player.getPrivateChannel();
            }
            player.setTg(player.getTg() + 1);
            player.setCommodities(player.getCommoditiesTotal());
            MessageHelper.sendMessageToChannel(channel,
                player.getRepresentation(true, true) + " your **Council Patronage** ability was triggered. Your "
                    + Emojis.comm
                    + " commodities have been replenished and you have gained 1 "
                    + Emojis.getTGorNomadCoinEmoji(activeGame) + " trade good (" + (player.getTg() - 1) + " -> "
                    + (player.getTg()) + ")");
            pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
            ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
            ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
            if (player.hasAbility("military_industrial_complex")
                && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                MessageHelper.sendMessageToChannelWithButtons(
                    ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                    ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
            }
            if (player.getLeaderIDs().contains("mykomentoricommander")
                && !player.hasLeaderUnlocked("mykomentoricommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
            }
        }
    }

    public static void starforgeTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        String pos = buttonID.replace("starforgeTile_", "");
        List<Button> buttons = new ArrayList<>();
        Button starforgerStroter = Button.danger("starforge_destroyer_" + pos, "Starforge Destroyer")
            .withEmoji(Emoji.fromFormatted(Emojis.destroyer));
        buttons.add(starforgerStroter);
        Button starforgerFighters = Button.danger("starforge_fighters_" + pos, "Starforge 2 Fighters")
            .withEmoji(Emoji.fromFormatted(Emojis.fighter));
        buttons.add(starforgerFighters);
        String message = "Use the buttons to select what you would like to starforge.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void starforge(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player,
        String ident) {
        String unitNPlace = buttonID.replace("starforge_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        String successMessage;
        if (player.getStrategicCC() > 0) {
            successMessage = player.getFactionEmoji() + " Spent 1 strategy token (" + (player.getStrategicCC()) + " -> "                + (player.getStrategicCC() - 1) + ")";
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event, Emojis.Muaat + "Starforge");
        } else {
            player.addExhaustedRelic("emelpar");
            successMessage = "Exhausted Scepter of Emelpar";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        List<Button> buttons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
        if ("destroyer".equals(unit)) {
            new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", activeGame);
            successMessage = "Produced 1 " + Emojis.destroyer + " in tile "
                + tile.getRepresentationForButtons(activeGame, player) + ".";

        } else {
            new AddUnits().unitParsing(event, player.getColor(), tile, "2 ff", activeGame);
            successMessage = "Produced 2 " + Emojis.fighter + " in tile "
                + tile.getRepresentationForButtons(activeGame, player) + ".";
        }
        if (!activeGame.getLaws().containsKey("articles_war")) {
            successMessage = ButtonHelper.putInfWithMechsForStarforge(pos, successMessage, activeGame, player, event);
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveMitosisMechPlacement(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player, String ident) {
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        String uH = buttonID.split("_")[2];
        String successMessage = "";
        if ("space".equalsIgnoreCase(uH)) {
            successMessage = ident + " Replaced an infantry with a mech in the space area of "
                + tile.getRepresentationForButtons(activeGame, player) + ".";
        } else {
            successMessage = ident + " Replaced an infantry with a mech on "
                + Helper.getPlanetRepresentation(uH, activeGame) + ".";
        }
        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), tile, "mech " + uH.replace("space", ""), activeGame);
        new RemoveUnits().removeStuff(event, tile, 1, uH, key, player.getColor(), false, activeGame);

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
        event.getMessage().delete().queue();
    }

    public static List<Button> getXxchaPeaceAccordsButtons(Game activeGame, Player player,
        GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Tile tile = activeGame.getTileFromPlanet(planet);
            for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)) {
                Tile tile2 = activeGame.getTileByPosition(pos2);
                for (UnitHolder planetUnit2 : tile2.getPlanetUnitHolders()) {
                    Planet planetReal2 = (Planet) planetUnit2;
                    String planet2 = planetReal2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeGame);
                    if (!player.getPlanetsAllianceMode().contains(planet2) && !planetRepresentation2.contains("Mecatol")
                        && (planetReal2.getUnits() == null || planetReal2.getUnits().isEmpty())
                        && !planetsChecked.contains(planet2)) {
                        buttons.add(Button.success(finChecker + "peaceAccords_" + planet2, planetRepresentation2)
                            .withEmoji(Emoji.fromFormatted(Emojis.Xxcha)));
                        planetsChecked.add(planet2);
                    }
                }
            }
        }
        return buttons;
    }

    public static List<Button> getKyroContagionButtons(Game activeGame, Player player,
        GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Tile tile = activeGame.getTileFromPlanet(planet);
            for (String pos2 : FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tile.getPosition(), player,
                false)) {
                Tile tile2 = activeGame.getTileByPosition(pos2);
                for (UnitHolder planetUnit2 : tile2.getPlanetUnitHolders()) {
                    Planet planetReal2 = (Planet) planetUnit2;
                    String planet2 = planetReal2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeGame);
                    if (!planetsChecked.contains(planet2)) {
                        buttons.add(Button.success(finChecker + "contagion_" + planet2, planetRepresentation2)
                            .withEmoji(Emoji.fromFormatted(Emojis.Xxcha)));
                        planetsChecked.add(planet2);
                    }
                }
            }
            for (UnitHolder planetUnit2 : tile.getPlanetUnitHolders()) {
                Planet planetReal2 = (Planet) planetUnit2;
                String planet2 = planetReal2.getName();
                String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeGame);
                if (!planetsChecked.contains(planet2)) {
                    buttons.add(Button.success(finChecker + "contagion_" + planet2, planetRepresentation2)
                        .withEmoji(Emoji.fromFormatted(Emojis.Xxcha)));
                    planetsChecked.add(planet2);
                }
            }
        }
        return buttons;
    }

    public static void resolveMoult(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        ButtonHelper.deleteTheOneButton(event);
        String pos = buttonID.split("_")[1];
        String generalMsg = player.getFactionEmoji()
            + " is resolving moult (after winning the space combat) to build 1 ship, reducing the cost by 1 for each of their non-fighter ships destroyed in the combat";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), generalMsg);
        String type = "sling";
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos),
            type,
            "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to produce a ship. "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
            buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void munitionsReserves(ButtonInteractionEvent event, Game activeGame, Player player) {

        if (player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getRepresentation() + " you dont have 2tg, and thus cant use munitions reserve");
            return;
        }
        String msg = player.getFactionEmoji() + " used munitions reserves (tg went from " + player.getTg() + " -> "
            + (player.getTg() - 2)
            + "). Their next roll will automatically reroll misses. If they wish to instead reroll hits as a part of a deal, they should just ignore the rerolls. ";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        player.setTg(player.getTg() - 2);
        activeGame.setStoredValue("munitionsReserves", player.getFaction());
    }

    public static void lastStepOfContagion(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player) {
        String planet = buttonID.split("_")[1];
        String amount = "1";
        TextChannel mainGameChannel = activeGame.getMainGameChannel();
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planet));

        new AddUnits().unitParsing(event, player.getColor(),
            activeGame.getTile(AliasHandler.resolveTile(planet)), amount + " inf " + planet,
            activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation() + " used contagion ability to land " + amount
                + " infantry on " + Helper.getPlanetRepresentation(planet, activeGame));
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        for (Player player2 : activeGame.getRealPlayers()) {
            if (player2 == player) {
                continue;
            }
            String colorID = Mapper.getColorID(player2.getColor());
            int numMechs = 0;
            int numInf = 0;
            if (unitHolder.getUnits() != null) {
                numMechs = unitHolder.getUnitCount(UnitType.Mech, colorID);
                numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);
            }

            if (numInf > 0 || numMechs > 0) {
                String messageCombat = "Resolve ground combat.";
                if (!activeGame.isFoWMode()) {
                    MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(messageCombat);
                    String threadName = activeGame.getName() + "-contagion-" + activeGame.getRound() + "-planet-"
                        + planet
                        + "-" + player.getFaction() + "-vs-" + player2.getFaction();
                    mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
                        ThreadChannelAction threadChannel = mainGameChannel.createThreadChannel(threadName,
                            message_.getId());
                        threadChannel = threadChannel
                            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                        threadChannel.queue(m5 -> {
                            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel(threadChannel_,
                                        player.getRepresentation(true, true)
                                            + player2.getRepresentation(true, true)
                                            + " Please resolve the interaction here.");
                                    int context = 0;
                                    FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame,
                                        context, tile.getPosition(), event);
                                    MessageHelper.sendMessageWithFile(threadChannel_, systemWithContext,
                                        "Picture of system", false);
                                    List<Button> buttons = StartCombat.getGeneralCombatButtons(activeGame,
                                        tile.getPosition(), player, player2, "ground", event);
                                    MessageHelper.sendMessageToChannelWithButtons(threadChannel_, "", buttons);
                                }
                            }
                        });
                    });
                }
                break;
            }

        }

        event.getMessage().delete().queue();
    }

    public static void resolvePeaceAccords(String buttonID, String ident, Player player, Game activeGame,
        ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        if ("lockedmallice".equalsIgnoreCase(planet)) {
            planet = "mallice";
            Tile tile = activeGame.getTileFromPlanet("lockedmallice");
            tile = MoveUnits.flipMallice(event, tile, activeGame);
        }
        new PlanetAdd().doAction(player, planet, activeGame, event);
        String planetRepresentation2 = Helper.getPlanetRepresentation(planet, activeGame);
        String msg = ident + " claimed the planet " + planetRepresentation2 + " using the peace accords ability. ";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }

    public static void resolveInitialIndoctrinationQuestion(Player player, Game activeGame, String buttonID,
        ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        List<Button> options = new ArrayList<>();
        options.add(Button.success("indoctrinate_" + planet + "_infantry", "Indoctrinate to place an infantry"));
        options.add(Button.success("indoctrinate_" + planet + "_mech", "Indoctrinate to place a mech"));
        options.add(Button.danger("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation(true, true) + " use buttons to resolve indoctrination", options);
    }

    public static void resolveFollowUpIndoctrinationQuestion(Player player, Game activeGame, String buttonID,
        ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = activeGame.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 " + unit + " " + planet, activeGame);
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet)) {
                new RemoveUnits().unitParsing(event, p2.getColor(), tile, "1 infantry " + planet, activeGame);
            }
        }
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "inf");
        if (player.getLeaderIDs().contains("yincommander") && !player.hasLeaderUnlocked("yincommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "yin", event);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " replaced 1 of their opponent's infantry with 1 " + unit + " on "
                + Helper.getPlanetRepresentation(planet, activeGame) + " using indoctrination");
        options.add(Button.danger("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation(true, true) + " pay for indoctrination.", options);
        event.getMessage().delete().queue();
    }

    public static void distantSuns(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String bID = buttonID.replace("distant_suns_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = player.getFactionEmoji() + " declined to use their Distant Suns ability";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, activeGame, 1, false);
        } else {
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, activeGame, 2, false);
        }

        event.getMessage().delete().queue();
    }

    public static void deepMining(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String bID = buttonID.replace("deep_mining_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = player.getFactionEmoji() + " declined to use their Deep Mining ability";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, activeGame, 1, false);
        } else {
            message = player.getFactionEmoji() + " used their Deep Mining ability to gain a tg (tg went from "
                + player.getTg() + "-> " + (player.getTg() + 1) + ")";
            player.setTg(player.getTg() + 1);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }

        event.getMessage().delete().queue();
    }

}
