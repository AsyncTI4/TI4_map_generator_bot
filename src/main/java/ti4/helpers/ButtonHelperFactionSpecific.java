package ti4.helpers;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.buttons.Buttons;
import ti4.buttons.handlers.agenda.VoteButtonHandler;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.ExploreModel;
import ti4.model.StrategyCardModel;
import ti4.model.UnitModel;
import ti4.service.button.ReactionService;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.game.StartPhaseService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.planet.AddPlanetService;
import ti4.service.tech.PlayerTechService;
import ti4.service.transaction.SendDebtService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class ButtonHelperFactionSpecific {

    @ButtonHandler("gloryTech")
    public static void getTechFromGlory(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("getAllTechOfType_unitupgrade_noPay", "Get A Unit Upgrade Technology"));
        buttons.add(Buttons.red("deleteButtons", "Delete This"));
        player.setStrategicCC(player.getStrategicCC() - 1);
        MessageChannel channel = player.getCorrectChannel();
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Glory");
        String message0 = player.getRepresentationUnfogged()
            + "1 command token has been subtracted from your strategy pool due to use of **Glory** to acquire the unit upgrade technology of one of the participating units.";
        String message = player.getRepresentationUnfogged()
            + " Use buttons to get a unit upgrade technology.";
        MessageHelper.sendMessageToChannel(channel, message0);
        MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveVadenTgForSpeed(Player player, GenericInteractionCreateEvent event) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " is paying trade goods to boost their non-fighter ships.");
        List<Button> buttons = new ArrayList<>();
        player.resetSpentThings();
        String whatIsItFor = "both";
        if (player.getTg() >= 1) {
            Button lost1TG = Buttons.red("reduceTG_1_" + whatIsItFor, "Spend 1 Trade Good");
            buttons.add(lost1TG);
        }
        if (player.getTg() >= 2) {
            Button lost2TG = Buttons.red("reduceTG_2_" + whatIsItFor, "Spend 2 Trade Goods");
            buttons.add(lost2TG);
        }
        if (player.getTg() >= 3) {
            Button lost3TG = Buttons.red("reduceTG_3_" + whatIsItFor, "Spend 3 Trade Goods");
            buttons.add(lost3TG);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() >= 1) {
            Button lost1C = Buttons.red("reduceComm_1_" + whatIsItFor, "Spend 1 Commodity");
            buttons.add(lost1C);
        }

        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() >= 2) {
            Button lost2C = Buttons.red("reduceComm_2_" + whatIsItFor, "Spend 2 Commodities");
            buttons.add(lost2C);
        }

        buttons.add(Buttons.gray("resetSpend_" + whatIsItFor, "Reset Spent Planets and Trade Goods"));
        Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            "Click how many trade goods you wish to spend.", buttons);
        PlayerTechService.deleteTheOneButtonIfButtonEvent(event);
    }

    @ButtonHandler("warStoriesFrontier_")
    public static void warStoriesFrontier(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " is using their **War Stories** ability to explore the frontier deck in the active system.");
        Tile tile = game.getTileByPosition(pos);
        ExploreService.expFront(event, tile, game, player, true);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("warStoriesPlanetExplore")
    public static void warStoriesPlanetExplore(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " is using their **War Stories** ability to explore a planet they control.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + ", please use buttons to explore a planet you control.", buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("resolveVadenMech_")
    public static void resolveVadenMech(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String opposingFaction = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planet);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " placed 1 infantry at the end of a round of ground combat on "
                + Helper.getPlanetRepresentation(planet, game)
                + " using the Collector (Vaden mech) ability. This forgave 1 debt from their opponent.");
        player.removeDebtTokens(game.getPlayerFromColorOrFaction(opposingFaction).getColor(), 1);
    }

    @ButtonHandler("collateralizedLoans_")
    public static void collateralizedLoans(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        Tile tile = game.getTileByPosition(pos);
        player.clearDebt(p2, 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation()
            + " used their **Collateralized Loans** ability to forgive 1 debt from their opponent to place 1 ship of a type that their opponent just lost.");
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation() + ", one Vaden debt of yours was forgiven via the **Collateralized Loans** ability.");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "sling",
            "placeOneNDone_skipbuild");
        String message = player.getRepresentation() + ", use the buttons to place 1 unit that was destroyed. ";

        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler("gheminaMechStart_")
    public static void gheminaMechStart(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game,
            (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, game), player);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " due to your mech ability, you may explore "
                + Helper.getPlanetRepresentation(planet, game) + " twice now.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + ", exploration #1",
            buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + ", exploration #2",
            buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveEdynAgendaStuffStep1(Player player, Game game, List<Tile> tiles) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : tiles) {
            buttons.add(Buttons.gray("edynAgendaStuffStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + ", please tell the bot which Sigil system you wish to place another player's command token in.", buttons);
    }

    @ButtonHandler("edynAgendaStuffStep2_")
    public static void resolveEdynAgendaStuffStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String pos = buttonID.split("_")[1];
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("edynAgendaStuffStep3_" + p2.getFaction() + "_" + pos, p2.getColor()));
            } else {
                Button button = Buttons.gray("edynAgendaStuffStep3_" + p2.getFaction() + "_" + pos, " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + ", please tell the bot which player's command token you wish to place down.",
            buttons);
    }

    @ButtonHandler("edynAgendaStuffStep3_")
    public static void resolveEdynAgendaStuffStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        CommandCounterHelper.addCC(event, p2, tile);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " you placed " + p2.getFactionEmojiOrColor()
                + " command token in tile " + tile.getRepresentationForButtons(game, player) + ".");
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentationUnfogged() + " you were jammed in " + tile.getRepresentationForButtons(game, p2) + " via the **Radiance** ability.");

    }

    public static void resolveCavStep1(Game game, Player player) {
        String msg = player.getRepresentation() + ", please choose the non-fighter ship you wish to use _The Cavalry_ on.";
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());

        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if (!(player.unitBelongsToPlayer(unitEntry.getKey())))
                continue;

            UnitKey unitKey = unitEntry.getKey();
            String unitName = unitKey.unitName();
            // System.out.println(unitKey.asyncID());
            int totalUnits = unitEntry.getValue();
            int damagedUnits = 0;
            if ("fighter".equalsIgnoreCase(unitName)) {
                continue;
            }

            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }
            if (damagedUnits > 0) {
                String buttonID = "cavStep2_" + unitName + "damaged";
                String buttonText = "Damaged " + unitKey.unitName();
                Button validTile2 = Buttons.red(buttonID, buttonText, unitKey.unitEmoji());
                buttons.add(validTile2);
            }

            totalUnits -= damagedUnits;
            if (totalUnits > 0) {
                String buttonID = "cavStep2_" + unitName;
                String buttonText = unitKey.unitName();
                Button validTile2 = Buttons.red(buttonID, buttonText, unitKey.unitEmoji());
                buttons.add(validTile2);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("cavStep2_")
    public static void resolveCavStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String unit = buttonID.split("_")[1];
        boolean damaged = false;
        if (unit.contains("damaged")) {
            damaged = true;
            unit = unit.replace("damaged", "");
        }
        String msg = player.getFactionEmoji() + " chose a " + unit
            + " to use _The Cavalry_ on. It has been temporarily replaced with a Cavalry unit. It will be automatically put back at the end of the tactical action.";
        game.setStoredValue("nomadPNShip", unit);
        String cav = "cavalry1";
        if (game.getPNOwner("cavalry").hasTech("m2")) {
            cav = "cavalry2";
        } else if (game.getPNOwner("cavalry").hasUnit("sigma_nomad_flagship_1")) {
            cav = "sigma_cavalry_1";
        } else if (game.getPNOwner("cavalry").hasUnit("sigma_nomad_flagship_2")) {
            cav = "sigma_cavalry_2";
        } else if (game.getPNOwner("cavalry").hasUnit("sigma_nomad_flagship_3")) {
            cav = "sigma_cavalry_3";
        }
        event.getMessage().delete().queue();
        Tile tile = game.getTileByPosition(game.getActiveSystem());

        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        player.addOwnedUnitByID(cav);
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), unit);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "cavalry");
        if (damaged) {
            unitHolder.removeDamagedUnit(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID()), 1);
            unitHolder.addDamagedUnit(Mapper.getUnitKey("cavalry", player.getColorID()), 1);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void cleanCavUp(Game game, GenericInteractionCreateEvent event) {
        for (Player player : game.getRealPlayers()) {
            if (player.hasUnit("cavalry1") || player.hasUnit("cavalry2")) {
                String cav = "cavalry1";
                String unit = game.getStoredValue("nomadPNShip");
                if (player.hasUnit("cavalry2")) {
                    cav = "cavalry2";
                } else if (player.hasUnit("sigma_cavalry_1")) {
                    cav = "sigma_cavalry_1";
                } else if (player.hasUnit("sigma_cavalry_2")) {
                    cav = "sigma_cavalry_2";
                } else if (player.hasUnit("sigma_cavalry_3")) {
                    cav = "sigma_cavalry_3";
                }

                for (Tile tile : game.getTileMap().values()) {
                    UnitHolder unitHolder = tile.getUnitHolders().get("space");
                    if (unitHolder.getUnitCount(UnitType.Cavalry, player.getColor()) > 0) {
                        if (unitHolder.getUnitDamage() != null
                            && unitHolder.getUnitDamage()
                                .get(Mapper.getUnitKey("cavalry", player.getColorID())) != null
                            && unitHolder.getUnitDamage()
                                .get(Mapper.getUnitKey("cavalry", player.getColorID())) > 0) {
                            unitHolder.addDamagedUnit(
                                Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID()), 1);
                            unitHolder.removeDamagedUnit(Mapper.getUnitKey("cavalry", player.getColorID()), 1);
                        }
                        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "cavalry");
                        AddUnitService.addUnits(event, tile, game, player.getColor(), unit);

                    }
                }
                player.removeOwnedUnitByID(cav);
                game.setStoredValue("nomadPNShip", "");
            }
        }
    }

    public static boolean doesAnyoneElseHaveJr(Game game, Player player) {
        for (Player p1 : game.getRealPlayers()) {
            if (p1 == player) {
                continue;
            }
            if (p1.hasRelic("titanprototype") || p1.hasRelic("absol_jr")) {
                return true;
            }
        }
        return false;
    }

    @ButtonHandler("yssarilAgentAsJr")
    public static void yssarilAgentAsJr(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons2 = VoteButtonHandler.getPlayerOutcomeButtons(game, null, "jrResolution", null);
        player.getLeader("yssarilagent").get().setExhausted(true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " is using Clever Clever JR-XS455-O, the Relic/Yssaril agent.");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Use buttons to decide on whom to use Clever Clever JR-XS455-O, the Relic/Yssaril agent.", buttons2);
        event.getMessage().delete().queue();
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
    }

    @ButtonHandler("titansConstructionMechDeployStep1")
    public static void handleTitansConstructionMechDeployStep1(Game game, Player player, ButtonInteractionEvent event) {
        String messageID = event.getMessageId();
        boolean used = ButtonHelperSCs.addUsedSCPlayer(messageID, game, player);
        StrategyCardModel scModel = game.getStrategyCardModelByName("construction").orElse(null);
        int scNum = scModel.getInitiative();
        boolean construction = scModel != null && scModel.usesAutomationForSCID("pok4construction");
        if (!used && scModel != null && construction && !player.getFollowedSCs().contains(scNum)
            && game.getPlayedSCs().contains(scNum)) {
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed construction");
            }
            String message = ButtonHelperSCs.deductCC(game, player, scNum);
            ReactionService.addReaction(event, game, player, message);
        }
        List<Button> buttons = new ArrayList<>();
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") > 3) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " you have all your mechs out and can't DEPLOY more.");
            return;
        }
        for (String planet : player.getPlanets()) {
            buttons.add(Buttons.green("titansConstructionMechDeployStep2_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentationUnfogged()
            + " select the planet that you wish to drop 1 mech and 1 infantry on";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("mahactStealCC_")
    public static void mahactStealCC(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String color = buttonID.replace("mahactStealCC_", "");
        String ident = player.getRepresentation(true, false);
        if (!player.getMahactCC().contains(color)) {
            player.addMahactCC(color);
            Helper.isCCCountCorrect(event, game, color);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                ident + " added a " + color + " command token to their fleet pool.");
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                ident + " already had a " + color + " command token in their fleet pool.");
        }
        CommanderUnlockCheckService.checkPlayer(player, "mahact");

        if (ButtonHelper.isLawInPlay(game, "regulations")
            && (player.getFleetCC() + player.getMahactCC().size()) > 4) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                + " reminder that under the _Fleet Regulations_ law, fleet pools are limited to 4 command tokens.");
        }
        ButtonHelper.deleteTheOneButton(event);

    }

    @ButtonHandler("nekroStealTech_")
    public static void nekroStealTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String trueIdentity = player.getRepresentation();
        String faction = buttonID.replace("nekroStealTech_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            return;
        }
        List<String> potentialTech = new ArrayList<>();
        game.setComponentAction(true);
        potentialTech = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, potentialTech,
            game);
        List<Button> buttons = ButtonHelperAbilities.getButtonsForPossibleTechForNekro(player, potentialTech, game);
        if (p2.getPromissoryNotesInPlayArea().contains("antivirus")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                trueIdentity + " the other player has _Antivirus_, so you cannot gain a technology from this combat.");
        } else if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                trueIdentity + " copy a technology from your opponent using the buttons.", buttons);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                trueIdentity + " there are no technologies available to copy <sad Nekro noises>.");
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("titansConstructionMechDeployStep2_")
    public static void handleTitansConstructionMechDeployStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planet);
        String msg = player.getRepresentationUnfogged() + " deployed 1 mech and 1 infantry on "
            + Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.sendMessageToRightStratThread(player, game, msg, "construction");
        if (!player.getSCs().contains(Integer.parseInt("4"))
            && !game.playerHasLeaderUnlockedOrAlliance(player, "rohdhnacommander")) {
            CommandCounterHelper.addCC(event, player, tile);
            ButtonHelper.sendMessageToRightStratThread(player, game,
                player.getFactionEmoji() + " Placed 1 command token from reinforcements in the "
                    + Helper.getPlanetRepresentation(planet, game) + " system.",
                "construction");
        }
        event.getMessage().delete().queue();
    }

    public static void checkForStymie(Game game, Player activePlayer, Tile tile) {
        for (Player p2 : ButtonHelper.getOtherPlayersWithUnitsInTheSystem(activePlayer, game, tile)) {
            if (p2.getPromissoryNotes().containsKey("stymie") && game.getPNOwner("stymie") != p2) {
                String msg = p2.getRepresentationUnfogged() + " you have the opportunity to _Stymie_ " + activePlayer.getFactionEmojiOrColor() + ".";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("stymiePlayerStep1_" + activePlayer.getFaction(), "Play Stymie"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    @ButtonHandler("stymiePlayerStep1_")
    public static void resolveStymiePlayerStep1(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player activePlayer = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String msg = player.getRepresentationUnfogged() + ", please choose the system in which you wish to _Stymie_.";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.getPosition().equalsIgnoreCase(game.getActiveSystem()) && !tile.isHomeSystem()
                && !CommandCounterHelper.hasCC(event, activePlayer.getColor(), tile)) {
                buttons.add(Buttons.green("stymiePlayerStep2_" + activePlayer.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        event.getMessage().delete().queue();
        PromissoryNoteHelper.resolvePNPlay("stymie", player, game, event);
    }

    @ButtonHandler("stymiePlayerStep2_")
    public static void resolveStymiePlayerStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        CommandCounterHelper.addCC(event, p2, tile);
        event.getMessage().delete().queue();
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you _Stymie_'d the tile: "
                    + tile.getRepresentationForButtons(game, player) + ".");
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                p2.getRepresentationUnfogged() + ", you were _Stymie_'d in tile: "
                    + tile.getRepresentationForButtons(game, p2) + ".");
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " has _Stymie_'d " + p2.getRepresentationUnfogged() + " in the tile: "
                    + tile.getRepresentationForButtons(game, player) + ".");
        }
    }

    public static void offerASNButtonsStep1(Game game, Player player, String warfareOrTactical) {
        String msg = player.getRepresentationUnfogged()
            + ", you may have the ability to use _Agency Supply Network_. Select the tile you wish to build out of, or decline."
            + " _Agency Supply Network_ cannot trigger off of itself, which isn't tracked by the bot; if this is the case, please decline.";
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(player, game);
        for (Tile tile : tiles) {
            buttons.add(Buttons.green("asnStep2_" + tile.getPosition() + "_" + warfareOrTactical,
                tile.getRepresentation()));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("asnStep2_")
    public static void resolveASNStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        String msg = player.getFactionEmoji() + " is resolving _Agency Supply Network_ in tile "
            + tile.getRepresentation();

        String warfareOrTactical = buttonID.split("_")[2];
        ButtonHelper.sendMessageToRightStratThread(player, game, msg, warfareOrTactical);
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, game, tile, warfareOrTactical + "asn", "place");
        String message = player.getRepresentation()
            + ", please use the buttons to produce."
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        event.getMessage().delete().queue();

    }

    public static boolean somebodyHasThisRelic(Game game, String relic) {
        for (Player player : game.getRealPlayers()) {
            if (player.hasRelic(relic)) {
                return true;
            }
        }
        return false;
    }

    public static Player findPNOwner(String pn, Game game) {
        for (Player player : game.getRealPlayers()) {
            if (player.ownsPromissoryNote(pn)) {
                return player;
            }
        }
        return null;
    }

    public static void delete(ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
    }

    @ButtonHandler("saarMechRes_")
    public static void placeSaarMech(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " paid 1 trade good " + player.gainTG(-1) + " to place 1 mech on " + Helper.getPlanetRepresentation(planet, game) + ".";
        delete(event);
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "mech " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    @ButtonHandler("getKeleresTechOptions")
    public static void offerKeleresStartingTech(Player player, Game game, ButtonInteractionEvent event) {
        List<String> techToGain = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain, game);
        }
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if (Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse("").isEmpty()) {
                techs.add(Buttons.green("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay", Mapper.getTech(tech).getName()));
            }
        }
        event.getMessage().delete().queue();
        List<Button> techs2 = new ArrayList<>(techs);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " use the buttons to gain a technology the other players have:",
            techs);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " use the buttons to gain another technology the other players have:",
            techs2);
    }

    public static void offerArgentStartingTech(Player player) {
        List<String> techToGain = new ArrayList<>();
        techToGain.add("st");
        techToGain.add("nm");
        techToGain.add("ps");
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if (Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse("").isEmpty()) {
                techs.add(Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                    Mapper.getTech(tech).getName()));
            }
        }
        List<Button> techs2 = new ArrayList<>(techs);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " use the buttons to choose one of the 3 starting Argent technologies.",
            techs);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " use the buttons to choose another of the 3 starting Argent technologies.",
            techs2);
    }

    public static void offerWinnuStartingTech(Player player) {
        List<String> techToGain = new ArrayList<>();
        techToGain.add("st");
        techToGain.add("nm");
        techToGain.add("ps");
        techToGain.add("amd");
        techToGain.add("det");
        techToGain.add("aida");
        techToGain.add("pa");
        techToGain.add("sdn");
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if (!player.getTechs().contains(tech)) {
                techs.add(Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                    Mapper.getTech(tech).getName()));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " use the buttons to get a technology.", techs);
    }

    public static void offerSpyNetOptions(Player player) {
        String msg = player.getRepresentation()
            + ", you have a choice now as to how you wish to resolve _Spy Net_. You may either look at all of the action cards in the Yssaril player's hand, or"
            + " (usually by prearrangement with the Yssaril player), you may allow the Yssaril player to choose an action card to give you.";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("spyNetPlayerChooses", "Look And Choose Yourself"));
        buttons.add(Buttons.green("spyNetYssarilChooses", "Have Yssaril Choose For You"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("spyNetYssarilChooses")
    public static void resolveSpyNetYssarilChooses(Player player, Game game, ButtonInteractionEvent event) {
        Player yssaril = findPNOwner("spynet", game);
        String buttonID = "transact_ACs_" + player.getFaction();
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Sent Yssaril buttons so that they can send you an action card.");
        TransactionHelper.resolveSpecificTransButtonsOld(game, yssaril, buttonID, event);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("spyNetPlayerChooses")
    public static void resolveSpyNetPlayerChooses(Player player, Game game, ButtonInteractionEvent event) {
        Player yssaril = findPNOwner("spynet", game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Use Buttons to take 1 action card.", ActionCardHelper.getToBeStolenActionCardButtons(yssaril));
        event.getMessage().delete().queue();
    }

    @ButtonHandler("returnFFToSpace_")
    public static void returnFightersToSpace(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                    int numff = unitHolder.getUnitCount(UnitType.Fighter, player.getColor());
                    AddUnitService.addUnits(event, tile, game, player.getColor(), numff + " ff");
                    RemoveUnitService.removeUnits(event, tile, game, player.getColor(), numff + " ff " + unitHolder.getName());
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " put all fighters in tile " + pos + " back into space");
        event.getMessage().delete().queue();
    }

    public static List<Button> getTradePlanetsWithHacanMechButtons(Player hacan, Player receiver, Game game) {
        List<Button> buttons = new ArrayList<>();
        if (!hacan.hasUnit("hacan_mech")) {
            return buttons;
        }
        for (String planet : hacan.getPlanetsAllianceMode()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            if (ButtonHelper.getUnitHolderFromPlanetName(planet, game).getUnitCount(UnitType.Mech,
                hacan.getColor()) > 0) {
                buttons.add(Buttons.gray("hacanMechTradeStepOne_" + planet + "_" + receiver.getFaction(),
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        return buttons;
    }

    public static List<Button> getRaghsCallButtons(Player player, Game game, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        Player saar = game.getPNOwner("ragh");
        saar = saar == null ? game.getPNOwner("sigma_raghs_call") : saar;
        if (saar == player || tile == null) {
            return buttons;
        }
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet && FoWHelper.playerHasUnitsOnPlanet(saar, tile, uH.getName())) {
                buttons.add(Buttons.gray("raghsCallStepOne_" + uH.getName(),
                    "Ragh's Call on " + Helper.getPlanetRepresentation(uH.getName(), game)));
            }
        }
        return buttons;
    }

    @ButtonHandler("rollForAmbush_")
    public static void rollAmbush(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getUnitHolders().get("space");
        int numCruisers = Math.min(2, space.getUnitCount(UnitType.Cruiser, player.getColor()));
        int remaining = 2 - numCruisers;
        int numDestroyers = Math.min(remaining, space.getUnitCount(UnitType.Destroyer, player.getColor()));

        String result = player.getFactionEmojiOrColor() + " rolling for ambush:\n";

        // Actually roll for each unit
        int totalHits = 0;
        StringBuilder resultBuilder = new StringBuilder(result);
        Map<UnitModel, Integer> playerUnits = CombatRollService.getUnitsInCombat(tile, space, player, event,
            CombatRollType.combatround, game);
        for (Map.Entry<UnitModel, Integer> entry : playerUnits.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit;
            if ("cruiser".equalsIgnoreCase(unit.getBaseType()) || "destroyer".equalsIgnoreCase(unit.getBaseType())) {
                if ("cruiser".equalsIgnoreCase(unit.getBaseType())) {
                    numOfUnit = numCruisers;
                } else {
                    numOfUnit = numDestroyers;
                }
                if (numOfUnit < 1) {
                    continue;
                }
                int toHit = unit.getCombatDieHitsOnForAbility(CombatRollType.combatround);
                int modifierToHit = 0;
                int extraRollsForUnit = 0;
                int numRollsPerUnit = 1;
                int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
                List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
                player.setExpectedHitsTimes10(
                    player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));
                int hitRolls = DiceHelper.countSuccesses(resultRolls);
                totalHits += hitRolls;
                String unitRoll = CombatMessageHelper.displayUnitRoll(unit, toHit, modifierToHit, numOfUnit,
                    numRollsPerUnit, extraRollsForUnit, resultRolls, hitRolls);
                resultBuilder.append(unitRoll);
            }

        }
        result = resultBuilder.toString();
        result += CombatMessageHelper.displayHitResults(totalHits);
        player.setActualHits(player.getActualHits() + totalHits);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void checkForNaaluPN(Game game) {
        game.setStoredValue("Play Naalu PN", "");
        for (Player player : game.getRealPlayers()) {
            boolean naalu = false;
            for (String pn : player.getPromissoryNotes().keySet()) {
                if ("gift".equalsIgnoreCase(pn) && !player.ownsPromissoryNote("gift")) {
                    naalu = true;
                }
            }
            if (naalu) {
                String msg = player.getRepresentation()
                    + " you have the option to pre-play _Gift of Prescience_."
                    + " _Gift of Prescience_ is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + "Feel free to ignore this message if you don't intend to play it any time soon.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("resolvePreassignment_Play Naalu PN", "Pre-play Gift of Prescience"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    @ButtonHandler("tnelisDeploy_")
    public static void tnelisDeploy(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 mech " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " deployed 1 Daedalon (Tnelis mech) on "
                + Helper.getPlanetRepresentation(planet, game) + ".");
        List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, game.getTileFromPlanet(planet), planet);
        if (players.size() > 1) {
            StartCombatService.startGroundCombat(players.get(0), players.get(1), game, event,
                ButtonHelper.getUnitHolderFromPlanetName(planet, game), game.getTileFromPlanet(planet));
        }
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        options.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " pay 3r for the mech", options);
    }

    @ButtonHandler("rohdhnaDeploy_")
    public static void rohdhnaDeploy(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];

        if (!planet.equalsIgnoreCase("space")) {
            AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 mech " + planet);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " deployed 1 mech on "
                    + Helper.getPlanetRepresentation(planet, game) + ".");
        } else {
            Tile tile = game.getTileByPosition(buttonID.split("_")[2]);
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech ");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " deployed 1 mech in the space area of "
                    + tile.getRepresentation() + ".");
        }
        event.getMessage().delete().queue();
    }

    @ButtonHandler("raghsCallStepOne_")
    public static void resolveRaghsCallStepOne(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        PromissoryNoteHelper.resolvePNPlay("ragh", player, game, event);
        List<Button> buttons = new ArrayList<>();
        Player saar = game.getPNOwner("ragh");
        saar = saar == null ? game.getPNOwner("sigma_raghs_call") : saar;
        for (String planet : saar.getPlanetsAllianceMode()) {
            if (!planet.equalsIgnoreCase(origPlanet)) {
                buttons.add(Buttons.gray("raghsCallStepTwo_" + origPlanet + "_" + planet,
                    "Relocate to " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + ", please choose which planet to relocate the Saar ground forces to.",
            buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("hacanMechTradeStepOne_")
    public static void resolveHacanMechTradeStepOne(Player hacan, Game game, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String planet : hacan.getPlanetsAllianceMode()) {
            if (!planet.equalsIgnoreCase(origPlanet)) {
                buttons.add(
                    Buttons.gray("hacanMechTradeStepTwo_" + origPlanet + "_" + receiverFaction + "_" + planet,
                        "Relocate to " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(hacan.getCorrectChannel(),
            hacan.getRepresentationUnfogged() + "Choose which planet to relocate your units to", buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("productionBiomes_")
    public static void resolveProductionBiomesStep2(Player hacan, Game game, ButtonInteractionEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Could not resolve target player, please resolve manually.");
            return;
        }
        int oldTg = player.getTg();
        player.setTg(oldTg + 2);
        String message = player.getFactionEmojiOrColor() + " gained 2 trade goods due to " + FactionEmojis.Hacan
            + "_Production Biomes_ (" + oldTg + "->" + player.getTg() + ").";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), message);
        }
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 2);
        event.getMessage().delete().queue();
    }

    public static void resolveProductionBiomesStep1(Player hacan, Game game, GenericInteractionCreateEvent event) {
        int oldStratCC = hacan.getStrategicCC();
        if (oldStratCC < 1) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(),
                hacan.getFactionEmoji() + " did not have any command tokens in their strategy pool. #rejected");
            return;
        }

        int oldTg = hacan.getTg();
        hacan.setTg(oldTg + 4);
        hacan.setStrategicCC(oldStratCC - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(hacan, game, event);
        MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), hacan.getFactionEmoji()
            + " spent a command token from their strategy pool to gain 4 trade goods (" + oldTg + "->" + hacan.getTg() + ").");
        ButtonHelperAbilities.pillageCheck(hacan, game);
        ButtonHelperAgents.resolveArtunoCheck(hacan, 4);

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == hacan) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("productionBiomes_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("productionBiomes_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(hacan.getCorrectChannel(),
            hacan.getRepresentationUnfogged() + " choose who should get the two trade goods.", buttons);
    }

    @ButtonHandler("startQDN")
    public static void resolveQuantumDataHubNodeStep1(Player hacan, Game game, ButtonInteractionEvent event) {
        hacan.exhaustTech("qdn");
        int oldStratCC = hacan.getStrategicCC();
        if (oldStratCC < 1) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(),
                hacan.getFactionEmoji() + " has no tokens in their strategy pool. #rejected");
            return;
        }

        hacan.setStrategicCC(oldStratCC - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(hacan, game, event);

        MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(),
            hacan.getFactionEmoji() + " spent 3 trade goods and a command token from their strategy pool"
                + hacan.gainTG(-3) + ".");

        List<Button> buttons = getSwapSCButtons(game, "qdn", hacan);
        MessageHelper.sendMessageToChannelWithButtons(hacan.getCorrectChannel(),
            hacan.getRepresentationUnfogged() + " choose who you wish to swap strategy cards with.", buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getSwapSCButtons(Game game, String type, Player hacan) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == hacan) {
                continue;
            }
            if (p2.getSCs().size() > 1) {
                if (game.isFowMode()) {
                    buttons.add(Buttons.gray("selectBeforeSwapSCs_" + p2.getFaction() + "_" + type, p2.getColor()));
                } else {
                    Button button = Buttons.gray("selectBeforeSwapSCs_" + p2.getFaction() + "_" + type, " ");
                    String factionEmojiString = p2.getFactionEmoji();
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    buttons.add(button);
                }
            } else {
                if (game.isFowMode()) {
                    buttons.add(Buttons.gray("swapSCs_" + p2.getFaction() + "_" + type + "_"
                        + p2.getSCs().toArray()[0] + "_" + hacan.getSCs().toArray()[0], p2.getColor()));
                } else {
                    Button button = Buttons.gray("swapSCs_" + p2.getFaction() + "_" + type + "_"
                        + p2.getSCs().toArray()[0] + "_" + hacan.getSCs().toArray()[0], " ");
                    String factionEmojiString = p2.getFactionEmoji();
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    buttons.add(button);
                }
            }
        }
        return buttons;
    }

    @ButtonHandler("selectBeforeSwapSCs_")
    public static void resolveSelectedBeforeSwapSC(Player player, Game game, String buttonID) {
        String type = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve second player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Integer sc : p2.getSCs()) {
            for (Integer sc2 : player.getSCs()) {
                buttons.add(Buttons.gray("swapSCs_" + p2.getFaction() + "_" + type + "_" + sc + "_" + sc2,
                    "Swap " + Helper.getSCName(sc2, game) + " with " + Helper.getSCName(sc, game) + "."));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " choose which strategy card you wish to swap with.",
            buttons);
    }

    @ButtonHandler("swapSCs_")
    public static void resolveSwapSC(Player player1, Game game, ButtonInteractionEvent event, String buttonID) {
        String type = buttonID.split("_")[2];
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(player1.getCorrectChannel(), "Could not resolve second player, please resolve manually.");
            return;
        }

        if ("qdn".equalsIgnoreCase(type)) {
            ButtonHelperAbilities.pillageCheck(player2, game);
            MessageHelper.sendMessageToChannel(player2.getCorrectChannel(), player2.getFactionEmoji()
                + " gained 3 consolation trade goods from having their strategy card stolen via _Quantumn Datahub Node_ " + player2.gainTG(3) + ".");
        }

        int player1SC = Integer.parseInt(buttonID.split("_")[4]);
        int player2SC = Integer.parseInt(buttonID.split("_")[3]);
        player1.addSC(player2SC);
        player1.removeSC(player1SC);
        player2.addSC(player1SC);
        player2.removeSC(player2SC);
        String sb = player1.getRepresentation() + " swapped strategy card with " + player2.getRepresentation() + "\n" +
            "> " + player2.getRepresentationNoPing() + CardEmojis.getSCFrontFromInteger(player2SC) + " :arrow_right: " + CardEmojis.getSCFrontFromInteger(player1SC) + "\n" +
            "> " + player1.getRepresentationNoPing() + CardEmojis.getSCFrontFromInteger(player1SC) + " :arrow_right: " + CardEmojis.getSCFrontFromInteger(player2SC) + "\n";
        MessageHelper.sendMessageToChannel(player2.getCorrectChannel(), sb);
        event.getMessage().delete().queue();
        StartPhaseService.startActionPhase(event, game);
    }

    @ButtonHandler("raghsCallStepTwo_")
    public static void resolveRaghsCallStepTwo(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String newPlanet = buttonID.split("_")[2];
        Player saar = game.getPNOwner("ragh");
        saar = saar == null ? game.getPNOwner("sigma_raghs_call") : saar;
        UnitHolder oriPlanet = ButtonHelper.getUnitHolderFromPlanetName(origPlanet, game);
        Map<UnitKey, Integer> units = new HashMap<>(oriPlanet.getUnits());
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            UnitModel unit = saar.getUnitFromUnitKey(unitKey);
            if (saar.unitBelongsToPlayer(unitKey) && unit.getIsGroundForce()) {
                String unitName = unitKey.unitName();
                int amount = unitEntry.getValue();
                RemoveUnitService.removeUnits(event, game.getTileFromPlanet(origPlanet), game, saar.getColor(), amount + " " + unitName + " " + origPlanet);
                AddUnitService.addUnits(event, game.getTileFromPlanet(newPlanet), game, saar.getColor(), amount + " " + unitName + " " + newPlanet);
            }
        }
        String ident = player.getFactionEmojiOrColor();
        String message2 = ident + " moved the ground forces on the planet "
            + Helper.getPlanetRepresentation(origPlanet, game) + " to "
            + Helper.getPlanetRepresentation(newPlanet, game);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannel(saar.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
        }
        event.getMessage().delete().queue();
    }

    @ButtonHandler("hacanMechTradeStepTwo_")
    public static void resolveHacanMechTradeStepTwo(Player hacan, Game game, ButtonInteractionEvent event, String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        String newPlanet = buttonID.split("_")[3];
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(),
                "Could not resolve second player, please resolve manually.");
            return;
        }

        UnitHolder oriPlanet = ButtonHelper.getUnitHolderFromPlanetName(origPlanet, game);
        Map<UnitKey, Integer> units = new HashMap<>(oriPlanet.getUnits());
        String unitGroupRef = "";
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            int amount = unitEntry.getValue();
            String unitName = unitKey.unitName();

            RemoveUnitService.removeUnits(event, game.getTileFromPlanet(origPlanet), game, hacan.getColor(), amount + " " + unitName + " " + origPlanet);
            AddUnitService.addUnits(event, game.getTileFromPlanet(newPlanet), game, hacan.getColor(), amount + " " + unitName + " " + newPlanet);
            if (!unitGroupRef.equalsIgnoreCase("units")) {
                if (!unitName.equalsIgnoreCase("mech")) {
                    unitGroupRef = "units";
                } else if (unitGroupRef.equalsIgnoreCase("mech")) {
                    unitGroupRef = "mechs";
                } else {
                    unitGroupRef = "mech";
                }
            }
        }
        AddPlanetService.addPlanet(p2, origPlanet, game, event, false);

        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Buttons.gray("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Buttons.gray("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = hacan.getFactionEmoji();
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(origPlanet, game)
            + " to " + p2.getFactionEmojiOrColor()
            + " and relocated the " + unitGroupRef + " to " + Helper.getPlanetRepresentation(newPlanet, game);
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        goAgainButtons.add(Buttons.green("demandSomething_" + p2.getColor(), "Expect something in return"));
        MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), message2);
        if (game.isFowMode() || !game.isNewTransactionMethod()) {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannelWithButtons(hacan.getPrivateChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
                MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
            } else {

                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
        }
        event.getMessage().delete().queue();
    }

    @ButtonHandler("cabalRelease_")
    public static void resolveReleaseButton(Player cabal, Game game, String buttonID, ButtonInteractionEvent event) {
        String faction = buttonID.split("_")[1];
        Player player = game.getPlayerFromColorOrFaction(faction);
        if (player == null) {
            MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(),
                "Could not resolve second player, please resolve manually.");
            return;
        }

        String unit = buttonID.split("_")[2];
        RemoveUnitService.removeUnits(event, cabal.getNomboxTile(), game, player.getColor(), unit);
        if (cabal == player) {
            MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(),
                cabal.getRepresentationUnfogged() + ", you released 1 of your own " + unit + " from your prison.");
        } else if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(),
                cabal.getRepresentationUnfogged() + ", you released 1 " + player.getFactionEmojiOrColor()
                    + " " + unit + " from your prison.");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", a " + unit + " of yours has been released from prison.");
        } else {
            MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(),
                cabal.getRepresentationUnfogged() + ", you released 1 " + unit + " from your prison and returned it to " + player.getRepresentationUnfogged() + ".");
        }
        if (!cabal.getNomboxTile().getUnitHolders().get("space").getUnits()
            .containsKey(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor()))) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    @ButtonHandler("kolleccRelease_")
    public static void resolveKolleccReleaseButton(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), unit);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " put 1 captured " + unit + " in the space area of "
                + tile.getRepresentationForButtons(game, player) + " using **Shroud of Lith** ability.");
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit);
        if (!player.getNomboxTile().getUnitHolders().get("space").getUnits()
            .containsKey(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor()))) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    public static void checkBlockadeStatusOfEverything(Player player, Game game, GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            if (doesPlayerHaveAnyCapturedUnits(p2, player)) {
                if (isCabalBlockadedByPlayer(player, game, p2)) {
                    releaseAllUnits(p2, game, player, event);
                }
            }
        }
    }

    public static boolean doesPlayerHaveAnyCapturedUnits(Player cabal, Player blockader) {
        if (cabal == blockader) {
            return false;
        }
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            List<UnitKey> unitKeys = new ArrayList<>(unitHolder.getUnits().keySet());
            for (UnitKey unitKey : unitKeys) {
                if (blockader.unitBelongsToPlayer(unitKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void releaseAllUnits(Player cabal, Game game, Player blockader, GenericInteractionCreateEvent event) {
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            List<UnitKey> unitKeys = new ArrayList<>(unitHolder.getUnits().keySet());
            for (UnitKey unitKey : unitKeys) {
                if (blockader.unitBelongsToPlayer(unitKey)) {
                    int amount = unitHolder.getUnits().get(unitKey);
                    String unit = unitKey.unitName();
                    RemoveUnitService.removeUnits(event, cabal.getNomboxTile(), game, blockader.getColor(), amount + " " + unit);
                    MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(),
                        cabal.getRepresentationUnfogged() + " released " + amount + " " + blockader.getFactionEmojiOrColor() + " " + unit + " from prison due to blockade");
                    if (cabal != blockader) {
                        MessageHelper.sendMessageToChannel(blockader.getCorrectChannel(),
                            blockader.getRepresentationUnfogged() + " " + amount + " " + unit + " of yours was released from prison.");
                    }
                }
            }
        }
    }

    public static List<Button> getReleaseButtons(Player cabal, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnits().keySet()) {
                for (Player player : game.getRealPlayers()) {
                    if (player.unitBelongsToPlayer(unitKey)) {
                        String unitName = unitKey.unitName();
                        String buttonID = "cabalRelease_" + player.getFaction() + "_" + unitName;
                        if (game.isFowMode()) {
                            buttons.add(Buttons.gray(buttonID, player.getColor() + " " + unitName));
                        } else {
                            buttons.add(Buttons.gray(buttonID, unitName).withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
                        }
                    }
                }
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        return buttons;
    }

    public static List<Button> getKolleccReleaseButtons(Player kollecc, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : kollecc.getNomboxTile().getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnits().keySet()) {
                if (kollecc.unitBelongsToPlayer(unitKey)) {
                    String unitName = unitKey.unitName();
                    String buttonID = "kolleccRelease_" + unitName;
                    buttons.add(Buttons.gray(buttonID, "Release 1  " + unitName).withEmoji(Emoji.fromFormatted(kollecc.getFactionEmoji())));
                }

            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        return buttons;
    }

    public static List<Button> getPharadnInf2ReleaseButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        boolean hasInf = false;
        for (UnitHolder unitHolder : player.getNomboxTile().getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnits().keySet()) {
                if (unitKey.getUnitType() == UnitType.Infantry && unitHolder.getUnits().get(unitKey) > 0) {
                    hasInf = true;
                }
            }
        }
        if (!hasInf) {
            return buttons;
        }
        for (Tile tile : game.getTileMap().values()) {
            if (ButtonHelperAgents.doesTileHaveAStructureInIt(player, tile)) {
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    buttons.add(Buttons.green(player.finChecker() + "pharadnInf2Revive_" + planet.getName(), planet.getPlanetModel().getAutoCompleteName()));
                }
            }
        }
        return buttons;
    }

    @ButtonHandler("pharadnInf2Revive_")
    public static void pharadnInf2Revive(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "infantry");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " put 1 captured infantry on the planet of "
                + Mapper.getPlanet(planet).getAutoCompleteName() + " using the Pharadn Infantry 2 ability.");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planet);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("capture1Pharad")
    public static void capture1Pharad(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " captured 1 destroyed infantry via their PN");
        AddUnitService.addUnits(event, player.getNomboxTile(), game, player.getColor(), "1 inf");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startPharadnInfRevive")
    public static void startPharadnInfRevive(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = getPharadnInf2ReleaseButtons(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " use buttons to release an infantry onto a planet", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void checkForGeneticRecombination(Player voter, Game game) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == voter) {
                continue;
            }
            if (p2.hasTechReady("gr")) {
                if (game.getStoredValue("Genetic Recombination " + p2.getFaction())
                    .contains(voter.getFaction())) {
                    p2.exhaustTech("gr");
                    String msg = p2.getRepresentation(false, true) + " is using _Genetic Recombination_ to force "
                        + voter.getRepresentation(false, true)
                        + " to vote a particular way. The technology has been exhausted, the owner should elaborate on which way to vote.";
                    MessageHelper.sendMessageToChannel(voter.getCorrectChannel(), msg);
                    if (voter.getFleetCC() > 0) {
                        msg = voter.getRepresentation(false, true) +
                            " has the option to remove 1 command token from their fleet pool to ignore the effect of _Genetic Recombination_.";
                        List<Button> conclusionButtons = new ArrayList<>();
                        String finChecker = "FFCC_" + voter.getFaction() + "_";
                        Button accept = Buttons.blue(finChecker
                            + "geneticRecombination_"
                            + p2.getUserID() + "_"
                            + "accept", "Vote For Chosen Outcome");
                        conclusionButtons.add(accept);
                        Button decline = Buttons.red(finChecker
                            + "geneticRecombination_"
                            + p2.getUserID() + "_"
                            + "decline", "Remove Fleet Token");
                        conclusionButtons.add(decline);
                        MessageHelper.sendMessageToChannelWithButtons(voter.getCorrectChannel(), msg,
                            conclusionButtons);
                    }
                }
            }
        }
    }

    @ButtonHandler("geneticRecombination")
    public static void resolveGeneticRecombination(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] fields = buttonID.split("_");
        Player mahactPlayer = game.getPlayer(fields[1]);
        String choice = fields[2];
        if (choice.equals("accept")) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getRepresentation()
                    + " has chosen to vote for the outcome indicated by "
                    + mahactPlayer.getRepresentation()
                    + ".");
        } else {
            player.setFleetCC(player.getFleetCC() - 1);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getRepresentation()
                    + " has removed a command token from their fleet pool and may vote in any manner that they wish.");
            ButtonHelper.checkFleetInEveryTile(player, game, event);
        }
        event.getMessage().delete().queue();
    }

    @ButtonHandler("dihmohnfs_")
    public static void resolveDihmohnFlagship(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation() + " is using the Maximus (the Dih-Mohn flagship) to produce units. They may produce up to 2 units with a combined cost of 4. They cannot produce ships if enemy ships are in the system. ");
        String pos = buttonID.replace("dihmohnfs_", "");
        List<Button> buttons;
        // Muaat agent works here as it's similar so no need to add more fluff
        buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveImpressmentPrograms(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " May produce 1 ship in the explored planet system.");
        String pos = buttonID.replace("dsdihmy_", "");
        List<Button> buttons;
        // Sling relay works for this
        buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "sling", "place");
        String message = player.getRepresentation() + " Use the buttons to produce 1 ship. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveVadenSCDebt(Player player, int sc, Game game, GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(sc) && p2 != player && p2.hasAbility("fine_print")) {
                SendDebtService.sendDebt(player, p2, 1);
                CommanderUnlockCheckService.checkPlayer(p2, "vaden");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you sent 1 debt token to "
                        + p2.getFactionEmojiOrColor() + " due to their **Fine Print** ability.");
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2
                    .getRepresentationUnfogged() + " you collected 1 debt token from "
                    + player.getFactionEmojiOrColor()
                    + " due to your **Fine Print** ability. This is technically optional, done automatically for convenience.");
                break;
            }

        }
    }

    public static void increaseMykoMech(Game game) {
        int amount;
        if (!game.getStoredValue("mykoMech").isEmpty()) {
            amount = Integer.parseInt(game.getStoredValue("mykoMech"));
            amount += 1;
        } else {
            amount = 1;
        }
        game.setStoredValue("mykoMech", "" + amount);
    }

    public static void decreaseMykoMech(Game game) {
        int amount = 0;
        if (!game.getStoredValue("mykoMech").isEmpty()) {
            amount = Integer.parseInt(game.getStoredValue("mykoMech"));
            amount -= 1;
        }
        if (amount < 0) {
            amount = 0;
        }
        game.setStoredValue("mykoMech", "" + amount);
    }

    @ButtonHandler("resolveMykoMech")
    public static void resolveMykoMech(Player player, Game game) {
        decreaseMykoMech(game);
        List<Button> buttons = new ArrayList<>(ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, game));
        String message = player.getRepresentationUnfogged() + " Use buttons to replace 1 infantry with 1 mech";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void resolveMykoMechCheck(Player player, Game game) {
        if (!player.hasUnit("mykomentori_mech") || game.getStoredValue("mykoMech").isEmpty()) {
            return;
        }
        int amount = Integer.parseInt(game.getStoredValue("mykoMech"));
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("resolveMykoMech", "Replace Infantry With Mech"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        if (amount > 0) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " you have " + amount
                    + " mech" + (amount == 1 ? "" : "s") + " that may replace infantry.",
                buttons);
        }
    }

    @ButtonHandler("deployMykoSD_")
    public static void deployMykoSD(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        int requiredNum = 4;
        Tile tile = game.getTileFromPlanet(planet);
        if (player.hasTech("dsmykosd")) {
            requiredNum = 3;
        }
        if (ButtonHelper.getNumberOfInfantryOnPlanet(planet, game, player) + 1 > requiredNum) {
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), requiredNum + " infantry " + planet);
            AddUnitService.addUnits(event, tile, game, player.getColor(), "sd " + planet);
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji()
                + " deployed 1 space dock on " + planet + " by removing " + requiredNum + " infantry");
            event.getMessage().delete().queue();
            if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5
                && !player.getFaction().contains("franken")) {
                player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                    Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji() + " does not have " + requiredNum + " infantry on " + planet
                    + " and therefore cannot DEPLOY the space dock");
        }
    }

    public static void offerMahactInfButtons(Player player, Game game) {
        String message = player.getRepresentationUnfogged() + " Resolve Mahact infantry loss using the buttons";
        List<Button> buttons = gainOrConvertCommButtons(player, false);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void offerHoldingCompanyButtons(Player player, Game game) {
        String message = player.getRepresentationUnfogged()
            + ", please resolve **Holding Company** commodity gain using the buttons. Remember you get 1 commodity per attachment you've given out. ";
        List<Button> buttons = gainOrConvertCommButtons(player, false);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("offerNecrophage")
    public static void offerNekrophageButtons(Player player, ButtonInteractionEvent event) {
        String message = player.getRepresentationUnfogged() + " Resolve **Necrophage** ability using buttons. ";
        List<Button> buttons = gainOrConvertCommButtons(player, true);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("pharadnPNUse")
    public static void pharadnPNUse(Player player, Game game, ButtonInteractionEvent event) {
        String message = player.getRepresentationUnfogged() + " Drop 2 infantry on a planet you control with buttons. ";
        game.setStoredValue("pharadnPNUsed", "true");
        List<Button> buttons = new ArrayList<>(
            Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static List<Button> gainOrConvertCommButtons(Player player, boolean deleteAfter) {
        List<Button> buttons = new ArrayList<>();
        String ffcc = player.getFinsFactionCheckerPrefix();
        if (deleteAfter) {
            buttons.add(Buttons.green(ffcc + "convertComms_1", "Convert 1 Commodity to Trade Good", MiscEmojis.Wash));
            buttons.add(Buttons.blue(ffcc + "gainComms_1", "Gain 1 Commodity", MiscEmojis.comm));
        } else {
            buttons.add(Buttons.green(ffcc + "convertComms_1_stay", "Convert 1 Commodity to Trade Good", MiscEmojis.Wash));
            buttons.add(Buttons.blue(ffcc + "gainComms_1_stay", "Gain 1 Commodity", MiscEmojis.comm));
        }
        buttons.add(Buttons.red("deleteButtons", "Done Resolving"));
        return buttons;
    }

    public static void checkIihqAttachment(Game game) {
        Tile tile = game.getMecatolTile();
        if (tile == null) return; // no mecatol tile
        for (Planet mecatol : tile.getPlanetUnitHolders()) {
            if (Constants.MECATOLS.contains(mecatol.getName())) {
                if (mecatol.getTokenList().contains(Constants.ATTACHMENT_IIHQ_1)) mecatol.removeToken(Constants.ATTACHMENT_IIHQ_1);
                if (mecatol.getTokenList().contains(Constants.ATTACHMENT_IIHQ_2)) mecatol.removeToken(Constants.ATTACHMENT_IIHQ_2);

                for (Player player : game.getRealPlayers()) {
                    if (!player.hasIIHQ()) continue;
                    if (player.controlsMecatol(true)) {
                        mecatol.addToken(Constants.ATTACHMENT_IIHQ_1);
                    } else {
                        mecatol.addToken(Constants.ATTACHMENT_IIHQ_2);
                    }
                }
            }
        }
    }

    public static void keleresIIHQCCGainCheck(Player player, Game game) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (p2.hasIIHQ()) {
                List<Button> buttons = ButtonHelper.getGainCCButtons(p2);
                String trueIdentity = p2.getRepresentationUnfogged();
                String message = trueIdentity + " Due to the Custodia Vigilia legendary ability, you gain 2 command tokens when someone scores an **Imperial** (Mecatol Rex) point.";
                String message2 = trueIdentity + "! Your current command tokens are " + p2.getCCRepresentation() + ". Use buttons to gain command tokens.";
                game.setStoredValue("originalCCsFor" + p2.getFaction(), p2.getCCRepresentation());
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message);
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2, buttons);
                break;
            }
        }
    }

    public static void resolveResearchAgreementCheck(Player player, String tech, Game game) {
        if (game.getPNOwner("ra") == null || game.getPNOwner("ra") != player ||
            !Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse("").isEmpty()) {
            return;
        }
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !p2.getPromissoryNotes().containsKey("ra") || p2.getTechs().contains(tech)) {
                continue;
            }
            String owner = game.getPNOwner("ra").getFaction().equalsIgnoreCase("jolnar") ? "Jol-Nar player" : "_Research Agreement_ owner";
            String msg = p2.getRepresentationUnfogged() + " the " + owner + " has researched the technology "
                + Mapper.getTech(AliasHandler.resolveTech(tech)).getRepresentation(false)
                + ". Use the below button if you wish to play _Research Agreement_ to gain it.";
            Button transact = Buttons.green("resolvePNPlay_ra_" + AliasHandler.resolveTech(tech),
                "Acquire " + Mapper.getTech(AliasHandler.resolveTech(tech)).getName());
            List<Button> buttons = new ArrayList<>();
            buttons.add(transact);
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void resolveMilitarySupportCheck(Player player, Game game) {
        if (game.getPlayerFromColorOrFaction(Mapper.getPromissoryNote("ms").getOwner()) != player) {
            return;
        }
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !p2.getPromissoryNotes().containsKey("ms")) {
                continue;
            }
            String msg = p2.getRepresentationUnfogged()
                + ", the " + (game.isFrankenGame() ? "_Military Support_ owner" : "Sol player") + " has started their turn, use the button to play _Military Support_ if you so wish.";
            Button transact = Buttons.green("resolvePNPlay_ms", "Play Military Support");
            List<Button> buttons = new ArrayList<>();
            buttons.add(transact);
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void resolveExpLook(Player player, Game game, GenericInteractionCreateEvent event, String deckType) {
        List<String> deck = game.getExploreDeck(deckType);
        String traitNameWithEmoji = ExploreEmojis.getTraitEmoji(deckType) + deckType;
        if (deck.isEmpty() && game.getExploreDiscard(deckType).isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "The" + traitNameWithEmoji + " exploration deck & discard is empty - nothing to look at.");
            return;
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "The top card of the " + traitNameWithEmoji + " exploration deck has been sent to " + player.getFactionEmojiOrColor() + " `#cards-info` thread.");
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, false) + " looked at top card of the "
                + traitNameWithEmoji + " exploration deck. The card has been sent to their `#cards-info` thread.");
        }

        // Cards Info Message
        String topCard = deck.getFirst();
        game.setStoredValue("lastExpLookedAt" + player.getFaction() + deckType, topCard);
        ExploreModel explore = Mapper.getExplore(topCard);
        String message = "You looked at the top of the " + traitNameWithEmoji + " exploration deck and saw _" + explore.getName() + "_.";
        MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, explore.getRepresentationEmbed());
    }

    public static void resolveExpDiscard(Player player, Game game, ButtonInteractionEvent event, String deckType) {
        List<String> deck = game.getExploreDeck(deckType);
        String topCard = deck.getFirst();
        ExploreModel top = Mapper.getExplore(topCard);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " discarded the top card of the " + deckType + " deck. The discarded card was _" + top.getName() + "_.");
        game.discardExplore(topCard);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveKolleccAbilities(Player player, Game game) {
        if (player.hasAbility("treasure_hunters")) {
            // resolve treasure hunters
            String msg = player.getRepresentation() + ", please choose which exploration deck to look at the top card of.";

            String deckType = "industrial";
            List<String> deck = game.getExploreDeck(deckType);
            String msg2 = StringUtils.capitalize(deckType);
            if (game.getStoredValue("lastExpLookedAt" + player.getFaction() + deckType)
                .equalsIgnoreCase(deck.getFirst())) {
                msg2 += " (Same as last time)";
            }
            Button transact1 = Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveExp_Look_industrial", msg2);
            deckType = "hazardous";
            deck = game.getExploreDeck(deckType);
            msg2 = StringUtils.capitalize(deckType);
            if (game.getStoredValue("lastExpLookedAt" + player.getFaction() + deckType)
                .equalsIgnoreCase(deck.getFirst())) {
                msg2 += " (Same as last time)";
            }
            Button transact2 = Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveExp_Look_hazardous", msg2);
            deckType = "cultural";
            deck = game.getExploreDeck(deckType);
            msg2 = StringUtils.capitalize(deckType);
            if (game.getStoredValue("lastExpLookedAt" + player.getFaction() + deckType)
                .equalsIgnoreCase(deck.getFirst())) {
                msg2 += " (Same as last time)";
            }
            Button transact3 = Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveExp_Look_cultural", msg2);
            List<Button> buttons1 = new ArrayList<>();
            buttons1.add(transact1);
            buttons1.add(transact2);
            buttons1.add(transact3);
            buttons1.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg,
                buttons1);

            Button transact = Buttons.green(player.getFinsFactionCheckerPrefix() + "relic_look_top",
                "Look at top of Relic Deck");
            msg2 = player.getRepresentation() + ", you may also look at the top card of the relic deck.";
            List<Button> buttons2 = new ArrayList<>();
            buttons2.add(transact);
            buttons2.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2,
                buttons2);
        }
        if (player.getPromissoryNotes().containsKey("dspnkoll") && !player.ownsPromissoryNote("dspnkoll")) {
            String msg = player.getRepresentationUnfogged() + ", please play _AI Survey_ if you so wish.";
            Button transact = Buttons.green("resolvePNPlay_dspnkoll", "Play AI Survey");
            List<Button> buttons = new ArrayList<>();
            buttons.add(transact);
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void offerKolleccPNButtons(Game game, Player player) {
        Button transact1 = Buttons.green("explore_look_All", "Peek at Industrial/Hazardous/Cultural decks");
        Button transact2 = Buttons.green("relic_look_top", "Peek at Relic deck");
        List<Button> buttons = new ArrayList<>();
        buttons.add(transact1);
        buttons.add(transact2);
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        String message = "Use buttons to select how to use _AI Survey_.";
        // System.out.println(player.getFaction() + " is playing PN KOLLEC");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("replacePDSWithFS_")
    public static void replacePDSWithFS(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("replacePDSWithFS_", "");
        String planet = buttonID;
        String message = player.getFactionEmojiOrColor() + " replaced " + UnitEmojis.pds + " on " + Helper.getPlanetRepresentation(planet, game) + " with a " + UnitEmojis.flagship;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), "flagship");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit("pds"), player.getColor());
        tile.removeUnit(planet, unitKey, 1);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("startChaosMapping")
    public static void firstStepOfChaos(Game game, Player p1, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(p1, game);
        for (Tile tile : tiles) {
            Button tileButton = Buttons.green("produceOneUnitInTile_" + tile.getPosition() + "_chaosM",
                tile.getRepresentationForButtons(game, p1));
            buttons.add(tileButton);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            p1.getFactionEmoji() + " has chosen to use _Chaos Mapping_.");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Select which tile you would like to Chaos Map in.", buttons);
    }

    public static boolean isCabalBlockadedByPlayer(Player player, Game game, Player cabal) {
        if (cabal == player) {
            return false;
        }
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, cabal, UnitType.Spacedock);
        if (tiles.isEmpty()) {
            return false;
        }
        for (Tile tile : tiles) {
            if (FoWHelper.playerHasShipsInSystem(player, tile) && !FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                return true;
            }
        }
        return false;
    }

    public static void cabalEatsUnit(Player player, Game game, Player cabal, int amount, String unit, GenericInteractionCreateEvent event) {
        cabalEatsUnit(player, game, cabal, amount, unit, event, false);
    }

    public static void cabalEatsUnitIfItShould(Player player, Game game, Player owner, int amount, String unit, GenericInteractionCreateEvent event, Tile tile, UnitHolder uH) {
        Player cabal = Helper.getPlayerFromAbility(game, "devour");
        if (cabal == owner) {
            cabal = null;
        }
        if (cabal == null) {
            for (Player p2 : game.getRealPlayers()) {
                if (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_vuilraith_flagship_1", p2, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_vuilraith_flagship_2", p2, tile)) {
                    cabal = p2;
                }
            }
        }
        if (cabal != null && (!uH.getPlayersUnitListOnHolder(cabal).isEmpty()
            || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_vuilraith_flagship_1", cabal, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_vuilraith_flagship_2", cabal, tile))) {
            cabalEatsUnit(player, game, cabal, amount, unit, event, false);
        }
    }

    public static void mentakHeroProducesUnit(Player player, Game game, Player mentak, int amount, String unit, GenericInteractionCreateEvent event, Tile tile) {
        String unitP = AliasHandler.resolveUnit(unit);
        if (mentak == player || unitP.contains("sd") || unitP.contains("pd")
            || (unitP.contains("ws") && !mentak.hasWarsunTech()) || unitP.contains("mf") || unitP.contains("gf")
            || (mentak.getAllianceMembers().contains(player.getFaction()))) {
            return;
        }
        String msg = mentak.getRepresentationUnfogged() + " placed " + amount + " of the " + unit
            + (amount == 1 ? "" : "s") + " which "
            + player.getRepresentation()
            + " just had destroyed in the active system using Ipswitch, Loose Cannon, the Mentak Hero.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        AddUnitService.addUnits(event, tile, game, mentak.getColor(), amount + " " + unit);
    }

    public static void cabalEatsUnit(Player player, Game game, Player cabal, int amount, String unit, GenericInteractionCreateEvent event, boolean cabalAgent) {
        String msg = cabal.getRepresentationUnfogged() + " has failed to eat " + amount + " of the " + unit
            + "s owned by "
            + player.getRepresentationNoPing() + " because they were blockaded. Womp Womp.";
        String unitP = AliasHandler.resolveUnit(unit);
        if (unitP.contains("sd") || unitP.contains("pd")
            || (cabal.getAllianceMembers().contains(player.getFaction()) && !cabalAgent)) {
            return;
        }
        if (!isCabalBlockadedByPlayer(player, game, cabal)) {
            msg = cabal.getFactionEmoji() + " has **Devour**'d " + amount + " of the " + unit + "s owned by "
                + player.getRepresentationNoPing() + ". Chomp chomp.";
            String color = player.getColor();

            if (unitP.contains("ff") || unitP.contains("gf")) {
                color = cabal.getColor();
            }
            msg = msg.replace("infantrys", "infantry");

            AddUnitService.addUnits(event, cabal.getNomboxTile(), game, color, amount + " " + unit);
        }
        MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(), msg);

    }

    @ButtonHandler("letnevMechRes_")
    public static void resolveLetnevMech(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit + " " + planet);
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentationNoPing() + " replaced 1 of their infantry with 1 " + unit + " on "
                + Helper.getPlanetRepresentation(planet, game) + " using the mech's DEPLOY ability.");
        options.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " pay 2r for it please", options);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveDarkPactCheck(Game game, Player sender, Player receiver, int numOfComms) {
        for (String pn : sender.getPromissoryNotesInPlayArea()) {
            if ("dark_pact".equalsIgnoreCase(pn)
                && game.getPNOwner(pn).getFaction().equalsIgnoreCase(receiver.getFaction())) {
                if (numOfComms == sender.getCommoditiesTotal()) {
                    MessageChannel channel = game.getActionsChannel();
                    if (game.isFowMode()) {
                        channel = sender.getPrivateChannel();
                    }
                    String message = sender.getRepresentationUnfogged()
                        + " _Dark Pact_ triggered, your trade goods have increased by 1.";
                    sender.setTg(sender.getTg() + 1);
                    MessageHelper.sendMessageToChannel(channel, message);
                    message = receiver.getRepresentationUnfogged()
                        + " _Dark Pact_ triggered, your trade goods have increased by 1.";
                    receiver.setTg(receiver.getTg() + 1);
                    if (game.isFowMode()) {
                        channel = receiver.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, message);
                    // ButtonHelperAbilities.pillageCheck(sender, game);
                    ButtonHelperAgents.resolveArtunoCheck(sender, 1);
                    // ButtonHelperAbilities.pillageCheck(receiver, game);
                    ButtonHelperAgents.resolveArtunoCheck(receiver, 1);
                }
            }
        }
    }

    public static List<Button> getUnitButtonsForVortex(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock);
        if (tiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Couldn't find any Dimensional Tears.");
            return List.of();
        }
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tiles.getFirst().getPosition(), player, false);
        for (Tile tile : tiles) {
            adjTiles.addAll(FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false));
        }

        Set<String> colorsBlockading = tiles.stream()
            .map(tile -> tile.getUnitHolders().get("space"))
            .flatMap(unitHolder -> unitHolder.getUnitColorsOnHolder().stream())
            .collect(Collectors.toSet());
        Set<UnitKey> availableUnits = adjTiles.stream()
            .map(game::getTileByPosition)
            .flatMap(tile -> tile.getUnitHolders().values().stream())
            .flatMap(uh -> uh.getUnits().entrySet().stream().filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey))
            .filter(unitKey -> !colorsBlockading.contains(unitKey.getColorID()))
            .collect(Collectors.toSet());
        return availableUnits.stream()
            .filter(unitKey -> vortexButtonAvailable(game, unitKey))
            .map(unitKey -> buildVortexButton(game, unitKey))
            .toList();
    }

    public static boolean vortexButtonAvailable(Game game, UnitKey unitKey) {
        int baseUnitCap = switch (unitKey.getUnitType()) {
            case Infantry, Fighter -> 10000;
            case Destroyer, Cruiser -> 8;
            case Dreadnought -> 5;
            case Mech, Carrier -> 4;
            case Warsun -> 2;
            case Flagship -> 1;
            default -> 0; // everything else that can't be captured
        };
        int unitCap = game.getPlayerByColorID(unitKey.getColorID())
            .filter(p -> p.getUnitCap(unitKey.asyncID()) != 0).map(p -> p.getUnitCap(unitKey.asyncID()))
            .orElse(baseUnitCap);
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, unitKey) < unitCap;
    }

    public static Button buildVortexButton(Game game, UnitKey unitKey) {
        String faction = game.getPlayerByColorID(unitKey.getColorID()).map(Player::getFaction).get();
        String buttonID = "cabalVortextCapture_" + unitKey.unitName() + "_" + faction;
        String buttonText = String.format("Capture %s %s", unitKey.getColor(),
            unitKey.getUnitType().humanReadableName());
        return Buttons.red(buttonID, buttonText, unitKey.unitEmoji());
    }

    @ButtonHandler("cabalVortextCapture_")
    public static void resolveVortexCapture(String buttonID, Player player, Game game, ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentationUnfogged() + ", a " + unit + " of yours has been consumed by _Vortex_.");
        cabalEatsUnit(p2, game, player, 1, unit, event);
        event.getMessage().delete().queue();
    }

    public static void offerTerraformButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<String> extraAllowedPlanets = List.of("custodiavigilia", "ghoti");
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet planetReal = game.getPlanetsInfo().get(planet);
            boolean oneOfThree = planetReal != null && isNotBlank(planetReal.getOriginalPlanetType()) &&
                List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || extraAllowedPlanets.contains(planet.toLowerCase())) {
                buttons.add(Buttons.green("terraformPlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Please choose the planet you wish to _Terraform_.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void offerAutomatonsButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<String> extraAllowedPlanets = List.of("custodiavigilia", "ghoti");
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            boolean oneOfThree = game.getPlanetsInfo().get(planet) != null
                && List.of("industrial", "cultural", "hazardous").contains(game.getPlanetsInfo().get(planet).getOriginalPlanetType());
            if (oneOfThree || extraAllowedPlanets.contains(planet.toLowerCase())) {
                buttons.add(Buttons.green("automatonsPlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Please choose which planet you wish to attach _Automatons_ to.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void offerBentorPNButtons(Player player, Game game) {
        List<String> extraAllowedPlanets = List.of("custodiavigilia", "ghoti");
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet planetReal = game.getPlanetsInfo().get(planet);
            boolean oneOfThree = planetReal != null && isNotBlank(planetReal.getOriginalPlanetType()) &&
                List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || extraAllowedPlanets.contains(planet.toLowerCase())) {
                buttons.add(Buttons.green("bentorPNPlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Please choose the planet you wish to attach the _Encryption Key_ to.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void resolveOlradinPN(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            buttons.add(Buttons.gray("khraskHeroStep4Exhaust_" + player.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        String message = player.getRepresentationUnfogged() + ", please choose the planet that you wish to exhaust.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        message = player.getRepresentationUnfogged() + ", please choose the planet (other than Mecatol Rex) that you wish to ready.";
        buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            if (Constants.MECATOLS.contains(planet)) {
                continue;
            }
            buttons.add(Buttons.gray("khraskHeroStep4Ready_" + player.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void resolveSigmaLizixPN(Player player, Game game, GenericInteractionCreateEvent event) {
        List<String> improvedPlanets = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                game.getPlanetsInfo().get(planet).addToken("attachment_sigma_cyber.png");
                improvedPlanets.add(planet);
            }
        }
        if (improvedPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", applied _Cybernetic Enhancements_, but you have no technology specialty planets.\n"
                    + "-# If you gain some, you will need to add the attachment manually.");
            return;
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + ", applied _Cybernetic Enhancements_, to " + String.join(", ", improvedPlanets) + ".\n"
                + "-# If you gain some more or lose any, you will need to add or remove the attachment manually.");
    }

    public static void offerGledgeBaseButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet planetReal = game.getPlanetsInfo().get(planet);
            boolean oneOfThree = planetReal != null && isNotBlank(planetReal.getOriginalPlanetType()) &&
                List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || planet.contains("custodiavigilia") || planet.contains("ghoti")) {
                buttons.add(Buttons.green("gledgeBasePlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Please choose which planet to attach the _Gledge Base_ to.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("scourPlanet_")
    public static void resolveScour(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " used the **Scour** ability to discard 1 action card and ready "
            + Helper.getPlanetRepresentation(planet, game) + ".";
        player.refreshPlanet(planet);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " use buttons to discard an action card.",
            ActionCardHelper.getDiscardActionCardButtons(player, false));
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void offerVeldyrButtons(Player player, Game game, String pnID) {
        List<String> extraAllowedPlanets = List.of("custodiavigilia", "ghoti");
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet planetReal = game.getPlanetsInfo().get(planet);
            boolean oneOfThree = planetReal != null && isNotBlank(planetReal.getOriginalPlanetType()) &&
                List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || extraAllowedPlanets.contains(planet.toLowerCase())) {
                buttons.add(Buttons.green("veldyrAttach_" + planet + "_" + pnID,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = player.getRepresentationUnfogged()
            + ", please choose which planet you wish to attach the _Branch Office_ to.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
    }

    public static List<Button> getButtonsToTakeSomeonesAC(Player thief, Player victim) {
        List<Button> takeACs = new ArrayList<>();
        String secretScoreMsg = "_ _\nClick a button to take 1 action card";
        List<Button> acButtons = ActionCardHelper.getToBeStolenActionCardButtons(victim);
        if (!acButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
            ThreadChannel cardsInfoThreadChannel = thief.getCardsInfoThread();
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
        return takeACs;
    }

    @ButtonHandler("takeAC_")
    public static void mageon(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("takeAC_", "");
        int acNum = Integer.parseInt(buttonID.split("_")[0]);

        String faction2 = buttonID.split("_")[1];
        Player player2 = game.getPlayerFromColorOrFaction(faction2);
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Could not find player, please resolve manually.");
            return;
        }
        if (!player2.getActionCards().containsValue(acNum)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that action, so no action card was added/lost.");
            return;
        }
        String ident2 = player2.getRepresentation();
        String message2 = player.getRepresentationUnfogged() + " took action card #" + acNum + " from " + ident2 + ".";
        String acID = null;
        for (Map.Entry<String, Integer> so : player2.getActionCards().entrySet()) {
            if (so.getValue().equals(acNum)) {
                acID = so.getKey();
            }
        }
        if (game.isFowMode()) {
            message2 = "Someone took action card #" + acNum + " from " + player2.getColor() + ".";
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
        }
        player2.removeActionCard(acNum);
        player.setActionCard(acID);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + "Acquired " + acID);
        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
            "# " + player2.getRepresentationUnfogged() + " Lost " + acID + " to a players ability");
        ActionCardHelper.sendActionCardInfo(game, player2);
        ActionCardHelper.sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, player);
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("terraformPlanet_")
    public static void terraformPlanet(Player player, String buttonID, ButtonInteractionEvent event, Game game) {
        String planet = buttonID.replace("terraformPlanet_", "");
        game.getPlanetsInfo().get(planet).addToken(Constants.ATTACHMENT_TITANSPN_PNG);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Attached _Terraform_ to " + Helper.getPlanetRepresentation(planet, game) + ".");
        game.setStoredValue("terraformedPlanet", planet);
        CommanderUnlockCheckService.checkPlayer(player, "sol");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("automatonsPlanet_")
    public static void automatonsPlanet(String buttonID, ButtonInteractionEvent event, Game game) {
        String planet = buttonID.replace("automatonsPlanet_", "");
        game.getPlanetsInfo().get(planet).addToken("attachment_automatons.png");
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Attached _Automatons_ to " + Helper.getPlanetRepresentation(planet, game) + ".");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("bentorPNPlanet_")
    public static void bentorPNPlanet(String buttonID, ButtonInteractionEvent event, Game game) {
        String planet = buttonID.replace("bentorPNPlanet_", "");
        Planet planetReal = game.getPlanetsInfo().get(planet);
        planetReal.addToken("attachment_encryptionkey.png");
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Attached encryption key to " + Helper.getPlanetRepresentation(planet, game));
        event.getMessage().delete().queue();
    }

    @ButtonHandler("gledgeBasePlanet_")
    public static void gledgeBasePlanet(String buttonID, ButtonInteractionEvent event, Game game) {
        String planet = buttonID.replace("gledgeBasePlanet_", "");
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        unitHolder.addToken("attachment_gledgebase.png");
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Attached _Gledge Base_ to " + Helper.getPlanetRepresentation(planet, game) + ".");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("veldyrAttach_")
    public static void resolveBranchOffice(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String pnID = buttonID.split("_")[2];
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        switch (pnID) {
            case "dspnveld1" -> unitHolder.addToken("attachment_veldyrtaxhaven.png");
            case "dspnveld2" -> unitHolder.addToken("attachment_veldyrbroadcasthub.png");
            case "dspnveld3" -> unitHolder.addToken("attachment_veldyrreservebank.png");
            case "dspnveld4" -> unitHolder.addToken("attachment_veldyrorbitalshipyard.png");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            "Attached _Branch Office_ to " + Helper.getPlanetRepresentation(planet, game) + ".");
        if (game.getPNOwner(pnID).getLeaderIDs().contains("veldyrcommander")
            && !game.getPNOwner(pnID).hasLeaderUnlocked("veldyrcommander")) {
            CommanderUnlockCheckService.checkPlayer(game.getPNOwner(pnID), "veldyr");
        }
        event.getMessage().delete().queue();
    }

    public static List<Player> getPlayersWithBranchOffices(Game game, Player player) {
        List<Player> players = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            for (String pn : p2.getPromissoryNotes().keySet()) {
                if (pn.contains("dspnveld")) {
                    players.add(p2);
                    break;
                }
            }
        }
        return players;
    }

    public static int getNumberOfBranchOffices(Game game, Player player) {
        int count = 0;
        for (String pn : player.getPromissoryNotes().keySet()) {
            if (pn.contains("dspnveld")) {
                count++;
            }
        }

        return count;
    }

    public static List<Button> getCreussIFFTypeOptions() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("creussIFFStart_alpha", "Alpha", MiscEmojis.CreussAlpha));
        buttons.add(Buttons.green("creussIFFStart_beta", "Beta", MiscEmojis.CreussBeta));
        buttons.add(Buttons.blue("creussIFFStart_gamma", "Gamma", MiscEmojis.CreussGamma));
        return buttons;
    }

    @ButtonHandler("creussMechStep3_")
    public static void creussMechStep3(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String tilePos = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        String tokenName = "creuss" + type;
        Tile tile = game.getTileByPosition(tilePos);
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        String msg = player.getRepresentation() + " moved " + MiscEmojis.getCreussWormhole(tokenName)
            + " " + type + " wormhole to " + tile.getRepresentationForButtons(game, player);
        for (Tile tile_ : game.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                msg += " (from " + tile_.getRepresentationForButtons(game, player) + ")";
                break;
            }
        }
        msg += ".";
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                String name = uH.getName();
                if ("space".equals(name)) {
                    name = "";
                }
                RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 mech " + name);
                msg += "\n" + player.getFactionEmoji() + " removed 1 mech from " + tile.getRepresentation()
                    + " (" + uH.getName() + ").";
                break;
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("nivynMechStep2_")
    public static void nivynMechStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String tilePos = buttonID.split("_")[1];
        String tokenName = "wound";
        Tile tile = game.getTileByPosition(tilePos);
        StringBuilder sb = new StringBuilder(player.getRepresentation());
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        sb.append(" moved the **Wound** token to ")
            .append(tile.getRepresentationForButtons(game, player));
        for (Tile tile_ : game.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                sb.append(" (from ").append(tile_.getRepresentationForButtons(game, player)).append(")");
                break;
            }
        }
        boolean removed = false;
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            int count = uH.getUnitCount(UnitType.Mech, player.getColor())
                - uH.getDamagedUnitCount(UnitType.Mech, player.getColorID());

            if (count > 0 && !removed) {
                removed = true;
                uH.addDamagedUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()), 1);
                sb.append("\n ").append(player.getFactionEmoji()).append(" damaged 1 mech on ")
                    .append(tile.getRepresentation()).append("(").append(uH.getName()).append(")");
            }
        }
        String msg = sb.toString();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        CommanderUnlockCheckService.checkPlayer(player, "nivyn");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("creussMechStep2_")
    public static void creussMechStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        String tilePos = buttonID.split("_")[1];
        buttons.add(Buttons.red("creussMechStep3_" + tilePos + "_alpha", "Alpha", MiscEmojis.CreussAlpha));
        buttons.add(Buttons.green("creussMechStep3_" + tilePos + "_beta", "Beta", MiscEmojis.CreussBeta));
        buttons.add(Buttons.blue("creussMechStep3_" + tilePos + "_gamma", "Gamma", MiscEmojis.CreussGamma));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " choose the type of wormhole you wish to place in " + tilePos + ".",
            buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("creussMechStep1_")
    public static void creussMechStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)) {
            buttons.add(Buttons.green("creussMechStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged()
                + " choose the tile with the mech you wish to remove in order to place a Creuss wormhole.",
            buttons);
    }

    @ButtonHandler("nivynMechStep1_")
    public static void nivynMechStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)) {
            buttons.add(Buttons.green("nivynMechStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged()
                + " choose the tile where you wish to damage 1 mech and place the **Wound** token.",
            buttons);
    }

    @ButtonHandler("winnuPNPlay_")
    public static void resolveWinnuPN(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String scNum = buttonID.split("_")[1];
        int sc = Integer.parseInt(scNum);
        player.addFollowedSC(sc, event);
        PromissoryNoteHelper.resolvePNPlay("acq", player, game, event);
        String msg = player.getRepresentationUnfogged() + " you will be marked as having followed " + Helper.getSCName(sc, game)
            + " without having needed to spend a command token. Please still use the strategy card buttons to resolve the strategy card effect.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static List<Button> getGreyfireButtons(Game game) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            buttons.add(Buttons.green("greyfire_" + uH.getName(),
                Helper.getPlanetRepresentation(uH.getName(), game)));
        }
        return buttons;
    }

    @ButtonHandler("greyfire_")
    public static void resolveGreyfire(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = "infantry";
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit + " " + planet);
        String opponent = "their opponent's";
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet)) {
                RemoveUnitService.removeUnits(event, tile, game, p2.getColor(), "1 infantry " + planet);
                opponent = p2.getRepresentationNoPing();
                break;
            }
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getFactionEmoji() + " replaced 1 of their opponent's infantry with 1 " + unit + " on "
                    + Helper.getPlanetRepresentation(planet, game) + " using _Greyfire Mutagen_.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getRepresentationNoPing() + " replaced 1 of " + opponent + " infantry with 1 " + unit + " on "
                    + Helper.getPlanetRepresentation(planet, game) + " using _Greyfire Mutagen_.");
        }
        event.getMessage().delete().queue();
    }

    @ButtonHandler("creussIFFStart_")
    public static void resolveCreussIFFStart(Game game, @NotNull Player player, String buttonID, ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        List<Button> buttons = getCreusIFFLocationOptions(game, player, type);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " please select the tile you would like to place the " + type + " wormhole in.", buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("creussIFFResolve_")
    public static void resolveCreussIFF(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        String tokenName = "creuss" + type;
        Tile tile = game.getTileByPosition(pos);
        String msg;
        if (game.isFowMode() && !isTileCreussIFFSuitable(game, player, tile)) {
            msg = "Tile was not suitable for the _Creuss IFF_.";
            if (player.getTg() > 0) {
                player.setTg(player.getTg() - 1);
                msg += " You lost 1 trade good.";
            } else {
                if (player.getTacticalCC() > 0) {
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    msg += " You lost 1 command token from your tactic pool.";
                } else {
                    if (player.getFleetCC() > 0) {
                        player.setFleetCC(player.getFleetCC() - 1);
                        msg += " You lost 1 command token from your fleet pool.";
                    }
                }
            }
        } else {
            StringBuilder sb = new StringBuilder(player.getRepresentation());
            tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
            sb.append(" moved ").append(MiscEmojis.getCreussWormhole(tokenName)).append(" to ")
                .append(tile.getRepresentationForButtons(game, player));
            for (Tile tile_ : game.getTileMap().values()) {
                if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                    sb.append(" (from ").append(tile_.getRepresentationForButtons(game, player)).append(").");
                    break;
                }
            }
            msg = sb.toString();
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        for (Player p2 : game.getRealPlayers()) {
            CommanderUnlockCheckService.checkPlayer(p2, "ghost");
        }
        event.getMessage().delete().queue();
    }

    public static List<Button> getCreusIFFLocationOptions(Game game, @NotNull Player player, String type) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (isTileCreussIFFSuitable(game, player, tile) || (game.isFowMode()
                && !FoWHelper.getTilePositionsToShow(game, player).contains(tile.getPosition()))) {
                buttons.add(Buttons.green("creussIFFResolve_" + type + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        SortHelper.sortButtonsByTitle(buttons);
        return buttons;
    }

    public static boolean isTileCreussIFFSuitable(Game game, Player player, Tile tile) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            if (game.getTileFromPlanet(planet) == null) {
                continue;
            }
            if (game.getTileFromPlanet(planet).getPosition().equalsIgnoreCase(tile.getPosition())) {
                return true;
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                return false;
            }
            Tile hs = game.getTile(AliasHandler.resolveTile(p2.getFaction()));
            if (hs == null) {
                hs = p2.getHomeSystemTile();
            }
            if (hs != null && hs.getPosition().equalsIgnoreCase(tile.getPosition())) {
                return false;
            }
            if (tile == null || tile.getRepresentationForButtons(game, player).toLowerCase().contains("hyperlane")) {
                return false;
            }
        }
        return true;
    }

    @ButtonHandler("exhaustTCS_")
    public static void resolveTCSExhaust(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("absol_jr", "absoljr");
        String agent = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null)
            return;
        player.exhaustTech("tcs");
        Leader playerLeader = p2.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            if (agent.contains("titanprototype")) {
                p2.removeExhaustedRelic("titanprototype");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + " exhausted _ Temporal Command Suite_ to ready " + agent + ", owned by "
                        + p2.getColor() + ".");
                if (p2 != player) {
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + " _ Temporal Command Suite_ was exhausted by " + player.getColor()
                            + " to ready your " + agent + ".");
                }
                event.getMessage().delete().queue();
            }
            if (agent.contains("absol")) {
                p2.removeExhaustedRelic("absol_jr");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + " exhausted _ Temporal Command Suite_ to ready " + agent + ", owned by "
                        + p2.getColor() + ".");
                if (p2 != player) {
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + " _Temporal Command Suite_ was exhausted by " + player.getColor()
                            + " to ready your " + agent + ".");
                }
                event.getMessage().delete().queue();
            }
            return;
        }
        RefreshLeaderService.refreshLeader(p2, playerLeader, game);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " exhausted _Temporal Command Suite_ to ready " + agent + ", owned by "
                + p2.getColor() + ".");

        if (p2 != player) {
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                p2.getRepresentationUnfogged() + " _Temporal Command Suite_ was exhausted by " + player.getColor()
                    + " to ready your " + agent + ".");
        }
        event.getMessage().delete().queue();
    }

    public static boolean isNextToEmpyMechs(Game game, Player ACPlayer, Player EmpyPlayer) {
        if (ACPlayer == null || EmpyPlayer == null) {
            return false;
        }
        if (ACPlayer.getFaction().equalsIgnoreCase(EmpyPlayer.getFaction())) {
            return false;
        }
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, EmpyPlayer, UnitType.Mech);
        for (Tile tile : tiles) {
            Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tile.getPosition(), EmpyPlayer, true);
            for (String adjTile : adjTiles) {
                Tile adjT = game.getTileMap().get(adjTile);
                if (FoWHelper.playerHasUnitsInSystem(ACPlayer, adjT)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Button> getLanefirATSButtons(Player p1, Player p2) {
        List<Button> ats = new ArrayList<>();

        for (int i = 0; i < p1.getAtsCount(); i++) {
            ats.add(Buttons.gray("FFCC_" + p1.getFaction() + "_" + "lanefirATS_" + (i + 1), String.valueOf(i + 1)));
        }

        for (int i = 0; i < p2.getAtsCount(); i++) {
            ats.add(Buttons.gray("FFCC_" + p2.getFaction() + "_" + "lanefirATS_" + (i + 1), String.valueOf(i + 1)));
        }

        return ats;
    }

    @ButtonHandler("salvageOps_")
    public static void resolveSalvageOps(Player player, ButtonInteractionEvent event, String buttonID, Game game) {
        ButtonHelper.deleteTheOneButton(event);
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        player.setTg(player.getTg() + 1);
        if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
            String type = "sling";
            List<Button> buttons;
            buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type,
                "placeOneNDone_dontskip");
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String message = player.getRepresentation()
                + " Use the buttons to produce 1 ship that was destroyed in the combat. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        String msg = player.getRepresentation() + " used _Salvage Operations_ to gain 1 trade good (you now have " + player.getTg()
            + " trade good" + (player.getTg() == 1 ? "" : "s") + ").";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    @ButtonHandler("lanefirATS_")
    public static void resolveLanefirATS(Player player, ButtonInteractionEvent event, String buttonID) {
        String count = buttonID.split("_")[1];
        int origATS = player.getAtsCount();

        if (player.getAtsCount() < Integer.parseInt(count)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getRepresentation(true, false) + " does not have " + (count == "1" ? "a commodity" : count + " commodities") +
                    " to remove from _ATS Armaments_. Current count: " + player.getAtsCount());
            return;
        }

        player.setAtsCount(player.getAtsCount() - Integer.parseInt(count));

        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentation(true, false) + " removed " + count + " commodities from _ATS Armaments_ ("
                + origATS + "->" + player.getAtsCount() + ")");
    }

    @ButtonHandler("rohdhnaIndustrious_")
    public static void resolveRohDhnaIndustrious(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String tilePos = buttonID.split("_")[1];
        String toRemove = buttonID.split("_")[2];
        String planet = toRemove.split(" ")[1];
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        AddUnitService.addUnits(event, game.getTileByPosition(tilePos), game, player.getColor(), "warsun");
        RemoveUnitService.removeUnits(event, game.getTileByPosition(tilePos), game, player.getColor(), toRemove);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " replaced " + UnitEmojis.spacedock + " on " + Helper.getPlanetRepresentationPlusEmoji(planet) + " with a " + UnitEmojis.warsun);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Click the names of the planets you wish to exhaust to pay the " + MiscEmojis.Resources_6, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getRohDhnaRecycleButtons(Game game, Player player) {
        List<UnitKey> availableUnits = new ArrayList<>();
        Map<UnitKey, Integer> units = game.getTileByPosition(game.getActiveSystem()).getUnitHolders()
            .get("space").getUnits();
        for (UnitKey unit : units.keySet()) {
            if (Objects.equals(unit.getColor(), player.getColor()) && (unit.getUnitType() == UnitType.Cruiser
                || unit.getUnitType() == UnitType.Carrier || unit.getUnitType() == UnitType.Dreadnought)) {
                // if unit is not in the list, add it
                if (!availableUnits.contains(unit)) {
                    availableUnits.add(unit);
                }
            }
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unit : availableUnits) {
            buttons.add(Buttons.green("FFCC_" + player.getFaction() + "_rohdhnaRecycle_" + unit.unitName(), unit.getUnitType().humanReadableName(), unit.unitEmoji()));
        }

        if (!buttons.isEmpty()) {
            buttons.add(Buttons.red("FFCC_" + player.getFaction() + "_deleteButtons", "Decline"));
        }

        return buttons;
    }

    @ButtonHandler("rohdhnaRecycle_")
    public static void resolveRohDhnaRecycle(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String unitName = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 " + unitName);
        UnitModel unit = Mapper.getUnit(unitName);
        int toGain = (int) unit.getCost() - 1;

        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentationUnfoggedNoPing() + " recycled " + unit.getUnitEmoji() + " " + unit.getName()
                + " for " + toGain + " trade good" + (toGain == 1 ? "" : "s") + " " + player.gainTG(toGain) + ".");

        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, toGain);

        if (player.hasAbility("rohdhna_mech") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4) {
            List<Button> buttons = new ArrayList<>();
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                String id = "rohdhnaDeploy_" + unitHolder.getName() + "_" + tile.getPosition();
                String label;
                if (unitHolder.getName().equalsIgnoreCase("space")) {
                    label = "Deploy Mech in space";
                } else {
                    label = "Deploy Mech on " + Helper.getPlanetRepresentation(unitHolder.getName(), game);
                    if (!player.getPlanetsAllianceMode().contains(unitHolder.getName())) {
                        continue;
                    }
                }
                buttons.add(Buttons.green(id, label, FactionEmojis.rohdhna));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation() + ", you may deploy a mech on a planet you control or the space area.", buttons);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("edynCommanderSODraw")
    public static void edynCommanderSODraw(ButtonInteractionEvent event, Player player, Game game) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "edyncommander")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " you don't have Kadryn, the Edyn commander, silly.");
        }
        String message = "Drew a secret objective instead of scoring a public objective, using Kadryn, the Edyn commander.";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message += " Drew a second secret objective due to **Plausible Deniability**.";
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("quash")
    public static void quash(ButtonInteractionEvent event, Player player, Game game) {
        int stratCC = player.getStrategicCC();
        player.setStrategicCC(stratCC - 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            "The agenda has been **Quash**'d. " + player.getRepresentationUnfogged()
                + " has spent a command token from their strategy pool (" + stratCC + " -> " + (stratCC - 1) + ").");
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Quash");
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount;
        if (agendaCount.isEmpty()) {
            aCount = 0;
        } else {
            aCount = Integer.parseInt(agendaCount) - 1;
        }
        game.setStoredValue("agendaCount", aCount + "");
        String agendaid = game.getCurrentAgendaInfo().split("_")[2];
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = game.revealAgenda(false);
            Map<String, Integer> discardAgendas = game.getDiscardAgendas();
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "The hidden agenda was _" + agendaName + "_; it has been placed in the agenda discard pile.");
        }
        AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nullificationField_")
    public static void nullificationField(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String color = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        Player attacker = game.getPlayerFromColorOrFaction(color);
        ButtonHelper.resolveNullificationFieldUse(player, attacker, game, tile, event);
    }

    @ButtonHandler("mahactMechHit_")
    public static void mahactMechHit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String color = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        Player attacker = game.getPlayerFromColorOrFaction(color);
        ButtonHelper.resolveMahactMechAbilityUse(player, attacker, game, tile, event);
    }
}
