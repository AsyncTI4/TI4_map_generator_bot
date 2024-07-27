package ti4.commands.status;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

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
        super(Constants.TURN_ORDER, "List turn order, with strategy card played and player passed status.");
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
        buttons.add(Button.success("offerInfoButtonStep2_tech", "Researched Technology"));
        buttons.add(Button.success("offerInfoButtonStep2_ftech", "Faction Technology"));
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
            if (p2 == null) return;
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
                        messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed(true, true, true, true));
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

    public static int getObjectiveThreshold(String objID, Game game) {
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

            //status phase secrets
            case "pem" -> 8; // 8 production
            case "sai" -> 1; //legendary
            case "syc" -> 1; // control a planet in same system someone else does
            case "sb" -> 1; // have a PN in your play area
            case "otf" -> 9; // 9 gf on a planet without a dock
            case "mtm" -> 4; // 4 mechs on 4 planets
            case "hrm" -> 12; // 12 resources
            case "eh" -> 12; // 12 influence
            case "dp" -> 3; // 3 laws in play
            case "dhw" -> 2; // 2 frags
            case "dfat" -> 1; //nexus
            case "te" -> 1; // be next to HS
            case "ose" -> 3; // control rex and have 3 ships
            case "mrm" -> 4; //4 hazardous
            case "mlp" -> 4; //4 techs of a color
            case "mp" -> 4; // 4 industrial
            case "lsc" -> 3; // 3 anomalies
            case "fwm" -> 3; // 3 SD
            case "fsn" -> 5; // 5 AC
            case "gamf" -> 5; // 5 dreads
            case "ans" -> 2; // 2 faction tech
            case "btgk" -> 2; // in systems with alphas and betas
            case "ctr" -> 6; // ships in 6 systems
            case "csl" -> 1; // blockade space dock
            case "eap" -> 4; // 4 PDS
            case "faa" -> 4; // 4 cultural
            case "fc" -> (game.getRealPlayers().size() - 1); // neighbors

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
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                representation = representation + player.getFactionEmoji() + ": ";
                if (game.getRevealedPublicObjectives().containsKey(objID)
                    && game.didPlayerScoreThisAlready(player.getUserID(), objID)) {
                    representation = representation + "✅  ";
                } else {
                    representation = representation + getPlayerProgressOnObjective(objID, game, player) + "/"
                        + getObjectiveThreshold(objID, game) + "  ";
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
                    boolean tileCounts = tile.isMecatol() || tile.isAnomaly(game) || ButtonHelper.isTileLegendary(tile, game);
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && tileCounts) {
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
                Tile mecatol = game.getMecatolTile();
                if (mecatol == null) return 2;
                for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, mecatol.getPosition(), player, false)) {
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
                    } else {
                        if (planet.contains("custodia") && game.getStoredValue("terraformedPlanet").equalsIgnoreCase(planet)) {
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
                for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship, UnitType.Warsun, UnitType.Lady)) {
                    if ((tile.isHomeSystem() && tile != player.getHomeSystemTile()) || tile.isMecatol()) {
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
            //status phase secrets
            case "pem" -> {
                return ButtonHelper.checkHighestProductionSystem(player, game); // 8 production
            }
            case "sai" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (uH != null && uH instanceof Planet planetP) {
                        if (planetP.isLegendary()) {
                            count++;
                        }
                    } else {
                        if (Mapper.getPlanet(planet) != null && Mapper.getPlanet(planet).isLegendary()) {
                            count++;
                        }
                    }
                }
                return count;
            }
            case "syc" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(planet);
                    for (Player p2 : game.getRealPlayers()) {
                        if (p2 == player) {
                            continue;
                        }
                        if (tile != null && FoWHelper.playerHasPlanetsInSystem(p2, tile)) {
                            count++;
                            break;
                        }
                    }
                }
                return count;
            }
            case "sb" -> {
                return player.getPromissoryNotesInPlayArea().size();
            }
            case "otf" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (uH != null && uH.getUnitCount(UnitType.Spacedock, player) < 1) {
                        count = Math.max(count, ButtonHelper.getNumberOfGroundForces(player, uH));
                    }
                }
                return count;
            }
            case "mtm" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (uH != null && uH.getUnitCount(UnitType.Mech, player) > 0) {
                        count++;
                    }
                }
                return count;
            }
            case "hrm" -> { // 12 resources
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources = resources + Helper.getPlanetResources(planet, game);
                }
                return resources;
            }
            case "eh" -> { // 12 influence
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources = resources + Helper.getPlanetInfluence(planet, game);
                }
                return resources;
            }
            case "dp" -> {
                return game.getLaws().size(); // 3 laws in play
            }
            case "dhw" -> {
                return player.getFragments().size(); // 2 frags
            }
            case "dfat" -> {
                Tile tile = game.getTileFromPlanet("mallice");
                if (tile == null || !FoWHelper.playerHasUnitsInSystem(player, tile)) {
                    return 0;
                } else {
                    return 1;
                }
            }
            case "te" -> {
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
                        false, false)) {
                        Tile tile2 = game.getTileByPosition(pos);
                        if (ButtonHelper.checkNumberShips(player, game, tile2) > 0) {
                            count++;
                        }
                    }
                }
                return count;
            }
            case "ose" -> {
                Tile mecatol = game.getMecatolTile();
                if (mecatol == null || !FoWHelper.playerHasUnitsInSystem(player, mecatol) || !CollectionUtils.containsAny(player.getPlanetsAllianceMode(), Constants.MECATOLS)) {
                    return 0;
                } else {
                    return ButtonHelper.checkNumberShips(player, game, mecatol);
                }
            }
            case "mrm" -> {
                return ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous"); //4 hazardous
            }
            case "mlp" -> {//4 techs of a color
                int maxNum = 0;
                maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyType.WARFARE));
                maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyType.PROPULSION));
                maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyType.CYBERNETIC));
                maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyType.BIOTIC));
                return maxNum;
            }
            case "mp" -> {
                return ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial"); // 4 industrial
            }
            case "lsc" -> {
                int count = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (ButtonHelper.checkNumberShips(player, game, tile) > 0) {
                        for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player,
                            false, false)) {
                            Tile tile2 = game.getTileByPosition(pos);
                            if (tile2.isAnomaly(game)) {
                                count++;
                                break;
                            }
                        }
                    }
                }
                return count;
            }
            case "fwm" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "spacedock"); // 3 SD
            }
            case "fsn" -> {
                return player.getAc(); // 5 AC
            }
            case "gamf" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought"); // 5 dreads
            }
            case "ans" -> {
                int count = 0;
                for (String nekroTech : player.getTechs()) {
                    if ("vax".equalsIgnoreCase(nekroTech) || "vay".equalsIgnoreCase(nekroTech)) {
                        continue;
                    }
                    if (!"".equals(Mapper.getTech(nekroTech).getFaction().orElse(""))) {
                        count = count + 1;
                    }

                }
                return count;
            }
            case "btgk" -> {
                int alpha = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.doesTileHaveAlpha(game, tile.getPosition()) && FoWHelper.playerHasShipsInSystem(player, tile)) {
                        alpha = 1;
                        break;
                    }
                }

                int beta = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.doesTileHaveBeta(game, tile.getPosition()) && FoWHelper.playerHasShipsInSystem(player, tile)) {
                        beta = 1;
                        break;
                    }
                }
                return alpha + beta;
            }
            case "ctr" -> {
                int count = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (ButtonHelper.checkNumberShips(player, game, tile) > 0) {
                        count++;
                    }
                }
                return count;
            }
            case "csl" -> {
                int count = 0;
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, p2, UnitType.Spacedock)) {
                        if (ButtonHelper.checkNumberShips(player, game, tile) > 0) {
                            count++;
                        }
                    }
                }
                return count;
            }
            case "eap" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds"); // 4 PDS
            }
            case "faa" -> { // 4 cultural
                return ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural");
            }
            case "fc" -> {
                return player.getNeighbourCount(); // neighbors
            }

        }

        return 0;
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        // We reply in execute command
    }
}
