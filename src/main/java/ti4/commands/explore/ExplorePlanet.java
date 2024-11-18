package ti4.commands.explore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands.units.AddUnits;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.PlanetModel;

public class ExplorePlanet extends ExploreSubcommandData {

    public ExplorePlanet() {
        super(Constants.PLANET, "Explore a specific planet.");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to explore").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TRAIT, "Planet trait to explore").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_EXPLORE_OWNERSHIP_REQ, "Override ownership requirement. Enter YES if so"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        String planetName = AliasHandler.resolvePlanet(StringUtils.substringBefore(planetOption.getAsString(), " ("));
        Game game = getActiveGame();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.sendMessageToEventChannel(event, "Planet not found in map");
            return;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "System not found that contains planet");
            return;
        }
        planetName = AddRemoveUnits.getPlanet(event, tile, AliasHandler.resolvePlanet(planetName));
        PlanetModel planet = Mapper.getPlanet(planetName);
        if (Optional.ofNullable(planet).isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid planet");
            return;
        }
        String drawColor = planet.getPlanetType() == null ? null : planet.getPlanetType().toString();
        OptionMapping traitOption = event.getOption(Constants.TRAIT);
        if (traitOption != null) {
            drawColor = traitOption.getAsString();
        }
        if (drawColor == null) {
            MessageHelper.sendMessageToEventChannel(event, "Cannot determine trait, please specify");
            return;
        }

        boolean over = false;
        OptionMapping overRider = event.getOption(Constants.OVERRIDE_EXPLORE_OWNERSHIP_REQ);
        if (overRider != null && "YES".equalsIgnoreCase(overRider.getAsString())) {
            over = true;
        }
        Player player = CommandHelper.getPlayerFromEvent(game, event);

