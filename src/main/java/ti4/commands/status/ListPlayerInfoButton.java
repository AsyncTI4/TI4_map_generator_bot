package ti4.commands.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.player.Stats;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;
import ti4.model.Source.ComponentSource;

public class ListPlayerInfoButton extends StatusSubcommandData {
    public ListPlayerInfoButton() {
        super(Constants.TURN_ORDER, "List Turn order with SC played and Player passed status");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
    }

    public static void offerInfoButtons(ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("offerInfoButtonStep2_allFaction", "All Info On A Faction"));
        buttons.add(Button.success("offerInfoButtonStep2_objective", "Objective Info"));
        buttons.add(Button.success("offerInfoButtonStep2_abilities", "Ability Info"));
        buttons.add(Button.success("offerInfoButtonStep2_stats", "Player Stats Info"));
        buttons.add(Button.success("offerInfoButtonStep2_agent", "Agent Info"));
        buttons.add(Button.success("offerInfoButtonStep2_commander", "Commander Info"));
        buttons.add(Button.success("offerInfoButtonStep2_hero", "Hero Info"));
        buttons.add(Button.success("offerInfoButtonStep2_relic", "Relic Info"));
        buttons.add(Button.success("offerInfoButtonStep2_planet", "Planet Info"));
        buttons.add(Button.success("offerInfoButtonStep2_units", "Special Units"));
        buttons.add(Button.success("offerInfoButtonStep2_pn", "Faction PN"));
        buttons.add(Button.success("offerInfoButtonStep2_tech", "Researched Tech"));
        buttons.add(Button.success("offerInfoButtonStep2_ftech", "Faction Tech"));
        buttons.add(Buttons.REFRESH_INFO);
        String msg = "Select the category you'd like more info on. You will then be able to select either a specific faction's info you want, or every factions";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
        // event.getMessage().delete().queue();
    }

    public static void resolveOfferInfoButtonStep2(ButtonInteractionEvent event, String buttonID, Game activeGame) {
        String category = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        String msg = "";
        if (category.equalsIgnoreCase("objective")) {
            buttons.add(Button.success("showObjInfo_both", "All Objectives in Game"));
            buttons.add(Button.primary("showObjInfo_1", "All Stage 1s Possible"));
            buttons.add(Button.primary("showObjInfo_2", "All Stage 2s Possible"));
        } else {
            for (Player p2 : activeGame.getRealPlayers()) {
                Button button = Button.secondary("offerInfoButtonStep3_" + category + "_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
            buttons.add(Button.success("offerInfoButtonStep3_" + category + "_all", "All Factions"));

        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
        // event.getMessage().delete().queue();
    }

    public static void resolveOfferInfoButtonStep3(ButtonInteractionEvent event, String buttonID, Game activeGame,
        Player player) {
        String category = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        if (faction.equalsIgnoreCase("all")) {
            for (Player p2 : activeGame.getRealPlayers()) {
                switch (category) {
                    case "allFaction" -> {
                        sb.append(new Stats().getPlayersCurrentStatsText(p2, activeGame));
                        for (String ability : p2.getAbilities()) {
                            messageEmbeds.add(Mapper.getAbility(ability).getRepresentationEmbed());
                        }
                        for (Leader lead : p2.getLeaders()) {
                            messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed());
                        }
                        for (String tech : p2.getFactionTechs()) {
                            messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                        }
                        for (String unit : p2.getUnitsOwned()) {
                            if (unit.contains("_")) {
                                messageEmbeds.add(Mapper.getUnit(unit).getRepresentationEmbed());
                            }
                        }
                        for (String relic : p2.getRelics()) {
                            messageEmbeds.add(Mapper.getRelic(relic).getRepresentationEmbed());
                        }
                        for (String planet : p2.getPlanets()) {
                            sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, activeGame)
                                + "\n");
                        }
                        for (String tech : p2.getTechs()) {
                            messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                        }
                        for (String pn : p2.getPromissoryNotesOwned()) {
                            if (!pn.contains("_")) {
                                messageEmbeds.add(Mapper.getPromissoryNote(pn).getRepresentationEmbed());
                            }
                        }
                    }
                    case "abilities" -> {
                        for (String ability : p2.getAbilities()) {
                            messageEmbeds.add(Mapper.getAbility(ability).getRepresentationEmbed());
                        }
                    }
                    case "stats" -> {
                        sb.append(new Stats().getPlayersCurrentStatsText(p2, activeGame));
                    }
                    case "relic" -> {
                        for (String relic : p2.getRelics()) {
                            messageEmbeds.add(Mapper.getRelic(relic).getRepresentationEmbed());
                        }
                    }
                    case "ftech" -> {
                        for (String tech : p2.getFactionTechs()) {
                            messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                        }
                    }
                    case "tech" -> {
                        for (String tech : p2.getTechs()) {
                            messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                        }
                    }
                    case "planet" -> {
                        for (String planet : p2.getPlanets()) {
                            sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, activeGame)
                                + "\n");
                        }
                    }
                    case "pn" -> {
                        for (String pn : p2.getPromissoryNotesOwned()) {
                            if (!pn.contains("_")) {
                                messageEmbeds.add(Mapper.getPromissoryNote(pn).getRepresentationEmbed());
                            }
                        }
                    }
                    case "agent", "commander", "hero" -> {
                        for (Leader lead : p2.getLeaders()) {
                            if (lead.getId().contains(category)) {
                                messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed());
                            }
                        }
                    }
                    case "units" -> {
                        for (String unit : p2.getUnitsOwned()) {
                            if (unit.contains("_")) {
                                messageEmbeds.add(Mapper.getUnit(unit).getRepresentationEmbed());
                            }
                        }
                    }
                }
            }
        } else {
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            switch (category) {
                case "allFaction" -> {
                    sb.append(new Stats().getPlayersCurrentStatsText(p2, activeGame));
                    for (String ability : p2.getAbilities()) {
                        messageEmbeds.add(Mapper.getAbility(ability).getRepresentationEmbed());
                    }
                    for (String unit : p2.getUnitsOwned()) {
                        if (unit.contains("_")) {
                            messageEmbeds.add(Mapper.getUnit(unit).getRepresentationEmbed());
                        }
                    }
                    for (Leader lead : p2.getLeaders()) {
                        messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed());
                    }
                    for (String tech : p2.getFactionTechs()) {
                        messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                    }
                    for (String relic : p2.getRelics()) {
                        messageEmbeds.add(Mapper.getRelic(relic).getRepresentationEmbed());
                    }
                    for (String planet : p2.getPlanets()) {
                        sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, activeGame)
                            + "\n");
                    }
                    for (String tech : p2.getTechs()) {
                        messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                    }
                    for (String pn : p2.getPromissoryNotesOwned()) {
                        if (!pn.contains("_")) {
                            messageEmbeds.add(Mapper.getPromissoryNote(pn).getRepresentationEmbed());
                        }
                    }
                }
                case "abilities" -> {
                    for (String ability : p2.getAbilities()) {
                        messageEmbeds.add(Mapper.getAbility(ability).getRepresentationEmbed());
                    }
                }
                case "stats" -> {
                    sb.append(new Stats().getPlayersCurrentStatsText(p2, activeGame));
                }
                case "relic" -> {
                    for (String relic : p2.getRelics()) {
                        messageEmbeds.add(Mapper.getRelic(relic).getRepresentationEmbed());
                    }
                }
                case "ftech" -> {
                    for (String tech : p2.getFactionTechs()) {
                        messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                    }
                }
                case "tech" -> {
                    for (String tech : p2.getTechs()) {
                        messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                    }
                }
                case "planet" -> {
                    for (String planet : p2.getPlanets()) {
                        sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, activeGame)
                            + "\n");
                    }
                }
                case "agent", "commander", "hero" -> {
                    for (Leader lead : p2.getLeaders()) {
                        if (lead.getId().contains(category)) {
                            messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed());
                        }
                    }
                }
                case "units" -> {
                    for (String unit : p2.getUnitsOwned()) {
                        if (unit.contains("_")) {
                            messageEmbeds.add(Mapper.getUnit(unit).getRepresentationEmbed());
                        }
                    }
                }
                case "pn" -> {
                    for (String pn : p2.getPromissoryNotesOwned()) {
                        if (!pn.contains("_")) {
                            messageEmbeds.add(Mapper.getPromissoryNote(pn).getRepresentationEmbed());
                        }
                    }
                }
            }

        }
        MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), sb.toString(), messageEmbeds);
        event.getMessage().delete().queue();
    }

    public static void showObjInfo(ButtonInteractionEvent event, String buttonID, Game activeGame) {
        event.getMessage().delete().queue();
        String extent = buttonID.split("_")[1];
        if (extent.equalsIgnoreCase("both")) {
            ListPlayerInfoButton.displayerScoringProgression(activeGame, true, event, "both");
        } else {
            ListPlayerInfoButton.displayerScoringProgression(activeGame, false, event, extent);
        }
    }

    public static int getObjectiveThreshold(String objID) {
        switch (objID) {
            case "push_boundaries" -> {
                return 2;
            }
            case "outer_rim" -> {
                return 3;
            }
            case "make_history" -> {
                return 2;
            }
            case "infrastructure" -> {
                return 3;
            }
            case "corner" -> {
                return 4;
            }
            case "develop" -> {
                return 2;
            }
            case "diversify" -> {
                return 2;
            }
            case "monument" -> {
                return 8;
            }
            case "expand_borders" -> {
                return 6;
            }
            case "research_outposts" -> {
                return 3;
            }
            case "intimidate" -> {
                return 2;
            }
            case "lead" -> {
                return 3;
            }
            case "trade_routes" -> {
                return 5;
            }
            case "amass_wealth" -> {
                return 9;
            }
            case "build_defenses" -> {
                return 4;
            }
            case "lost_outposts" -> {
                return 2;
            }
            case "engineer_marvel" -> {
                return 1;
            }
            case "deep_space" -> {
                return 3;
            }
            case "raise_fleet" -> {
                return 5;
            }
            case "sway_council" -> {
                return 8;
            }
            case "centralize_trade" -> {
                return 10;
            }
            case "conquer" -> {
                return 1;
            }
            case "brain_trust" -> {
                return 5;
            }
            case "golden_age" -> {
                return 16;
            }
            case "galvanize" -> {
                return 6;
            }
            case "manipulate_law" -> {
                return 16;
            }
            case "master_science" -> {
                return 4;
            }
            case "revolutionize" -> {
                return 3;
            }
            case "subdue" -> {
                return 11;
            }
            case "unify_colonies" -> {
                return 6;
            }
            case "supremacy" -> {
                return 1;
            }
            case "become_legend" -> {
                return 4;
            }
            case "command_armada" -> {
                return 8;
            }
            case "massive_cities" -> {
                return 7;
            }
            case "control_borderlands" -> {
                return 5;
            }
            case "vast_reserves" -> {
                return 18;
            }
            case "vast_territories" -> {
                return 5;
            }
            case "protect_border" -> {
                return 5;
            }
            case "ancient_monuments" -> {
                return 3;
            }
            case "distant_lands" -> {
                return 2;
            }

        }
        return 0;
    }

    public static void displayerScoringProgression(Game activeGame, boolean onlyThisGameObj,
        GenericInteractionCreateEvent event, String stage1sOrTwos) {
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), " This doesnt work in fog");
            return;
        }
        String msg = "";
        int x = 1;
        if (onlyThisGameObj) {
            for (String id : activeGame.getRevealedPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(id) != null) {
                    msg = msg + representScoring(activeGame, id, x) + "\n";
                    x++;
                }
            }
        } else {
            for (String id : Mapper.getPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(id).getSource() == ComponentSource.pok
                    || Mapper.getPublicObjective(id).getSource() == ComponentSource.base) {
                    if (stage1sOrTwos.equalsIgnoreCase("" + Mapper.getPublicObjective(id).getPoints())
                        || stage1sOrTwos.equalsIgnoreCase("both")) {
                        msg = msg + representScoring(activeGame, id, x) + "\n";
                        x++;
                    }

                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    public static String representScoring(Game activeGame, String objID, int x) {
        String representation = "";
        PublicObjectiveModel model = Mapper.getPublicObjective(objID);
        if (x > 0) {
            representation = x + ". " + model.getRepresentation() + "\n> ";
        } else {
            representation = model.getRepresentation() + "\n> ";
        }
        for (Player player : activeGame.getRealPlayers()) {
            representation = representation + player.getFactionEmoji() + ": ";
            if (activeGame.getRevealedPublicObjectives().containsKey(objID)
                && activeGame.didPlayerScoreThisAlready(player.getUserID(), objID)) {
                representation = representation + "✅  ";
            } else {
                representation = representation + getPlayerProgressOnObjective(objID, activeGame, player) + "/"
                    + getObjectiveThreshold(objID) + "  ";
            }
        }
        return representation;
    }

    public static int getPlayerProgressOnObjective(String objID, Game activeGame, Player player) {
        int comms = 0;
        if (player.hasUnexhaustedLeader("keleresagent")) {
            comms = player.getCommodities();
        }
        switch (objID) {
            case "push_boundaries" -> {
                int aboveN = 0;
                for (Player p2 : player.getNeighbouringPlayers()) {
                    if (player.getPlanets().size() > p2.getPlanets().size()) {
                        aboveN = aboveN + 1;
                    }
                }
                return aboveN;
            }
            case "outer_rim", "control_borderlands" -> {
                int edge = 0;
                for (Tile tile : activeGame.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.isEdgeOfBoard(activeGame)
                        && tile != FoWHelper.getPlayerHS(activeGame, player)) {
                        edge++;
                    }
                }
                return edge;
            }
            case "make_history", "become_legend" -> {
                int counter = 0;
                for (Tile tile : activeGame.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && (tile.getTileID().equalsIgnoreCase("18")
                        || tile.isAnomaly(activeGame) || ButtonHelper.isTileLegendary(tile, activeGame))) {
                        counter++;
                    }
                }
                return counter;
            }
            case "infrastructure", "protect_border" -> {
                int counter = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
                    if (uH != null && activeGame.getTileFromPlanet(planet) != FoWHelper.getPlayerHS(activeGame, player)
                        && (uH.getUnitCount(UnitType.Spacedock, player) > 0
                            || uH.getUnitCount(UnitType.Pds, player) > 0)) {
                        counter++;
                    }
                }
                if (player.hasAbility("privileged_citizenry")) {
                    counter = counter + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "pds", false);
                }
                return counter;
            }
            case "corner", "unify_colonies" -> {
                int max = Math.max(ButtonHelper.getNumberOfXTypePlanets(player, activeGame, "industrial"),
                    ButtonHelper.getNumberOfXTypePlanets(player, activeGame, "cultural"));
                max = Math.max(ButtonHelper.getNumberOfXTypePlanets(player, activeGame, "hazardous"), max);
                return max;
            }
            case "develop", "revolutionize" -> {
                return ButtonHelper.getNumberOfUnitUpgrades(player);
            }
            case "diversify", "master_science" -> {
                int numAbove1 = 0;
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, "warfare") > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, "propulsion") > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, "biotic") > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, "cybernetic") > 1) {
                    numAbove1++;
                }
                return numAbove1;
            }
            case "monument", "golden_age" -> {
                int x = Helper.getPlayerResourcesAvailable(player, activeGame) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x = x + player.getTg() + comms;
                }
                return x;
            }
            case "expand_borders", "subdue" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    Tile tile = activeGame.getTileFromPlanet(planet);
                    if (tile != null && (!tile.isHomeSystem() || tile.getTileID().equalsIgnoreCase("17"))) {
                        count++;
                    }
                }
                return count;
            }
            case "research_outposts", "brain_trust" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    if (ButtonHelper.checkForTechSkips(activeGame, planet)) {
                        count++;
                    }
                }
                return count;
            }
            case "intimidate" -> {
                int count = 0;
                Tile tile = activeGame.getTileFromPlanet("mr");
                if (tile == null) {
                    return 2;
                }
                for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tile.getPosition(), player,
                    false)) {
                    Tile tile2 = activeGame.getTileByPosition(pos);
                    if (FoWHelper.playerHasShipsInSystem(player, tile2)) {
                        count++;
                    }
                }
                return count;
            }
            case "lead", "galvanize" -> {
                return player.getTacticalCC() + player.getStrategicCC();
            }
            case "trade_routes", "centralize_trade" -> {
                return player.getTg() + comms;
            }
            case "amass_wealth" -> {
                int forTG = Math.min(3, player.getTg() + comms);
                int leftOverTg = player.getTg() + comms - forTG;
                int forResources = Math.min(3, Helper.getPlayerResourcesAvailable(player, activeGame));
                int forInfluence = Math.min(3, Helper.getPlayerInfluenceAvailable(player, activeGame));
                if (player.hasTech("mc")) {
                    leftOverTg = leftOverTg * 2;
                }
                return forTG + leftOverTg + forInfluence + forResources;
            }
            case "build_defenses", "massive_cities" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "pds", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "sd", false);
            }
            case "lost_outposts", "ancient_monuments" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
                    if (uH != null && uH instanceof Planet plan) {
                        if (plan.hasAttachment()) {
                            count++;
                        }
                    }
                }
                return count;
            }
            case "engineer_marvel" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "fs", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "lady", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "warsun", false);
            }
            case "deep_space", "vast_territories" -> {
                return ButtonHelper.getNumberOfTilesPlayerIsInWithNoPlanets(activeGame, player);
            }
            case "raise_fleet", "command_armada" -> {
                int x = 0;
                for (Tile tile : activeGame.getTileMap().values()) {
                    if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                        x = Math.max(x, ButtonHelper.checkNumberNonFighterShips(player, activeGame, tile));
                    }
                }
                return x;
            }
            case "sway_council", "manipulate_law" -> {
                int x = Helper.getPlayerInfluenceAvailable(player, activeGame) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x = x + player.getTg() + comms;
                }
                return x;
            }
            case "conquer" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    Tile tile = activeGame.getTileFromPlanet(planet);
                    if (tile != null && tile.isHomeSystem() && tile != FoWHelper.getPlayerHS(activeGame, player)) {
                        count++;
                    }
                }
                return count;
            }
            case "supremacy" -> {
                int count = 0;
                for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship,
                    UnitType.Warsun, UnitType.Lady)) {
                    if ((tile.isHomeSystem() && tile != FoWHelper.getPlayerHS(activeGame, player))
                        || tile.getTileID().equalsIgnoreCase("18")) {
                        count++;
                    }
                }
                return count;
            }
            case "vast_reserves" -> {
                int forTG = Math.min(6, player.getTg() + comms);
                int leftOverTg = player.getTg() + comms - forTG;
                int forResources = Math.min(6, Helper.getPlayerResourcesAvailable(player, activeGame));
                int forInfluence = Math.min(6, Helper.getPlayerInfluenceAvailable(player, activeGame));
                if (player.hasTech("mc")) {
                    leftOverTg = leftOverTg * 2;
                }
                return forTG + leftOverTg + forInfluence + forResources;
            }
            case "distant_lands" -> {
                int count = 0;
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    Tile tile = FoWHelper.getPlayerHS(activeGame, p2);
                    if (tile == null) {
                        continue;
                    }
                    for (String pos : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player,
                        false)) {
                        Tile tile2 = activeGame.getTileByPosition(pos);
                        if (FoWHelper.playerHasPlanetsInSystem(player, tile2)) {
                            count++;
                            break;
                        }
                    }
                }
                return count;
            }

        }

        return 0;
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        // We reply in execute command
    }
}
