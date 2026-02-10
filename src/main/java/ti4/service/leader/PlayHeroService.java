package ti4.service.leader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperRelics;
import ti4.helpers.ButtonHelperTwilightsFallActionCards;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.LeaderModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.explore.AddFrontierTokensService;
import ti4.service.info.ListTurnOrderService;
import ti4.service.planet.PlanetService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class PlayHeroService {

    public static void playHero(GenericInteractionCreateEvent event, Game game, Player player, Leader playerLeader) {
        LeaderModel leaderModel = playerLeader.getLeaderModel().orElse(null);
        boolean showFlavourText = Constants.VERBOSITY_VERBOSE.equals(game.getOutputVerbosity());
        StringBuilder sb = new StringBuilder();
        if (leaderModel != null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " played:");
            player.getCorrectChannel()
                    .sendMessageEmbeds(leaderModel.getRepresentationEmbed(
                            false, true, false, showFlavourText, game.isTwilightsFallMode()))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    LeaderEmojis.getLeaderEmoji(playerLeader).toString());
            sb.append(player.getRepresentation())
                    .append(" played ")
                    .append(Helper.getLeaderFullRepresentation(playerLeader));
            BotLogger.warning(new LogOrigin(event), "Missing LeaderModel: " + playerLeader.getId());
        }

        if ("letnevhero".equals(playerLeader.getId())
                || "nomadhero".equals(playerLeader.getId())
                || "zealotshero".equals(playerLeader.getId())
                || "nokarhero".equals(playerLeader.getId())
                || "kolumehero".equals(playerLeader.getId())
                || "qhethero".equals(playerLeader.getId())) {
            playerLeader.setLocked(false);
            playerLeader.setActive(true);
            sb.append("\nLeader will be purged after status cleanup.");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
            if ("zealotshero".equals(playerLeader.getId())) {
                MessageHelper.sendMessageToChannelWithButton(
                        event.getMessageChannel(),
                        player.getRepresentation() + ", please choose your first non-faction technology.",
                        Buttons.GET_A_FREE_TECH);
                MessageHelper.sendMessageToChannelWithButton(
                        event.getMessageChannel(),
                        player.getRepresentation() + ", please choose your second non-faction technology.",
                        Buttons.GET_A_FREE_TECH);
                MessageHelper.sendMessageToChannelWithButton(
                        event.getMessageChannel(),
                        player.getRepresentation() + ", please choose your third non-faction technology.",
                        Buttons.GET_A_FREE_TECH);
            }
        } else {
            boolean purged = true;
            LeaderModel playerLeaderModel = playerLeader.getLeaderModel().orElse(null);
            String leaderName = playerLeaderModel == null
                    ? "Hero " + playerLeader.getId()
                    : playerLeaderModel.getName() + ", the " + StringUtils.capitalize(playerLeaderModel.getFaction())
                            + " hero,";
            String msg = leaderName + " has been purged.";
            if (!"mykomentorihero".equals(playerLeader.getId())) {
                purged = player.removeLeader(playerLeader);
                ButtonHelperHeroes.checkForMykoHero(game, playerLeader.getId(), player);
            } else {
                msg = "Coprinus Comatus, the Myko-Mentori hero, was used to copy another hero.";
            }

            if (purged) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

            } else {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), leaderName + " was not purged - something went wrong.");
                return;
            }
        }

        switch (playerLeader.getId()) {
            case "kollecchero" ->
                RelicHelper.drawWithAdvantage(
                        player, game, game.getRealPlayers().size());
            case "xxchahero-te" -> ButtonHelperHeroes.xxchaHeroTEStart(game, player);
            case "ralnelhero" -> {
                // You may choose to no longer be passed; if you do, gain 2 command tokens, draw 1 action card, and
                // purge this card
                player.setPassed(false);
                String prefix = player.getFinsFactionCheckerPrefix();
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(prefix + "gain_CC", "Gain 2 Command Tokens"));
                buttons.add(Buttons.green(prefix + "drawActionCards_1", "Draw 1 Action Card"));
                buttons.add(Buttons.DONE_DELETE_BUTTONS);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        "Use these buttons to gain 2 command tokens, then draw 1 action card.",
                        buttons);
            }
            case "obsidianhero" -> {
                player.clearExhaustedPlanets(false);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged() + ", all of your planets have been readied.");
            }
            case "firmamenthero" -> {
                ActionCardHelper.sendPlotCardInfo(game, player);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged()
                                + ", please choose a Plot car in your `#cards-info` thread to put into play.");
            }
            case "deepwroughthero" -> {
                List<Button> buttons = new ArrayList<>();
                for (TechnologyType type : TechnologyType.mainFive) {
                    List<TechnologyModel> techs =
                            ListTechService.getAllTechOfAType(game, type.toString(), player, false, false, true);
                    for (TechnologyModel tech : techs) {
                        if (tech.isUnitUpgrade() || tech.getFaction().isPresent()) {
                            continue;
                        }
                        buttons.add(Buttons.gray(
                                "dwsHeroPurge_" + tech.getAlias(), tech.getName(), tech.getSingleTechEmoji()));
                    }
                }
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged()
                                + ", please choose a technology you wish to purge from the universe.",
                        buttons);
            }
            case "titanshero" -> {
                Tile t = player.getHomeSystemTile();
                if (!game.isTwilightsFallMode()
                        && game.getTileFromPlanet("elysium") != null
                        && game.getTileFromPlanet("elysium") == t) {
                    t.addToken("attachment_titanshero.png", "elysium");
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(), "Elysium has had Ul The Progenitor attached, and been readied.");
                    PlanetService.refreshPlanet(player, "elysium");
                } else {
                    ButtonHelperRelics.offerTitansHeroButtons(player, game, event);
                }
            }
            case "onyxxahero" -> {
                List<Button> buttons = new ArrayList<>();
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasActualShipsInSystem(player, tile)) {
                        buttons.add(Buttons.green(
                                "moveShipToAdjacentSystemStep2_" + tile.getPosition() + "_hero",
                                tile.getRepresentationForButtons(game, player)));
                    }
                }

                buttons.add(Buttons.red("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", please use buttons to resolve your _Titles Are Silly_ hero ability.",
                        buttons);
            }
            case "xanhero" -> {
                int amount = 0;
                for (Tile tile : game.getTileMap().values()) {
                    for (UnitHolder uh : tile.getUnitHolders().values()) {
                        for (UnitKey uk : uh.getUnitKeys()) {
                            amount += uh.getUnitCountForState(uk, UnitState.dmg);
                        }
                    }
                }
                game.getTileMap().values().stream()
                        .flatMap(t -> t.getUnitHolders().values().stream())
                        .forEach(uh -> uh.removeAllUnitDamage(player.getColorID()));
                String gainedTg = player.gainTG(amount, true);
                String message = player.getRepresentation() + " repaired all " + amount
                        + " of their damaged units, and consequently gained " + amount + " trade good"
                        + (amount == 1 ? "" : "s") + " " + gainedTg + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                ButtonHelperAgents.resolveArtunoCheck(player, amount);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + " can now repair other players' units near their space docks (not automated, use `/remove_all_sustain_damage`).");
            }
            case "mirvedahero" -> {
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "pds", "placeOneNDone_skipbuild");
                String message = "Please choose a planet to place a PDS";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        "You will unfortunately need to use dicecord's `/roll` command for the SPACE CANNON and BOMBARDMENT of all your units against one system and planet respectively.");
            }
            case "florzenhero" -> {
                for (Tile tile : game.getTileMap().values()) {
                    for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                        if (player.getPlanetsAllianceMode().contains(uH.getName())
                                && !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                            AddUnitService.addUnits(event, tile, game, player.getColor(), "2 ff");
                            break;
                        }
                    }
                }
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged()
                                + " has added 2 fighters to every system with an owned planet and no other players' ships.");
                ButtonHelperHeroes.resolveFlorzenHeroStep1(player, game);
            }
            case "kyrohero" -> {
                int dieResult = player.getLowestSC();
                game.setStoredValue("kyroHeroSC", dieResult + "");
                game.setStoredValue("kyroHeroPlayer", player.getFaction());
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        Helper.getSCName(dieResult, game)
                                + " has been blighted with Speygh, the Kyro hero"
                                + (game.isFrankenGame()
                                        ? ", and the faction that played the hero as " + player.getFaction()
                                        : "")
                                + ".");
                ListTurnOrderService.turnOrder(event, game);
            }
            case "ghotihero" ->
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        "Please choose the systems in which you wish to resolve Nmenmede, the Ghoti hero.",
                        ButtonHelperHeroes.getTilesToGhotiHeroIn(player, game, event));
            case "gledgehero" -> ButtonHelperHeroes.resolveGledgeHero(player, game);
            case "khraskhero" -> {
                ButtonHelperHeroes.resolveKhraskHero(player, game);
                ButtonHelperHeroes.resolveKhraskHero(player, game);
                ButtonHelperHeroes.resolveKhraskHero(player, game);
                ButtonHelperHeroes.resolveKhraskHero(player, game);
            }
            case "mortheushero" ->
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        "Please choose the systems in which you wish to resolve Bayan, the Mortheus hero.",
                        ButtonHelperHeroes.getTilesToGlimmersHeroIn(player, game, event));
            case "axishero" -> ButtonHelperHeroes.resolveAxisHeroStep1(player, game);
            case "lanefirhero" -> ButtonHelperHeroes.resolveLanefirHeroStep1(player, game);
            case "cymiaehero" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(
                        "cymiaeHeroStep1_" + (game.getRealPlayers().size() + 1), "Resolve Cymiae Hero"));
                buttons.add(Buttons.blue("cymiaeHeroAutonetic", "Resolve Autonetic Memory First"));

                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", please choose whether to resolve **Autonetic Memory** or not.",
                        buttons);
            }
            case "lizhohero" ->
                MessageHelper.sendMessageToChannelWithButton(
                        event.getMessageChannel(),
                        "You may use the buttons in your `#cards-info` thread to set traps, then when you're done with that, press the following button to start distributing 12 fighters.",
                        Buttons.green("lizhoHeroFighterResolution", "Distribute 12 Fighters"));
            case "solhero" -> {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged()
                                + ", all of your command tokens on the game have been returned to your reinforcements.");
                for (Tile t : game.getTileMap().values()) {
                    if (CommandCounterHelper.hasCC(event, player.getColor(), t)) {
                        RemoveCommandCounterService.fromTile(player.getColor(), t, game);
                    }
                }
            }
            case "cheiranhero" -> ButtonHelperHeroes.cheiranHeroResolution(player, game, event);
            case "olradinhero" -> {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged() + " added 1 infantry to each planet.");
                ActionCardHelper.doRise(player, event, game);
                ButtonHelperHeroes.offerOlradinHeroFlips(game, player);
            }
            case "argenthero" -> ButtonHelperHeroes.argentHeroStep1(game, player, event);
            case "l1z1xhero" -> {
                String message = player.getRepresentation()
                        + " is resolving The Helmsman, the L1Z1X Hero. At the moment, this is implemented as a sort of tactical action, relying on the player to follow the rules."
                        + " The game will know not to take a command token from your tactic pool, and will allow you to move out of locked systems."
                        + " Reminder that you may carry ground forces and fighters with your dreadnoughts/flagship, and that they can't move into supernovae (or asteroid fields if you don't have _Antimass Deflectors_).";
                List<Button> ringButtons = ButtonHelper.getPossibleRings(player, game);
                game.setL1Hero(true);
                game.resetCurrentMovedUnitsFrom1TacticalAction();
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
            }
            case "winnuhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getWinnuHeroSCButtons(game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", you invoke the Imperial Seal" + (RandomHelper.isOneInX(50) ? " 🦭" : "") + "."
                                + " Please choose which strategy card you wish to do the primary of."
                                + " Reminder you may allow others to do the secondary, but they should still spend 1 command token from their strategy pool to resolving it (unless it's **Leadership**).",
                        buttons);
            }
            case "gheminaherolady" -> {
                List<Button> buttons = ButtonHelperHeroes.getButtonsForGheminaLadyHero(player, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged()
                                + ", please choose which planet you wish to resolve The Lady, a Ghemina hero.",
                        buttons);
            }
            case "gheminaherolord" -> {
                List<Button> buttons = ButtonHelperHeroes.getButtonsForGheminaLordHero(player, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged()
                                + ", please choose which planet you wish to resolve The Lord, a Ghemina hero.",
                        buttons);
            }
            case "arborechero" -> {
                List<Button> buttons = ButtonHelperHeroes.getArboHeroButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", please use the buttons to build in the desired system(s).",
                        buttons);
            }
            case "saarhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getSaarHeroButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", please choose the system to remove all opposing fighters and infantry from.",
                        buttons);
            }
            case "edynhero" -> {
                int size = CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Mech)
                        .size();
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji() + " may resolve " + size
                                + " agenda" + (size == 1 ? "" : "s") + " because that's how many Sigils they got."
                                + " After putting the agendas on top in the order you wish (don't bottom any), please press the button to reveal an agenda.");
                AgendaHelper.drawAgenda(size, game, player);
                Button flipAgenda = Buttons.blue("flip_agenda", "Press This to Flip Agenda");
                List<Button> buttons = List.of(flipAgenda);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Flip Agenda", buttons);
            }
            case "kjalengardhero" -> {
                int size = ButtonHelperAgents.getGloryTokenTiles(game).size();
                for (Tile tile : ButtonHelperAgents.getGloryTokenTiles(game)) {
                    List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(
                            player, game, event, "kjalHero_" + tile.getPosition());
                    if (!buttons.isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                player.getCorrectChannel(),
                                "Use buttons to remove a command token from "
                                        + tile.getRepresentationForButtons(game, player) + " or an adjacent tile.",
                                buttons);
                    }
                }
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji() + " may gain " + size + " command token" + (size == 1 ? "" : "s")
                                + ".");
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String trueIdentity = player.getRepresentationUnfogged();
                String message2 = trueIdentity + ", your current command tokens are " + player.getCCRepresentation()
                        + ". Use buttons to gain command tokens.";
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
            case "vaylerianhero" -> {
                if (!game.isNaaluAgent() && !game.isWarfareAction()) {
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    CommandCounterHelper.addCC(event, player, game.getTileByPosition(game.getActiveSystem()));
                    game.setStoredValue("vaylerianHeroActive", "true");
                }
                List<Button> removeCCs = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "vaylerianhero");
                if (!removeCCs.isEmpty()) {
                    for (int x = 0;
                            x < ButtonHelperAgents.getGloryTokenTiles(game).size();
                            x++) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                player.getCorrectChannel(),
                                "Use buttons to remove 1 of your command tokens from the game board.",
                                removeCCs);
                    }
                }
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), player.getFactionEmoji() + " may gain 1 command token.");
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String trueIdentity = player.getRepresentationUnfogged();
                String message2 = trueIdentity + "! Your current command tokens are " + player.getCCRepresentation()
                        + ". Use buttons to gain command tokens.";
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
            case "freesystemshero" -> ButtonHelperHeroes.offerFreeSystemsButtons(player, game, event);
            case "vadenhero" -> ButtonHelperHeroes.startVadenHero(game, player);
            case "veldyrhero" -> {
                game.setComponentAction(true);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", for each planet with a _Branch Office_, you may copy 1 unit upgrade technology from the player that controls that planet.");
                for (Player p2 : ButtonHelperFactionSpecific.getPlayersWithBranchOffices(game, player)) {
                    if (ButtonHelperFactionSpecific.getNumberOfBranchOffices(game, p2) == 1) {
                        String msg = p2.getFactionEmojiOrColor()
                                + " owns 1 _Branch Office_. You may copy 1 of these unit upgrade technologies.";
                        MessageHelper.sendMessageToChannelWithButtons(
                                player.getCorrectChannel(),
                                msg,
                                ButtonHelperHeroes.getPossibleTechForVeldyrToGainFromPlayer(player, p2, game));
                    } else {
                        String msg = p2.getFactionEmojiOrColor() + " owns "
                                + ButtonHelperFactionSpecific.getNumberOfBranchOffices(game, p2)
                                + " _Branch Offices_. You may copy "
                                + ButtonHelperFactionSpecific.getNumberOfBranchOffices(game, p2)
                                + " of these unit upgrade technologies.";
                        MessageHelper.sendMessageToChannelWithButtons(
                                player.getCorrectChannel(),
                                msg,
                                ButtonHelperHeroes.getPossibleTechForVeldyrToGainFromPlayer(player, p2, game));
                        for (int x = 1; x < ButtonHelperFactionSpecific.getNumberOfBranchOffices(game, p2); x++) {
                            if (!ButtonHelperHeroes.getPossibleTechForVeldyrToGainFromPlayer(player, p2, game)
                                    .isEmpty()) {
                                MessageHelper.sendMessageToChannelWithButtons(
                                        player.getCorrectChannel(),
                                        "",
                                        ButtonHelperHeroes.getPossibleTechForVeldyrToGainFromPlayer(player, p2, game));
                            }
                        }
                    }
                }
            }
            case "nekrohero" -> {
                List<Button> buttons = ButtonHelperHeroes.getNekroHeroButtons(player, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", please choose which planet you wish to get a technology and trade goods from (and kill any enemy units).",
                        buttons);
            }
            case "witchinghero" -> {
                String assignSpeakerMessage = player.getRepresentation()
                        + ", please choose a faction below to receive the Speaker token."
                        + MiscEmojis.SpeakerToken;

                List<Button> assignSpeakerActionRow =
                        PlayStrategyCardService.getPoliticsAssignSpeakerButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(), assignSpeakerMessage, assignSpeakerActionRow);
            }
            case "lawshero" -> ButtonHelperTwilightsFallActionCards.resolveLawsHero(game, player);
            case "devourhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getNekroHeroButtons(player, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", please choose which planet you wish to get trade goods from (and kill any enemy units).",
                        buttons);
            }
            case "sanctionhero" -> {
                boolean singleDock = false;
                Tile tile = player.getHomeSystemTile();
                List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "warfare", "place");
                int productionValue = Helper.getProductionValue(player, game, tile, singleDock);

                String message = player.getRepresentation() + " Use the buttons to produce.";
                message += "\nYou have " + productionValue + " PRODUCTION value in this system.";
                if (productionValue > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
                    message +=
                            "\nYou also have the That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 fighters/infantry that don't count towards PRODUCTION limit.";
                }
                MessageHelper.sendMessageToChannel(
                        player.getPrivateChannel(), message + " You do not need to pay for these units.");
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Produce Units", buttons);

                if (player.hasUnit("tf-productionbiomes")
                        && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)
                                .contains(tile)) {
                    String msg = player.getRepresentation()
                            + ", you have the Production Biomes spacedock unit upgrade, and so may spend a command counter to gain 4 trade goods that you can spend on this build."
                            + " If you do, you will also choose another player, who will gain 2 trade goods.";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.blue("useProductionBiomes", "Use Production Biomes", FactionEmojis.Hacan));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons2);
                }
            }
            case "voicehero" -> {
                List<String> edicts = Mapper.getShuffledDeck("agendas_twilights_fall");
                if (ButtonHelper.isLawInPlay(game, "tf-censure")) {
                    edicts.removeIf("tf-censure"::equalsIgnoreCase);
                }
                List<Button> buttons = new ArrayList<>();
                List<MessageEmbed> embeds = new ArrayList<>();
                Player tyrant = player;
                for (int x = 0; x < 3; x++) {
                    AgendaModel edict = Mapper.getAgenda(edicts.get(x));
                    buttons.add(Buttons.green(
                            tyrant.getFinsFactionCheckerPrefix() + "resolveEdict_" + edicts.get(x), edict.getName()));
                    embeds.add(edict.getRepresentationEmbed());
                }
                String msg = tyrant.getRepresentation()
                        + " you should now choose which of the 3 edicts you wish to resolve.";
                MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                        tyrant.getCorrectChannel(), msg, embeds, buttons);
            }
            case "brilliancehero" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
                buttons.add(Buttons.green("drawSingularNewSpliceCard_units", "Draw 1 Unit Upgrade"));
                buttons.add(Buttons.green("drawSingularNewSpliceCard_genome", "Draw 1 Genome"));
                buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText) + ", please use the buttons to resolve.",
                        buttons);
            }
            case "poisonhero" -> ButtonHelperTwilightsFallActionCards.resolvePoison(game, player);
            case "eternityhero" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("searchSpliceDeck_ability", "Search For Ability"));
                buttons.add(Buttons.green("searchSpliceDeck_units", "Search For Unit Upgrade"));
                buttons.add(Buttons.green("searchSpliceDeck_genome", "Search For Genome"));
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText) + ", please use the buttons to resolve.",
                        buttons);
            }
            case "bentorhero" -> {
                ButtonHelperHeroes.resolveBentorHero(game, player);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getFactionEmoji() + " has been offered buttons to explore all their planets.");
            }
            case "kaltrimhero" -> {
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String propMsg = player.getRepresentation() + ", your current command tokens are "
                        + player.getCCRepresentation() + ". Use these buttons to gain command tokens.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), propMsg, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getFactionEmoji()
                                + " has been offered buttons to gain command tokens and look at Shrines.");
            }
            case "toldarhero" -> ButtonHelperHeroes.resolveToldarHero(game, player);
            case "nivynhero" -> {
                ButtonHelperHeroes.resolveNivynHeroSustainEverything(game, player);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getFactionEmoji() + " has sustained each unit on the game board, except their mechs.");
            }
            case "jolnarhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getJolNarHeroSwapOutOptions(player);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", please choose which what technology you wish to swap out."
                                + "\n-# Reminder that since all swap are simultaneous, you cannot swap out a technology and then swap it back in.",
                        buttons);
            }
            case "yinhero" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue(
                        player.getFinsFactionCheckerPrefix() + "yinHeroStart", "Invade A Planet With Yin Hero"));
                buttons.add(Buttons.red("deleteButtons", "Delete Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", use the button to do individual invasions, then delete the buttons when you have placed 3 total infantry.",
                        buttons);
            }
            case "naazhero" -> {
                RelicHelper.drawRelicAndNotify(player, event, game);
                List<Button> buttons = ButtonHelperHeroes.getNRAHeroButtons(game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", use the button to do __two__ of the available secondaries.\n-# All are presented for convenience, but two is the limit.",
                        buttons);
            }
            case "forgehero" -> {
                RelicHelper.drawRelicAndNotify(player, event, game);
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String trueIdentity = player.getRepresentationUnfogged();
                String message2 = trueIdentity + ", your current command tokens are " + player.getCCRepresentation()
                        + ". Use buttons to gain 2 command tokens.";
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
            case "mahacthero" -> {
                List<Button> buttons = ButtonHelperHeroes.getBenediction1stTileOptions(player, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", please choose which system you wish to force ships to move from.",
                        buttons);
            }
            case "ghosthero" -> {
                List<Button> buttons = ButtonHelperHeroes.getGhostHeroTilesStep1(game, player);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", please choose the first system you wish to swap with Riftwalker Meian, the Creuss hero.",
                        buttons);
            }
            case "augershero" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue(
                        player.getFinsFactionCheckerPrefix() + "augersHeroStart_" + 1,
                        "Resolve Ilyxum Hero on Stage 1 Deck"));
                buttons.add(Buttons.blue(
                        player.getFinsFactionCheckerPrefix() + "augersHeroStart_" + 2,
                        "Resolve Ilyxum Hero on Stage 2 Deck"));
                buttons.add(Buttons.red("deleteButtons", "Delete Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", please choose on which objective type you wish to resolve Atropha, the Ilyxum hero.",
                        buttons);
            }
            case "empyreanhero" -> {
                AddFrontierTokensService.addFrontierTokens(event, game);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Added frontier tokens");
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        "Use buttons to explore empty systems.",
                        ButtonHelperHeroes.getEmpyHeroButtons(player, game));
            }
            case "cabalhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getCabalHeroButtons(player, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        "Use buttons to roll some dice, and maybe even capture some stuff.",
                        buttons);
            }
            case "eventhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getCabalHeroButtons(player, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        "Use buttons to roll some dice, and maybe even kill some stuff.",
                        buttons);
            }
            case "yssarilhero" -> {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player || p2.getAcCount() == 0) {
                        continue;
                    }
                    List<Button> buttons = new ArrayList<>(getYssarilHeroActionCardButtons(player, p2));
                    MessageHelper.sendMessageToChannelWithButtons(
                            p2.getCardsInfoThread(),
                            p2.getRepresentationUnfogged()
                                    + " Kyver, Blade and Key, the Yssaril hero, has been played. Please choose which action card you wish offer to them.",
                            buttons);
                }
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentation(true, showFlavourText)
                                + ", all other players have been sent a ping in their `#cards-info` thread with buttons to choose an action card to offer you.");
            }
            case "keleresheroharka" -> resolveKeleresHeroMentak(game, player, event);
        }
        TemporaryCombatModifierModel posssibleCombatMod = CombatTempModHelper.getPossibleTempModifier(
                Constants.LEADER, playerLeader.getId(), player.getNumberOfTurns());
        if (posssibleCombatMod != null) {
            player.addNewTempCombatMod(posssibleCombatMod);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Combat modifier will be applied next time you push the combat roll button.");
        }
    }

    private static List<Button> getYssarilHeroActionCardButtons(Player yssaril, Player notYssaril) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = notYssaril.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String acName = Mapper.getActionCard(key).getName();
                acButtons.add(Buttons.gray(
                        "yssarilHeroInitialOffering_" + value + "_" + yssaril.getFaction(),
                        acName,
                        CardEmojis.getACEmoji(yssaril)));
            }
        }
        return acButtons;
    }

    private static void resolveKeleresHeroMentak(Game game, Player player, GenericInteractionCreateEvent event) {
        int originalACDeckCount = game.getActionCards().size();
        StringBuilder acRevealMessage = new StringBuilder(
                "The following non-component action cards were revealed before drawing three component action cards:\n");
        StringBuilder acDrawMessage =
                new StringBuilder("The following component action cards were drawn into their hand:\n");
        List<String> cardsToShuffleBackIntoDeck = new ArrayList<>();
        int componentActionACCount = 0;
        int index = 1;
        boolean noMoreComponentActionCards = false;
        while (componentActionACCount < 3) {
            Integer acID = null;
            String acKey = null;
            for (Map.Entry<String, Integer> ac : Helper.getLastEntryInHashMap(game.drawActionCard(player.getUserID()))
                    .entrySet()) {
                acID = ac.getValue();
                acKey = ac.getKey();
            }
            ActionCardModel actionCard = Mapper.getActionCard(acKey);
            String acName = actionCard.getName();
            String acWindow = actionCard.getWindow();
            if ("Action".equalsIgnoreCase(acWindow)) {
                acDrawMessage
                        .append("> `")
                        .append(String.format("%02d", index))
                        .append(".` ")
                        .append(actionCard.getRepresentation());
                componentActionACCount++;
            } else {
                acRevealMessage
                        .append("> `")
                        .append(String.format("%02d", index))
                        .append(".` ")
                        .append(CardEmojis.getACEmoji(game))
                        .append(" ")
                        .append(acName)
                        .append("\n");
                game.discardActionCard(player.getUserID(), acID);
                cardsToShuffleBackIntoDeck.add(acKey);
            }
            index++;
            if (index >= originalACDeckCount) {
                if (index > originalACDeckCount * 2) {
                    noMoreComponentActionCards = true;
                    break;
                }
            }
        }
        for (String card : cardsToShuffleBackIntoDeck) {
            Integer cardID = game.getDiscardActionCards().get(card);
            game.shuffleActionCardBackIntoDeck(cardID);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), LeaderEmojis.KeleresHeroHarka.toString());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " uses Harka Leeds, the Keleres (Mentak) hero, to reveal "
                        + CardEmojis.getACEmoji(game) + "action cards, until drawing 3 component action cards.\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), acRevealMessage.toString());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), acDrawMessage.toString());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "All non-component action cards have been reshuffled back into the deck.");
        ActionCardHelper.sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, player);
        if (noMoreComponentActionCards) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "**All action cards in the deck have been revealed. __No component action cards remain.__**");
        }
    }
}
