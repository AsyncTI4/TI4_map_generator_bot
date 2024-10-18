package ti4.helpers;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.buttons.Buttons;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardspn.PlayPN;
import ti4.commands.ds.DrawBlueBackTile;
import ti4.commands.leaders.ExhaustLeader;
import ti4.commands.leaders.HeroPlay;
import ti4.commands.player.TurnStart;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;

public class ComponentActionHelper {

    public static List<Button> getAllPossibleCompButtons(Game game, Player p1, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        String prefix = "componentActionRes_";
        List<Button> compButtons = new ArrayList<>();
        // techs
        for (String tech : p1.getTechs()) {
            if (!p1.getExhaustedTechs().isEmpty() && p1.getExhaustedTechs().contains(tech)) {
                continue;
            }
            TechnologyModel techRep = Mapper.getTechs().get(tech);
            String techName = techRep.getName();
            String techEmoji = techRep.getCondensedReqsEmojis(true);
            String techText = techRep.getText();

            boolean detAgeOfExp = (tech.equalsIgnoreCase("det") || tech.equalsIgnoreCase("absol_det")) && game.isAgeOfExplorationMode();
            if (techText.contains("ACTION") || detAgeOfExp) {
                if ("lgf".equals(tech) && !p1.controlsMecatol(false)) {
                    continue;
                }
                Button tButton = Buttons.red(finChecker + "exhaustTech_" + tech, "Exhaust " + techName, techEmoji);
                compButtons.add(tButton);
            }
        }
        if (ButtonHelper.getNumberOfStarCharts(p1) > 1) {
            Button tButton = Buttons.red(finChecker + prefix + "doStarCharts_", "Purge 2 Starcharts ");
            compButtons.add(tButton);
        }

        // Legendary Planets
        List<String> implementedLegendaryPlanets = new ArrayList<>();
        if (Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(1721048723431L)) > 0)
            implementedLegendaryPlanets.add("prism");
        for (String planet : implementedLegendaryPlanets) {
            String prettyPlanet = Mapper.getPlanet(planet).getName();
            if (p1.getPlanets().contains(planet) && !p1.getExhaustedPlanetsAbilities().contains(planet)) {
                compButtons.add(Buttons.green(finChecker + "planetAbilityExhaust_" + planet, "Use " + prettyPlanet + " Ability"));
            }
        }

