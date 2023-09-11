package ti4.commands.planet;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.List;

public class PlanetAdd extends PlanetAddRemove {
    public PlanetAdd() {
        super(Constants.PLANET_ADD, "Add Planet");
    }

    @Override
    public void doAction(Player player, String planet, Game activeGame) {
      doAction(player, planet, activeGame, null);
    }

    public void doAction(Player player, String planet, Game activeGame, GenericInteractionCreateEvent event) {
        boolean doubleCheck = Helper.isAllianceModeAndPreviouslyOwnedCheck(activeGame, planet);
        player.addPlanet(planet);
        player.exhaustPlanet(planet);
        if ("mirage".equals(planet)){
            activeGame.clearPlanetsCache();
        }
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
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
                if(activeGame.isFoWMode()){
                    channel = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannel(channel, Helper.getPlayerRepresentation(player, activeGame)+" scored custodians!");
                String message2 = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Click the names of the planets you wish to exhaust to spend 6i.";
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
                Button DoneExhausting =  Button.danger("deleteButtons", "Done Exhausting Planets");
                buttons.add(DoneExhausting);
                if(!player.hasAbility("reclamation")){
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
                    if(player_.hasRelic("shard") && ButtonHelper.isPlanetLegendaryOrHome(planet, activeGame)){
                        String msg2 = Helper.getPlayerRepresentation(player_, activeGame) + " lost shard and lost a victory point. "+Helper.getPlayerRepresentation(player, activeGame) +" gained shard and a victory point.";
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
                        player_.removeRelic("shard");
                        player.addRelic("shard");
                        int shardID = activeGame.getRevealedPublicObjectives().get("Shard of the Throne");
                        activeGame.unscorePublicObjective(player_.getUserID(), shardID);
                        activeGame.scorePublicObjective(player.getUserID(), shardID);
                    }
                    String msg = Helper.getPlayerRepresentation(player_, activeGame) + " has a window to play reparations for the taking of the planet "+planet+" (and maybe also a window for parley if this wasnt taken after a combat). You can maybe float this window. ";
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                    if (moveTitanPN){
                       if (player_.getPromissoryNotesInPlayArea().contains(Constants.TERRAFORM)){
                           player_.removePromissoryNote(Constants.TERRAFORM);
                           player.setPromissoryNote(Constants.TERRAFORM);
                           player.setPromissoryNotesInPlayArea(Constants.TERRAFORM);
                       }
                    }
                }
            }
        }
        if((alreadyOwned || player.hasAbility("contagion_blex")) && player.hasTech("dxa")){
            String msg10 = ButtonHelper.getTrueIdentity(player, activeGame) + " you may have an opportunity to use Dacxive Animators on "+Helper.getPlanetRepresentation(planet, activeGame) +". Click to confirm a combat occurred and to add an infantry or delete these buttons";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg10, ButtonHelper.getDacxiveButtons(activeGame, player, planet));
        }

        if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "naazcommander"))
        {
            alreadyOwned = false;
        }
        if(activeGame.getActivePlayer() != null && !("".equalsIgnoreCase(activeGame.getActivePlayer())) && player.hasAbility("scavenge") && event != null)
        {
            String fac = Helper.getFactionIconFromDiscord(player.getFaction());
            
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac+" gained 1tg from Scavenge ("+player.getTg()+"->"+(player.getTg()+1)+"). Reminder that this is optional, but was done automatically for convenience. You do not legally have this tg prior to exploring." );
            player.setTg(player.getTg()+1);
            ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
        }
        for(String law : activeGame.getLaws().keySet()){
            if("minister_exploration".equalsIgnoreCase(law)){
                if(activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction()) && event != null){
                    String fac = Helper.getFactionIconFromDiscord(player.getFaction());
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac+" gained 1tg from Minister of Exploration ("+player.getTg()+"->"+(player.getTg()+1)+"). You do have this tg prior to exploring." );
                    player.setTg(player.getTg()+1);
                    ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                }
            }
        }
        int numMechs = 0;
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        if (unitHolder.getUnits() != null) {
            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs =  unitHolder.getUnits().get(mechKey);
            }
        }
        if(numMechs > 0 && player.getUnitsOwned().contains("winnu_mech")){
            
            Button sdButton = Button.success("winnuStructure_sd_"+planet, "Place A SD on "+Helper.getPlanetRepresentation(planet, activeGame));
            sdButton = sdButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("spacedock")));
            Button pdsButton = Button.success("winnuStructure_pds_"+planet, "Place a PDS on "+Helper.getPlanetRepresentation(planet, activeGame));
            pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("pds")));
            Button tgButton = Button.danger("deleteButtons", "Delete Buttons");
            List<Button> buttons = new ArrayList<>();
            buttons.add(sdButton);
            buttons.add(pdsButton);
            buttons.add(tgButton);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame)+" Use buttons to place structures equal to the amount of mechs you have", buttons );

        }
        if (!alreadyOwned && !doubleCheck && (!"mirage".equals(planet))&& !activeGame.isBaseGameMode()) {
            Planet planetReal = (Planet) unitHolder;
            List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(activeGame, planetReal, player);
            if (event != null && buttons != null && !buttons.isEmpty()) {
                String message = "Click button to explore " + Helper.getPlanetRepresentation(planet, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
        }
        if(player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "sol", event);
        }
        if(player.getLeaderIDs().contains("xxchacommander") && !player.hasLeaderUnlocked("xxchacommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "xxcha", event);
        }
        if(player.getLeaderIDs().contains("sardakkcommander") && !player.hasLeaderUnlocked("sardakkcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "sardakk", event);
        }
        if("mr".equalsIgnoreCase(planet)&&player.getLeaderIDs().contains("winnucommander") && !player.hasLeaderUnlocked("winnucommander") && player.getPlanets().contains("mr")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "winnu", event);
        }
        if("mr".equalsIgnoreCase(planet)&& player.hasAbility("reclamation")){
             new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planet)), "sd " + planet + ", pds "+planet, activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Due to the reclamation ability, pds and SD have been added to Mecatol Rex. This is optional though.");
        }
    }
}
