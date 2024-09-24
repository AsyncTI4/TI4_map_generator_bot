package ti4.commands.planet;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.leaders.UnlockLeader;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public class PlanetAdd extends PlanetAddRemove {
    public PlanetAdd() {
        super(Constants.PLANET_ADD, "Add or transfer a planet card to your player area");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        doAction(player, planet, game, event, false);
    }

    public static void doAction(Player player, String planet, Game game) {
        doAction(player, planet, game, null, false);
    }

    public static void doAction(Player player, String planet, Game game, GenericInteractionCreateEvent event, boolean setUP) {
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
        if (Constants.MR.equalsIgnoreCase(planet) && player.hasTech("iihq")) {
            Planet mecatolRex = unitHolder;
            PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
            mecatolRex.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
            mecatolRex.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
        }
        String color = player.getColor();
        boolean moveTitanPN = false;
        if (color != null && !"null".equals(color)) {
            String ccID = Mapper.getControlID(color);
            String ccPath = Mapper.getCCPath(ccID);
            if (ccPath != null) {
                unitHolder.addControl(ccID);
            }
            if (unitHolder.getTokenList().contains(Constants.ATTACHMENT_TITANSPN_PNG)) {
                moveTitanPN = true;
            } else if (unitHolder.getTokenList().contains(Constants.CUSTODIAN_TOKEN_PNG)) {
                unitHolder.removeToken(Constants.CUSTODIAN_TOKEN_PNG);
                game.scorePublicObjective(player.getUserID(), 0);
                MessageChannel channel = game.getMainGameChannel();
                if (game.isFowMode()) {
                    channel = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannel(channel, "# " + player.getRepresentation() + " scored custodians!");
                String message2 = player.getRepresentation(true, true)
                    + " Click the names of the planets you wish to exhaust to spend 6i.";
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                Button DoneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
                buttons.add(DoneExhausting);
                if (!player.hasAbility("reclamation")) {
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
                    List<String> relics = new ArrayList<>();
                    relics.addAll(player_.getRelics());
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
                    if (Mapper.getPlanet(planet) != null) {
                        String msg = player_.getRepresentation()
                            + " has a window to play Reparations for the taking of "
                            + Mapper.getPlanet(planet).getName();
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    }
                    if (moveTitanPN) {
                        if (player_.getPromissoryNotesInPlayArea().contains(Constants.TERRAFORM)) {
                            player_.removePromissoryNote(Constants.TERRAFORM);
                            player.setPromissoryNote(Constants.TERRAFORM);
                            player.setPromissoryNotesInPlayArea(Constants.TERRAFORM);
                        }
                    }
                }
            }
        }
        if ((alreadyOwned || player.hasAbility("contagion_blex") || player.hasAbility("plague_reservoir"))
            && player.hasTech("dxa") && !doubleCheck) {
            String msg10 = player.getRepresentation(true, true)
                + " you may have an opportunity to use Dacxive Animators on "
                + Helper.getPlanetRepresentation(planet, game)
                + ". Click to confirm a combat occurred and to add 1 infantry or delete these buttons.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg10,
                ButtonHelper.getDacxiveButtons(planet, player));
        }
        if (!alreadyOwned && game.isMinorFactionsMode() && player.isRealPlayer() && (unitHolder.getOriginalPlanetType().equalsIgnoreCase("FACTION"))) {
            PlanetModel p = Mapper.getPlanet(unitHolder.getName());
            if (!p.getFactionHomeworld().equalsIgnoreCase(player.getFaction())) {
                unitHolder.addToken("attachment_threetraits.png");
            }
        }

        if (game.isMinorFactionsMode() && unitHolder.getTokenList().contains("attachment_threetraits.png") && player.isRealPlayer() && tile != null) {
            boolean ownsThemAll = true;
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (!player.getPlanets().contains(uH.getName())) {
                    ownsThemAll = false;
                }
            }
            if (ownsThemAll) {
                PlanetModel p = Mapper.getPlanet(unitHolder.getName());
                if (p != null && p.getFactionHomeworld() != null && !player.hasLeader(p.getFactionHomeworld() + "commander")) {
                    String leaderID = p.getFactionHomeworld() + "commander";
                    player.addLeader(leaderID);
                    game.addFakeCommander(leaderID);
                    UnlockLeader.unlockLeader(leaderID, game, player);
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

        if (game.playerHasLeaderUnlockedOrAlliance(player, "naazcommander") && !setUP) {
            if (alreadyOwned && "mirage".equalsIgnoreCase(planet)) {
                Planet planetReal = unitHolder;
                List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
                if (event != null && buttons != null && !buttons.isEmpty()) {
                    String message = player.getFactionEmoji() + " Click button to explore "
                        + Helper.getPlanetRepresentation(planet, game);
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        message, buttons);
                }
            }
            alreadyOwned = false;
        }
        if (!game.getPhaseOfGame().contains("agenda")) {
            game.setStoredValue("planetsTakenThisRound",
                game.getStoredValue("planetsTakenThisRound") + "_" + planet);
        }

        if ((game.getPhaseOfGame().contains("agenda") || (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))))
            && player.hasAbility("scavenge") && !doubleCheck && !setUP) {
            String fac = player.getFactionEmoji();

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), fac
                + " gained 1TG from Scavenge (" + player.getTg() + "->" + (player.getTg() + 1)
                + "). Reminder that you do not legally have this TG prior to exploring, and that this was mandatory.");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        }

        if (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))
            && player.hasUnexhaustedLeader("vaylerianagent") && !setUP) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("exhaustAgent_vaylerianagent_" + player.getFaction(),
                "Use Vaylerian Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.vaylerian)));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentation(true, true) + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Yvin Korduul, the Vaylerian" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to draw 1AC";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2,
                buttons);
        }

        if (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))
            && player.hasAbility("scour") && !setUP) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("scourPlanet_" + planet, "Use Scour")
                .withEmoji(Emoji.fromFormatted(Emojis.vaylerian)));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentation(true, true)
                + " if you have not already used Scour this tactical action, you may discard 1AC to ready the planet "
                + Helper.getPlanetRepresentation(planet, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2,
                buttons);
        }

        if (tile != null && game.getActivePlayer() == player
            && game.playerHasLeaderUnlockedOrAlliance(player, "freesystemscommander") && !tile.isHomeSystem()
            && FoWHelper.playerHasShipsInSystem(player, tile)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("produceOneUnitInTile_" + tile.getPosition() + "_sling", "Produce 1 Ship")
                .withEmoji(Emoji.fromFormatted(Emojis.freesystems)));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentation(true, true)
                + " you may produce 1 ship in the system due to President Cyhn, the Free Systems Commander.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2,
                buttons);
        }

        if (game.getActivePlayer() == player
            && game.playerHasLeaderUnlockedOrAlliance(player, "cymiaecommander")) {
            List<Button> saarButton = new ArrayList<>();
            saarButton.add(Buttons.green("cymiaeCommanderRes_" + planet,
                "Discard AC for mech on " + Helper.getPlanetRepresentation(planet, game)));
            saarButton.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation(true, true)
                    + " due to Koryl Ferax, the Cymiae Commander, you may discard 1AC here to place or move 1 mech on "
                    + Helper.getPlanetRepresentation(planet, game)
                    + ". Do not do this prior to exploring. It is an after, while exploring is a when.",
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
                    player.getRepresentation(true, true)
                        + " if you have the correct amount of infantry (3 or 4), you may remove them and deploy 1 space dock on "
                        + planet + " using the buttons.",
                    buttons);

            }
        }
        if (ButtonHelper.isPlayerElected(game, player, "minister_exploration") && event != null) {
            String fac = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                fac + " gained 1TG from Minister of Exploration (" + player.getTg() + "->" + (player.getTg() + 1)
                    + ").");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);

        }

        if (!alreadyOwned && !doubleCheck && (!"mirage".equals(planet)) && !game.isBaseGameMode()) {
            Planet planetReal = unitHolder;
            List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
            if (event != null && buttons != null && !buttons.isEmpty()) {
                String message = player.getFactionEmoji() + " Click button to explore "
                    + Helper.getPlanetRepresentation(planet, game);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message, buttons);
            }
        }
        if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
            || game.getPhaseOfGame().contains("agenda")) && player.hasUnit("saar_mech")
            && event != null && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
            List<Button> saarButton = new ArrayList<>();
            saarButton.add(Buttons.green("saarMechRes_" + planet,
                "Pay 1TG for mech on " + Helper.getPlanetRepresentation(planet, game)));
            saarButton.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation(true, true)
                    + " you may pay 1TG to place 1 mech here. Do not do this prior to exploring. It is an after, while exploring is a when.",
                saarButton);
        }
        if (player.hasTech("ie") && unitHolder.getResources() > 0) {
            String message = player.getRepresentation() + " Click the button to resolve an integrated build on " + Helper.getPlanetRepresentation(planet, game);
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.blue("integratedBuild_" + planet, "Integrated on " + Helper.getPlanetRepresentation(planet, game)));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message, buttons);
        }
        CommanderUnlockCheck.checkPlayer(player, "sol", "vaylerian", "olradin", "xxcha", "sardakk");
        CommanderUnlockCheck.checkAllPlayersInGame(game, "freesystems");
        if (Constants.MECATOLS.contains(planet.toLowerCase()) && player.controlsMecatol(true)) {
            CommanderUnlockCheck.checkPlayer(player, "winnu");
        }
    }
}
