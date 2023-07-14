package ti4.commands.explore;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public class ExpPlanet extends ExploreSubcommandData {

    public ExpPlanet() {
        super(Constants.PLANET, "Explore a specific planet.");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to explore").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TRAIT, "Planet trait to explore").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_EXPLORE_OWNERSHIP_REQ, "Override ownership requirement. Enter YES if so").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        @SuppressWarnings("ConstantConditions")
        String planetName = AliasHandler.resolvePlanet(StringUtils.substringBefore(planetOption.getAsString(), " ("));
        Map activeMap = getActiveMap();
        if (!activeMap.getPlanets().contains(planetName)) {
            sendMessage("Planet not found in map");
            return;
        }
        Tile tile = Helper.getTileFromPlanet(planetName, activeMap);
        if (tile == null) {
            sendMessage("System not found that contains planet");
            return;
        }
        planetName = AddRemoveUnits.getPlanet(event, tile, AliasHandler.resolvePlanet(planetName));
        PlanetModel planet = Mapper.getPlanet(planetName);
        if (Optional.ofNullable(planet).isEmpty()) {
            sendMessage("Invalid planet");
            return;
        }
        String drawColor = planet.getPlanetType().toString();
        OptionMapping traitOption = event.getOption(Constants.TRAIT);
        if (traitOption != null){
            drawColor = traitOption.getAsString();
        }
        boolean over = false;
        OptionMapping overRider = event.getOption(Constants.OVERRIDE_EXPLORE_OWNERSHIP_REQ);
        if(overRider != null && overRider.getAsString().equalsIgnoreCase("YES"))
        {
            over = true;
        }
        Player player = activeMap.getPlayer(event.getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);


        explorePlanet(event, tile, planetName, drawColor, player, false, activeMap, 1, over);
    }

    public void explorePlanet(GenericInteractionCreateEvent event, Tile tile, String planetName, String drawColor, Player player, boolean NRACheck, Map activeMap, int numExplores, boolean ownerShipOverride) {
        if (!player.getPlanets().contains(planetName) && !activeMap.isAllianceMode() && !ownerShipOverride) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You do not own this planet, thus cannot explore it.");
            return;
        }

        if (player.ownsUnit("naaz_mech")) {
            if (Helper.mechCheck(planetName, activeMap, player)) {
                if (!NRACheck) {
                    if (player.hasTech("pfa")) { //Pre-Fab Arcologies
                        new PlanetRefresh().doAction(player, planetName, activeMap);
                        MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), "Planet has been automatically refreshed because you have Pre-Fab");
                    }
                    String message = "Please decide whether or not to use your distant suns (explore twice) ability.";
                    Button resolveExplore1  = Button.success("distant_suns_accept_"+planetName+"_"+drawColor, "Choose to Explore Twice");
                    Button resolveExplore2 = Button.success("distant_suns_decline_"+planetName+"_"+drawColor, "Decline Distant Suns");
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    return;
                } else {
                    if (numExplores == 2) {
                        String cardID = activeMap.drawExplore(drawColor);
                        if (cardID == null) {
                            sendMessage("Planet cannot be explored");
                            return;
                        }
                        String cardID2 = activeMap.drawExplore(drawColor);

                        String card = Mapper.getExplore(cardID);
                        String[] cardInfo1 = card.split(";");
                        String name1 = cardInfo1[0];
                        String card2 = Mapper.getExplore(cardID2);
                        String[] cardInfo2 = card2.split(";");
                        String name2 = cardInfo2[0];

                        Button resolveExplore1  = Button.success("resolve_explore_"+cardID+"_"+planetName, "Choose "+name1);
                        Button resolveExplore2 = Button.success("resolve_explore_"+cardID2+"_"+planetName, "Choose "+name2);
                        List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                        //code to draw 2 explores and get their names
                        //Send Buttons to decide which one to explore
                        String message = "Please decide which card to resolve.";

                        if (activeMap != null && !activeMap.isFoWMode() &&(event.getChannel() !=  activeMap.getActionsChannel())) {
                            
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                            
                            MessageHelper.sendMessageToChannel(activeMap.getActionsChannel(), "Using Distant Suns,  " + pF + " found a "+name1+" and a " +name2+ " on "+Helper.getPlanetRepresentation(planetName, activeMap));
                            
                        }
                        else
                        {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a "+name1+" and a " +name2+ " on "+Helper.getPlanetRepresentation(planetName, activeMap));
                        }

                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);




                        return;
                    }
                }
            }
        }

        String cardID = activeMap.drawExplore(drawColor);
        if (cardID == null) {
            sendMessage("Planet cannot be explored");
            return;
        }
        StringBuilder messageText = new StringBuilder();
        messageText.append(Helper.getPlayerRepresentation(player, activeMap)).append(" explored ");
        messageText.append(Helper.getEmojiFromDiscord(drawColor));
        messageText.append("Planet "+ Helper.getPlanetRepresentationPlusEmoji(planetName) +" *(tile "+ tile.getPosition() + ")*:\n");
        messageText.append("> ").append(displayExplore(cardID));
        resolveExplore(event, cardID, tile, planetName, messageText.toString(), false, player, activeMap);
        if (player.hasTech("pfa")) { //Pre-Fab Arcologies
            new PlanetRefresh().doAction(player, planetName, activeMap);
            MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), "Planet has been automatically refreshed because you have Pre-Fab");
        }
        return;
    }
}
