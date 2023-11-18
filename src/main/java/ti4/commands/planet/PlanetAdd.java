package ti4.commands.planet;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public class PlanetAdd extends PlanetAddRemove {
    public PlanetAdd() {
        super(Constants.PLANET_ADD, "Add Planet");
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

        if(unitHolder.getTokenList().contains("token_freepeople.png")){
            unitHolder.removeToken("token_freepeople.png");
        }
        if (Constants.MR.equals(planet) && player.hasCustodiaVigilia()) {
            Planet mecatolRex = (Planet) unitHolder;
            if (mecatolRex != null) {
                PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
                mecatolRex.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
                mecatolRex.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
            }
        }
        String color = player.getColor();
        boolean moveTitanPN = false;
        if (unitHolder != null && color != null && !"null".equals(color)) {
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
                MessageHelper.sendMessageToChannel(channel, player.getRepresentation() + " scored custodians!");
                String message2 = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Click the names of the planets you wish to exhaust to spend 6i.";
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
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
                    if (player_.hasRelic("shard") && ButtonHelper.isPlanetLegendaryOrHome(planet, activeGame, true, player_)&& !doubleCheck) {
                        String msg2 = player_.getRepresentation() + " lost shard and lost a victory point. " + player.getRepresentation()
                            + " gained shard and a victory point.";
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
                        player_.removeRelic("shard");
                        player.addRelic("shard");
                        int shardID = activeGame.getRevealedPublicObjectives().get("Shard of the Throne");
                        activeGame.unscorePublicObjective(player_.getUserID(), shardID);
                        activeGame.scorePublicObjective(player.getUserID(), shardID);
                        Helper.checkEndGame(activeGame, player);
                    }
                    String msg = player_.getRepresentation() + " has a window to play reparations for the taking of the planet " + planet
                        + " (and maybe also a window for parley if this wasnt taken after a combat). You can maybe float this window. ";
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
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
        if ((alreadyOwned || player.hasAbility("contagion_blex")) && player.hasTech("dxa")&& !doubleCheck) {
            String msg10 = ButtonHelper.getTrueIdentity(player, activeGame) + " you may have an opportunity to use Dacxive Animators on " + Helper.getPlanetRepresentation(planet, activeGame)
                + ". Click to confirm a combat occurred and to add an infantry or delete these buttons";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg10, ButtonHelper.getDacxiveButtons(activeGame, player, planet));
        }

        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "naazcommander")) {
            alreadyOwned = false;
        }
        if (activeGame.getActivePlayer() != null && !("".equalsIgnoreCase(activeGame.getActivePlayer())) && player.hasAbility("scavenge") && !doubleCheck&& event != null) {
            String fac = player.getFactionEmoji();

            MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac + " gained 1tg from Scavenge (" + player.getTg() + "->" + (player.getTg() + 1)
                + "). Reminder that this is optional, but was done automatically for convenience. You do not legally have this tg prior to exploring.");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        }

        if (activeGame.getActivePlayer() != null && !("".equalsIgnoreCase(activeGame.getActivePlayer())) && (player.hasUnit("mykomentori_spacedock") || player.hasUnit("mykomentori_spacedock2")) && !doubleCheck&& event != null) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("deployMykoSD_"+planet, "Deploy Dock "+planet));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            if(ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "sd") < 3){
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " if you have the correct amount of infantry (3 or 4), you can remove them and deploy a spacedock on "+planet+" using the buttons.", buttons);

            }
        }
        for (String law : activeGame.getLaws().keySet()) {
            if ("minister_exploration".equalsIgnoreCase(law)&& !doubleCheck) {
                if (activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction()) && event != null) {
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        fac + " gained 1tg from Minister of Exploration (" + player.getTg() + "->" + (player.getTg() + 1) + "). You do have this tg prior to exploring.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                }
            }
        }
        int numMechs = 0;
        String colorID = Mapper.getColorID(player.getColor());
        if (unitHolder != null && unitHolder.getUnits() != null) {
            numMechs = unitHolder.getUnitCount(UnitType.Mech, colorID);
        }
        if (numMechs > 0 && player.getUnitsOwned().contains("winnu_mech")&& !doubleCheck) {

            Button sdButton = Button.success("winnuStructure_sd_" + planet, "Place A SD on " + Helper.getPlanetRepresentation(planet, activeGame));
            sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
            Button pdsButton = Button.success("winnuStructure_pds_" + planet, "Place a PDS on " + Helper.getPlanetRepresentation(planet, activeGame));
            pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
            Button tgButton = Button.danger("deleteButtons", "Delete Buttons");
            List<Button> buttons = new ArrayList<>();
            buttons.add(sdButton);
            buttons.add(pdsButton);
            buttons.add(tgButton);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getTrueIdentity(player, activeGame) + " Use buttons to place structures equal to the amount of mechs you have", buttons);

        }
        if (!alreadyOwned && !doubleCheck && (!"mirage".equals(planet)) && !activeGame.isBaseGameMode()) {
            Planet planetReal = (Planet) unitHolder;
            List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(activeGame, planetReal, player);
            if (event != null && buttons != null && !buttons.isEmpty()) {
                String message = "Click button to explore " + Helper.getPlanetRepresentation(planet, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
        }
        if (player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "sol", event);
        }
        if (player.getLeaderIDs().contains("xxchacommander") && !player.hasLeaderUnlocked("xxchacommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "xxcha", event);
        }
        if (player.getLeaderIDs().contains("sardakkcommander") && !player.hasLeaderUnlocked("sardakkcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "sardakk", event);
        }
        if ("mr".equalsIgnoreCase(planet) && player.getLeaderIDs().contains("winnucommander") && !player.hasLeaderUnlocked("winnucommander") && player.getPlanets().contains("mr")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "winnu", event);
        }
        if ("mr".equalsIgnoreCase(planet) && player.hasAbility("reclamation")) {
            new AddUnits().unitParsing(event, player.getColor(),
                activeGame.getTile(AliasHandler.resolveTile(planet)), "sd " + planet + ", pds " + planet, activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                "Due to the reclamation ability, pds and SD have been added to Mecatol Rex. This is optional though.");
        }
    }
}
