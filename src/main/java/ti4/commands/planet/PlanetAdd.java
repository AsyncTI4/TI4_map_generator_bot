package ti4.commands.planet;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
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
    public void doAction(Player player, String planet, Game activeGame) {
        doAction(player, planet, activeGame, null);
    }

    public void doAction(Player player, String planet, Game activeGame, GenericInteractionCreateEvent event) {
        boolean doubleCheck = Helper.doesAllianceMemberOwnPlanet(activeGame, planet, player);
        player.addPlanet(planet);

        player.exhaustPlanet(planet);
        if ("mirage".equals(planet)) {
            activeGame.clearPlanetsCache();
        }
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);

        if (unitHolder.getTokenList().contains("token_freepeople.png")) {
            unitHolder.removeToken("token_freepeople.png");
        }
        if (Constants.MR.equals(planet) && player.hasCustodiaVigilia()) {
            Planet mecatolRex = (Planet) unitHolder;
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
                activeGame.scorePublicObjective(player.getUserID(), 0);
                MessageChannel channel = activeGame.getMainGameChannel();
                if (activeGame.isFoWMode()) {
                    channel = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannel(channel, "# " + player.getRepresentation() + " scored custodians!");
                String message2 = player.getRepresentation(true, true) + " Click the names of the planets you wish to exhaust to spend 6i.";
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "inf");
                Button DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
                buttons.add(DoneExhausting);
                if (!player.hasAbility("reclamation")) {
                    MessageHelper.sendMessageToChannelWithButtons(channel, message2, buttons);
                }
            }
        }
        boolean alreadyOwned = false;
        for (Player player_ : activeGame.getPlayers().values()) {
            if (player_ != player) {
                List<String> planets = player_.getPlanets();
                if (planets.contains(planet)) {
                    if (player_.getExhaustedPlanetsAbilities().contains(planet)) {
                        player.exhaustPlanetAbility(planet);
                    }
                    alreadyOwned = true;
                    player_.removePlanet(planet);
                    List<String> relics = new ArrayList<>();
                    relics.addAll(player_.getRelics());
                    for (String relic : relics) {
                        if (relic.contains("shard") && ButtonHelper.isPlanetLegendaryOrHome(planet, activeGame, true, player_) && !doubleCheck) {
                            String msg2 = player_.getRepresentation() + " lost shard and lost a victory point. " + player.getRepresentation()
                                + " gained shard and a victory point.";
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
                            player_.removeRelic(relic);
                            player.addRelic(relic);
                            String customPOName = "Shard of the Throne";
                            if (relic.contains("absol_")) {
                                int absolShardNum = Integer.parseInt(StringUtils.right(relic, 1));
                                customPOName = "Shard of the Throne (" + absolShardNum + ")";
                            }
                            int shardID = activeGame.getRevealedPublicObjectives().get(customPOName);
                            activeGame.unscorePublicObjective(player_.getUserID(), shardID);
                            activeGame.scorePublicObjective(player.getUserID(), shardID);
                            Helper.checkEndGame(activeGame, player);
                        }
                    }
                    if (Mapper.getPlanet(planet) != null) {
                        String msg = player_.getRepresentation() + " has a window to play reparations for the taking of " + Mapper.getPlanet(planet).getName();
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
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
        if ((alreadyOwned || player.hasAbility("contagion_blex") || player.hasAbility("plague_reservoir")) && player.hasTech("dxa") && !doubleCheck) {
            String msg10 = player.getRepresentation(true, true) + " you may have an opportunity to use Dacxive Animators on " + Helper.getPlanetRepresentation(planet, activeGame)
                + ". Click to confirm a combat occurred and to add an infantry or delete these buttons";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg10, ButtonHelper.getDacxiveButtons(planet));
        }

        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "naazcommander")) {
            if (alreadyOwned && "mirage".equalsIgnoreCase(planet)) {
                Planet planetReal = (Planet) unitHolder;
                List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(activeGame, planetReal, player);
                if (event != null && buttons != null && !buttons.isEmpty()) {
                    String message = ButtonHelper.getIdent(player) + " Click button to explore " + Helper.getPlanetRepresentation(planet, activeGame);
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
                }
            }
            alreadyOwned = false;
        }
        if (!activeGame.getCurrentPhase().contains("agenda")) {
            activeGame.setCurrentReacts("planetsTakenThisRound", activeGame.getFactionsThatReactedToThis("planetsTakenThisRound") + "_" + planet);
        }
        if (activeGame.getActivePlayerID() != null && !("".equalsIgnoreCase(activeGame.getActivePlayerID())) && player.hasAbility("scavenge") && !doubleCheck && event != null) {
            String fac = player.getFactionEmoji();

            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), fac + " gained 1tg from Scavenge (" + player.getTg() + "->" + (player.getTg() + 1)
                + "). Reminder that you do not legally have this tg prior to exploring, and that this was mandatory.");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        }

        if (activeGame.getActivePlayerID() != null && !("".equalsIgnoreCase(activeGame.getActivePlayerID())) && player.hasUnexhaustedLeader("vaylerianagent")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("exhaustAgent_vaylerianagent_" + player.getFaction(), "Use Vaylerian Agent").withEmoji(Emoji.fromFormatted(Emojis.vaylerian)));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            String msg2 = player.getRepresentation(true, true) + " you can use Vaylerian Agent to draw an AC";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg2, buttons);
        }

        if (activeGame.getActivePlayerID() != null && !("".equalsIgnoreCase(activeGame.getActivePlayerID())) && player.hasAbility("scour")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("scourPlanet_" + planet, "Use Scour").withEmoji(Emoji.fromFormatted(Emojis.vaylerian)));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            String msg2 = player.getRepresentation(true, true) + " if you have not already used Scour this tactical action, you can discard an AC to ready the planet "
                + Helper.getPlanetRepresentation(planet, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg2, buttons);
        }
        Tile tile = activeGame.getTileFromPlanet(planet);
        if (tile != null && activeGame.getActivePlayer() == player && activeGame.playerHasLeaderUnlockedOrAlliance(player, "freesystemscommander") && !tile.isHomeSystem()
            && FoWHelper.playerHasShipsInSystem(player, tile)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("produceOneUnitInTile_" + tile.getPosition() + "_sling", "Produce 1 Ship").withEmoji(Emoji.fromFormatted(Emojis.freesystems)));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            String msg2 = player.getRepresentation(true, true) + " you can produce 1 ship in the system due to Free Systems Commander";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg2, buttons);
        }
        if (((activeGame.getActivePlayerID() != null && !("".equalsIgnoreCase(activeGame.getActivePlayerID()))) || activeGame.getCurrentPhase().contains("agenda")) && player.hasUnit("saar_mech")
            && event != null && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "mech") < 4) {
            List<Button> saarButton = new ArrayList<>();
            saarButton.add(Button.success("saarMechRes_" + planet, "Pay 1tg for mech on " + Helper.getPlanetRepresentation(planet, activeGame)));
            saarButton.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you can pay 1tg to place a mech here. Do not do this prior to exploring. It is an after, while exploring is a when", saarButton);
        }
        if (activeGame.getActivePlayer() == player && activeGame.playerHasLeaderUnlockedOrAlliance(player, "cymiaecommander")) {
            List<Button> saarButton = new ArrayList<>();
            saarButton.add(Button.success("cymiaeCommanderRes_" + planet, "Discard AC for mech on " + Helper.getPlanetRepresentation(planet, activeGame)));
            saarButton.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " due to Cymiae Commander, you can discard an AC here to place or move a mech on " + Helper.getPlanetRepresentation(planet, activeGame)
                    + ". Do not do this prior to exploring. It is an after, while exploring is a when",
                saarButton);
        }

        if (activeGame.getActivePlayerID() != null && !("".equalsIgnoreCase(activeGame.getActivePlayerID())) && (player.hasUnit("mykomentori_spacedock") || player.hasUnit("mykomentori_spacedock2"))
            && !doubleCheck && event != null) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("deployMykoSD_" + planet, "Deploy Dock " + planet));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            if (ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "sd") < 3) {
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " if you have the correct amount of infantry (3 or 4), you can remove them and deploy a spacedock on " + planet + " using the buttons.",
                    buttons);

            }
        }
        if (ButtonHelper.isPlayerElected(activeGame, player, "minister_exploration") && event != null) {
            String fac = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                fac + " gained 1tg from Minister of Exploration (" + player.getTg() + "->" + (player.getTg() + 1) + ").");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        }

        if (!alreadyOwned && !doubleCheck && (!"mirage".equals(planet)) && !activeGame.isBaseGameMode()) {
            Planet planetReal = (Planet) unitHolder;
            List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(activeGame, planetReal, player);
            if (event != null && buttons != null && !buttons.isEmpty()) {
                String message = player.getFactionEmoji() + " Click button to explore " + Helper.getPlanetRepresentation(planet, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
            }
        }
        if (player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "sol", event);
        }
        if (player.getLeaderIDs().contains("vayleriancommander") && !player.hasLeaderUnlocked("vayleriancommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "vaylerian", event);
        }
        if (player.getLeaderIDs().contains("olradincommander") && !player.hasLeaderUnlocked("olradincommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "olradin", event);
        }
        if (player.getLeaderIDs().contains("xxchacommander") && !player.hasLeaderUnlocked("xxchacommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "xxcha", event);
        }
        if (player.getLeaderIDs().contains("sardakkcommander") && !player.hasLeaderUnlocked("sardakkcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "sardakk", event);
        }
        for (Player p2 : activeGame.getRealPlayers()) {
            ButtonHelper.fullCommanderUnlockCheck(p2, activeGame, "freesystems", event);
        }
        if ("mr".equalsIgnoreCase(planet) && player.getLeaderIDs().contains("winnucommander") && !player.hasLeaderUnlocked("winnucommander") && player.getPlanets().contains("mr")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "winnu", event);
        }
    }
}
