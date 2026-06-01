package ti4.discord.interactions.buttons.handlers.other;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersLeadersHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.abilities.MahactTokenService;
import ti4.service.breakthrough.AutoFactoriesService;
import ti4.service.breakthrough.EidolonMaximumService;
import ti4.service.breakthrough.TheIconService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.LoreService;
import ti4.service.turn.StartTurnService;

@UtilityClass
class DeleteButtonsButtonHandler {

    @ButtonHandler("deleteButtons")
    public static void deleteButtons(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        buttonID = buttonID.replace("deleteButtons_", "");
        String editedMessage = event.getMessage().getContentRaw();
        if (("Done Gaining Command Tokens".equalsIgnoreCase(buttonLabel)
                        || "Done Redistributing Command Tokens".equalsIgnoreCase(buttonLabel)
                        || "Done Losing Command Tokens".equalsIgnoreCase(buttonLabel)
                        || "Done Losing Fleet Tokens".equalsIgnoreCase(buttonLabel))
                && editedMessage.contains("command tokens have gone from")) {

            String playerRep = player.getRepresentationNoPing();
            String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
            String shortCCs = editedMessage.substring(editedMessage.indexOf("command tokens have gone from "));
            shortCCs = shortCCs.replace("command tokens have gone from ", "");
            shortCCs = shortCCs.substring(0, shortCCs.indexOf(' '));
            if (event.getMessage().getContentRaw().contains("Net gain")) {
                boolean cyber = false;
                boolean malevolency = false;
                int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                finalCCs += ". You gained a net total of " + netGain + " command token" + (netGain == 1 ? "" : "s");
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                        cyber = true;
                    }
                    if (!player.ownsPromissoryNote("malevolency") && "malevolency".equalsIgnoreCase(pn)) {
                        malevolency = true;
                    }
                }
                if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
                    if (malevolency && !player.getMahactCC().isEmpty()) {
                        malevolency = false;
                        MahactTokenService.removeFleetCC(game, player, "due to _Malevolency_");
                    }
                    if (player.hasAbility("versatile")
                            || player.hasTech("hm")
                            || cyber
                            || malevolency
                            || player.hasTech("tf-inheritancesystems")) {
                        int properGain = 2;
                        String reasons = "";
                        if (player.hasAbility("versatile")) {
                            properGain += 1;
                            reasons = "**Versatile**";
                        }
                        if (player.hasTech("hm")) {
                            properGain += 1;
                            reasons += (properGain == 3 ? "" : ", ") + "_Hyper Metabolism_";
                        }
                        if (player.hasTech("tf-inheritancesystems")) {
                            properGain += 1;
                            reasons += (properGain == 3 ? "" : ", ") + "_Inheritance Systems_";
                        }
                        if (malevolency) {
                            properGain -= 1;
                            reasons += (properGain == 1 ? "" : ", ") + "_Malevolency_";
                        }
                        if (cyber) {
                            properGain += 1;
                            reasons += (properGain == 3 ? "" : ", ") + "_Cybernetic Enhancements_";
                        }
                        if (netGain != properGain) {
                            MessageHelper.sendMessageToChannel(
                                    player.getCorrectChannel(),
                                    player.getRepresentationUnfogged()
                                            + ", heads up, bot thinks you should have gained "
                                            + (properGain == 1 ? "only " : "") + properGain
                                            + " command token" + (properGain == 1 ? "" : "s") + " due to " + reasons
                                            + ".");
                        } else {
                            if (netGain > 2 && cyber) {
                                PromissoryNoteHelper.resolvePNPlay("ce", player, game, event);
                            }
                        }
                    }
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(
                                player.getPrivateChannel(),
                                "## Remember to click \"Ready for "
                                        + (game.isCustodiansScored() ? "Agenda" : "Strategy Phase")
                                        + "\" when done with homework!\n"
                                        + game.getMainGameChannel().getJumpUrl());
                    }
                }
                player.setTotalExpenses(player.getTotalExpenses() + netGain * 3);
            }

            if ("Done Redistributing Command Tokens".equalsIgnoreCase(buttonLabel)) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        playerRep + ", your initial command token allocation was " + shortCCs
                                + ". Your final command token allocation is " + finalCCs + ".");
            } else {
                if ("leadership".equalsIgnoreCase(buttonID)) {
                    game.setStoredValue("ledSpend" + player.getFaction(), "");
                    String message = playerRep + ", your initial command token allocation was " + shortCCs
                            + ". Your final command tokens allocation is "
                            + finalCCs + ".";
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "leadership");
                } else {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            playerRep + ", your final command tokens allocation is " + finalCCs + ".");
                }
            }
            ButtonHelper.checkFleetInEveryTile(player, game);
        }
        if (("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
                || "Done Producing Units".equalsIgnoreCase(buttonLabel))) {
            Tile tile = null;
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel) && buttonID.contains("_")) {
                String pos = buttonID.split("_")[1];
                buttonID = buttonID.split("_")[0];
                tile = game.getTileByPosition(pos);
                game.setStoredValue(
                        "currentActionSummary" + player.getFaction(),
                        game.getStoredValue("currentActionSummary" + player.getFaction()) + " Produced units in "
                                + tile.getRepresentationForButtons() + ".");
            }
            if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
                    && player.hasAbility("amalgamation")
                    && !game.getStoredValue("amalgAmount").isEmpty()) {
                editedMessage = Helper.buildSpentThingsMessage(player, game, "res");
            }
            ButtonHelper.sendMessageToRightStratThread(player, game, editedMessage, buttonID);
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel)) {
                event.getChannel().getHistory().retrievePast(2).queue(messageHistory -> {
                    Message previousMessage = messageHistory.get(1);
                    if (previousMessage.getContentRaw().contains("You have available to you")) {
                        previousMessage.delete().queue(Consumers.nop(), BotLogger::catchRestError);
                    }
                });
                AutoFactoriesService.resolveAutoFactories(game, player, buttonID);
                TheIconService.checkAndSendIconButton(event, game, player, buttonID);
                EidolonMaximumService.sendEidolonMaximumFlipButtons(game, player);
                int cost = Helper.calculateCostOfProducedUnits(player, game, true);
                game.setStoredValue("producedUnitCostFor" + player.getFaction(), "" + cost);
                player.setTotalExpenses(
                        player.getTotalExpenses() + Helper.calculateCostOfProducedUnits(player, game, true));
                String message2 = player.getRepresentationUnfogged()
                        + ", please choose the planets you wish to exhaust to pay a cost of " + cost + ".";
                boolean warM = player.getSpentThingsThisWindow().contains("warmachine");

                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");

                if (player.hasTechReady("htp")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)) {
                    buttons.add(Buttons.red("exhaustTech_htp", "Exhaust Hegemonic Trade Policy", FactionEmojis.Winnu));
                }
                if ((game.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
                                || player.hasTech("tf-abundance"))
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("generic")) {
                    ButtonHelperCommanders.titansCommanderUsage(event, game, player);
                }
                if (player.hasTechReady("dsbenty")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.green("exhaustTech_dsbenty", "Use Merged Replicators", FactionEmojis.bentor));
                }
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 0
                        && player.hasTechReady("aida")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red(
                            "exhaustTech_aida",
                            "Exhaust AI Development Algorithm (" + ButtonHelper.getNumberOfUnitUpgrades(player) + "r)",
                            TechEmojis.WarfareTech));
                }
                if (player.hasTech("st")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("useTech_st", "Use Sarween Tools", TechEmojis.CyberneticTech));
                }
                if (player.hasTechReady("tf-sledfactories")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(
                            Buttons.red("useTech_tf-sledfactories", "Use Sled Factories", TechEmojis.CyberneticTech));
                }
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    buttons.add(Buttons.red("useRelic_boon", "Use Boon Of The Cerulean God Relic"));
                }
                if (player.hasTechReady("absol_st")) {
                    buttons.add(Buttons.red("useTech_absol_st", "Use Sarween Tools"));
                }
                if (game.getRealPlayers().stream()
                        .anyMatch(player_ -> player_.hasUnexhaustedLeader("netrunnersagent"))) {
                    buttons.addAll(NetrunnersLeadersHandler.getOverclockButtons(game, player, tile));
                }
                if (player.hasUnexhaustedLeader("winnuagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("exhaustAgent_winnuagent", "Use Winnu Agent", FactionEmojis.Winnu));
                }
                if (player.hasUnexhaustedLeader("lunariumagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(
                            Buttons.red("exhaustAgent_lunariumagent", "Use Lunarium Agent", FactionEmojis.lunarium));
                }
                if (player.hasUnexhaustedLeader("gledgeagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_gledgeagent_" + player.getFaction(),
                            "Use Gledge Agent",
                            FactionEmojis.gledge));
                }

                if (player.hasUnexhaustedLeader("ghotiagent")) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_ghotiagent_" + player.getFaction(), "Use Ghoti Agent", FactionEmojis.ghoti));
                }

                if (player.hasUnexhaustedLeader("mortheusagent")) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_mortheusagent_" + player.getFaction(),
                            "Use Mortheus Agent",
                            FactionEmojis.mortheus));
                }
                if (player.hasUnexhaustedLeader("rohdhnaagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_rohdhnaagent_" + player.getFaction(),
                            "Use Roh'Dhna Agent",
                            FactionEmojis.rohdhna));
                }
                if (player.hasLeaderUnlocked("hacanhero")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("purgeHacanHero", "Purge Hacan Hero", FactionEmojis.Hacan));
                }
                Button doneExhausting;
                if (!buttonID.contains("deleteButtons")) {
                    doneExhausting = Buttons.red("deleteButtons_" + buttonID, "Done Exhausting Planets");
                } else {
                    doneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
                }
                if (warM) {
                    player.addSpentThing("warmachine");
                }
                if (!game.getStoredValue("manifestDiscount").isEmpty()) {
                    player.addSpentThing("manifest");
                    game.removeStoredValue("manifestDiscount");
                }
                if (player.hasUnlockedBreakthrough("ghostbt")
                        && tile != null
                        && !tile.getWormholes(game).isEmpty()) {
                    Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
                    int adjust = 0;
                    for (Map.Entry<String, Integer> entry : producedUnits.entrySet()) {
                        String unit2 = entry.getKey().split("_")[0];
                        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit2), player.getColor());
                        UnitModel producedUnit =
                                player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();

                        if (producedUnit.getUnitType() == UnitType.Flagship && player.ownsUnit("ghost_flagship")) {
                            adjust = 1;
                        }
                    }
                    if (tile.getWormholes(game).size() - adjust > 0) {
                        player.addSpentThing(
                                "ghostbt" + (tile.getWormholes(game).size() - adjust));
                    }
                }
                buttons.add(doneExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                if (tile != null
                        && player.hasAbility("rally_to_the_cause")
                        && player.getHomeSystemTile() == tile
                        && !ButtonHelperAbilities.getTilesToRallyToTheCause(game, player)
                                .isEmpty()) {
                    String msg = player.getRepresentation()
                            + " due to your **Rally to the Cause** ability, if you just produced a ship in your home system,"
                            + " you may produce up to 2 ships in a system that contains a planet with a trait,"
                            + " but does not contain a legendary planet or another player's units. Please use the button to resolve.";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.green("startRallyToTheCause", "Rally To The Cause"));
                    buttons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons2);
                }
            }
        }
        if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            if (player.hasTech("asn")
                    && game.getStoredValue("ASN" + player.getFaction()).isEmpty()
                    && (buttonID.contains("tacticalAction")
                            || buttonID.contains("warfare")
                            || buttonID.contains("construction")
                            || buttonID.contains("anarchy7Build")
                            || buttonID.contains("lumi7Build")
                            || buttonID.contains("ministerBuild"))) {
                ButtonHelperFactionSpecific.offerASNButtonsStep1(game, player, buttonID);
            }
            player.resetSpentThings();
            game.removeStoredValue("producedUnitCostFor" + player.getFaction());
            if (player.hasAbility("control_network")) {
                NetrunnersAbilitiesHandler.cleanupControlNetworkProduction(game, player);
            }
            if (player.hasAbility("amalgamation")) {
                game.removeStoredValue("amalgAmount");
            }
            if (buttonID.contains("lumi7Build")) {
                if (!game.getStoredValue("lumi7System").isEmpty()) {
                    Tile tile = game.getTileByPosition(game.getStoredValue("lumi7System"));
                    CommandCounterHelper.addCC(event, player, tile);
                    String message =
                            player.getFactionEmojiOrColor() + " placed 1 command token from reinforcements in the "
                                    + tile.getRepresentation() + " system.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                }
            }
            if (buttonID.contains("tacticalAction")
                    && game.getStoredValue("ASN" + player.getFaction()).isEmpty()) {
                ButtonHelperTacticalAction.endOfTacticalActionThings(player, game, event);
                List<Button> systemButtons2;
                if (player.hasUnexhaustedLeader("sardakkagent")) {
                    String message = player.getRepresentationUnfogged() + ", you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "T'ro, the N'orr" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent.";
                    systemButtons2 = new ArrayList<>(ButtonHelperAgents.getSardakkAgentButtons(game));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("nomadagentmercer")) {
                    String message = player.getRepresentationUnfogged() + ", you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Field Marshal Mercer, a Nomad"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
                    systemButtons2.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(game, player));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }

                if (game.isNaaluAgent()) {
                    player = game.getPlayer(game.getActivePlayerID());
                }

                if (game.isFowMode()) {
                    LoreService.showSystemLore(player, game, game.getActiveSystem(), LoreService.TRIGGER.CONTROLLED);
                }

                game.removeStoredValue("producedUnitCostFor" + player.getFaction());

                String message = player.getRepresentationUnfogged()
                        + ", please use the buttons to end turn or do another action.";
                List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, systemButtons);
                player.resetOlradinPolicyFlags();
            }
        }
        if ("diplomacy".equalsIgnoreCase(buttonID)) {
            ButtonHelper.sendMessageToRightStratThread(player, game, editedMessage, "diplomacy", null);
        }
        if ("spitItOut".equalsIgnoreCase(buttonID) && !"Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), editedMessage);
        }
        ButtonHelper.deleteMessage(event);
    }
}
