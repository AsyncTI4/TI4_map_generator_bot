package ti4.service.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class AddPlanetService {

    public static void addPlanet(Player player, String planet, Game game) {
        addPlanet(player, planet, game, null, false);
    }

    public static void addPlanet(Player player, String planet, Game game, GenericInteractionCreateEvent event, boolean setup) {
        boolean doubleCheck = Helper.doesAllianceMemberOwnPlanet(game, planet, player);
        player.addPlanet(planet);

        player.exhaustPlanet(planet);
        if ("mirage".equals(planet)) {
            game.clearPlanetsCache();
        }
        Tile tile = game.getTileFromPlanet(planet);
        Planet unitHolder = game.getPlanetsInfo().get(planet);

        if (unitHolder.getTokenList().contains("token_freepeople.png")) {
            unitHolder.removeToken("token_freepeople.png");
        }
        if (unitHolder.getTokenList().contains("token_tomb.png") && player.hasAbility("ancient_empire")) {
            unitHolder.removeToken("token_tomb.png");
            AddUnitService.addUnits(event, player.getNomboxTile(), game, player.getColor(), "2 inf");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " you captured 2 infantry from a tomb token.");
        }

        List<String> mecatols = Constants.MECATOLS;
        if (mecatols.contains(planet) && player.hasIIHQ()) {
            PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
            unitHolder.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
            unitHolder.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
        }
        String color = player.getColor();
        if (color != null && !"null".equals(color)) {
            String ccID = Mapper.getControlID(color);
            String ccPath = Mapper.getCCPath(ccID);
            if (ccPath != null) {
                unitHolder.addControl(ccID);
            }
            if (unitHolder.getTokenList().contains(Constants.CUSTODIAN_TOKEN_PNG)) {
                unitHolder.removeToken(Constants.CUSTODIAN_TOKEN_PNG);
                game.scorePublicObjective(player.getUserID(), 0);
                MessageChannel channel = game.getMainGameChannel();
                if (game.isFowMode()) {
                    channel = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannel(channel, "# " + player.getRepresentation() + " scored custodians!");
                String message2 = player.getRepresentationUnfogged() + " Click the names of the planets you wish to exhaust to spend " + MiscEmojis.Influence_6;
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                Button doneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
                buttons.add(doneExhausting);
                if (!player.hasAbility("blood_ties")) {
                    MessageHelper.sendMessageToChannelWithButtons(channel, message2, buttons);
                }
            }
        }
        boolean alreadyOwned = false;
        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player) {
                List<String> planets = player_.getPlanets();
                if (planets.contains(planet)) {
                    if (player_.getExhaustedPlanetsAbilities().contains(planet)) {
                        player.exhaustPlanetAbility(planet);
                    }
                    if (player_.isRealPlayer()) {
                        alreadyOwned = true;
                    }
                    player_.removePlanet(planet);
                    CommanderUnlockCheckService.checkPlayer(player_, "uydai");
                    List<String> relics = new ArrayList<>(player_.getRelics());
                    if (planet.equalsIgnoreCase("mr")) {
                        String customPOName = "Ixthian Rex Point";
                        if (game.getRevealedPublicObjectives().get(customPOName) != null) {
                            int shardID = game.getRevealedPublicObjectives().get(customPOName);
                            game.unscorePublicObjective(player_.getUserID(), shardID);
                            game.scorePublicObjective(player.getUserID(), shardID);
                            String msg2 = player_.getRepresentation() + " lost rex and lost a victory point. "
                                + player.getRepresentation()
                                + " gained rex and a victory point.";
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                                msg2);
                            Helper.checkEndGame(game, player);
                        }
                    }
                    for (String relic : relics) {
                        if (relic.contains("shard")
                            && ButtonHelper.isPlanetLegendaryOrHome(planet, game, true, player_)
                            && !doubleCheck) {
                            String msg2 = player_.getRepresentation() + " lost shard and lost a victory point. "
                                + player.getRepresentation()
                                + " gained shard and a victory point.";
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                                msg2);
                            player_.removeRelic(relic);
                            player.addRelic(relic);
                            String customPOName = "Shard of the Throne";
                            if (relic.contains("absol_")) {
                                int absolShardNum = Integer.parseInt(StringUtils.right(relic, 1));
                                customPOName = "Shard of the Throne (" + absolShardNum + ")";
                            }
                            int shardID = game.getRevealedPublicObjectives().get(customPOName);
                            game.unscorePublicObjective(player_.getUserID(), shardID);
                            game.scorePublicObjective(player.getUserID(), shardID);
                            Helper.checkEndGame(game, player);
                        }
                    }
                    List<String> currentPns = new ArrayList<>(player_.getPromissoryNotesInPlayArea());
                    for (String pn : currentPns) {
                        PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pn);
                        if (pnModel.getAttachment().isPresent()
                            && unitHolder.getTokenList().stream().anyMatch(s -> s.contains(pnModel.getAttachment().get()))) {
                            player_.removePromissoryNote(pn);
                            player.setPromissoryNote(pn);
                            player.addPromissoryNoteToPlayArea(pn);
                        }
                    }
                    if (Mapper.getPlanet(planet) != null &&
                        !"action_deck_2".equals(game.getAcDeckID()) &&
                        !game.isACInDiscard("Reparations")) {
                        String msg = player_.getRepresentation()
                            + " has a window to play _Reparations_ for the taking of "
                            + Mapper.getPlanet(planet).getName() + ".";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    }
                }
            }
        }
        if ((alreadyOwned || player.hasAbility("contagion_blex") || player.hasAbility("plague_reservoir"))
            && player.hasTech("dxa") && !doubleCheck) {
            String msg10 = player.getRepresentationUnfogged()
                + " you may have an opportunity to use Dacxive Animators on "
                + Helper.getPlanetRepresentation(planet, game)
                + ". Click to confirm a combat occurred and to add 1 infantry or delete these buttons.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg10,
                ButtonHelper.getDacxiveButtons(planet, player));
        }
        if (!alreadyOwned && game.isMinorFactionsMode() && player.isRealPlayer()
            && (unitHolder.getPlanetModel().getPlanetTypes().contains(PlanetType.FACTION))) {
            PlanetModel p = Mapper.getPlanet(unitHolder.getName());
            if (!p.getFactionHomeworld().equalsIgnoreCase(player.getFaction())) {
                unitHolder.addToken("attachment_threetraits.png");
            }
        }

        if (game.isMinorFactionsMode() && unitHolder.getTokenList().contains("attachment_threetraits.png")
            && player.isRealPlayer() && tile != null) {
            boolean ownsThemAll = true;
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (!player.getPlanets().contains(uH.getName())) {
                    ownsThemAll = false;
                    break;
                }
            }
            if (ownsThemAll) {
                PlanetModel p = Mapper.getPlanet(unitHolder.getName());
                if (p != null && p.getFactionHomeworld() != null
                    && !player.hasLeader(p.getFactionHomeworld() + "commander")) {
                    String leaderID = p.getFactionHomeworld() + "commander";
                    if (leaderID.toLowerCase().contains("keleres")) {
                        leaderID = "kelerescommander";
                    }
                    player.addLeader(leaderID);
                    game.addFakeCommander(leaderID);
                    UnlockLeaderService.unlockLeader(leaderID, game, player);
                    for (Player p2 : game.getRealPlayers()) {
                        if (p2 == player) {
                            continue;
                        }
                        if (p2.hasLeader(leaderID)) {
                            p2.removeLeader(leaderID);
                        }
                    }
                }

            }
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "naazcommander") && !setup) {
            if (alreadyOwned && "mirage".equalsIgnoreCase(planet)) {
                List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, unitHolder, player);
                if (event != null && buttons != null && !buttons.isEmpty()) {
                    String message = player.getFactionEmoji() + " Click button to explore " + Helper.getPlanetRepresentation(planet, game);
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                }
            }
            alreadyOwned = false;
        }
        if (!game.getPhaseOfGame().contains("agenda")) {
            game.setStoredValue("planetsTakenThisRound",
                game.getStoredValue("planetsTakenThisRound") + "_" + planet);
        }

        if ((game.getPhaseOfGame().contains("agenda")
            || (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))))
            && player.hasAbility("scavenge") && !doubleCheck && !setup) {
            String fac = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), fac
                + " gained 1 trade good from **Scavenge** (" + player.getTg() + "->" + (player.getTg() + 1)
                + "). Reminder that you do not legally have this trade good prior to exploring, and that this was mandatory.");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        if ((game.getPhaseOfGame().contains("agenda")
            || (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))))
            && player.hasTech("absol_dxa") && !doubleCheck && !setup) {
            String message = "";
            if (tile != null && planet != null) {
                Set<String> tokenList = ButtonHelper.getUnitHolderFromPlanetName(planet, game).getTokenList();
                boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
                if (!containsDMZ) {
                    AddUnitService.addUnits(event, tile, game, player.getColor(), "inf " + planet);
                    message = player.getFactionEmoji() + ColorEmojis.getColorEmojiWithName(player.getColor()) + UnitEmojis.infantry
                        + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planet)
                        + " due to Absol's Daxcive, however this placement is __optional__.";
                } else {
                    message = "Planet has the _Demilitarized Zone_ attached, so no infantry could be placed.";
                }
            } else {
                message = "Tile was null, no infantry placed.";
            }
            MessageHelper.sendMessageToEventChannel(event, message);
        }

        if (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))
            && player.hasUnexhaustedLeader("vaylerianagent") && !setup) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("exhaustAgent_vaylerianagent_" + player.getFaction(), "Use Vaylerian Agent", FactionEmojis.vaylerian));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentationUnfogged() + " you may use "
                + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Yvin Korduul, the Vaylerian" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                + " agent, to draw 1 action card.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2, buttons);
        }

        if (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))
            && player.hasAbility("scour") && !setup) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("scourPlanet_" + planet, "Use Scour", FactionEmojis.vaylerian));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentationUnfogged()
                + " if you have not already used **Scour** this tactical action, you may discard 1 action card to ready "
                + Helper.getPlanetRepresentation(planet, game) + ".";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2, buttons);
        }

        if (tile != null && game.getActivePlayer() == player
            && game.playerHasLeaderUnlockedOrAlliance(player, "freesystemscommander") && !tile.isHomeSystem()
            && FoWHelper.playerHasShipsInSystem(player, tile)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("produceOneUnitInTile_" + tile.getPosition() + "_sling", "Produce 1 Ship", FactionEmojis.freesystems));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentationUnfogged() + " you may produce 1 ship in the system due to President Cyhn, the Free Systems Commander.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2, buttons);
        }

        if (game.getActivePlayer() == player
            && game.playerHasLeaderUnlockedOrAlliance(player, "cymiaecommander")) {
            List<Button> saarButton = new ArrayList<>();
            saarButton.add(Buttons.green("cymiaeCommanderRes_" + planet,
                "Discard Action Card for Mech on " + Helper.getPlanetRepresentation(planet, game)));
            saarButton.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                    + " due to Koryl Ferax, the Cymiae Commander, you may discard 1 action card here to place or move 1 mech on "
                    + Helper.getPlanetRepresentation(planet, game)
                    + ". Do not do this prior to exploring. It is an \"after\", while exploring is a \"when\".",
                saarButton);
        }

        if (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))
            && (player.hasUnit("mykomentori_spacedock") || player.hasUnit("mykomentori_spacedock2"))
            && !doubleCheck && event != null) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("deployMykoSD_" + planet, "Deploy Space Dock " + planet));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "sd") < 3) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                        + " if you have the correct amount of infantry (3 or 4), you may remove them and DEPLOY 1 space dock on "
                        + planet + " using the buttons.",
                    buttons);

            }
        }
        if (ButtonHelper.isPlayerElected(game, player, "minister_exploration") && event != null) {
            String fac = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                fac + " gained 1 trade good from _Minister of Exploration_ (" + player.getTg() + "->" + (player.getTg() + 1)
                    + ").");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);

        }

        if (!alreadyOwned && !doubleCheck && (!"mirage".equals(planet)) && !game.isBaseGameMode()) {
            List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, unitHolder, player);
            if (event != null && buttons != null && !buttons.isEmpty()) {
                String message = player.getFactionEmoji() + " Click button to explore " + Helper.getPlanetRepresentation(planet, game) + ".";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
        }

        if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
            || game.getPhaseOfGame().contains("agenda")) && player.hasUnit("saar_mech")
            && event != null && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
            List<Button> saarButton = new ArrayList<>();
            saarButton.add(Buttons.green("saarMechRes_" + planet,
                "Pay 1 Trade Good for a Mech on " + Helper.getPlanetRepresentation(planet, game)));
            saarButton.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                    + " you may pay 1 trade good to place 1 Scavenger mech here. Do not do this prior to exploring. It is an \"after\", while exploring is a \"when\".",
                saarButton);
        }
        if (player.hasTech("ie") && unitHolder.getResources() > 0) {
            String message = player.getRepresentation() + " Click the button to resolve an _Integrated Economy_ build on "
                + Helper.getPlanetRepresentation(planet, game) + ".";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.blue("integratedBuild_" + planet,
                "Integrated on " + Helper.getPlanetRepresentation(planet, game)));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        CommanderUnlockCheckService.checkPlayer(player, "sol", "vaylerian", "olradin", "xxcha", "sardakk");
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "freesystems");
        if (Constants.MECATOLS.contains(planet) && player.controlsMecatol(true)) {
            CommanderUnlockCheckService.checkPlayer(player, "winnu");
        }
    }
}
