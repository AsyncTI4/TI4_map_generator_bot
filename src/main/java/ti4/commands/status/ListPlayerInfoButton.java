package ti4.commands.status;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
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
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
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
import ti4.model.TechnologyModel.TechnologyType;

public class ListPlayerInfoButton extends StatusSubcommandData {
    public ListPlayerInfoButton() {
        super(Constants.TURN_ORDER, "List Turn order with SC played and Player passed status");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        //Game game = getActiveGame();
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

    public static void resolveOfferInfoButtonStep2(ButtonInteractionEvent event, String buttonID, Game game) {
        String category = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        String msg = "";
        if (category.equalsIgnoreCase("objective")) {
            buttons.add(Button.success("showObjInfo_both", "All Objectives in Game"));
            buttons.add(Button.primary("showObjInfo_1", "All Stage 1s Possible"));
            buttons.add(Button.primary("showObjInfo_2", "All Stage 2s Possible"));
        } else {
            for (Player p2 : game.getRealPlayers()) {
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

    public static void resolveOfferInfoButtonStep3(ButtonInteractionEvent event, String buttonID, Game game,
        Player player) {
        String category = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        if (faction.equalsIgnoreCase("all")) {
            for (Player p2 : game.getRealPlayers()) {
                switch (category) {
                    case "allFaction" -> {
                        sb.append(new Stats().getPlayersCurrentStatsText(p2, game));
                        for (String ability : p2.getAbilities()) {
                            messageEmbeds.add(Mapper.getAbility(ability).getRepresentationEmbed());
                        }
                        for (Leader lead : p2.getLeaders()) {
                            messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed(true, true, true, true));
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
                            sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
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
                        sb.append(new Stats().getPlayersCurrentStatsText(p2, game));
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
                            sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
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
                                messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed(true, true, true, true));
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
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            switch (category) {
                case "allFaction" -> {
                    sb.append(new Stats().getPlayersCurrentStatsText(p2, game));
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
                        sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
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
                    sb.append(new Stats().getPlayersCurrentStatsText(p2, game));
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
                        sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
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

    public static void showObjInfo(ButtonInteractionEvent event, String buttonID, Game game) {

        String extent = buttonID.split("_")[1];
        if (extent.equalsIgnoreCase("both")) {
            ListPlayerInfoButton.displayerScoringProgression(game, true, event, "both");
        } else {
            ListPlayerInfoButton.displayerScoringProgression(game, false, event, extent);
            event.getMessage().delete().queue();
        }
    }

    public static int getObjectiveThreshold(String objID) {
        return switch (objID) {
            // stage 1's
            case "push_boundaries" -> 2;
            case "outer_rim" -> 3;
            case "make_history" -> 2;
            case "infrastructure" -> 3;
            case "corner" -> 4;
            case "develop" -> 2;
            case "diversify" -> 2;
            case "monument" -> 8;
            case "expand_borders" -> 6;
            case "research_outposts" -> 3;
            case "intimidate" -> 2;
            case "lead" -> 3;
            case "trade_routes" -> 5;
            case "amass_wealth" -> 9;
            case "build_defenses" -> 4;
            case "lost_outposts" -> 2;
            case "engineer_marvel" -> 1;
            case "deep_space" -> 3;
            case "raise_fleet" -> 5;
            case "sway_council" -> 8;
            // stage 2's
            case "centralize_trade" -> 10;
            case "conquer" -> 1;
            case "brain_trust" -> 5;
            case "golden_age" -> 16;
            case "galvanize" -> 6;
            case "manipulate_law" -> 16;
            case "master_science" -> 4;
            case "revolutionize" -> 3;
            case "subdue" -> 11;
            case "unify_colonies" -> 6;
            case "supremacy" -> 1;
            case "become_legend" -> 4;
            case "command_armada" -> 8;
            case "massive_cities" -> 7;
            case "control_borderlands" -> 5;
            case "vast_reserves" -> 18;
            case "vast_territories" -> 5;
            case "protect_border" -> 5;
            case "ancient_monuments" -> 3;
            case "distant_lands" -> 2;
            default -> 0;
        };
    }

    public static void displayerScoringProgression(Game game, boolean onlyThisGameObj,
        GenericInteractionCreateEvent event, String stage1sOrTwos) {
        String msg = "";
        int x = 1;
        if (onlyThisGameObj) {
            for (String id : game.getRevealedPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(id) != null) {
                    msg = msg + representScoring(game, id, x) + "\n";
                    x++;
                }
            }
        } else {
            for (String id : Mapper.getPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(id).getSource() == ComponentSource.pok
                    || Mapper.getPublicObjective(id).getSource() == ComponentSource.base) {
                    if (stage1sOrTwos.equalsIgnoreCase("" + Mapper.getPublicObjective(id).getPoints())
                        || stage1sOrTwos.equalsIgnoreCase("both")) {
                        msg = msg + representScoring(game, id, x) + "\n";
                        x++;
                    }

                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    public static String representScoring(Game game, String objID, int x) {
        String representation = "";
        PublicObjectiveModel model = Mapper.getPublicObjective(objID);
        if (x > 0) {
            representation = x + ". " + model.getRepresentation() + "\n> ";
        } else {
            representation = model.getRepresentation() + "\n> ";
        }
        if (!game.isFoWMode()) {
            for (Player player : game.getRealPlayers()) {
                representation = representation + player.getFactionEmoji() + ": ";
                if (game.getRevealedPublicObjectives().containsKey(objID)
                    && game.didPlayerScoreThisAlready(player.getUserID(), objID)) {
                    representation = representation + "✅  ";
                } else {
                    representation = representation + getPlayerProgressOnObjective(objID, game, player) + "/"
                        + getObjectiveThreshold(objID) + "  ";
                }
            }
        }
        return representation;
    }

    public static int getPlayerProgressOnObjective(String objID, Game game, Player player) {
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
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.isEdgeOfBoard(game)
                        && tile != player.getHomeSystemTile()) {
                        edge++;
                    }
                }
                return edge;
            }
            case "make_history", "become_legend" -> {
                int counter = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && (tile.getTileID().equalsIgnoreCase("18")
                        || tile.isAnomaly(game) || ButtonHelper.isTileLegendary(tile, game))) {
                        counter++;
                    }
                }
                return counter;
            }
            case "infrastructure", "protect_border" -> {
                int counter = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (uH != null && game.getTileFromPlanet(planet) != player.getHomeSystemTile()
                        && (uH.getUnitCount(UnitType.Spacedock, player) > 0
                            || uH.getUnitCount(UnitType.Pds, player) > 0)) {
                        counter++;
                    }
                }
                if (player.hasAbility("privileged_citizenry")) {
                    counter = counter + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds", false);
                }
                return counter;
            }
            case "corner", "unify_colonies" -> {
                int max = Math.max(ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial"),
                    ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural"));
                max = Math.max(ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous"), max);
                return max;
            }
            case "develop", "revolutionize" -> {
                return ButtonHelper.getNumberOfUnitUpgrades(player);
            }
            case "diversify", "master_science" -> {
                int numAbove1 = 0;
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyType.WARFARE) > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyType.PROPULSION) > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyType.BIOTIC) > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyType.CYBERNETIC) > 1) {
                    numAbove1++;
                }
                return numAbove1;
            }
            case "monument", "golden_age" -> {
                int x = Helper.getPlayerResourcesAvailable(player, game) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x = x + player.getTg() + comms;
                }
                return x;
            }
            case "expand_borders", "subdue" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(planet);
                    if (tile != null && (!tile.isHomeSystem() || tile.getTileID().equalsIgnoreCase("17"))) {
                        count++;
                    }
                }
                return count;
            }
            case "research_outposts", "brain_trust" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    if (ButtonHelper.checkForTechSkips(game, planet)) {
                        count++;
                    }
                }
                return count;
            }
            case "intimidate" -> {
                int count = 0;
                Tile tile = game.getTileFromPlanet("mr");
                if (tile == null) {
                    return 2;
                }
                for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player,
                    false)) {
                    Tile tile2 = game.getTileByPosition(pos);
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
                int forResources = Math.min(3, Helper.getPlayerResourcesAvailable(player, game));
                int forInfluence = Math.min(3, Helper.getPlayerInfluenceAvailable(player, game));
                if (player.hasTech("mc")) {
                    leftOverTg = leftOverTg * 2;
                }
                return forTG + leftOverTg + forInfluence + forResources;
            }
            case "build_defenses", "massive_cities" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "sd", false);
            }
            case "lost_outposts", "ancient_monuments" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (uH != null && uH instanceof Planet plan) {
                        if (plan.hasAttachment()) {
                            count++;
                        }
                    }
                }
                return count;
            }
            case "engineer_marvel" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "fs", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "lady", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "warsun", false);
            }
            case "deep_space", "vast_territories" -> {
                return ButtonHelper.getNumberOfTilesPlayerIsInWithNoPlanets(game, player);
            }
            case "raise_fleet", "command_armada" -> {
                int x = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                        x = Math.max(x, ButtonHelper.checkNumberNonFighterShips(player, game, tile));
                    }
                }
                return x;
            }
            case "sway_council", "manipulate_law" -> {
                int x = Helper.getPlayerInfluenceAvailable(player, game) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x = x + player.getTg() + comms;
                }
                return x;
            }
            case "conquer" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(planet);
                    if (tile != null && tile.isHomeSystem() && tile != player.getHomeSystemTile()) {
                        count++;
                    }
                }
                return count;
            }
            case "supremacy" -> {
                int count = 0;
                for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship,
                    UnitType.Warsun, UnitType.Lady)) {
                    if ((tile.isHomeSystem() && tile != player.getHomeSystemTile())
                        || tile.getTileID().equalsIgnoreCase("18")) {
                        count++;
                    }
                }
                return count;
            }
            case "vast_reserves" -> {
                int forTG = Math.min(6, player.getTg() + comms);
                int leftOverTg = player.getTg() + comms - forTG;
                int forResources = Math.min(6, Helper.getPlayerResourcesAvailable(player, game));
                int forInfluence = Math.min(6, Helper.getPlayerInfluenceAvailable(player, game));
                if (player.hasTech("mc")) {
                    leftOverTg = leftOverTg * 2;
                }
                return forTG + leftOverTg + forInfluence + forResources;
            }
            case "distant_lands" -> {
                int count = 0;
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    Tile tile = p2.getHomeSystemTile();
                    if (tile == null) {
                        continue;
                    }
                    for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player,
                        false)) {
                        Tile tile2 = game.getTileByPosition(pos);
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
