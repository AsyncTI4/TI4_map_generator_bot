package ti4.service.leader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.commandcounter.RemoveCommandCounterService;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.LeaderModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.PlanetService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.explore.AddFrontierTokensService;
import ti4.service.info.ListTurnOrderService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class PlayHeroService {

    public static void playHero(GenericInteractionCreateEvent event, Game game, Player player, Leader playerLeader) {
        LeaderModel leaderModel = playerLeader.getLeaderModel().orElse(null);
        boolean showFlavourText = Constants.VERBOSITY_VERBOSE.equals(game.getOutputVerbosity());
        StringBuilder sb = new StringBuilder();
        if (leaderModel != null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " played:");
            player.getCorrectChannel()
                .sendMessageEmbeds(leaderModel.getRepresentationEmbed(false, true, false, showFlavourText)).queue();
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), LeaderEmojis.getLeaderEmoji(playerLeader).toString());
            sb.append(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
            BotLogger.warning(new BotLogger.LogMessageOrigin(event), "Missing LeaderModel: " + playerLeader.getId());
        }

        if ("letnevhero".equals(playerLeader.getId()) || "nomadhero".equals(playerLeader.getId())
            || "zealotshero".equals(playerLeader.getId()) || "nokarhero".equals(playerLeader.getId())
            || "kolumehero".equals(playerLeader.getId())) {
            playerLeader.setLocked(false);
            playerLeader.setActive(true);
            sb.append("\nLeader will be purged after status cleanup.");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
            if ("zealotshero".equals(playerLeader.getId())) {
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(),
                    player.getRepresentation() + ", please use the button to get your first non-faction technology.",
                    Buttons.GET_A_FREE_TECH);
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(),
                    player.getRepresentation() + ", please use the button to get your second non-faction technology.",
                    Buttons.GET_A_FREE_TECH);
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(),
                    player.getRepresentation() + ", please use the button to get your third non-faction technology.",
                    Buttons.GET_A_FREE_TECH);
            }
        } else {
            boolean purged = true;
            LeaderModel playerLeaderModel = playerLeader.getLeaderModel().orElse(null);
            String leaderName = playerLeaderModel == null ? "Hero " + playerLeader.getId() : playerLeaderModel.getName() + ", the " + playerLeaderModel.getFaction() + " hero,";
            String msg = leaderName + " has been purged.";
            if (!"mykomentorihero".equals(playerLeader.getId())) {
                purged = player.removeLeader(playerLeader);
                ButtonHelperHeroes.checkForMykoHero(game, playerLeader.getId(), player);
            } else {
                msg = "Coprinus Comatus, the Myko-Mentori hero, was used to copy another hero.";
            }

            if (purged) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    msg);

            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    leaderName + " was not purged - something went wrong.");
                return;
            }
        }

        switch (playerLeader.getId()) {
            case "kollecchero" -> RelicHelper.drawWithAdvantage(player, game, game.getRealPlayers().size());
            case "titanshero" -> {
                Tile t = player.getHomeSystemTile();
                if (game.getTileFromPlanet("elysium") != null && game.getTileFromPlanet("elysium") == t) {
                    t.addToken("attachment_titanshero.png", "elysium");
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Elysium has had Ul The Progenitor attached, and been readied.");
                    PlanetService.refreshPlanet(player, "elysium");
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Use the following command to add the attachment: `/add_token token:titanshero`");
                }
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentationUnfogged()
                    + "Added 2 fighters to every system with an owned planet and no other players' ships.");
                ButtonHelperHeroes.resolveFlorzenHeroStep1(player, game);
            }
            case "kyrohero" -> {
                int dieResult = player.getLowestSC();
                game.setStoredValue("kyroHeroSC", dieResult + "");
                game.setStoredValue("kyroHeroPlayer", player.getFaction());
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getSCName(dieResult, game)
                    + " has been with Speygh, the Kyro hero"
                    + (game.isFrankenGame() ? ", and the faction that played the hero as " + player.getFaction() : "") + ".");
                ListTurnOrderService.turnOrder(event, game);
            }
            case "ghotihero" -> MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Choose the tiles in which you would like to resolve Nmenmede, the Ghoti hero.",
                ButtonHelperHeroes.getTilesToGhotiHeroIn(player, game, event));
            case "gledgehero" -> ButtonHelperHeroes.resolveGledgeHero(player, game);
            case "khraskhero" -> {
                ButtonHelperHeroes.resolveKhraskHero(player, game);
                ButtonHelperHeroes.resolveKhraskHero(player, game);
                ButtonHelperHeroes.resolveKhraskHero(player, game);
                ButtonHelperHeroes.resolveKhraskHero(player, game);
            }
            case "mortheushero" -> MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Choose the tiles in which you would like to resolve Bayan, the Mortheus hero.",
                ButtonHelperHeroes.getTilesToGlimmersHeroIn(player, game, event));
            case "axishero" -> ButtonHelperHeroes.resolveAxisHeroStep1(player, game);
            case "lanefirhero" -> ButtonHelperHeroes.resolveLanefirHeroStep1(player, game);
            case "cymiaehero" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(
                    Buttons.green("cymiaeHeroStep1_" + (game.getRealPlayers().size() + 1), "Resolve Cymiae Hero"));
                buttons.add(Buttons.blue("cymiaeHeroAutonetic", "Resolve Autonetic Memory First"));

                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation() + " choose whether to resolve **Autonetic Memory** or not.", buttons);

            }
            case "lizhohero" -> MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(),
                "You may use the buttons in your `#cards-info` thread to set traps, then when you're done with that, press the following button to start distributing 12 fighters.",
                Buttons.green("lizhoHeroFighterResolution", "Distribute 12 Fighters"));
            case "solhero" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getRepresentationUnfogged() + ", all of your command tokens on the game have been returned to your reinforcements.");
                for (Tile t : game.getTileMap().values()) {
                    if (CommandCounterHelper.hasCC(event, player.getColor(), t)) {
                        RemoveCommandCounterService.fromTile(event, player.getColor(), t, game);
                    }
                }
            }
            case "cheiranhero" -> ButtonHelperHeroes.cheiranHeroResolution(player, game, event);
            case "olradinhero" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " added 1 infantry to each planet");
                ActionCardHelper.doRise(player, event, game);
                ButtonHelperHeroes.offerOlradinHeroFlips(game, player);
            }
            case "argenthero" -> ButtonHelperHeroes.argentHeroStep1(game, player, event);
            case "l1z1xhero" -> {
                String message = player.getRepresentation()
                    + " Resolving The Helmsman, the L1Z1X Hero. At the moment, this is implemented as a sort of tactical action, relying on the player to follow the rules."
                    + " The game will know not to take a command token from your tactic pool, and will allow you to move out of locked systems."
                    + " Reminder that you may carry ground forces and fighters with your dreadnoughts/flagship, and that they can't move into supernovae (or asteroid fields if you don't have _Antimass Deflectors_).";
                List<Button> ringButtons = ButtonHelper.getPossibleRings(player, game);
                game.setL1Hero(true);
                game.resetCurrentMovedUnitsFrom1TacticalAction();
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
            }
            case "winnuhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getWinnuHeroSCButtons(game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, showFlavourText)
                    + ", you invoke the Imperial Seal" + (RandomHelper.isOneInX(50) ? " 🦭" : "") + "."
                    + " Please choose which strategy card you'd like to do the primary of."
                    + " Reminder you may allow others to do the secondary, but they should still spend 1 command token from their strategy pool to resolving it (unless it's **Leadership**).",
                    buttons);
            }
            case "gheminaherolady" -> {
                List<Button> buttons = ButtonHelperHeroes.getButtonsForGheminaLadyHero(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                        + " use the button to pick on which planet you wish to resolve The Lady, a Ghemina hero.",
                    buttons);
            }
            case "gheminaherolord" -> {
                List<Button> buttons = ButtonHelperHeroes.getButtonsForGheminaLordHero(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                        + " use the button to pick on which planet you wish to resolve The Lord, a Ghemina hero",
                    buttons);
            }
            case "arborechero" -> {
                List<Button> buttons = ButtonHelperHeroes.getArboHeroButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getRepresentation(true, showFlavourText)
                        + " use the buttons to build in the desired system(s)",
                    buttons);
            }
            case "saarhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getSaarHeroButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, showFlavourText) + " use the buttons to select the system to remove all opposing fighters and infantry from", buttons);
            }
            case "edynhero" -> {
                int size = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech).size();
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player
                    .getFactionEmoji() + " may resolve " + size
                    + " agenda" + (size == 1 ? "" : "s") + " because that's how many Sigils they got."
                    + " After putting the agendas on top in the order you wish (don't bottom any), please press the button to reveal an agenda.");
                AgendaHelper.drawAgenda(event, size, game, player);
                Button flipAgenda = Buttons.blue("flip_agenda", "Press this to flip agenda");
                List<Button> buttons = List.of(flipAgenda);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Flip Agenda", buttons);
            }
            case "kjalengardhero" -> {
                int size = ButtonHelperAgents.getGloryTokenTiles(game).size();
                for (Tile tile : ButtonHelperAgents.getGloryTokenTiles(game)) {
                    List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event,
                        "kjalHero_" + tile.getPosition());
                    if (!buttons.isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                            "Use buttons to remove a command token from "
                                + tile.getRepresentationForButtons(game, player) + " or an adjacent tile.",
                            buttons);
                    }
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + " may gain " + size + " command token" + (size == 1 ? "" : "s") + ".");
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String trueIdentity = player.getRepresentationUnfogged();
                String message2 = trueIdentity + ", your current command tokens are " + player.getCCRepresentation()
                    + ". Use buttons to gain command tokens.";
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
            case "vaylerianhero" -> {
                if (!game.isNaaluAgent()) {
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    CommandCounterHelper.addCC(event, player, game.getTileByPosition(game.getActiveSystem()));
                    game.setStoredValue("vaylerianHeroActive", "true");
                }
                List<Button> removeCCs = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "vaylerianhero");
                if (!removeCCs.isEmpty()) {
                    for (int x = 0; x < ButtonHelperAgents.getGloryTokenTiles(game).size(); x++) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                            "Use buttons to remove 1 of your command tokens from the game board.", removeCCs);
                    }
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + " may gain 1 command token.");
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
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged()
                    + ", for each planet with a _Branch Office_, you may copy 1 unit upgrade technology from the player that controls that planet.");
                for (Player p2 : ButtonHelperFactionSpecific.getPlayersWithBranchOffices(game, player)) {
                    if (ButtonHelperFactionSpecific.getNumberOfBranchOffices(game, p2) == 1) {
                        String msg = p2.getFactionEmojiOrColor() + " owns 1 _Branch Office_. You may copy 1 of these unit upgrade technologies.";
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg,
                            ButtonHelperHeroes.getPossibleTechForVeldyrToGainFromPlayer(player, p2, game));
                    } else {
                        String msg = p2.getFactionEmojiOrColor() + " owns " + ButtonHelperFactionSpecific.getNumberOfBranchOffices(game, p2)
                            + " _Branch Offices_. You may copy " + ButtonHelperFactionSpecific.getNumberOfBranchOffices(game, p2) + " of these unit upgrade technologies.";
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg,
                            ButtonHelperHeroes.getPossibleTechForVeldyrToGainFromPlayer(player, p2, game));
                        for (int x = 1; x < ButtonHelperFactionSpecific.getNumberOfBranchOffices(game, p2); x++) {
                            if (!ButtonHelperHeroes.getPossibleTechForVeldyrToGainFromPlayer(player, p2, game).isEmpty()) {
                                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "",
                                    ButtonHelperHeroes.getPossibleTechForVeldyrToGainFromPlayer(player, p2, game));
                            }
                        }
                    }
                }
            }
            case "nekrohero" -> {
                List<Button> buttons = ButtonHelperHeroes.getNekroHeroButtons(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true,
                    showFlavourText)
                    + " use the button to pick which planet you'd like to get a technology and trade goods from (and kill any enemy units).",
                    buttons);
            }
            case "bentorhero" -> {
                ButtonHelperHeroes.resolveBentorHero(game, player);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmoji() + " offered buttons to explore all planets.");
            }
            case "nivynhero" -> {
                ButtonHelperHeroes.resolveNivynHeroSustainEverything(game, player);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmoji() + " sustained all units except their mechs.");
            }
            case "jolnarhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getJolNarHeroSwapOutOptions(player);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true,
                    showFlavourText)
                    + " use the buttons to pick what technology you would like to swap out."
                    + "\n-# Reminder that since all swap are simultaneous, you cannot swap out a technology and then swap it back in.",
                    buttons);
            }
            case "yinhero" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue(player.getFinsFactionCheckerPrefix() + "yinHeroStart",
                    "Invade A Planet With Yin Hero"));
                buttons.add(Buttons.red("deleteButtons", "Delete Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true,
                    showFlavourText)
                    + " use the button to do individual invasions, then delete the buttons when you have placed 3 total infantry.",
                    buttons);
            }
            case "naazhero" -> {
                RelicHelper.drawRelicAndNotify(player, event, game);
                List<Button> buttons = ButtonHelperHeroes.getNRAHeroButtons(game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true,
                    showFlavourText)
                    + " use the button to do __two__ of the available secondaries.\n-# All are presented for convenience, but two is the limit.",
                    buttons);
            }
            case "mahacthero" -> {
                List<Button> buttons = ButtonHelperHeroes.getBenediction1stTileOptions(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getRepresentation(true, showFlavourText)
                        + " use the button to decide which tile you wish to force ships to move from.",
                    buttons);
            }
            case "ghosthero" -> {
                List<Button> buttons = ButtonHelperHeroes.getGhostHeroTilesStep1(game, player);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getRepresentation(true, showFlavourText)
                        + " use the button to select the first tile you would like to swap with Riftwalker Meian, the Creuss hero.",
                    buttons);
            }
            case "augershero" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue(player.getFinsFactionCheckerPrefix() + "augersHeroStart_" + 1,
                    "Resolve Ilyxum Hero on Stage 1 Deck"));
                buttons.add(Buttons.blue(player.getFinsFactionCheckerPrefix() + "augersHeroStart_" + 2,
                    "Resolve Ilyxum Hero on Stage 2 Deck"));
                buttons.add(Buttons.red("deleteButtons", "Delete Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getRepresentation(true, showFlavourText)
                        + " use the button to choose on which objective type you wanna resolve Atropha, the Ilyxum hero.",
                    buttons);
            }
            case "empyreanhero" -> {
                AddFrontierTokensService.addFrontierTokens(event, game);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Added frontier tokens");
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Use Buttons to explore empties", ButtonHelperHeroes.getEmpyHeroButtons(player, game));
            }
            case "cabalhero" -> {
                List<Button> buttons = ButtonHelperHeroes.getCabalHeroButtons(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Use Buttons to capture people", buttons);
            }
            case "yssarilhero" -> {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player || p2.getAc() == 0) {
                        continue;
                    }
                    List<Button> buttons = new ArrayList<>(getYssarilHeroActionCardButtons(player, p2));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                        p2.getRepresentationUnfogged()
                            + " Kyver, Blade and Key, the Yssaril hero, has been played. Please buttons to select which action card you will offer to them.",
                        buttons);
                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation(true, showFlavourText) + " sent everyone a ping in their `#cards-info` thread with buttons to choose an action card to offer you.");
            }
            case "keleresheroharka" -> resolveKeleresHeroMentak(game, player, event);
        }
        TemporaryCombatModifierModel posssibleCombatMod = CombatTempModHelper.getPossibleTempModifier(Constants.LEADER,
            playerLeader.getId(), player.getNumberOfTurns());
        if (posssibleCombatMod != null) {
            player.addNewTempCombatMod(posssibleCombatMod);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
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
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Buttons.gray("yssarilHeroInitialOffering_" + value + "_" + yssaril.getFaction(), ac_name, CardEmojis.ActionCard));
                }
            }
        }
        return acButtons;
    }

    private static void resolveKeleresHeroMentak(Game game, Player player, GenericInteractionCreateEvent event) {
        int originalACDeckCount = game.getActionCards().size();
        StringBuilder acRevealMessage = new StringBuilder("The following non-component action cards were revealed before drawing three component action cards:\n");
        StringBuilder acDrawMessage = new StringBuilder("The following component action cards were drawn into their hand:\n");
        List<String> cardsToShuffleBackIntoDeck = new ArrayList<>();
        int componentActionACCount = 0;
        int index = 1;
        boolean noMoreComponentActionCards = false;
        while (componentActionACCount < 3) {
            Integer acID = null;
            String acKey = null;
            for (Map.Entry<String, Integer> ac : Helper.getLastEntryInHashMap(game.drawActionCard(player.getUserID())).entrySet()) {
                acID = ac.getValue();
                acKey = ac.getKey();
            }
            ActionCardModel actionCard = Mapper.getActionCard(acKey);
            String acName = actionCard.getName();
            String acWindow = actionCard.getWindow();
            if ("Action".equalsIgnoreCase(acWindow)) {
                acDrawMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(actionCard.getRepresentation());
                componentActionACCount++;
            } else {
                acRevealMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(CardEmojis.ActionCard).append(" ").append(acName).append("\n");
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
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " uses Harka Leeds, the Keleres (Mentak) hero, to reveal " + CardEmojis.ActionCard + "action cards, until drawing 3 component action cards.\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), acRevealMessage.toString());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), acDrawMessage.toString());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "All non-component action cards have been reshuffled back into the deck.");
        ActionCardHelper.sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, player);
        if (noMoreComponentActionCards) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**All action cards in the deck have been revealed. __No component action cards remain.__**");
        }
    }
}
