package ti4.commands.explore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
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
        String planetName = AliasHandler.resolvePlanet(StringUtils.substringBefore(planetOption.getAsString(), " ("));
        Game activeGame = getActiveGame();
        if (!activeGame.getPlanets().contains(planetName)) {
            sendMessage("Planet not found in map");
            return;
        }
        Tile tile = activeGame.getTileFromPlanet(planetName);
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
        if (traitOption != null) {
            drawColor = traitOption.getAsString();
        }
        boolean over = false;
        OptionMapping overRider = event.getOption(Constants.OVERRIDE_EXPLORE_OWNERSHIP_REQ);
        if (overRider != null && "YES".equalsIgnoreCase(overRider.getAsString())) {
            over = true;
        }
        Player player = activeGame.getPlayer(event.getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);

        explorePlanet(event, tile, planetName, drawColor, player, false, activeGame, 1, over);
    }

    public void explorePlanet(GenericInteractionCreateEvent event, Tile tile, String planetName, String drawColor, Player player, boolean NRACheck, Game activeGame, int numExplores,
        boolean ownerShipOverride) {
        if (!player.getPlanetsAllianceMode().contains(planetName) && !ownerShipOverride) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You do not own this planet, thus cannot explore it.");
            return;
        }

        if (player.hasAbility("distant_suns")) {
            if (Helper.mechCheck(planetName, activeGame, player)) {
                if (!NRACheck) {
                    if (player.hasTech("pfa")) { //Pre-Fab Arcologies
                        new PlanetRefresh().doAction(player, planetName, activeGame);
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been automatically refreshed because you have Pre-Fab");
                    }
                    String message = "Please decide whether or not to use your distant suns (explore twice) ability.";
                    Button resolveExplore1 = Button.success("distant_suns_accept_" + planetName + "_" + drawColor, "Choose to Explore Twice");
                    Button resolveExplore2 = Button.success("distant_suns_decline_" + planetName + "_" + drawColor, "Decline Distant Suns");
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    return;
                } else {
                    if (numExplores == 2) {
                        String cardID = activeGame.drawExplore(drawColor);
                        if (cardID == null) {
                            sendMessage("Planet cannot be explored");
                            return;
                        }
                        String cardID2 = activeGame.drawExplore(drawColor);

                        String card = Mapper.getExploreRepresentation(cardID);
                        String[] cardInfo1 = card.split(";");
                        String name1 = cardInfo1[0];
                        String card2 = Mapper.getExploreRepresentation(cardID2);
                        String[] cardInfo2 = card2.split(";");
                        String name2 = cardInfo2[0];

                        Button resolveExplore1 = Button.success("resolve_explore_" + cardID + "_" + planetName, "Choose " + name1);
                        Button resolveExplore2 = Button.success("resolve_explore_" + cardID2 + "_" + planetName, "Choose " + name2);
                        List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                        //code to draw 2 explores and get their names
                        //Send Buttons to decide which one to explore
                        String message = "Please decide which card to resolve.";

                        if (!activeGame.isFoWMode() && event.getChannel() != activeGame.getActionsChannel()) {

                            String pF = player.getFactionEmoji();

                            MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                                "Using Distant Suns,  " + pF + " found a " + name1 + " and a " + name2 + " on " + Helper.getPlanetRepresentation(planetName, activeGame));

                        } else {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player)+" Found a " + name1 + " and a " + name2 + " on " + Helper.getPlanetRepresentation(planetName, activeGame));
                        }

                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                        String msg2 = "As a reminder of their text, the card abilities read as: \n";
                        msg2 = msg2 + name1 +": "+cardInfo1[4]+"\n";
                        msg2 = msg2 + name2 +": "+cardInfo2[4]+"\n";
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);

                        return;
                    }
                }
            }
        }

        String cardID = activeGame.drawExplore(drawColor);
        if (cardID == null) {
            sendMessage("Planet cannot be explored");
            return;
        }
        String messageText = player.getRepresentation() + " explored " +
            Emojis.getEmojiFromDiscord(drawColor) +
            "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tile.getPosition() + ")*:\n" +
            "> " + displayExplore(cardID);
        resolveExplore(event, cardID, tile, planetName, messageText, false, player, activeGame);
        if (player.hasTech("pfa")) { //Pre-Fab Arcologies
            new PlanetRefresh().doAction(player, planetName, activeGame);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been automatically refreshed because you have Pre-Fab");
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "florzencommander") && activeGame.getCurrentPhase().contains("agenda")) {
            new PlanetRefresh().doAction(player, planetName, activeGame);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been refreshed because of Florzen Commander");
            ListVoteCount.turnOrder(event, activeGame, activeGame.getMainGameChannel());
        }
        if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
            UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
            activeGame.getTileFromPlanet(planetName).getUnitHolders().get(planetName).addUnit(infKey, 1);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Added inf to planet because of Lanefir Commander");
        }
        if(player.hasTech("dslaner")){
            player.setAtsCount(player.getAtsCount()+numExplores);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
        }
        if(ButtonHelper.isPlanetLegendaryOrTechSkip(planetName, activeGame) && Helper.getPlayerFromUnlockedLeader(activeGame, "augersagent") != null){
            for(Player p2 : activeGame.getRealPlayers()){
                if(p2.hasUnexhaustedLeader("augersagent")){
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Button.success("exhaustAgent_augersagent_"+player.getFaction(), "Use Augers Agent on "+player.getColor()).withEmoji(Emoji.fromFormatted(Emojis.augers)));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    String msg2 = p2.getRepresentation(true, true) + " you can use Augers Agent on "+ButtonHelper.getIdentOrColor(player, activeGame) + " to give them 2tg";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg2, buttons);
                }
            }
        }
    }
}
