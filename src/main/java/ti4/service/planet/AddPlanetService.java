package ti4.service.planet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.PromissoryNoteModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.breakthrough.FealtyUplinkService;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class AddPlanetService {

    public static void addPlanet(Player player, String planet, Game game) {
        addPlanet(player, planet, game, null, false);
    }

    public static void addPlanet(
            Player player, String planet, Game game, GenericInteractionCreateEvent event, boolean setup) {
        boolean doubleCheck = Helper.doesAllianceMemberOwnPlanet(game, planet, player);
        player.addPlanet(planet);
        EronousPlanetService.resolveCantrisPO(game, planet, player);
        player.exhaustPlanet(planet);
        if ("mirage".equals(planet) || "avernus".equals(planet) || "thundersedge".equals(planet)) {
            game.clearPlanetsCache();
        }
        Tile tile = game.getTileFromPlanet(planet);
        Planet unitHolder = game.getPlanetsInfo().get(planet);

        if (!"custodiavigilia".equalsIgnoreCase(planet) && !"ghoti".equalsIgnoreCase(planet)) {
            if (unitHolder == null || tile == null) {
                return;
            }
        }
        if (game.getRevealedPublicObjectives().size() < 3 || (unitHolder != null && unitHolder.isSpaceStation())) {
            setup = true;
        }
        if ("avernus".equalsIgnoreCase(planet)) {
            setup = false;
        }
        if (unitHolder == null) {
            BotLogger.error(
                    event != null ? new LogOrigin(event) : null,
                    "Unitholder found null in addPlanet for planet " + planet);
            unitHolder = game.getUnitHolderFromPlanet(planet);
        }
        if (player.isRealPlayer() && unitHolder.getTokenList().contains("token_freepeople.png")) {
            unitHolder.removeToken("token_freepeople.png");
        }
        if (unitHolder.getTokenList().contains("token_tomb.png") && player.hasAbility("ancient_empire")) {
            unitHolder.removeToken("token_tomb.png");
            AddUnitService.addUnits(event, player.getNomboxTile(), game, player.getColor(), "2 inf");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you captured 2 infantry from a Tomb token.");
        }

        int shrineCount = 0;
        shrineCount += (unitHolder.getTokenList().contains("token_kaltrimshrine1.png") ? 1 : 0);
        shrineCount += (unitHolder.getTokenList().contains("token_kaltrimshrine2.png") ? 1 : 0);
        shrineCount += (unitHolder.getTokenList().contains("token_kaltrimshrine3.png") ? 1 : 0);
        shrineCount += (unitHolder.getTokenList().contains("token_kaltrimshrine4.png") ? 1 : 0);
        if ((shrineCount >= 1) && player.hasAbility("questing_prince")) {
            unitHolder.removeToken("token_kaltrimshrine1.png");
            unitHolder.removeToken("token_kaltrimshrine2.png");
            unitHolder.removeToken("token_kaltrimshrine3.png");
            unitHolder.removeToken("token_kaltrimshrine4.png");
            if (game.getStoredValue("kaltrimcrownplanet").equalsIgnoreCase(planet)) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", you reclaimed your Crown Shrine from "
                                + Helper.getPlanetRepresentation(planet, game)
                                + " with your **The Questing Prince** ability. Congratz!");
                String kalt = "Kaltrim Crown Token";
                Integer id = game.addCustomPO(kalt, 1);
                game.scorePublicObjective(player.getUserID(), id);
                String message2 = "Custom public objective \"_" + kalt + "_\" has been added.\n"
                        + player.getRepresentation() + " scored \"_" + kalt + "_\".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2);
                CommanderUnlockCheckService.checkPlayer(player, "kaltrim");
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", you reclaimed " + (shrineCount == 1 ? "a Shrine" : shrineCount + " Shrines")
                                + " from "
                                + Helper.getPlanetRepresentation(planet, game)
                                + " with your **The Questing Prince** ability, but did not find the Crown token.");
            }
        }

        if (game.mecatols().contains(planet) && player.hasIIHQ()) {
            PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
            unitHolder.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
            unitHolder.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
        }
        String color = player.getColor();
        if (color != null && !"null".equals(color)) {
            String ccID = Mapper.getControlID(color);
            String ccPath = Mapper.getCCPath(ccID);
            if (ccPath != null) {
                unitHolder.addControl(ccID);
            }
            if (unitHolder.getTokenList().contains(Constants.CUSTODIAN_TOKEN_PNG)) {
                unitHolder.removeToken(Constants.CUSTODIAN_TOKEN_PNG);
                game.scorePublicObjective(player.getUserID(), 0);
                MessageChannel channel = game.getMainGameChannel();
                if (game.isFowMode()) {
                    channel = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannel(channel, "# " + player.getRepresentation() + " scored Custodians!");
                String message2 = player.getRepresentationUnfogged()
                        + ", choose the planets you wish to exhaust to spend " + MiscEmojis.Influence_6 + ".";
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                Button doneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
                buttons.add(doneExhausting);
                if (!player.hasAbility("blood_ties")) {
                    MessageHelper.sendMessageToChannelWithButtons(channel, message2, buttons);
                }
            }
        }
        boolean alreadyOwned = false;
        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player) {
                List<String> planets = player_.getPlanets();
                if (planets.contains(planet)) {
                    if (player_.getExhaustedPlanetsAbilities().contains(planet)) {
                        player.exhaustPlanetAbility(planet);
                    }
                    if (player_.isRealPlayer()) {
                        alreadyOwned = true;
                    }
                    player_.removePlanet(planet);
                    CommanderUnlockCheckService.checkPlayer(player_, "uydai");
                    List<String> relics = new ArrayList<>(player_.getRelics());
                    if ("mr".equalsIgnoreCase(planet)) {
                        String customPOName = "Ixthian Rex Point";
                        if (game.getRevealedPublicObjectives().get(customPOName) != null) {
                            int shardID = game.getRevealedPublicObjectives().get(customPOName);
                            game.unscorePublicObjective(player_.getUserID(), shardID);
                            game.scorePublicObjective(player.getUserID(), shardID);
                            String msg2 = player_.getRepresentation() + " lost Mecatol Rex and lost a victory point. "
                                    + player.getRepresentation()
                                    + " gained Mecatol Rex and a victory point.";
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
                            Helper.checkEndGame(game, player);
                        }
                    }
                    for (String relic : relics) {
                        if (relic.contains("shard")
                                && player.isRealPlayer()
                                && ButtonHelper.isPlanetLegendaryOrHome(planet, game, true, player_)
                                && !doubleCheck) {
                            String msg2 = player_.getRepresentation()
                                    + " lost _Shard of the Throne_ and lost a victory point. "
                                    + player.getRepresentation()
                                    + " gained _Shard of the Throne_ and a victory point.";
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
                            player_.removeRelic(relic);
                            player.addRelic(relic);
                            String customPOName = "Shard of the Throne";
                            if (relic.contains("absol_")) {
                                int absolShardNum = Integer.parseInt(StringUtils.right(relic, 1));
                                customPOName = "Shard of the Throne (" + absolShardNum + ")";
                            }
                            int shardID = game.getRevealedPublicObjectives().get(customPOName);
                            game.unscorePublicObjective(player_.getUserID(), shardID);
                            game.scorePublicObjective(player.getUserID(), shardID);
                            Helper.checkEndGame(game, player);
                        }
                    }
                    List<String> currentPns = new ArrayList<>(player_.getPromissoryNotesInPlayArea());
                    for (String pn : currentPns) {
                        PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pn);
                        if (pnModel.getAttachment().isPresent()
                                && unitHolder.getTokenList().stream()
                                        .anyMatch(s -> s.contains(
                                                pnModel.getAttachment().get()))) {
                            player_.removePromissoryNote(pn);
                            player.setPromissoryNote(pn);
                            player.addPromissoryNoteToPlayArea(pn);
                        }
                    }
                    Set<String> tokens = new HashSet<>(unitHolder.getTokenList());
                    for (String token : tokens) {
                        if (token.contains("facility") || token.contains("superweapon")) {
                            unitHolder.removeToken(token);
                            if (token.contains("embassy")) {
                                ButtonHelperSCs.updateEmbassies(game, player_, tile);
                            }
                            if (token.contains("superweapon")) {
                                player_.removeRelic(token.replace(".png", "")
                                        .replace("attachment", "")
                                        .replace("_", ""));
                            }
                        }
                    }
                    if (Mapper.getPlanet(planet) != null) {
                        String msg = player_.getRepresentation()
                                + " lost control of "
                                + Mapper.getPlanet(planet).getName()
                                + (player_.isNeutral() ? "" : " (and could perhaps resolve some applicable ability)")
                                + ".";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                        if (game.isFowMode() && player_.isRealPlayer()) {
                            MessageHelper.sendMessageToChannel(
                                    player_.getPrivateChannel(),
                                    player_.getRepresentationUnfogged() + ", you lost control of "
                                            + Mapper.getPlanet(planet).getName() + ".");
                        }
                        if (player_.isRealPlayer()
                                && player_.getNumberOfRealPlanetsAllianceMode() == 0
                                && CheckUnitContainmentService.getTilesContainingPlayersUnits(
                                                game, player_, UnitType.Infantry, UnitType.Mech, UnitType.Spacedock)
                                        .isEmpty()) {
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Buttons.red(
                                    "eliminatePlayer_" + player_.getFaction(),
                                    "Eliminate " + player_.getFlexibleDisplayName()));
                            msg = player_.getRepresentation()
                                    + ", the game believes that you ought to be eliminated. Press the button if this is accurate (anyone can press the button).";
                            MessageHelper.sendMessageToChannel(player_.getCorrectChannel(), msg, buttons);
                        }
                    }
                }
            }
        }
        if ((alreadyOwned || player.hasAbility("contagion_blex") || player.hasAbility("plague_reservoir"))
                && player.hasTech("dxa")
                && !doubleCheck
                && !setup
                && !unitHolder.isSpaceStation()) {
            String msg10 = player.getRepresentationUnfogged()
                    + " you may have an opportunity to use _Dacxive Animators_ on "
                    + Helper.getPlanetRepresentation(planet, game)
                    + ". Click to confirm a combat occurred and to add 1 infantry or delete these buttons.";
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), msg10, ButtonHelper.getDacxiveButtons(planet, player));
        }

        if ((alreadyOwned || player.hasAbility("contagion_blex") || player.hasAbility("plague_reservoir"))
                && player.hasTech("dsvaylr")
                && !doubleCheck) {
            String msg10 = player.getRepresentationUnfogged()
                    + " you may have an opportunity to use your _Scavenger Exos_ technology on "
                    + Helper.getPlanetRepresentation(planet, game)
                    + ". Click to confirm a valid combat occurred and to draw 1 action card or delete these buttons (note: this technology is max once per action).";
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), msg10, ButtonHelper.getScavengerExosButtons(player));
        }
        if (!alreadyOwned
                && game.isMinorFactionsMode()
                && player.isRealPlayer()
                && tile != null
                && !tile.isHomeSystem(game)
                && (unitHolder.getPlanetModel().getPlanetTypes().contains(PlanetType.FACTION))) {
            PlanetModel p = Mapper.getPlanet(unitHolder.getName());
            if (!p.getFactionHomeworld().equalsIgnoreCase(player.getFaction())) {
                unitHolder.addToken("attachment_threetraits.png");
            }
        }
        if (!alreadyOwned
                && game.isDangerousWildsMode()
                && player.isRealPlayer()
                && tile != null
                && ButtonHelper.getTypeOfPlanet(game, unitHolder.getName()).contains("hazardous")) {
            if (!player.hasAbility("propagation")) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", you may use the button to research your technology (after all combats are done). You may ignore up to "
                                + unitHolder.getResources() + " prerequisites.",
                        List.of(Buttons.GET_A_TECH));
            } else {
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation()
                        + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens."
                        + " Your current command tokens are " + player.getCCRepresentation()
                        + ". Use buttons to gain command tokens.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
        }

        if (game.isMinorFactionsMode()
                && tile != null
                && unitHolder.getTokenList().contains("attachment_threetraits.png")
                && player.isRealPlayer()) {
            boolean ownsThemAll = true;
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (!player.getPlanets().contains(uH.getName())) {
                    ownsThemAll = false;
                    break;
                }
            }
            if (ownsThemAll) {
                PlanetModel p = Mapper.getPlanet(unitHolder.getName());
                if (p != null
                        && p.getFactionHomeworld() != null
                        && !player.hasLeader(p.getFactionHomeworld() + "commander")) {
                    String leaderID = p.getFactionHomeworld() + "commander";
                    if (leaderID.toLowerCase().contains("keleres")) {
                        leaderID = "kelerescommander";
                    }
                    player.addLeader(leaderID);
                    game.addFakeCommander(leaderID);
                    UnlockLeaderService.unlockLeader(leaderID, game, player);
                    for (Player p2 : game.getRealPlayers()) {
                        if (p2 == player) {
                            continue;
                        }
                        if (p2.hasLeader(leaderID)) {
                            p2.removeLeader(leaderID);
                        }
                    }
                }
            }
        }

        if (!game.getStoredValue("CommsOnPlanet" + planet).isEmpty() && game.isTotalWarMode()) {
            int comms = Integer.parseInt(game.getStoredValue("CommsOnPlanet" + planet));
            String planet2 = ButtonHelperActionCards.getBestResPlanetInHomeSystem(player, game);
            game.changeCommsOnPlanet(-comms, planet);
            game.changeCommsOnPlanet(comms, planet2);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    comms + " commodit" + (comms == 1 ? "y" : "ies") + " were moved from the planet of "
                            + Helper.getPlanetRepresentation(planet, game)
                            + " to the planet of " + Helper.getPlanetRepresentation(planet2, game) + ".");
        }

        if (unitHolder.getTokenList().contains("token_relictoken.png") && player.isRealPlayer()) {
            unitHolder.removeToken("token_relictoken.png");
            if (!alreadyOwned) {
                Button draw = Buttons.green(player.getFinsFactionCheckerPrefix() + "drawRelic", "Draw A Relic");
                String message = player.getRepresentation()
                        + " has gained control of a planet which allows them to draw a relic!\nUse the button __after__ you have resolved __all__ ground combats.";
                MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), message, draw);
            }
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "naazcommander") && !setup) {
            if (alreadyOwned && "mirage".equalsIgnoreCase(planet)) {
                List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, unitHolder, player);
                if (buttons != null && !buttons.isEmpty()) {
                    String message = player.getFactionEmoji() + ", click button to explore "
                            + Helper.getPlanetRepresentation(planet, game) + ".";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                }
            }
            alreadyOwned = false;
        }
        if (!game.getPhaseOfGame().contains("agenda")) {
            game.setStoredValue("planetsTakenThisRound", game.getStoredValue("planetsTakenThisRound") + "_" + planet);
        }

        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " Established control of "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        if ((game.getPhaseOfGame().contains("agenda")
                        || (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))))
                && player.hasAbility("scavenge")
                && !doubleCheck
                && !setup) {
            String fac = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    fac
                            + " gained 1 trade good from **Scavenge** (" + player.getTg() + "->" + (player.getTg() + 1)
                            + "). Reminder that you do not legally have this trade good prior to exploring, and that this was mandatory.");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }

        if ((game.getPhaseOfGame().contains("agenda")
                        || (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))))
                && player.hasUnlockedBreakthrough("zealotsbt")
                && unitHolder != null
                && tile != null
                && (tile.getPosition().contains("frac") || unitHolder.isLegendary())
                && !doubleCheck
                && !setup) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("removeCCFromBoard_zealotsbt_" + tile.getPosition(), "Remove Command Token"));
            buttons.add(Buttons.gray("acquireATech_deleteThisMessage", "Research a Technology"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));

            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " is resolving _Rhodun's Reliquary_ to either research a technology or remove the command token from the system.");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", please choose whether you want to __research__ a technology or remove the command token from the system (or neither).",
                    buttons);
        }

        if ((game.getPhaseOfGame().contains("agenda")
                        || (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))))
                && player.hasAbility("veiled_ember_forge")
                && !doubleCheck
                && !setup
                && !unitHolder.getTechSpecialities().isEmpty()) {
            String fac = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    fac + " placed 1 PDS on " + Helper.getPlanetRepresentation(unitHolder.getName(), game)
                            + " due to the Veiled Ember Forge ability. This is optional but was done automatically.");
            AddUnitService.addUnits(event, tile, game, player.getColor(), "pds " + unitHolder.getName());
        }
        if ((game.getPhaseOfGame().contains("agenda")
                        || (game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID()))))
                && player.hasTech("absol_dxa")
                && !doubleCheck
                && !setup) {
            String message;
            if (tile != null && planet != null) {
                Set<String> tokenList =
                        ButtonHelper.getUnitHolderFromPlanetName(planet, game).getTokenList();
                boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
                if (!containsDMZ) {
                    AddUnitService.addUnits(event, tile, game, player.getColor(), "inf " + planet);
                    message = player.getFactionEmoji() + ColorEmojis.getColorEmojiWithName(player.getColor())
                            + UnitEmojis.infantry
                            + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planet)
                            + " due to Absol's Daxcive, however this placement is __optional__.";
                } else {
                    message = "Planet has the _Demilitarized Zone_ attached, so no infantry could be placed.";
                }
            } else {
                message = "Tile was null, no infantry placed.";
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }

        if (player.hasAbility("liberate") && tile != null && !setup) {
            // When you gain control of a planet
            // (MUST) ready that planet if it contains a number of your infantry equal to or greater than that planet's
            // resource value;
            // otherwise, place 1 infantry on that planet.
            List<Button> liberateButtons = new ArrayList<>();
            String planetStr = unitHolder.getName();
            String planetName = Mapper.getPlanet(planetStr).getName();
            liberateButtons.add(Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "liberate_" + planetStr,
                    "Liberate " + planetName,
                    FactionEmojis.Bastion));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    "Please resolve **Liberate** on " + planetName + " (before __or__ after exploration).",
                    liberateButtons);
        }

        if (game.getActivePlayerID() != null
                && !("".equalsIgnoreCase(game.getActivePlayerID()))
                && player.hasUnexhaustedLeader("vaylerianagent")
                && !setup) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    "exhaustAgent_vaylerianagent_" + player.getFaction(),
                    "Use Vaylerian Agent",
                    FactionEmojis.vaylerian));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentationUnfogged() + " you may use "
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Yvin Korduul, the Vaylerian" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                    + " agent, to draw 1 action card.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2, buttons);
        }
        if (game.getActivePlayerID() != null
                && !("".equalsIgnoreCase(game.getActivePlayerID()))
                && player.hasAbility("enslave")
                && !setup
                && tile != null) {
            UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
            tile.getUnitHolders().get(planet).addUnit(infKey, 1);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Added 1 infantry to " + planet + " due to **Enslave**.");
        }

        if (game.getActivePlayerID() != null
                && !("".equalsIgnoreCase(game.getActivePlayerID()))
                && player.hasAbility("scour")
                && !setup) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("scourPlanet_" + planet, "Use Scour", FactionEmojis.vaylerian));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentationUnfogged()
                    + " if you have not already used **Scour** this tactical action, you may discard 1 action card to ready "
                    + Helper.getPlanetRepresentation(planet, game) + ".";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2, buttons);
        }

        if (tile != null
                && game.getActivePlayer() == player
                && !setup
                && game.playerHasLeaderUnlockedOrAlliance(player, "freesystemscommander")
                && !tile.isHomeSystem(game)
                && FoWHelper.playerHasShipsInSystem(player, tile)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    "produceOneUnitInTile_" + tile.getPosition() + "_sling",
                    "Produce 1 Ship",
                    FactionEmojis.freesystems));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String msg2 = player.getRepresentationUnfogged()
                    + ", you may produce 1 ship in the system due to President Cyhn, the Free Systems Commander.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2, buttons);
        }

        if (game.getActivePlayer() == player && game.playerHasLeaderUnlockedOrAlliance(player, "cymiaecommander")) {
            List<Button> saarButton = new ArrayList<>();
            saarButton.add(Buttons.green(
                    "cymiaeCommanderRes_" + planet,
                    "Discard Action Card for Mech on " + Helper.getPlanetRepresentation(planet, game)));
            saarButton.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", due to Koryl Ferax, the Cymiae Commander, you may discard 1 action card here to place or move 1 mech on "
                            + Helper.getPlanetRepresentation(planet, game)
                            + ". Do not do this prior to exploring. It is an \"after\", while exploring is a \"when\".",
                    saarButton);
        }

        if (game.getActivePlayerID() != null
                && !("".equalsIgnoreCase(game.getActivePlayerID()))
                && !setup
                && (player.hasUnit("mykomentori_spacedock") || player.hasUnit("mykomentori_spacedock2"))
                && !doubleCheck) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("deployMykoSD_" + planet, "Deploy Space Dock " + planet));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "sd") < 3) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", if you have the correct amount of infantry (3 or 4), you may remove them and DEPLOY 1 space dock on "
                                + planet
                                + " using the buttons. Note that you will be able to build out of this space dock this action.",
                        buttons);
            }
        }
        if (IsPlayerElectedService.isPlayerElected(game, player, "minister_exploration")) {
            String fac = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    fac + " gained 1 trade good from _Minister of Exploration_ (" + player.getTg() + "->"
                            + (player.getTg() + 1)
                            + ").");
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }

        if (!alreadyOwned
                && !doubleCheck
                && (!"mirage".equals(planet))
                && !game.isBaseGameMode()
                && !setup
                && player.isRealPlayer()) {
            List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, unitHolder, player);
            if (buttons != null && !buttons.isEmpty()) {
                String message = player.getFactionEmoji() + " Click button to explore "
                        + Helper.getPlanetRepresentation(planet, game) + ".";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
        }

        if (player.hasUnlockedBreakthrough("l1z1xbt") && tile != null && !setup) {
            Planet p = tile.getUnitHolderFromPlanet(planet);
            if (p != null && !alreadyOwned) {
                FealtyUplinkService.postInitialButtons(game, player, planet);
            } else {
                FealtyUplinkService.resolveAddInf(player, p);
            }
        }

        if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
                        || game.getPhaseOfGame().contains("agenda"))
                && player.hasUnit("saar_mech")
                && !setup
                && !ButtonHelper.isLawInPlay(game, "articles_war")
                && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
            List<Button> saarButton = new ArrayList<>();
            saarButton.add(Buttons.green(
                    "saarMechRes_" + planet,
                    "Pay 1 Trade Good for a Mech on " + Helper.getPlanetRepresentation(planet, game)));
            saarButton.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " you may pay 1 trade good to place 1 Scavenger mech here. Do not do this prior to exploring. It is an \"after\", while exploring is a \"when\".",
                    saarButton);
        }
        if (player.hasTech("ie") && unitHolder.getResources() > 0 && !setup) {
            String message = player.getRepresentation()
                    + " Click the button to resolve an _Integrated Economy_ build on "
                    + Helper.getPlanetRepresentation(planet, game) + ".";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.blue(
                    "integratedBuild_" + planet, "Integrated on " + Helper.getPlanetRepresentation(planet, game)));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        CommanderUnlockCheckService.checkPlayer(player, "sol", "vaylerian", "olradin", "xxcha", "sardakk");
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "freesystems");
        if (game.mecatols().contains(planet) && player.controlsMecatol(true)) {
            CommanderUnlockCheckService.checkPlayer(player, "winnu");
        }
        if (player.isRealPlayer() && "styx".equalsIgnoreCase(planet)) {
            String marrow = "A Song Like Marrow";
            Integer id = game.getRevealedPublicObjectives().getOrDefault(marrow, null);
            if (id == null) id = game.getRevealedPublicObjectives().getOrDefault("styx", null);
            if (id == null) id = game.getRevealedPublicObjectives().getOrDefault("Styx", null);

            String message;
            if (id != null) {
                game.scorePublicObjective(player.getUserID(), id);
                message = player.getRepresentation() + " scored '" + marrow + "'";
            } else {
                id = game.addCustomPO(marrow, 1);
                game.scorePublicObjective(player.getUserID(), id);
                message = "Custom public objective \"_" + marrow + "_\" has been added.\n" + player.getRepresentation()
                        + " scored \"_" + marrow + "_\".";
            }
            for (Player p : game.getRealPlayers()) {
                if (p.is(player)) continue;
                game.unscorePublicObjective(p.getUserID(), id);
            }
            Helper.checkEndGame(game, player);

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }

        if ("thundersedge".equalsIgnoreCase(planet) && player.isRealPlayer()) {
            BreakthroughCommandHelper.unlockAllBreakthroughs(game, player);
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
    }
}
