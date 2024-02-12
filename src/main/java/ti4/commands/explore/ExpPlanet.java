package ti4.commands.explore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.MessageEmbed;
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
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
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
        activeGame.setCurrentReacts(player.getFaction()+"planetsExplored",activeGame.getFactionsThatReactedToThis(player.getFaction()+"planetsExplored")+planetName+"*");

        if (player.hasAbility("distant_suns")) {
            if (Helper.mechCheck(planetName, activeGame, player)) {
                if (!NRACheck) {
                    if (player.hasTech("pfa")) { //Pre-Fab Arcologies
                        new PlanetRefresh().doAction(player, planetName, activeGame);
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been automatically refreshed because you have Pre-Fab");
                    }
                    String message = "Please decide whether or not to use your " + Emojis.Naaz + "**Distant Suns** (explore twice) ability.";
                    Button resolveExplore1 = Button.success("distant_suns_accept_" + planetName + "_" + drawColor, "Choose to Explore Twice");
                    Button resolveExplore2 = Button.danger("distant_suns_decline_" + planetName + "_" + drawColor, "Decline Distant Suns");
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    return;
                } else if (numExplores == 2) {
                    String cardID1 = activeGame.drawExplore(drawColor);
                    String cardID2 = activeGame.drawExplore(drawColor);
                    if (cardID1 == null) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Planet cannot be explored");
                        return;
                    }
                    ExploreModel exploreModel1 = Mapper.getExplore(cardID1);
                    ExploreModel exploreModel2 = Mapper.getExplore(cardID2);

                    // Report to common channel
                    String reportMessage = player.getFactionEmoji() + " used their " + Emojis.Naaz + "**Distant Suns** ability and found a **" + exploreModel1.getName() + "** and a **" + exploreModel2.getName() + "** on " + Helper.getPlanetRepresentationPlusEmoji(planetName);
                    if (!activeGame.isFoWMode() && event.getChannel() != activeGame.getActionsChannel()) {
                        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), reportMessage);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), reportMessage);
                    }
                    
                    Button resolveExplore1 = Button.success("resolve_explore_" + cardID1 + "_" + planetName + "_distantSuns", exploreModel1.getName());
                    Button resolveExplore2 = Button.success("resolve_explore_" + cardID2 + "_" + planetName + "_distantSuns", exploreModel2.getName());
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed(), exploreModel2.getRepresentationEmbed());
                    String message = player.getRepresentation() + " please choose 1 Explore card to resolve.";
                    MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
                    return;
                }
            }
        }

        String cardID = activeGame.drawExplore(drawColor);
        if (cardID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Planet cannot be explored");
            return;
        }
        String messageText = player.getRepresentation() + " explored " + Emojis.getEmojiFromDiscord(drawColor) +
            "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tile.getPosition() + ")*:";
        if (player.hasUnexhaustedLeader("lanefiragent")) {
            ExploreModel exploreModel = Mapper.getExplore(cardID);
            String name1 = exploreModel.getName();
            Button resolveExplore1 = Button.success("lanefirAgentRes_Decline_" + drawColor + "_" + cardID + "_" + planetName, "Choose " + name1);
            Button resolveExplore2 = Button.success("lanefirAgentRes_Accept_" + drawColor + "_" + planetName, "Use Lanefir Agent");
            List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
            String message = player.getRepresentation(true, true) + " You have Lanefir Agent, and thus can decline this explore to draw another one instead.";
            if (!activeGame.isFoWMode() && event.getChannel() != activeGame.getActionsChannel()) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), pF + " found a " + name1 + " on " + planetName);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " on " + planetName);
            }
            ExploreModel exploreModel1 = Mapper.getExplore(cardID);
            List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
            return;
        }
        if(player.hasTech("absol_sdn")){
            ExploreModel exploreModel = Mapper.getExplore(cardID);
            String name1 = exploreModel.getName();
            Button resolveExplore1 = Button.success("absolsdn_Decline_" + drawColor + "_" + cardID + "_" + planetName, "Resolve " + name1);
            Button resolveExplore2 = Button.success("absolsdn_Accept" + drawColor + "_" + planetName, "Get 1tg");
            List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
            String message = player.getRepresentation(true, true) + " You have Absol Scanlink, and thus can decline this explore to get a tg.";
            if (!activeGame.isFoWMode() && event.getChannel() != activeGame.getActionsChannel()) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), pF + " found a " + name1 + " on " + planetName);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " on " + planetName);
            }
            ExploreModel exploreModel1 = Mapper.getExplore(cardID);
            List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
            return;
        }
        resolveExplore(event, cardID, tile, planetName, messageText, player, activeGame);
        if (player.hasTech("pfa")) { //Pre-Fab Arcologies
            new PlanetRefresh().doAction(player, planetName, activeGame);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been automatically refreshed because you have Pre-Fab");
        }
        if (ButtonHelper.doesPlayerHaveFSHere("ghemina_flagship_lord", player, tile)) {
            new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf " + planetName, activeGame);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Infantry added due to presence of The Lord FS. Technically happens after exploring");
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "florzencommander") && activeGame.getCurrentPhase().contains("agenda")) {
            new PlanetRefresh().doAction(player, planetName, activeGame);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been refreshed because of Florzen Commander");
            ListVoteCount.turnOrder(event, activeGame, activeGame.getMainGameChannel());
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
            UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
            Tile tileWithPlanet = activeGame.getTileFromPlanet(planetName);
            if (tileWithPlanet == null) {
                sendMessage("An error occurred while placing an infantry. Resolve manually.");
                return;
            }
            tileWithPlanet.getUnitHolders().get(planetName).addUnit(infKey, 1);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Added inf to planet because of Lanefir Commander");
        }
        if (player.hasTech("dslaner")) {
            player.setAtsCount(player.getAtsCount() + numExplores);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
        }
        if (ButtonHelper.isPlanetLegendaryOrTechSkip(planetName, activeGame) && Helper.getPlayerFromUnlockedLeader(activeGame, "augersagent") != null) {
            for (Player p2 : activeGame.getRealPlayers()) {
                if (p2.hasUnexhaustedLeader("augersagent")) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Button.success("exhaustAgent_augersagent_" + player.getFaction(), "Use Augers Agent on " + player.getColor()).withEmoji(Emoji.fromFormatted(Emojis.augers)));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    String msg2 = p2.getRepresentation(true, true) + " you can use Augers Agent on " + ButtonHelper.getIdentOrColor(player, activeGame) + " to give them 2tg";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg2, buttons);
                }
            }
        }
    }
}