        // Leaders
        for (Leader leader : p1.getLeaders()) {
            if (!leader.isExhausted() && !leader.isLocked()) {
                String leaderID = leader.getId();

                LeaderModel leaderModel = Mapper.getLeader(leaderID);
                if (leaderModel == null) {
                    continue;
                }

                String leaderName = leaderModel.getName();
                String leaderAbilityWindow = leaderModel.getAbilityWindow();

                String factionEmoji = Emojis.getFactionLeaderEmoji(leader);
                if ("ACTION:".equalsIgnoreCase(leaderAbilityWindow) || leaderName.contains("Ssruu")) {
                    if (leaderName.contains("Ssruu")) {
                        String led = "muaatagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Muaat Agent", factionEmoji);
                            compButtons.add(lButton);
                        }
                        led = "naaluagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Naalu Agent", factionEmoji);
                            compButtons.add(lButton);
                        }
                        led = "arborecagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Arborec Agent", factionEmoji);
                            compButtons.add(lButton);
                        }
                        led = "bentoragent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Bentor Agent", factionEmoji);
                            compButtons.add(lButton);
                        }
                        led = "kolumeagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Kolume Agent", factionEmoji);
                            compButtons.add(lButton);
                        }

                        led = "axisagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Axis Agent", factionEmoji);
                            compButtons.add(lButton);
                        }
                        led = "xxchaagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Xxcha Agent", factionEmoji);
                            compButtons.add(lButton);
                        }
                        led = "yssarilagent";
                        Button lButton = Buttons.gray(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Unimplemented Component Agent", factionEmoji);
                        compButtons.add(lButton);
                        if (ButtonHelperFactionSpecific.doesAnyoneElseHaveJr(game, p1)) {
                            Button jrButton = Buttons.gray(finChecker + "yssarilAgentAsJr", "Use " + leaderName + " as JR-XS455-O", factionEmoji);
                            compButtons.add(jrButton);
                        }

                    } else {
                        Button lButton = Buttons.gray(finChecker + prefix + "leader_" + leaderID, "Use " + leaderName, factionEmoji);
                        compButtons.add(lButton);
                    }

                } else if ("mahactcommander".equalsIgnoreCase(leaderID) && p1.getTacticalCC() > 0
                    && ButtonHelper.getTilesWithYourCC(p1, game, event).size() > 0) {
                    Button lButton = Buttons.gray(finChecker + "mahactCommander", "Use " + leaderName, factionEmoji);
                    compButtons.add(lButton);
                }
            }
        }

        // Relics
        boolean enigmaticSeen = false;
        for (String relic : p1.getRelics()) {
            RelicModel relicData = Mapper.getRelic(relic);
            if (relicData == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that PN, no PN sent");
                continue;
            }

            if (relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE) || !relic.contains("starchart")
                && (relicData.getText().contains("Action:") || relicData.getText().contains("ACTION:"))) {
                Button rButton;
                if (relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE)) {
                    if (enigmaticSeen) {
                        continue;
                    }
                    rButton = Buttons.red(finChecker + prefix + "relic_" + relic, "Purge Enigmatic Device");
                    enigmaticSeen = true;
                } else {
                    List<String> exhaustRelics = List.of("titanprototype", "absol_jr");
                    if (exhaustRelics.contains(relic.toLowerCase())) {
                        if (!p1.getExhaustedRelics().contains(relic)) {
                            rButton = Buttons.blue(finChecker + prefix + "relic_" + relic, "Exhaust " + relicData.getName());
                        } else {
                            continue;
                        }
                    } else {
                        rButton = Buttons.red(finChecker + prefix + "relic_" + relic, "Purge " + relicData.getName());
                    }
                }
                compButtons.add(rButton);
            }
        }

        // PNs
        for (String pn : p1.getPromissoryNotes().keySet()) {
            PromissoryNoteModel prom = Mapper.getPromissoryNote(pn);
            if (pn != null && prom != null && prom.getOwner() != null
                && !prom.getOwner().equalsIgnoreCase(p1.getFaction())
                && !prom.getOwner().equalsIgnoreCase(p1.getColor())
                && !p1.getPromissoryNotesInPlayArea().contains(pn) && prom.getText() != null) {
                String pnText = prom.getText();
                if (pnText.toLowerCase().contains("action:") && !"bmf".equalsIgnoreCase(pn)) {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                    String pnName = pnModel.getName();
                    Button pnButton = Buttons.red(finChecker + prefix + "pn_" + pn, "Use " + pnName);
                    compButtons.add(pnButton);
                }
            }
            if (prom == null) {
                MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), p1.getRepresentationUnfogged()
                    + " you have a null PN. Please use /pn purge after reporting it " + pn);
                PNInfo.sendPromissoryNoteInfo(game, p1, false);
            }
        }

        // Abilities
        if (p1.hasAbility("star_forge") && (p1.getStrategicCC() > 0 || p1.hasRelicReady("emelpar"))
            && ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Warsun).size() > 0) {
            Button abilityButton = Buttons.green(finChecker + prefix + "ability_starForge", "Starforge", Emojis.Muaat);
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("meditation") && (p1.getStrategicCC() > 0 || p1.hasRelicReady("emelpar"))
            && p1.getExhaustedTechs().size() > 0) {
            Button abilityButton = Buttons.green(finChecker + prefix + "ability_meditation", "Meditation", Emojis.kolume);
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("orbital_drop") && p1.getStrategicCC() > 0) {
            Button abilityButton = Buttons.green(finChecker + prefix + "ability_orbitalDrop", "Orbital Drop", Emojis.Sol);
            compButtons.add(abilityButton);
        }
        if (p1.hasUnit("lanefir_mech") && p1.getFragments().size() > 0
            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p1, "mech", true) < 4) {
            Button abilityButton = Buttons.green(finChecker + prefix + "ability_lanefirMech", "Purge 1 Fragment For Mech", Emojis.lanefir);
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("mantle_cracking")
            && ButtonHelperAbilities.getMantleCrackingButtons(p1, game).size() > 0) {
            Button abilityButton = Buttons.green(finChecker + prefix + "ability_mantlecracking", "Mantle Crack", Emojis.gledge);
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("stall_tactics") && p1.getActionCards().size() > 0) {
            Button abilityButton = Buttons.green(finChecker + prefix + "ability_stallTactics", "Stall Tactics", Emojis.Yssaril);
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("fabrication") && p1.getFragments().size() > 0) {
            Button abilityButton = Buttons.green(finChecker + prefix + "ability_fabrication", "Purge 1 Fragment for 1 CC", Emojis.Naaz);
            compButtons.add(abilityButton);
        }

        // Other "abilities"
        if (p1.getUnitsOwned().contains("muaat_flagship") && p1.getStrategicCC() > 0
            && ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Flagship).size() > 0) {
            Button abilityButton = Buttons.green(finChecker + prefix + "ability_muaatFS", "Spend 1 strategy CC for 1 cruiser with The Inferno (Muaat Flagship)", Emojis.Muaat);
            compButtons.add(abilityButton);
        }

        // Get Relic
        if (p1.enoughFragsForRelic()) {
            Button getRelicButton = Buttons.green(finChecker + prefix + "getRelic_", "Get Relic");
            if (p1.hasAbility("a_new_edifice")) {
                getRelicButton = Buttons.green(finChecker + prefix + "getRelic_", "Purge Fragments to Explore");
            }
            compButtons.add(getRelicButton);
        }

        // ACs
        Button acButton = Buttons.gray(finChecker + prefix + "actionCards_", "Play \"ACTION:\" AC");
        compButtons.add(acButton);

        // absol
        if (ButtonHelper.isPlayerElected(game, p1, "absol_minswar")
            && !game.getStoredValue("absolMOW").contains(p1.getFaction())) {
            Button absolButton = Buttons.gray(finChecker + prefix + "absolMOW_", "Minister of War Action");
            compButtons.add(absolButton);
        }

        // Generic
        Button genButton = Buttons.gray(finChecker + prefix + "generic_", "Generic Component Action");
        compButtons.add(genButton);

        return compButtons;
    }

    @ButtonHandler("componentActionRes_")
    public static void resolvePressedCompButton(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        String prefix = "componentActionRes_";
        String finChecker = p1.getFinsFactionCheckerPrefix();
        buttonID = buttonID.replace(prefix, "");

        String firstPart = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(firstPart + "_", "");

        switch (firstPart) {
            case "tech" -> {
                // DEPRECATED: uses the "exhaustTech_" stack of ButtonListener: `else if
                // (buttonID.startsWith("exhaustTech_"))`
            }
            case "leader" -> {
                if (!Mapper.isValidLeader(buttonID)) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve leader.");
                    return;
                }
                if (buttonID.contains("agent")) {
                    List<String> leadersThatNeedSpecialSelection = List.of("naaluagent", "muaatagent", "kolumeagent", "arborecagent", "bentoragent", "xxchaagent", "axisagent");
                    if (leadersThatNeedSpecialSelection.contains(buttonID)) {
                        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID);
                        String message = p1.getRepresentationUnfogged() + " Use buttons to select the user of the agent";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    } else {
                        if ("fogallianceagent".equalsIgnoreCase(buttonID)) {
                            ExhaustLeader.exhaustLeader(event, game, p1, p1.getLeader(buttonID).orElse(null));
                            ButtonHelperAgents.exhaustAgent("fogallianceagent", event, game, p1);
                        } else {
                            ButtonHelperAgents.exhaustAgent(buttonID, event, game, p1);
                        }
                    }
                } else if (buttonID.contains("hero")) {
                    HeroPlay.playHero(event, game, p1, p1.getLeader(buttonID).orElse(null));
                }
            }
            case "relic" -> resolveRelicComponentAction(game, p1, event, buttonID);
            case "pn" -> PlayPN.resolvePNPlay(buttonID, p1, game, event);
            case "ability" -> {
                if ("starForge".equalsIgnoreCase(buttonID)) {

                    List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Warsun);
                    List<Button> buttons = new ArrayList<>();
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        p1.getFactionEmoji() + " Chose to use the starforge ability");
                    String message = "Select the tile you would like to starforge in";
                    for (Tile tile : tiles) {
                        Button starTile = Buttons.green("starforgeTile_" + tile.getPosition(),
                            tile.getRepresentationForButtons(game, p1));
                        buttons.add(starTile);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("orbitalDrop".equalsIgnoreCase(buttonID)) {
                    String successMessage = p1.getFactionEmoji() + " Spent 1 strategy token using " + Emojis.Sol
                        + "Orbital Drop (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event, Emojis.Sol + "Orbital Drop");
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    String message = "Select the planet you would like to place 2 infantry on.";
                    List<Button> buttons = new ArrayList<>(
                        Helper.getPlanetPlaceUnitButtons(p1, game, "2gf", "placeOneNDone_skipbuildorbital"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("muaatFS".equalsIgnoreCase(buttonID)) {
                    String successMessage = p1.getFactionEmoji() + " Spent 1 strategy token using " + Emojis.Muaat
                        + Emojis.flagship + "The Inferno (" + (p1.getStrategicCC()) + "->"
                        + (p1.getStrategicCC() - 1) + ") \n";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event,
                        Emojis.Muaat + Emojis.flagship + "The Inferno");
                    List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Flagship);
                    Tile tile = tiles.get(0);
                    List<Button> buttons = TurnStart.getStartOfTurnButtons(p1, game, true, event);
                    new AddUnits().unitParsing(event, p1.getColor(), tile, "1 cruiser", game);
                    successMessage = successMessage + "Produced 1 " + Emojis.cruiser + " in tile "
                        + tile.getRepresentationForButtons(game, p1) + ".";
                    MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
                    String message = "Use buttons to end turn or do another action";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    ButtonHelper.deleteMessage(event);
                } else if ("lanefirMech".equalsIgnoreCase(buttonID)) {
                    String message3 = "Use buttons to drop 1 mech on a planet";
                    List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(p1, game,
                        "mech", "placeOneNDone_skipbuild"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message3, buttons);
                    String message2 = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_1",
                            "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact3 = Buttons.red(finChecker + "deleteButtons",
                        "Done Purging");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2,
                        purgeFragButtons);
                    String message = "Use buttons to end turn or do an action";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(p1, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, systemButtons);

                } else if ("fabrication".equalsIgnoreCase(buttonID)) {
                    String message = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_1",
                            "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact2 = Buttons.green(finChecker + "gain_CC", "Gain CC");
                    purgeFragButtons.add(transact2);
                    Button transact3 = Buttons.red(finChecker + "finishComponentAction",
                        "Done Resolving Fabrication");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, purgeFragButtons);

                } else if ("stallTactics".equalsIgnoreCase(buttonID)) {
                    String secretScoreMsg = "_ _\n" + p1.getRepresentationUnfogged()
                        + " Click a button below to discard an Action Card";
                    List<Button> acButtons = ACInfo.getDiscardActionCardButtons(game, p1, true);
                    MessageHelper.sendMessageToChannel(p1.getCorrectChannel(),
                        p1.getRepresentation() + " is resolving their Stall Tactics ability");
                    if (!acButtons.isEmpty()) {
                        List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg,
                            acButtons);
                        ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread();
                        for (MessageCreateData message : messageList) {
                            cardsInfoThreadChannel.sendMessage(message).queue();
                        }
                    }
                } else if ("mantlecracking".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = ButtonHelperAbilities.getMantleCrackingButtons(p1, game);
                    // MessageHelper.sendMessageToChannel(event.getChannel(),
                    // p1.getFactionEmoji()+" Chose to use the mantle cracking ability");
                    String message = "Select the planet you would like to mantle crack";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("meditation".equalsIgnoreCase(buttonID)) {
                    if (p1.getStrategicCC() > 0) {
                        String successMessage = p1.getFactionEmoji() + " Reduced strategy pool CCs by 1 ("
                            + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
                        p1.setStrategicCC(p1.getStrategicCC() - 1);
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event, Emojis.kolume + "Meditation");
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    } else {
                        String successMessage = p1.getFactionEmoji() + " Exhausted Scepter";
                        p1.addExhaustedRelic("emelpar");
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    }
                    String message = "Select the tech you would like to ready";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message, ButtonHelper.getAllTechsToReady(game, p1));
                    List<Button> buttons = TurnStart.getStartOfTurnButtons(p1, game, true, event);
                    String message2 = "Use buttons to end turn or do another action";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                }
            }
            case "getRelic" -> {
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - p1.getUrf();
                if (game.isAgeOfExplorationMode()) {
                    numToBeat = numToBeat - 1;
                }
                if ((p1.hasAbility("fabrication") || p1.getPromissoryNotes().containsKey("bmf"))) {
                    numToBeat = numToBeat - 1;
                    if (p1.getPromissoryNotes().containsKey("bmf") && game.getPNOwner("bmf") != p1) {
                        Button transact = Buttons.blue(finChecker + "resolvePNPlay_bmfNotHand", "Play Black Market Forgery");
                        purgeFragButtons.add(transact);
                    }

                }
                if (p1.getCrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getCrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_" + x,
                            "Cultural Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (p1.getIrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getIrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_" + x,
                            "Industrial Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (p1.getHrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getHrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_" + x,
                            "Hazardous Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }

                if (p1.getUrf() > 0) {
                    for (int x = 1; x < p1.getUrf() + 1; x++) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_" + x,
                            "Frontier Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Buttons.red(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                if (p1.hasAbility("a_new_edifice")) {
                    transact2 = Buttons.red(finChecker + "drawRelicFromFrag", "Finish Purging and Explore");
                }
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, purgeFragButtons);
            }
            case "generic" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Doing unspecified component action.");
            case "absolMOW" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), p1.getFactionEmoji() + " is exhausting the " + Emojis.Agenda + "Minister of War" + Emojis.Absol + " and spending a strategy CC to remove 1 CC from the board");
                if (p1.getStrategicCC() > 0) {
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), p1.getFactionEmoji() + " strategy CC went from " + (p1.getStrategicCC() + 1) + " to " + p1.getStrategicCC());
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event);
                }
                List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(p1, game, event, "absol");
                MessageChannel channel = p1.getCorrectChannel();
                MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
                game.setStoredValue("absolMOW", p1.getFaction());
            }
            case "actionCards" -> {
                String secretScoreMsg = "_ _\nClick a button below to play an Action Card";
                List<Button> acButtons = ACInfo.getActionPlayActionCardButtons(game, p1);
                if (!acButtons.isEmpty()) {
                    List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
                    ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread();
                    for (MessageCreateData message : messageList) {
                        cardsInfoThreadChannel.sendMessage(message).queue();
                    }
                }

            }
            case "doStarCharts" -> {
                ButtonHelper.purge2StarCharters(p1);
                DrawBlueBackTile.drawBlueBackTiles(event, game, p1, 1);
            }
        }

        if (!firstPart.contains("ability") && !firstPart.contains("getRelic") && !firstPart.contains("pn")) {
            serveNextComponentActionButtons(event, game, p1);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveRelicComponentAction(Game game, Player player, ButtonInteractionEvent event,
        String relicID) {
        if (!Mapper.isValidRelic(relicID) || !player.hasRelic(relicID)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Invalid relic or player does not have specified relic: `" + relicID + "`");
            return;
        }
        String purgeOrExhaust = "Purged";
        List<String> juniorRelics = List.of("titanprototype", "absol_jr");
        if (juniorRelics.contains(relicID)) { // EXHAUST THE RELIC
            List<Button> buttons2 = AgendaHelper.getPlayerOutcomeButtons(game, null, "jrResolution", null);
            player.addExhaustedRelic(relicID);
            purgeOrExhaust = "Exhausted";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Use buttons to decide who to use JR on", buttons2);

            // OFFER TCS
            for (Player p2 : game.getRealPlayers()) {
                if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                    List<Button> buttons3 = new ArrayList<>();
                    buttons3.add(Buttons.green("exhaustTCS_" + relicID + "_" + player.getFaction(),
                        "Exhaust TCS to Ready " + relicID));
                    buttons3.add(Buttons.red("deleteButtons", "Decline"));
                    String msg = p2.getRepresentationUnfogged()
                        + " you have the opportunity to exhaust your TCS tech to ready " + relicID
                        + " and potentially resolve a transaction.";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg, buttons3);
                }
            }
        } else { // PURGE THE RELIC
            player.removeRelic(relicID);
            player.removeExhaustedRelic(relicID);
        }

        RelicModel relicModel = Mapper.getRelic(relicID);
        String message = player.getFactionEmoji() + " " + purgeOrExhaust + ": " + relicModel.getName();
        MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), message, relicModel.getRepresentationEmbed(false, true));

        // SPECIFIC HANDLING //TODO: Move this shite to RelicPurge
        switch (relicID) {
            case "enigmaticdevice" -> ButtonHelperActionCards.resolveResearch(game, player, relicID, event);
            case "codex", "absol_codex" -> ButtonHelper.offerCodexButtons(player, game, event);
            case "nanoforge", "absol_nanoforge", "baldrick_nanoforge" -> ButtonHelper.offerNanoforgeButtons(player, game, event);
            case "decrypted_cartoglyph" -> DrawBlueBackTile.drawBlueBackTiles(event, game, player, 3);
            case "throne_of_the_false_emperor" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("drawRelic", "Draw a relic"));
                buttons.add(Buttons.blue("thronePoint", "Score a secret someone else scored"));
                buttons.add(Buttons.red("deleteButtons", "Score one of your unscored secrets"));
                message = player.getRepresentation()
                    + " choose one of the options. Reminder than you can't score more secrets than normal with this relic (even if they're someone else's), and you can't score the same secret twice."
                    + " If scoring one of your unscored secrets, just score it via the normal process after pressing the button.";
                MessageHelper.sendMessageToChannel(event.getChannel(), message, buttons);
            }
            case "dynamiscore", "absol_dynamiscore" -> {
                int oldTg = player.getTg();
                player.setTg(oldTg + player.getCommoditiesTotal() + 2);
                if ("absol_dynamiscore".equals(relicID)) {
                    player.setTg(oldTg + Math.min(player.getCommoditiesTotal() * 2, 10));
                } else {
                    player.setTg(oldTg + player.getCommoditiesTotal() + 2);
                }
                message = player.getRepresentationUnfogged() + " Your TGs increased from " + oldTg + " -> "
                    + player.getTg();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, player.getTg() - oldTg);
            }
            case "stellarconverter" -> {
                message = player.getRepresentationUnfogged() + " Select the planet you want to destroy";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                    ButtonHelper.getButtonsForStellar(player, game));
            }
            case "passturn" -> {
                MessageHelper.sendMessageToChannelWithButton(event.getChannel(), null, Buttons.REDISTRIBUTE_CCs);
            }
            case "titanprototype", "absol_jr" -> {
                // handled above
            }
            default -> MessageHelper.sendMessageToChannel(event.getChannel(),
                "This Relic is not tied to any automation. Please resolve manually.");
        }
    }

    public static void serveNextComponentActionButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, systemButtons);
    }
}