        explorePlanet(event, tile, planetName, drawColor, player, false, game, 1, over);
    }

    public void explorePlanet(GenericInteractionCreateEvent event, Tile tile, String planetName, String drawColor, Player player, boolean NRACheck, Game game, int numExplores,
        boolean ownerShipOverride) {
        if (!player.getPlanetsAllianceMode().contains(planetName) && !ownerShipOverride) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You do not own this planet, thus cannot explore it.");
            return;
        }
        game.setStoredValue(player.getFaction() + "planetsExplored", game.getStoredValue(player.getFaction() + "planetsExplored") + planetName + "*");

        if (planetName.equalsIgnoreCase("garbozia")) {
            if (player.hasAbility("distant_suns")) {
                String reportMessage = "Garbozia exploration with Distant Suns is not implemented.\nPlease use `/explore draw_and_discard trait` then `/explore use explore_card_id` to manually resolve this exploration.\n(NB: Player chooses a trait, reveals two of that trait and one of each other; reveal four cards total.)";
                if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                    MessageHelper.sendMessageToChannel(game.getActionsChannel(), reportMessage);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), reportMessage);
                }
                return;
            }
            String cardIDC = game.drawExplore("CULTURAL");
            String cardIDH = game.drawExplore("INDUSTRIAL");
            String cardIDI = game.drawExplore("HAZARDOUS");

            ExploreModel exploreModelC = Mapper.getExplore(cardIDC);
            ExploreModel exploreModelH = Mapper.getExplore(cardIDH);
            ExploreModel exploreModelI = Mapper.getExplore(cardIDI);

            String reportMessage = player.getFactionEmoji() + " explored " + Emojis.LegendaryPlanet + "**Garbozia** ability and found a **" + exploreModelC.getName() + "**, **" + exploreModelH.getName() + "** and a **" + exploreModelI.getName() + "**";
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), reportMessage);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), reportMessage);
            }

            Button resolveExploreC = Buttons.green("resolve_explore_" + cardIDC + "_" + planetName + "_distantSuns", exploreModelC.getName());
            Button resolveExploreH = Buttons.green("resolve_explore_" + cardIDH + "_" + planetName + "_distantSuns", exploreModelH.getName());
            Button resolveExploreI = Buttons.green("resolve_explore_" + cardIDI + "_" + planetName + "_distantSuns", exploreModelI.getName());
            List<Button> buttons = List.of(resolveExploreC, resolveExploreH, resolveExploreI);
            List<MessageEmbed> embeds = List.of(exploreModelC.getRepresentationEmbed(), exploreModelH.getRepresentationEmbed(), exploreModelI.getRepresentationEmbed());
            String message = player.getRepresentation() + " please choose 1 Explore card to resolve.";
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
            return;

        }

        if (player.hasAbility("distant_suns")) {
            if (Helper.mechCheck(planetName, game, player)) {
                if (!NRACheck) {
                    if (player.hasTech("pfa")) { //Pre-Fab Arcologies
                        PlanetRefresh.doAction(player, planetName);
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been automatically refreshed because you have Pre-Fab Arcologies.");
                    }
                    String message = "Please decide whether or not to use your " + Emojis.Naaz + "**Distant Suns** (explore twice) ability.";
                    Button resolveExplore1 = Buttons.green("distant_suns_accept_" + planetName + "_" + drawColor, "Choose to Explore Twice");
                    Button resolveExplore2 = Buttons.red("distant_suns_decline_" + planetName + "_" + drawColor, "Decline Distant Suns");
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    return;
                } else if (numExplores == 2) {
                    String cardID1 = game.drawExplore(drawColor);
                    String cardID2 = game.drawExplore(drawColor);
                    if (cardID1 == null) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Planet cannot be explored");
                        return;
                    }
                    ExploreModel exploreModel1 = Mapper.getExplore(cardID1);
                    ExploreModel exploreModel2 = Mapper.getExplore(cardID2);

                    // Report to common channel
                    String reportMessage = player.getFactionEmoji() + " used their " + Emojis.Naaz + "**Distant Suns** ability and found a **" + exploreModel1.getName() + "** and a **" + exploreModel2.getName() + "** on " + Helper.getPlanetRepresentationPlusEmoji(planetName);
                    if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                        MessageHelper.sendMessageToChannel(game.getActionsChannel(), reportMessage);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), reportMessage);
                    }

                    Button resolveExplore1 = Buttons.green("resolve_explore_" + cardID1 + "_" + planetName + "_distantSuns", exploreModel1.getName());
                    Button resolveExplore2 = Buttons.green("resolve_explore_" + cardID2 + "_" + planetName + "_distantSuns", exploreModel2.getName());
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed(), exploreModel2.getRepresentationEmbed());
                    String message = player.getRepresentation() + " please choose 1 Explore card to resolve.";
                    MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
                    return;
                }
            }
        }
        if (player.hasAbility("deep_mining") && tile != null) {
            UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
            if (unitHolder.getUnitCount(UnitType.Mech, player.getColor()) > 0 || unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) > 0 || unitHolder.getUnitCount(UnitType.Pds, player.getColor()) > 0) {
                if (!NRACheck) {
                    String message = "Please decide whether or not to use your " + Emojis.gledge + "**Deep Mining** (gain 1TG instead of explore) ability.";
                    Button resolveExplore1 = Buttons.green("deep_mining_accept", "Choose to Gain 1TG instead of exploring");
                    Button resolveExplore2 = Buttons.red("deep_mining_decline_" + planetName + "_" + drawColor, "Choose to Explore");
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    return;
                }
            }
        }

        String cardID = game.drawExplore(drawColor);
        if (cardID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Planet cannot be explored");
            return;
        }
        String position_ = tile == null ? "none" : tile.getPosition();
        String messageText = player.getRepresentation() + " explored " + Emojis.getEmojiFromDiscord(drawColor) +
            "Planet " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " *(tile " + position_ + ")*:";
        if (player.hasUnexhaustedLeader("lanefiragent")) {
            ExploreModel exploreModel = Mapper.getExplore(cardID);
            String name1 = exploreModel.getName();
            Button resolveExplore1 = Buttons.green("lanefirAgentRes_Decline_" + drawColor + "_" + cardID + "_" + planetName, "Choose " + name1);
            Button resolveExplore2 = Buttons.green("lanefirAgentRes_Accept_" + drawColor + "_" + planetName, "Use Lanefir Agent");
            List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
            String message = player.getRepresentationUnfogged() + " You have " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Vassa Hagi, the Lanefir" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, and thus may decline this explore to draw another one instead.";
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), pF + " found a " + name1 + " on " + planetName);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " on " + planetName);
            }
            ExploreModel exploreModel1 = Mapper.getExplore(cardID);
            List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
            return;
        }
        if (player.hasTech("absol_sdn")) {
            ExploreModel exploreModel = Mapper.getExplore(cardID);
            String name1 = exploreModel.getName();
            Button resolveExplore1 = Buttons.green("absolsdn_Decline_" + drawColor + "_" + cardID + "_" + planetName, "Resolve " + name1);
            Button resolveExplore2 = Buttons.green("absolsdn_Accept" + drawColor + "_" + planetName, "Get 1TG");
            List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
            String message = player.getRepresentationUnfogged() + " You have Scanlink Drone Network, and thus may decline this explore to get 1TG.";
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), pF + " found a " + name1 + " on " + planetName);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " on " + planetName);
            }
            ExploreModel exploreModel1 = Mapper.getExplore(cardID);
            List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
            return;
        }
        resolveExplore(event, cardID, tile, planetName, messageText, player, game);
        if (player.hasTech("pfa")) { //Pre-Fab Arcologies
            PlanetRefresh.doAction(player, planetName);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been automatically refreshed because you have Pre-Fab Arcologies.");
        }
        if (ButtonHelper.doesPlayerHaveFSHere("ghemina_flagship_lord", player, tile)) {
            new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf " + planetName, game);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Infantry added due to presence of The Lord (a Ghemina flagship) . Technically happens after exploring.");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander") && game.getPhaseOfGame().contains("agenda")) {
            PlanetRefresh.doAction(player, planetName);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been refreshed because of Quaxdol Junitas, the Florzen Commander.");
            AgendaHelper.listVoteCount(game, game.getMainGameChannel());
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
            UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
            Tile tileWithPlanet = game.getTileFromPlanet(planetName);
            if (tileWithPlanet == null) {
                MessageHelper.sendMessageToEventChannel(event, "An error occurred while placing 1 infantry. Resolve manually.");
                return;
            }
            tileWithPlanet.getUnitHolders().get(planetName).addUnit(infKey, 1);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Added infantry to planet because of Master Halbert, the Lanefir Commander.");
        }
        if (player.hasTech("dslaner")) {
            player.setAtsCount(player.getAtsCount() + numExplores);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
        }
        if (ButtonHelper.isPlanetLegendaryOrTechSkip(planetName, game) && Helper.getPlayerFromUnlockedLeader(game, "augersagent") != null) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2.hasUnexhaustedLeader("augersagent")) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("exhaustAgent_augersagent_" + player.getFaction(), "Use Augers Agent on " + player.getColor(), Emojis.augers));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    String msg2 = p2.getRepresentationUnfogged() + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Clodho, the Augers" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, on " + player.getFactionEmojiOrColor() + " to give them 2TGs.";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg2, buttons);
                }
            }
        }
    }
}
