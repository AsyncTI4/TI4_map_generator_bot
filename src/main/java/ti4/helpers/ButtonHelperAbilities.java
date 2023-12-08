package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.ds.TrapReveal;
import ti4.commands.ds.TrapToken;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.special.SleeperToken;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
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

public class ButtonHelperAbilities {

    public static void autoneticMemoryStep1(Game activeGame, Player player, int count) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("autoneticMemoryStep2_" + count, "Use Autonetic Memory"));
        buttons.add(Button.danger("autoneticMemoryDecline_" + count, "Decline"));
        String msg = player.getRepresentation(true, true) + " you have the ability to draw 1 less action card and utilize your autonetic memory ability. Please use or decline to use.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void autoneticMemoryDecline(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
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
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "To " + ButtonHelper.getIdent(player) + ": This button aint for you ");
            return;
        }
        player.addExhaustedAbility("grace");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " use buttons to resolve grace, reminder you have to spend a strat CC if applicable, and that you can only do one of these.",
            getGraceButtons(activeGame, player, scPlayed));
    }

    public static List<Button> getGraceButtons(Game activeGame, Player edyn, int scPlayed) {
        List<Button> scButtons = new ArrayList<>();
        scButtons.add(Button.secondary("spendAStratCC", "Spend a Strategy CC"));
        if (scPlayed > 1 && (activeGame.getScPlayed().get(1) == null || !activeGame.getScPlayed().get(1))) {
            scButtons.add(Button.success("leadershipGenerateCCButtons", "Gain CCs"));
            scButtons.add(Button.danger("leadershipExhaust", "Exhaust Planets"));
        }
        if (scPlayed > 2 && (activeGame.getScPlayed().get(2) == null || !activeGame.getScPlayed().get(2))) {
            scButtons.add(Button.success("diploRefresh2", "Ready 2 Planets"));
        }
        if (scPlayed > 3 && (activeGame.getScPlayed().get(3) == null || !activeGame.getScPlayed().get(3))) {
            scButtons.add(Button.secondary("sc_ac_draw", "Draw 2 Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
        }
        if (scPlayed > 4 && (activeGame.getScPlayed().get(4) == null || !activeGame.getScPlayed().get(4))) {
            scButtons.add(Button.success("construction_sd", "Place A SD").withEmoji(Emoji.fromFormatted(Emojis.spacedock)));
            scButtons.add(Button.success("construction_pds", "Place a PDS").withEmoji(Emoji.fromFormatted(Emojis.pds)));
        }
        if (scPlayed > 5 && (activeGame.getScPlayed().get(5) == null || !activeGame.getScPlayed().get(5))) {
            scButtons.add(Button.secondary("sc_refresh", "Replenish Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm)));
        }
        if (scPlayed > 6 && (activeGame.getScPlayed().get(6) == null || !activeGame.getScPlayed().get(6))) {
            scButtons.add(Button.success("warfareBuild", "Build At Home"));
        }
        if (scPlayed > 7 && (activeGame.getScPlayed().get(7) == null || !activeGame.getScPlayed().get(7))) {
            activeGame.setComponentAction(true);
            scButtons.add(Button.success("acquireATech", "Get a Tech"));
        }
        if (scPlayed > 8 && (activeGame.getScPlayed().get(8) == null || !activeGame.getScPlayed().get(8))) {
            scButtons.add(Button.secondary("sc_draw_so", "Draw Secret Objective").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
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
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true) + " tell the bot who's planet you want to put a trap on", buttons);
    }

    public static void setTrapStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Button.secondary("setTrapStep3_" + planet, Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true) + " select the planet you want to put a trap on", buttons);
    }

    public static void setTrapStep3(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : getUnusedTraps(activeGame, player)) {
            availableTraps.add(Button.success("setTrapStep4_" + planet + "_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentation(true, true) + " choose the trap you want to set on the planet " + Helper.getPlanetRepresentation(planet, activeGame);
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
            if (!activeGame.getFactionsThatReactedToThis(trapName).isEmpty()) {
                trappedPlanets.add(activeGame.getFactionsThatReactedToThis(trapName));
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

    public static void autoneticMemoryStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        int count = Integer.parseInt(buttonID.split("_")[1]);
        activeGame.drawActionCard(player.getUserID(), count - 1);
        ACInfo.sendActionCardInfo(activeGame, player, event);
        String msg2 = ButtonHelper.getIdent(player) + " is choosing to resolve their Autonetic Memory ability";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("autoneticMemoryStep3a", "Pick A Card From the Discard"));
        buttons.add(Button.primary("autoneticMemoryStep3b", "Drop an infantry"));
        String msg = player.getRepresentation(true, true) + " you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void autoneticMemoryStep3b(Game activeGame, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, "gf", "placeOneNDone_skipbuild");
        String message = player.getRepresentation(true, true) + " Use buttons to drop 1 infantry on a planet";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
    }

    public static void autoneticMemoryStep3a(Game activeGame, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        ButtonHelper.pickACardFromDiscardStep1(activeGame, player);
    }

    public static void addOmenDie(Game activeGame, int omenDie) {
        String omenDice;
        if (!activeGame.getFactionsThatReactedToThis("OmenDice").isEmpty()) {
            omenDice = activeGame.getFactionsThatReactedToThis("OmenDice");
            omenDice = omenDice + "_" + omenDie;
            activeGame.setCurrentReacts("OmenDice", "" + omenDice);
        } else {
            activeGame.setCurrentReacts("OmenDice", "" + omenDie);
        }
    }

    public static void removeOmenDie(Game activeGame, int omenDie) {
        String omenDice;
        if (!activeGame.getFactionsThatReactedToThis("OmenDice").isEmpty()) {
            omenDice = activeGame.getFactionsThatReactedToThis("OmenDice");
            omenDice = omenDice.replaceFirst("" + omenDie, "");
            omenDice = omenDice.replace("__", "_");
            activeGame.setCurrentReacts("OmenDice", "" + omenDice);
        }
    }

    public static List<Integer> getAllOmenDie(Game activeGame) {
        List<Integer> dice = new ArrayList<>();
        for (String dieResult : activeGame.getFactionsThatReactedToThis("OmenDice").split("_")) {
            if (!dieResult.isEmpty() && !dieResult.contains("_")) {
                int die = Integer.parseInt(dieResult);
                dice.add(die);
            }
        }
        return dice;
    }

    public static void offerOmenDiceButtons(Game activeGame, Player player) {
        String msg = player.getRepresentation(true, true) + " you can play an omen die with the following buttons. Duplicate dice are not shown.";
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
        activeGame.setCurrentReacts("OmenDice", "");
        String msg = myko.getRepresentation(true, true) + " rolled 4 omen dice and rolled the following numbers: ";
        for (int x = 0; x < 4; x++) {
            Die d1 = new Die(6);
            msg = msg + d1.getResult() + " ";
            addOmenDie(activeGame, d1.getResult());
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(myko, activeGame), msg.toString());
    }

    public static void pillage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String finsFactionCheckerPrefix) {
        buttonID = buttonID.replace("pillage_", "");
        String colorPlayer = buttonID.split("_")[0];
        String checkedStatus = buttonID.split("_")[1];
        Player pillaged = activeGame.getPlayerFromColorOrFaction(colorPlayer);
        if (pillaged == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Could not find player, please resolve manually.");
            return;
        }
        if (checkedStatus.contains("unchecked")) {
            List<Button> buttons = new ArrayList<>();
            String message2 = "Please confirm this is a valid pillage opportunity and that you wish to pillage.";
            buttons.add(Button.danger(finsFactionCheckerPrefix + "pillage_" + pillaged.getColor() + "_checked", "Pillage a TG"));
            if (pillaged.getCommodities() > 0) {
                buttons.add(Button.danger(finsFactionCheckerPrefix + "pillage_" + pillaged.getColor() + "_checkedcomm", "Pillage a Commodity"));
            }
            buttons.add(Button.success(finsFactionCheckerPrefix + "deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        } else {
            MessageChannel channel1 = ButtonHelper.getCorrectChannel(pillaged, activeGame);
            MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeGame);
            String pillagerMessage = player.getRepresentation(true, true) + " you pillaged, your tgs have gone from " + player.getTg() + " to "
                + (player.getTg() + 1) + ".";
            String pillagedMessage = pillaged.getRepresentation(true, true) + " you have been pillaged";

            if (pillaged.getCommodities() > 0 && checkedStatus.contains("checkedcomm")) {
                pillagedMessage = pillagedMessage + ", your comms have gone from " + pillaged.getCommodities() + " to " + (pillaged.getCommodities() - 1) + ".";
                pillaged.setCommodities(pillaged.getCommodities() - 1);
            } else {
                pillagedMessage = pillagedMessage + ", your tgs have gone from " + pillaged.getTg() + " to " + (pillaged.getTg() - 1) + ".";
                pillaged.setTg(pillaged.getTg() - 1);
            }
            player.setTg(player.getTg() + 1);
            MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
            MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
            if (player.hasUnexhaustedLeader("mentakagent")) {
                List<Button> buttons = new ArrayList<>();
                Button winnuButton = Button
                    .success("FFCC_" + player.getFaction() + "_" + "exhaustAgent_mentakagent_" + pillaged.getFaction(), "Use Mentak Agent To Draw ACs for you and pillaged player")
                    .withEmoji(Emoji.fromFormatted(Emojis.Mentak));
                buttons.add(winnuButton);
                buttons.add(Button.danger("deleteButtons", "Done"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, "Wanna use Mentak Agent?", buttons);
            }
            for (Player p2 : activeGame.getRealPlayers()) {
                if (p2 != pillaged && p2 != player && p2.hasUnexhaustedLeader("yssarilagent") && player.hasLeader("mentakagent")) {
                    List<Button> buttons = new ArrayList<>();
                    Button winnuButton = Button
                        .success("FFCC_" + p2.getFaction() + "_" + "exhaustAgent_mentakagent_" + pillaged.getFaction(), "Use Mentak Agent To Draw ACs for you and pillaged player")
                        .withEmoji(Emoji.fromFormatted(Emojis.Mentak));
                    buttons.add(winnuButton);
                    buttons.add(Button.danger("deleteButtons", "Done"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), p2.getRepresentation() + " Wanna use Mentak Agent?", buttons);
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

    public static void resolveGetDiplomatButtons(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        ButtonHelper.deleteTheOneButton(event);
        String message = player.getRepresentation() + " select the planet you would like to exhaust";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, getDiplomatButtons(activeGame, player));
    }

    public static List<Button> getDiplomatButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getTokenList().contains("token_freepeople.png")) {
                    buttons.add(Button.success("exhaustViaDiplomats_" + unitHolder.getName(), Helper.getPlanetRepresentation(unitHolder.getName(), activeGame)));
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

    public static void resolveDiplomatExhaust(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String planet = buttonID.split("_")[1];
        String message = ButtonHelper.getIdent(player) + " exhausted the unowned planet " + Helper.getPlanetRepresentation(planet, activeGame) + " using the diplomats ability";
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
        unitHolder.removeToken("token_freepeople.png");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
        event.getMessage().delete().queue();

    }

    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame, "infantry", "placeOneNDone_skipbuild"));
        String message = player.getRepresentation(true, true) + " Use buttons to put 1 infantry on a planet";

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String finChecker) {
        List<Button> buttons = new ArrayList<>(getPlanetPlaceUnitButtonsForMechMitosis(player, activeGame, finChecker));
        String message = player.getRepresentation(true, true) + " Use buttons to replace 1 infantry with a mech";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game activeGame, String finChecker) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        List<String> tiles = new ArrayList<>();
        for (String planet : planets) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Tile tile = activeGame.getTile(AliasHandler.resolveTile(planet));
            if (tiles.contains(tile.getPosition())) {
                continue;
            } else {
                tiles.add(tile.getPosition());
            }
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if ("space".equalsIgnoreCase(unitHolder.getName())) {
                    continue;
                }
                String colorID = Mapper.getColorID(player.getColor());
                int numInf = 0;
                if (unitHolder.getUnits() != null) {
                    numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);
                }
                if (numInf > 0) {
                    String buttonID = finChecker + "mitoMechPlacement_" + unitHolder.getName().toLowerCase().replace("'", "").replace("-", "").replace(" ", "");
                    Button button = Button.success(buttonID, Helper.getPlanetRepresentation(unitHolder.getName(), activeGame));
                    planetButtons.add(button);
                }
            }
        }
        return planetButtons;
    }

    public static void putSleeperOn(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        buttonID = buttonID.replace("putSleeperOnPlanet_", "");
        String planet = buttonID;
        String message = ident + " put a sleeper on " + Helper.getPlanetRepresentation(planet, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planet, player);
        event.getMessage().delete().queue();
    }

    public static void oribtalDropFollowUp(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success("dropAMechToo", "Spend 3 resource to Drop a Mech Too");
        startButtons.add(tacticalAction);
        Button componentAction = Button.danger("finishComponentAction_spitItOut", "Decline Mech");
        startButtons.add(componentAction);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Decide whether to drop mech",
            startButtons);
        event.getMessage().delete().queue();
    }

    public static void oribtalDropExhaust(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event, "res");
        Button DoneExhausting = Button.danger("finishComponentAction", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
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
            if (!planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG) && !"mr".equalsIgnoreCase(planetName) && !ButtonHelper.isTileHomeSystem(tile)) {
                buttons.add(Button.secondary("mantleCrack_" + planetName, Helper.getPlanetRepresentation(planetName, activeGame)));
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

    public static void resolveAxisOrderExhaust(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String order = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " Chose to Use " + Mapper.getRelic(order).getName());
        List<Button> buttons = new ArrayList<>();
        String message = "";
        String techName = "";
        boolean hasTech = false;
        if (Mapper.getRelic(order).getName().contains("Dreadnought")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "dreadnought", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 dreadnought in a system with your ships and cc";
            techName = "dn2";
        }
        if (Mapper.getRelic(order).getName().contains("Carrier")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "carrier", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 carrier in a system with your ships and cc";
            techName = "cv2";
        }
        if (Mapper.getRelic(order).getName().contains("Cruiser")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 cruiser in a system with your ships and cc";
            techName = "cr2";
        }
        if (Mapper.getRelic(order).getName().contains("Destroyer")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "2destroyer", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 2 destroyers in a system with your ships and cc";
            techName = "dd2";
        }
        message = player.getRepresentation(true, true) + " " + message;
        ButtonHelper.deleteTheOneButton(event);
        player.addExhaustedRelic(order);
        for (Player p2 : activeGame.getRealPlayers()) {
            if (activeGame.playerHasLeaderUnlockedOrAlliance(p2, "axiscommander") && !p2.hasTech(techName)) {
                activeGame.setComponentAction(true);
                Button getTech = Button.success("acquireATech", "Get a tech");
                List<Button> buttons2 = new ArrayList<>();
                buttons2.add(getTech);
                buttons2.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), p2.getRepresentation(true, true)
                    + " a player has resolved an Axis Order (" + Mapper.getRelic(order).getName() + ") and you can use the button to gain the corresponding unit upgrade tech if you pay 6r", buttons2);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
    }

    public static List<Button> getBuyableAxisOrders(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        int maxCost = player.getCommodities();

        String relicName = "axisorderdd";
        String extra = "duplicate";
        int orderCost = 1;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisordercr";
        orderCost = 1;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisordercv";
        orderCost = 2;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisorderdn";
        orderCost = 3;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete these buttons"));

        return buttons;
    }

    public static void resolveAxisOrderBuy(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String relicName = buttonID.split("_")[1];
        String cost = buttonID.split("_")[2];
        int lostComms = Integer.parseInt(cost);
        int oldComms = player.getCommodities();
        if (lostComms > oldComms) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation(true, true) + " you don't have that many comms");
            return;
        }
        player.addRelic(relicName);
        player.setCommodities(oldComms - lostComms);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation(true, true) + " acquired " + Mapper.getRelic(relicName).getName()
            + " and paid " + lostComms + " commodities (" + oldComms + "->" + player.getCommodities() + ")");
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void pillageCheck(Player player, Game activeGame) {
        if (player.getPromissoryNotesInPlayArea().contains("pop")) {
            return;
        }
        if (Helper.getPlayerFromAbility(activeGame, "pillage") != null && !Helper.getPlayerFromAbility(activeGame, "pillage").getFaction().equalsIgnoreCase(player.getFaction())) {

            Player pillager = Helper.getPlayerFromAbility(activeGame, "pillage");
            String finChecker = "FFCC_" + pillager.getFaction() + "_";
            if (player.getTg() > 2 && player.getNeighbouringPlayers().contains(pillager)) {
                List<Button> buttons = new ArrayList<>();
                String playerIdent = StringUtils.capitalize(player.getFaction());
                MessageChannel channel = activeGame.getMainGameChannel();
                if (activeGame.isFoWMode()) {
                    playerIdent = StringUtils.capitalize(player.getColor());
                    channel = pillager.getPrivateChannel();
                }
                String message = pillager.getRepresentation(true, true) + " you may have the opportunity to pillage " + playerIdent
                    + ". Please check this is a valid pillage opportunity, and use buttons to resolve.";
                buttons.add(Button.danger(finChecker + "pillage_" + player.getColor() + "_unchecked", "Pillage " + playerIdent));
                buttons.add(Button.success(finChecker + "deleteButtons", "Decline Pillage Window"));
                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
            }
        }
    }

    public static void mantleCracking(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        int oldTg = player.getTg();
        player.setTg(oldTg + 4);
        Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
        planet.addToken(Constants.GLEDGE_CORE_PNG);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdentOrColor(player, activeGame) + "cracked the mantle of "
            + Helper.getPlanetRepresentation(planetName, activeGame) + " and gained 4tg (" + oldTg + "->" + player.getTg() + "). This is technically an optional gain");
        pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 4);
        List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getButtonsForPossibleTechForNekro(Player nekro, List<String> currentList, Game activeGame) {
        List<Button> techToGain = new ArrayList<>();
        for (String tech : currentList) {
            techToGain.add(Button.success("getTech_" + Mapper.getTech(tech).getName() + "_noPay", Mapper.getTech(tech).getName()));
        }
        return techToGain;
    }

    public static List<String> getPossibleTechForNekroToGainFromPlayer(Player nekro, Player victim, List<String> currentList, Game activeGame) {
        List<String> techToGain = new ArrayList<>(currentList);
        for (String tech : victim.getTechs()) {
            if (!nekro.getTechs().contains(tech) && !techToGain.contains(tech) && !"iihq".equalsIgnoreCase(tech)) {
                techToGain.add(tech);
            }
        }
        return techToGain;
    }

    public static void removeSleeper(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
        String planet = buttonID;
        String message = ident + " removed a sleeper from " + planet;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planet, player);
        event.getMessage().delete().queue();
    }

    public static void replaceSleeperWith(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        buttonID = buttonID.replace("replaceSleeperWith_", "");
        String planetName = buttonID.split("_")[1];
        String unit = buttonID.split("_")[0];
        String message;
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planetName, player);
        if ("mech".equalsIgnoreCase(unit)) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName + ", inf " + planetName, activeGame);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName, activeGame) + " with a " + Emojis.mech + " and "
                + Emojis.infantry;
        } else {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "pds " + planetName, activeGame);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName, activeGame) + " with a " + Emojis.pds;
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
            if (!player.hasAbility("council_patronage")) continue;
            MessageChannel channel = activeGame.getActionsChannel();
            if (activeGame.isFoWMode()) {
                channel = player.getPrivateChannel();
            }
            player.setTg(player.getTg() + 1);
            player.setCommodities(player.getCommoditiesTotal());
            MessageHelper.sendMessageToChannel(channel, player.getRepresentation(true, true) + " your **Council Patronage** ability was triggered. Your " + Emojis.comm
                + " commodities have been replenished and you have gained 1 " + Emojis.getTGorNomadCoinEmoji(activeGame) + " trade good (" + (player.getTg()-1) + " -> " + (player.getTg()) + ")");
            pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
            ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
            ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
        }
    }

    public static void starforgeTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
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

    public static void starforge(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String unitNPlace = buttonID.replace("starforge_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        String successMessage;
        if (player.getStrategicCC() > 0) {
            successMessage = "Reduced strategy pool CCs by 1 (" + (player.getStrategicCC()) + "->"
                + (player.getStrategicCC() - 1) + ")";
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
        } else {
            player.addExhaustedRelic("emelpar");
            successMessage = "Exhausted scepter of emelpar";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
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

    public static void resolveMitosisMechPlacement(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String planetName = buttonID.replace("mitoMechPlacement_", "");
        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName, activeGame);
        new RemoveUnits().removeStuff(event, activeGame.getTile(AliasHandler.resolveTile(planetName)), 1, planetName, key, player.getColor(), false, activeGame);
        String successMessage = ident + " Replaced an infantry with a mech on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
        event.getMessage().delete().queue();
    }

    public static List<Button> getXxchaPeaceAccordsButtons(Game activeGame, Player player, GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Tile tile = activeGame.getTileFromPlanet(planet);
            for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)) {
                Tile tile2 = activeGame.getTileByPosition(pos2);
                for (UnitHolder planetUnit2 : tile2.getUnitHolders().values()) {
                    if ("space".equalsIgnoreCase(planetUnit2.getName())) {
                        continue;
                    }
                    Planet planetReal2 = (Planet) planetUnit2;
                    String planet2 = planetReal2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeGame);
                    if (!player.getPlanetsAllianceMode().contains(planet2) && !planetRepresentation2.contains("Mecatol") && (planetReal2.getUnits() == null || planetReal2.getUnits().isEmpty())
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

    public static void resolvePeaceAccords(String buttonID, String ident, Player player, Game activeGame, ButtonInteractionEvent event) {
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

    public static void resolveInitialIndoctrinationQuestion(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        List<Button> options = new ArrayList<>();
        options.add(Button.success("indoctrinate_" + planet + "_infantry", "Indoctrinate to place an infantry"));
        options.add(Button.success("indoctrinate_" + planet + "_mech", "Indoctrinate to place a mech"));
        options.add(Button.danger("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, true) + " use buttons to resolve indoctrination", options);
    }

    public static void resolveFollowUpIndoctrinationQuestion(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
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
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event, "inf");
        if (player.getLeaderIDs().contains("yincommander") && !player.hasLeaderUnlocked("yincommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "yin", event);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " replaced 1 of their opponent's infantry with 1 " + unit + " on " + Helper.getPlanetRepresentation(planet, activeGame) + " using indoctrination");
        options.add(Button.danger("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, true) + " pay for indoctrination.", options);
        event.getMessage().delete().queue();
    }

    public static void distantSuns(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String bID = buttonID.replace("distant_suns_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = "Rejected Distant Suns Ability";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, activeGame, 1, false);
        } else {
            message = "Exploring twice";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, activeGame, 2, false);
        }

        event.getMessage().delete().queue();
    }

}