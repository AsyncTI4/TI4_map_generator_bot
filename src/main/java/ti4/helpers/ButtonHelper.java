package ti4.helpers;

import java.io.File;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.checkerframework.checker.nullness.qual.Nullable;

import lombok.Data;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.buttons.Buttons;
import ti4.buttons.UnfiledButtonHandlers;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.ShowDiscardActionCards;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.ShowUnScoredSOs;
import ti4.commands.combat.CombatRoll;
import ti4.commands.explore.ExploreFrontier;
import ti4.commands.explore.ExploreInfo;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.SendDebt;
import ti4.commands.player.Setup;
import ti4.commands.player.TurnStart;
import ti4.commands.relic.RelicShowRemaining;
import ti4.commands.special.CheckDistance;
import ti4.commands.special.DiploSystem;
import ti4.commands.tech.TechShowDeck;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.commands2.CommandHelper;
import ti4.generator.MapRenderPipeline;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileGenerator;
import ti4.generator.TileHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.listeners.ButtonListener;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.selections.selectmenus.SelectFaction;

public class ButtonHelper {

    public static String getButtonRepresentation(Button button) {
        String id = button.getId();
        String label = button.getLabel();
        EmojiUnion emoji = button.getEmoji();
        return (emoji != null ? emoji.getFormatted() : "") + "__**" + (label.isEmpty() ? " " : label) + "**__  `[" + id + "]`";
    }

    public static boolean doesPlayerHaveFSHere(String flagshipID, Player player, Tile tile) {
        if (!player.hasUnit(flagshipID) || tile == null) {
            return false;
        }
        UnitHolder space = tile.getUnitHolders().get("space");
        return space.getUnitCount(UnitType.Flagship, player.getColor()) > 0;
    }

    public static boolean doesPlayerHaveMechHere(String mechID, Player player, Tile tile) {
        if (!player.hasUnit(mechID) || tile == null) {
            return false;
        }
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                return true;
            }
        }

        return false;
    }

    public static void resolveInfantryDeath(Game game, Player player, int amount) {
        if (player.hasInf2Tech()) {
            for (int x = 0; x < amount; x++) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), rollInfantryRevival(player));
            }
        }
    }

    public static List<Button> getDacxiveButtons(String planet, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "dacxive_" + planet,
            "Resolve Dacxive Animators"));
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons", "No Dacxive Animators"));
        return buttons;
    }

    public static List<Button> getForcedPNSendButtons(Game game, Player receiver, Player sender) {
        List<Button> stuffToTransButtons = new ArrayList<>();
        for (String pnShortHand : sender.getPromissoryNotes().keySet()) {
            if (sender.getPromissoryNotesInPlayArea().contains(pnShortHand)
                || (receiver.getAbilities().contains("hubris") && pnShortHand.endsWith("an"))) {
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
            Player owner = game.getPNOwner(pnShortHand);
            Button transact;
            if (game.isFowMode()) {
                transact = Buttons.green(
                    "naaluHeroSend_" + receiver.getFaction() + "_" + sender.getPromissoryNotes().get(pnShortHand),
                    owner.getColor() + " " + promissoryNote.getName());
            } else {
                transact = Buttons.green("naaluHeroSend_" + receiver.getFaction() + "_" + sender.getPromissoryNotes().get(pnShortHand), promissoryNote.getName()).withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
            }
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }

    public static void arboAgentOnButton(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String rest = buttonID.replace("arboAgentOn_", "").toLowerCase();
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unit = rest;
        for (int x = 0; x < amount; x++) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), ident + " " + RiftUnitsHelper.riftUnit(unit, tile, game, event, player, null));
        }
        deleteMessage(event);
    }

    public static boolean shouldKeleresRiderExist(Game game) {
        return game.getPNOwner("ridera") != null || game.getPNOwner("riderm") != null
            || game.getPNOwner("riderx") != null || game.getPNOwner("rider") != null;
    }

    public static String rollInfantryRevival(Player player) {
        Die d1 = new Die(6);
        if (player.hasTech("so2")) {
            d1 = new Die(5);
        }
        String msg = Emojis.infantry + " rolled a " + d1.getGreenDieIfSuccessOrRedDieIfFailure();
        if (player.hasTech("cl2")) {
            msg = Emojis.infantry + " died";
        }
        if (d1.isSuccess() || player.hasTech("cl2")) {
            msg += " and revived. You will be prompted to place them on a planet in your HS at the start of your next turn.";
            player.setStasisInfantry(player.getGenSynthesisInfantry() + 1);
        } else {
            msg += " and failed. No revival";
        }
        return player.getFactionEmoji() + " " + msg;
    }

    public static void rollMykoMechRevival(Game game, Player player) {
        Die d1 = new Die(6);
        String msg = player.getFactionEmoji() + Emojis.mech + " rolled a " + d1.getGreenDieIfSuccessOrRedDieIfFailure();
        if (d1.isSuccess()) {
            msg += " and revived. You will be prompted to replace 1 infantry with 1 mech at the start of your turn.";
            ButtonHelperFactionSpecific.increaseMykoMech(game);
        } else {
            msg += " and failed. No revival";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    @ButtonHandler("statusInfRevival_")
    public static void placeInfantryFromRevival(Game game, ButtonInteractionEvent event, Player player, String buttonID) {
        String planet = buttonID.split("_")[1];
        String amount;
        if (StringUtils.countMatches(buttonID, "_") > 1) {
            amount = buttonID.split("_")[2];
        } else {
            amount = "1";
        }

        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, amount + " inf " + planet, game);
        player.setStasisInfantry(player.getGenSynthesisInfantry() - Integer.parseInt(amount));
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " Placed " + amount + " infantry on "
            + Helper.getPlanetRepresentation(planet, game) + ". You have " + player.getGenSynthesisInfantry() + " infantry left to revive.");
        if (player.getGenSynthesisInfantry() == 0) {
            deleteMessage(event);
        }
    }

    public static MessageChannel getSCFollowChannel(Game game, Player player, int scNum) {
        String threadName = game.getName() + "-round-" + game.getRound() + "-";
        switch (scNum) {
            case 1 -> threadName = threadName + "leadership";
            case 2 -> threadName = threadName + "diplomacy";
            case 3 -> threadName = threadName + "politics";
            case 4 -> threadName = threadName + "construction";
            case 5 -> threadName = threadName + "trade";
            case 6 -> threadName = threadName + "warfare";
            case 7 -> threadName = threadName + "technology";
            case 8 -> threadName = threadName + "imperial";
            default -> {
                return player.getCorrectChannel();
            }
        }
        List<ThreadChannel> threadChannels = game.getMainGameChannel().getThreadChannels();
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                return threadChannel_;
            }
        }
        return player.getCorrectChannel();
    }

    public static Set<String> getTypesOfPlanetPlayerHas(Game game, Player player) {
        Set<String> types = new HashSet<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Planet unitHolder = game.getPlanetsInfo().get(planet);
            if (unitHolder == null)
                continue;

            Planet planetReal = unitHolder;
            types.addAll(planetReal.getPlanetTypes());
        }
        return types;
    }

    public static List<Button> getPlaceStatusInfButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (player.getGenSynthesisInfantry() == 0) {
            return buttons;
        }
        Tile tile = player.getHomeSystemTile();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                if (player.getPlanets().contains(unitHolder.getName())) {
                    buttons.add(Buttons.green("statusInfRevival_" + unitHolder.getName() + "_1",
                        "Place 1 infantry on " + Helper.getPlanetRepresentation(unitHolder.getName(), game)));
                    if (player.getGenSynthesisInfantry() > 1) {
                        buttons.add(Buttons.green(
                            "statusInfRevival_" + unitHolder.getName() + "_" + player.getGenSynthesisInfantry(),
                            "Place " + player.getGenSynthesisInfantry() + " infantry on "
                                + Helper.getPlanetRepresentation(unitHolder.getName(), game)));

                    }
                }

            }
        }
        if (player.ownsUnit("cymiae_infantry2")) {
            buttons = new ArrayList<>();
            for (String planet : player.getPlanets()) {
                if (game.getTileFromPlanet(planet) != null) {
                    buttons.add(Buttons.green("statusInfRevival_" + planet + "_1",
                        "Place 1 infantry on " + Helper.getPlanetRepresentation(planet, game)));
                }
            }
        }
        return buttons;

    }

    public static List<Button> getExhaustButtonsWithTG(Game game, Player player) {
        return getExhaustButtonsWithTG(game, player, "both");
    }

    public static List<Button> getExhaustButtonsWithTG(Game game, Player player, String whatIsItFor) {
        List<Button> buttons = Helper.getPlanetExhaustButtons(player, game, whatIsItFor);
        if (whatIsItFor.contains("tgsonly")) {
            buttons = new ArrayList<>();
        }
        if (player.getTg() > 0 || (game.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
            && !whatIsItFor.contains("inf"))) {
            Button lost1TG = Buttons.red("reduceTG_1_" + whatIsItFor, "Spend 1TG");
            buttons.add(lost1TG);
        }
        if (player.getTg() > 1) {
            Button lost2TG = Buttons.red("reduceTG_2_" + whatIsItFor, "Spend 2TGs");
            buttons.add(lost2TG);
        }
        if (player.getTg() > 2) {
            Button lost3TG = Buttons.red("reduceTG_3_" + whatIsItFor, "Spend 3TGs");
            buttons.add(lost3TG);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 0) {
            Button lost1C = Buttons.red("reduceComm_1_" + whatIsItFor, "Spend 1 commodity");
            buttons.add(lost1C);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 1) {
            Button lost2C = Buttons.red("reduceComm_2_" + whatIsItFor, "Spend 2 commodities");
            buttons.add(lost2C);
        }
        if (player.hasUnexhaustedLeader("olradinagent")) {
            Button hacanButton = Buttons.gray("exhaustAgent_olradinagent_" + player.getFaction(), "Use Olradin Agent", Emojis.olradin);
            buttons.add(hacanButton);
        }
        if (!player.getNomboxTile().getUnitHolders().get("space").getUnits().isEmpty() && !whatIsItFor.contains("inf")
            && !whatIsItFor.contains("both") && (player.hasAbility("devour") || player.hasAbility("riftmeld"))) {
            Button release = Buttons.gray("getReleaseButtons", "Release captured units").withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("cabal")));
            buttons.add(release);
        }
        if (player.hasUnexhaustedLeader("khraskagent")
            && (whatIsItFor.contains("inf") || whatIsItFor.contains("both"))) {
            Button release = Buttons.gray("exhaustAgent_khraskagent_" + player.getFaction(), "Use Khrask Agent").withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("khrask")));
            buttons.add(release);
        }
        if (player.hasAbility("diplomats") && !ButtonHelperAbilities.getDiplomatButtons(game, player).isEmpty()) {
            Button release = Buttons.gray("getDiplomatsButtons", "Use Diplomats Ability", Emojis.freesystems);
            buttons.add(release);
        }
        buttons.add(Buttons.gray("resetSpend_" + whatIsItFor, "Reset Spent Planets and TGs"));

        return buttons;
    }

    public static List<Player> getPlayersWhoHaveNoSC(Player player, Game game) {
        List<Player> playersWhoDontHaveSC = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (!p2.getSCs().isEmpty() || p2 == player) {
                continue;
            }
            playersWhoDontHaveSC.add(p2);
        }
        if (playersWhoDontHaveSC.isEmpty()) {
            playersWhoDontHaveSC.add(player);
        }
        return playersWhoDontHaveSC;
    }

    public static List<Player> getPlayersWhoHaventReacted(String messageId, Game game) {
        List<Player> playersWhoAreMissed = new ArrayList<>();
        if (messageId == null || "".equalsIgnoreCase(messageId)) {
            return playersWhoAreMissed;
        }
        TextChannel mainGameChannel = game.getMainGameChannel();
        if (mainGameChannel == null) {
            return playersWhoAreMissed;
        }
        try {
            Message mainMessage = mainGameChannel.retrieveMessageById(messageId).completeAfter(100,
                TimeUnit.MILLISECONDS);
            for (Player player : game.getPlayers().values()) {
                if (!player.isRealPlayer()) {
                    continue;
                }

                String faction = player.getFaction();
                if (faction == null || faction.isEmpty() || "null".equals(faction)) {
                    continue;
                }

                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (game.isFowMode()) {
                    int index = 0;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, messageId));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction == null) {
                    playersWhoAreMissed.add(player);
                }
            }
            return playersWhoAreMissed;
        } catch (Exception e) {
            return playersWhoAreMissed;
        }
    }

    public static String playerHasDMZPlanet(Player player, Game game) {
        String dmzPlanet = "no";
        for (String planet : player.getPlanets()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = game.getPlanetsInfo().get(planet);
            if (p == null) {
                BotLogger.log("Null unitholder for planet " + planet);
                continue;
            }
            Set<String> tokenList = p.getTokenList();
            if (tokenList.stream().anyMatch(token -> token.contains("dmz_large") || token.contains("dmz"))) {
                dmzPlanet = planet;
                break;
            }
        }
        return dmzPlanet;
    }

    public static List<Button> getTradePlanetsWithAlliancePartnerButtons(Player p1, Player receiver, Game game) {
        List<Button> buttons = new ArrayList<>();
        if (!p1.getAllianceMembers().contains(receiver.getFaction())) {
            return buttons;
        }
        for (String planet : p1.getPlanets()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder unitHolder = getUnitHolderFromPlanetName(planet, game);
            if (unitHolder != null && unitHolder.getUnitColorsOnHolder().contains(receiver.getColorID())) {
                String refreshed = "refreshed";
                if (p1.getExhaustedPlanets().contains(planet)) {
                    refreshed = "exhausted";
                }
                buttons.add(Buttons.gray(
                    "resolveAlliancePlanetTrade_" + planet + "_" + receiver.getFaction() + "_" + refreshed,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        return buttons;
    }

    @ButtonHandler("resolveAlliancePlanetTrade_")
    public static void resolveAllianceMemberPlanetTrade(Player p1, Game game, ButtonInteractionEvent event, String buttonID) {
        String dmzPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        String exhausted = buttonID.split("_")[3];
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(p1.getCorrectChannel(),
                "Could not resolve second player, please resolve manually.");
            return;
        }
        PlanetAdd.doAction(p2, dmzPlanet, game, event, true);
        if (!"exhausted".equalsIgnoreCase(exhausted)) {
            p2.refreshPlanet(dmzPlanet);
        }
        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Buttons.gray("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Buttons.gray("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = p1.getFactionEmojiOrColor();
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(dmzPlanet, game) + " to "
            + p2.getFactionEmojiOrColor();
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        goAgainButtons.add(Buttons.green("demandSomething_" + p2.getColor(), "Expect something in return"));
        if (game.isFowMode() || !game.isNewTransactionMethod()) {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
                MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
                MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
            deleteMessage(event);
        }
    }

    @ButtonHandler("resolveDMZTrade_")
    public static void resolveDMZTrade(Player p1, Game game, ButtonInteractionEvent event, String buttonID) {
        String dmzPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(p1.getCorrectChannel(),
                "Could not resolve second player, please resolve manually.");
            return;
        }
        PlanetAdd.doAction(p2, dmzPlanet, game, event, false);
        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Buttons.gray("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Buttons.gray("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = p1.getFactionEmojiOrColor();
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(dmzPlanet, game) + " to " + p2.getFactionEmojiOrColor();
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        goAgainButtons.add(Buttons.green("demandSomething_" + p2.getColor(), "Expect something in return"));

        if (game.isFowMode() || !game.isNewTransactionMethod()) {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
                MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
                MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
        }
        deleteMessage(event);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " you got traded the DMZ");
    }

    public static boolean canIBuildGFInSpace(Game game, Player player, Tile tile, String kindOfBuild) {
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();

        if ("arboCommander".equalsIgnoreCase(kindOfBuild) || "freelancers".equalsIgnoreCase(kindOfBuild)
            || "genericBuild".equalsIgnoreCase(kindOfBuild) || "muaatagent".equalsIgnoreCase(kindOfBuild)) {
            return true;
        }
        boolean tileHasShips = tile.containsPlayersUnitsWithModelCondition(player, UnitModel::getIsShip);
        if (player.hasAbility("voidmaker") && tile.getPlanetUnitHolders().isEmpty() && tileHasShips) {
            return true;
        }
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }

            for (Map.Entry<UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                if (unitEntry.getValue() > 0 && player.unitBelongsToPlayer(unitEntry.getKey())) {
                    UnitModel model = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (model == null)
                        continue;
                    if (model.getProductionValue() > 0)
                        return true;
                    if (player.hasUnit("ghoti_flagship") && "flagship".equalsIgnoreCase(model.getBaseType())) {
                        return true;
                    }
                }
            }
        }
        if (tile.getUnitHolders().size() == 1 && player.hasTech("dsmorty")) {
            return true;
        }

        return player.getTechs().contains("mr") && tile.getTileModel().isSupernova();
    }

    @ButtonHandler("forceARefresh_")
    public static void forceARefresh(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String msg = player.getFactionEmoji() + " forced " + p2.getFactionEmojiOrColor() + " to refresh";
        String msg2 = p2.getRepresentationUnfogged() + " the trade holder has forced you to refresh";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        deleteTheOneButton(event);
        if (!p2.getFollowedSCs().contains(5)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(p2, 5, game, event);
        }
        p2.addFollowedSC(5, event);
        ButtonHelperStats.replenishComms(event, game, p2, true);
    }

    public static List<Button> getForcedRefreshButtons(Game game, Player player, List<Player> followingPlayers) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : followingPlayers) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("forceARefresh_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("forceARefresh_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done Resolving"));
        return buttons;
    }

    public static void resolveTACheck(Game game, Player player, GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getFaction().equalsIgnoreCase(player.getFaction()) || player.getAllianceMembers().contains((p2.getFaction()))) {
                continue;
            }
            if (p2.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("useTA_" + player.getColor(), "Use TA"));
                buttons.add(Buttons.red("deleteButtons", "Decline to use TA"));
                String message = p2.getRepresentationUnfogged()
                    + " a player whose TA you hold has refreshed their comms, would you like to play the TA?";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, buttons);
            }
        }
    }

    @ButtonHandler("offerDeckButtons")
    public static void offerDeckButtons(Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("showDeck_frontier", "Frontier", Emojis.Frontier));
        buttons.add(Buttons.blue("showDeck_cultural", "Cultural", Emojis.Cultural));
        buttons.add(Buttons.red("showDeck_hazardous", "Hazardous", Emojis.Hazardous));
        buttons.add(Buttons.green("showDeck_industrial", "Industrial", Emojis.Industrial));
        buttons.add(Buttons.gray("showDeck_all", "All Explores"));
        buttons.add(Buttons.blue("showDeck_propulsion", "Propulsion Techs", Emojis.PropulsionTech));
        buttons.add(Buttons.red("showDeck_warfare", "Warfare Techs", Emojis.WarfareTech));
        buttons.add(Buttons.gray("showDeck_cybernetic", "Cybernetic Techs", Emojis.CyberneticTech));
        buttons.add(Buttons.green("showDeck_biotic", "Biotic Techs", Emojis.BioticTech));
        buttons.add(Buttons.green("showDeck_unitupgrade", "Unit Upgrade Techs", Emojis.UnitUpgradeTech));
        buttons.add(Buttons.gray("showDeck_ac", "AC Discards", Emojis.ActionCard));
        buttons.add(Buttons.gray("showDeck_unplayedAC", "Unplayed ACs", Emojis.ActionCard));
        buttons.add(Buttons.gray("showDeck_agenda", "Agenda Discards", Emojis.Agenda));
        buttons.add(Buttons.gray("showDeck_relic", "Relics", Emojis.Relic));
        buttons.add(Buttons.gray("showDeck_unscoredSO", "Unscored SOs", Emojis.SecretObjective));
        buttons.add(Buttons.gray("showObjInfo_both", "All Revealed Objectives in Game", Emojis.Public1));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Pick a deck to show:", buttons);
    }

    @ButtonHandler("showDeck_")
    public static void resolveDeckChoice(Game game, ButtonInteractionEvent event, String buttonID, Player player) {
        String deck = buttonID.replace("showDeck_", "");
        switch (deck) {
            case "ac" -> ShowDiscardActionCards.showDiscard(game, event, false);
            case "agenda" -> AgendaHelper.showDiscards(game, event);
            case "relic" -> RelicShowRemaining.showRemaining(event, false, game, player);
            case "unscoredSO" -> ShowUnScoredSOs.showUnscored(game, event);
            case Constants.PROPULSION, Constants.WARFARE, Constants.CYBERNETIC, Constants.BIOTIC, Constants.UNIT_UPGRADE -> TechShowDeck.displayTechDeck(game, event, deck);
            case Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER, "all" -> {
                List<String> types = new ArrayList<>();
                String msg = "You may click this button to get the full text.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("showTextOfDeck_" + deck, "Show full text"));
                buttons.add(Buttons.red("deleteButtons", "No Thanks"));
                if ("all".equalsIgnoreCase(deck)) { // Show all explores
                    types.add(Constants.CULTURAL);
                    types.add(Constants.INDUSTRIAL);
                    types.add(Constants.HAZARDOUS);
                    types.add(Constants.FRONTIER);
                } else {
                    types.add(deck);
                }
                ExploreInfo.secondHalfOfExpInfo(types, event, player, game, false);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            }
            default -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Deck Button Not Implemented: " + deck);
        }
        deleteMessage(event);
    }

    @ButtonHandler("showTextOfDeck_")
    public static void resolveShowFullTextDeckChoice(Game game, ButtonInteractionEvent event, String buttonID, Player player) {
        String type = buttonID.split("_")[1];
        List<String> types = new ArrayList<>();
        if ("all".equalsIgnoreCase(type)) {
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
            ExploreInfo.secondHalfOfExpInfo(types, event, player, game, false, true);
        } else {
            types.add(type);
            ExploreInfo.secondHalfOfExpInfo(types, event, player, game, false, true);
        }
        deleteMessage(event);
    }

    public static boolean isLawInPlay(Game game, String lawID) {
        if (game.getStoredValue("lawsDisabled").equalsIgnoreCase("yes")) {
            return false;
        }
        return game.getLaws().containsKey(lawID);
    }

    public static boolean isPlayerElected(Game game, Player player, String lawID) {
        if (game.getStoredValue("lawsDisabled").equalsIgnoreCase("yes")) {
            return false;
        }
        if (player == null) {
            return false;
        }
        for (String law : game.getLaws().keySet()) {
            if (lawID.equalsIgnoreCase(law) && game.getLawsInfo().get(law) != null) {
                if (game.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction()) ||
                    game.getLawsInfo().get(law).equalsIgnoreCase(player.getColor())) {
                    return true;
                }
            }
        }
        return false;
    }

    @ButtonHandler("drawStatusACs")
    public static void drawStatusACs(Game game, Player player, ButtonInteractionEvent event) {
        if (game.getCurrentACDrawStatusInfo().contains(player.getFaction())) {
            addReaction(event, true, false,
                "It seems you already drew ACs this status phase. As such, I will not deal you more. Please draw manually if this is a mistake.",
                "");
            return;
        }
        String message = "";
        int amount = 1;
        if (player.hasAbility("autonetic_memory")) {
            if (player.hasTech("nm")) {
                ButtonHelperAbilities.autoneticMemoryStep1(game, player, 2);
            } else {
                ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            }
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(player.getUserID());
            if (player.hasTech("nm")) {
                message = " Neural Motivator has been accounted for.";
                game.drawActionCard(player.getUserID());
                amount = 2;
            }
            if (player.hasAbility("scheming")) {
                message = message
                    + " Scheming has been accounted for, please use blue button inside your card info thread to discard 1 AC.";
                game.drawActionCard(player.getUserID());
                amount = amount + 1;
            }
        }

        StringBuilder messageBuilder = new StringBuilder(message);
        if (isPlayerElected(game, player, "minister_policy") && !player.hasAbility("scheming")) {
            messageBuilder.append(
                " Minister of Policy has been accounted for. If this AC is Political Stability, you cannot play it at this time. ");
            game.drawActionCard(player.getUserID());
            amount++;
        }
        message = messageBuilder.toString();

        if (!player.hasAbility("autonetic_memory")) {
            message = "Drew " + amount + " AC." + message;
        }

        ACInfo.sendActionCardInfo(game, player, event);
        CommanderUnlockCheck.checkPlayer(player, "yssaril");
        if (player.hasAbility("scheming")) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(player, false));
        }

        addReaction(event, true, false, message, "");
        checkACLimit(game, event, player);
        game.setCurrentACDrawStatusInfo(game.getCurrentACDrawStatusInfo() + "_" + player.getFaction());
        ButtonHelperActionCards.checkForAssigningPublicDisgrace(game, player);
        ButtonHelperActionCards.checkForPlayingManipulateInvestments(game, player);
        ButtonHelperActionCards.checkForPlayingSummit(game, player);
    }

    public static void resolveMinisterOfCommerceCheck(Game game, Player player, GenericInteractionCreateEvent event) {
        resolveTACheck(game, player, event);
        for (String law : game.getLaws().keySet()) {
            if ("minister_commrece".equalsIgnoreCase(law) || "absol_minscomm".equalsIgnoreCase(law)) {
                if (game.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())) {
                    MessageChannel channel = event.getMessageChannel();
                    if (game.isFowMode()) {
                        channel = player.getPrivateChannel();
                    }
                    int numOfNeighbors = player.getNeighbourCount();
                    String message = player.getRepresentationUnfogged()
                        + " Minister of Commerce triggered, your TGs have increased due to your " +
                        numOfNeighbors + " neighbors (" + player.getTg() + "->" + (player.getTg() + numOfNeighbors)
                        + ")";
                    player.setTg(numOfNeighbors + player.getTg());
                    ButtonHelperAgents.resolveArtunoCheck(player, game, numOfNeighbors);
                    MessageHelper.sendMessageToChannel(channel, message);
                    ButtonHelperAbilities.pillageCheck(player, game);
                }
            }
        }
    }

    public static int getNumberOfInfantryOnPlanet(String planetName, Game game, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, game);
        UnitKey infKey = Mapper.getUnitKey("gf", colorID);
        int numInf = 0;
        if (unitHolder != null && unitHolder.getUnits() != null) {
            if (unitHolder.getUnits().get(infKey) != null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        return numInf;
    }

    public static int getNumberOfMechsOnPlanet(String planetName, Game game, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, game);
        if (unitHolder == null)
            return 0;
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        int numMechs = 0;
        if (unitHolder.getUnits() != null && unitHolder.getUnits().get(mechKey) != null) {
            numMechs = unitHolder.getUnits().get(mechKey);
        }
        return numMechs;
    }

    public static int resolveOnActivationEnemyAbilities(Game game, Tile activeSystem, Player player,
        boolean justChecking, ButtonInteractionEvent event) {
        int numberOfAbilities = 0;
        if (game.isL1Hero()) {
            return 0;
        }
        String activePlayerident = player.getRepresentation();
        MessageChannel channel = game.getActionsChannel();
        System.out.println("beep");
        Player ghostPlayer = Helper.getPlayerFromUnit(game, "ghost_mech");
        if (!game.isFowMode() && ghostPlayer != null && ghostPlayer != player
            && getNumberOfUnitsOnTheBoard(game, ghostPlayer, "mech", false) > 0
            && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            System.out.println("boop");
            event.getHook().sendMessage(player.getRepresentation() + " This is a reminder that if you are moving via Creuss wormhole, you should first pause and check if the Creuss player wants to use their mech to move that wormhole.").setEphemeral(true).queue();
        }
        if (!game.isFowMode() && ButtonHelper.isLawInPlay(game, "minister_peace")) {
            if (FoWHelper.otherPlayersHaveUnitsInSystem(player, activeSystem, game)) {
                for (Player p2 : game.getRealPlayers()) {
                    if (isPlayerElected(game, p2, "minister_peace")) {
                        if (p2 != player) {
                            List<Button> buttons2 = new ArrayList<>();
                            Button hacanButton = Buttons.gray("ministerOfPeace", "Use Minister of Peace", Emojis.Agenda);
                            buttons2.add(hacanButton);
                            MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(),
                                "Reminder you may use Minister of Peace.", buttons2);
                            event.getHook().sendMessage(player.getRepresentation() + " Reminder you should really check in with the Minister of Peace if this activation has the possibility of being relevant. If you proceed over their window, a rollback may be required.").queue();
                        }
                    }
                }
            }
        }

        for (Player nonActivePlayer : game.getPlayers().values()) {
            if (!nonActivePlayer.isRealPlayer() || nonActivePlayer.isPlayerMemberOfAlliance(player)
                || nonActivePlayer.getFaction().equalsIgnoreCase(player.getFaction())) {
                continue;
            }
            if (game.isFowMode()) {
                channel = nonActivePlayer.getPrivateChannel();
            }
            String fincheckerForNonActive = "FFCC_" + nonActivePlayer.getFaction() + "_";
            String ident = nonActivePlayer.getRepresentationUnfogged();
            // eres
            if (nonActivePlayer.getTechs().contains("ers")
                && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger eres.");
                    }
                    numberOfAbilities++;
                } else {
                    int cTG = nonActivePlayer.getTg();
                    nonActivePlayer.setTg(cTG + 4);
                    MessageHelper.sendMessageToChannel(channel,
                        ident + " gained 4TGs (" + cTG + "->" + nonActivePlayer.getTg() + ")");
                    ButtonHelperAgents.resolveArtunoCheck(nonActivePlayer, game, 4);
                    ButtonHelperAbilities.pillageCheck(nonActivePlayer, game);
                }
            }
            // keleres_fs
            if (nonActivePlayer.hasUnit("keleres_flagship") && activeSystem.getUnitHolders().get("space")
                .getUnitCount(UnitType.Flagship, nonActivePlayer.getColor()) > 0) {
                if (justChecking) {
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel,
                            "Warning: you would have to pay 2 influence to activate this system due to the Artemiris (the Keleres flagship).");
                    }
                    numberOfAbilities++;
                } else {
                    List<Button> buttons = getExhaustButtonsWithTG(game, player, "inf");
                    Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    MessageHelper.sendMessageToChannel(channel,
                        activePlayerident + " you must pay 2 influence due to the Artemiris (the Keleres flagship).");
                    MessageHelper.sendMessageToChannelWithButtons(channel,
                        "Click the names of the planets you wish to exhaust.", buttons);
                }
            }
            // neuroglaive
            if (nonActivePlayer.getTechs().contains("ng")
                && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger Neuroglaive.");
                    }
                    numberOfAbilities++;
                } else {
                    int cTG = player.getFleetCC();
                    player.setFleetCC(cTG - 1);
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel, ident + " you triggered Neuroglaive.");
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, activePlayerident
                        + " lost 1 fleet CC due to Neuroglaive (" + cTG + "->" + player.getFleetCC() + ")");
                    checkFleetInEveryTile(player, game, event);
                }
            }
            if (FoWHelper.playerHasUnitsInSystem(nonActivePlayer, activeSystem)) {
                if (nonActivePlayer.getActionCards().containsKey("fsb")) {
                    MessageHelper.sendMessageToChannel(nonActivePlayer.getCardsInfoThread(),
                        nonActivePlayer.getRepresentation()
                            + " Reminder that you have Forward Supply Base and this is the window for it.");
                }
                if (nonActivePlayer.getPromissoryNotes().containsKey(player.getColor() + "_cf")) {
                    MessageHelper.sendMessageToChannel(nonActivePlayer.getCardsInfoThread(), nonActivePlayer
                        .getRepresentation()
                        + " Reminder that you have the active players Ceasefire and this is the window for it.");
                }
            }

            if (AddCC.hasCC(nonActivePlayer, activeSystem)) {
                if (nonActivePlayer.getActionCards().containsKey("counterstroke")
                    && !isPlayerElected(game, player, "censure")
                    && !isPlayerElected(game, player, "absol_censure")) {
                    List<Button> reverseButtons = new ArrayList<>();
                    String key = "counterstroke";
                    String ac_name = Mapper.getActionCard(key).getName();
                    if (ac_name != null) {
                        reverseButtons.add(Buttons.green(
                            Constants.AC_PLAY_FROM_HAND + nonActivePlayer.getActionCards().get(key)
                                + "_counterstroke_" + activeSystem.getPosition(),
                            "Counterstroke in " + activeSystem.getRepresentationForButtons(game, nonActivePlayer)));
                    }
                    reverseButtons.add(Buttons.red("deleteButtons", "Decline"));
                    String cyberMessage = nonActivePlayer.getRepresentationUnfogged()
                        + " reminder that you may use Counterstroke in "
                        + activeSystem.getRepresentationForButtons(game, nonActivePlayer);
                    MessageHelper.sendMessageToChannelWithButtons(nonActivePlayer.getCardsInfoThread(),
                        cyberMessage, reverseButtons);
                }
            }
            if (nonActivePlayer.ownsUnit("nivyn_mech")
                && getTilesOfPlayersSpecificUnits(game, nonActivePlayer, UnitType.Mech).contains(activeSystem)) {
                Button nivynButton = Buttons.gray("nivynMechStep1_", "Use Nivyn Mech", Emojis.nivyn);
                Button decline = Buttons.red(fincheckerForNonActive + "deleteButtons", "Decline Wound");
                List<Button> buttons = List.of(nivynButton, decline);
                MessageHelper.sendMessageToChannelWithButtons(nonActivePlayer.getCorrectChannel(),
                    ident + " use buttons to resolve potential wound ", buttons);
            }
            if (game.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "arboreccommander")
                && Helper.getProductionValue(nonActivePlayer, game, activeSystem, false) > 0) {
                if (justChecking) {
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger the Dirzuga Rophal, the Arborec commander.");
                    }
                    numberOfAbilities++;
                } else {
                    Button arboCommander = Buttons.green(
                        fincheckerForNonActive + "arboCommanderBuild_" + activeSystem.getPosition(),
                        "Build 1 Unit");
                    Button decline = Buttons.red(fincheckerForNonActive + "deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(arboCommander, decline);
                    MessageHelper.sendMessageToChannelWithButtons(nonActivePlayer.getCorrectChannel(),
                        ident + " use buttons to resolve Dirzuga Rophal, the Arborec commander.", buttons);
                }
            }
            if (doesPlayerHaveFSHere("arborec_flagship", player, activeSystem)) {
                Button arboCommander = Buttons.green(
                    fincheckerForNonActive + "umbatTile_" + activeSystem.getPosition(),
                    "Build 5 Units With Arborec FS");
                Button decline = Buttons.red("deleteButtons", "Decline Build");
                List<Button> buttons = List.of(arboCommander, decline);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    ident + " use buttons to resolve the Arborec Flagship Build.", buttons);

            }
            if (nonActivePlayer.hasLeaderUnlocked("celdaurihero")
                && FoWHelper.playerHasPlanetsInSystem(nonActivePlayer, activeSystem)
                && !activeSystem.isMecatol()) {
                if (justChecking) {
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel,
                            "Warning: you would trigger a chance to play Titus Flavius, the Celdauri Hero.");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Buttons.green(
                        fincheckerForNonActive + "purgeCeldauriHero_" + activeSystem.getPosition(),
                        "Use Celdauri Hero");
                    Button decline = Buttons.red(fincheckerForNonActive + "deleteButtons", "Decline Hero");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(nonActivePlayer.getCorrectChannel(),
                        ident + " use buttons to decide if you want to use Titus Flavius, the Celdauri Hero.", buttons);
                }
            }
            if (nonActivePlayer.hasUnit("mahact_mech") && nonActivePlayer.hasMechInSystem(activeSystem)
                && nonActivePlayer.getMahactCC().contains(player.getColor())
                && !ButtonHelper.isLawInPlay(game, "articles_war")) {
                if (justChecking) {
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger an opportunity for a mahact mech trigger");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Buttons.green(fincheckerForNonActive + "mahactMechHit_" + activeSystem.getPosition() + "_" + player.getColor(), "Return " + player.getColor() + " CC and end their turn");
                    Button decline = Buttons.red(fincheckerForNonActive + "deleteButtons", "Decline To Use Mech");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(nonActivePlayer.getCorrectChannel(), ident + " use buttons to resolve Mahact mech ability ", buttons);
                }
            }
            if (nonActivePlayer.hasTechReady("nf") && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)
                && nonActivePlayer.getStrategicCC() > 0) {
                if (justChecking) {
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel,
                            "Warning: you would trigger an opportunity for nullification field to trigger");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Buttons.green(
                        fincheckerForNonActive + "nullificationField_" + activeSystem.getPosition() + "_"
                            + player.getColor(),
                        "Spend Strat CC and end their turn");
                    Button decline = Buttons.red(fincheckerForNonActive + "deleteButtons",
                        "Decline To Use Nullification Field");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(channel,
                        ident + " use buttons to resolve Nullfication field ", buttons);
                }
            }
            if (game.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "yssarilcommander")
                && FoWHelper.playerHasUnitsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger So Ata, the Yssaril commander.");
                    }
                    numberOfAbilities++;
                } else {
                    Button lookAtACs = Buttons.green(
                        fincheckerForNonActive + "yssarilcommander_ac_" + player.getFaction(),
                        "Look at ACs (" + player.getAc() + ")");
                    Button lookAtPNs = Buttons.green(
                        fincheckerForNonActive + "yssarilcommander_pn_" + player.getFaction(),
                        "Look at PNs (" + player.getPnCount() + ")");
                    Button lookAtSOs = Buttons.green(
                        fincheckerForNonActive + "yssarilcommander_so_" + player.getFaction(),
                        "Look at SOs (" + (player.getSo()) + ")");
                    Button decline = Buttons.red(fincheckerForNonActive + "deleteButtons", "Decline Yssaril commander");
                    List<Button> buttons = List.of(lookAtACs, lookAtPNs, lookAtSOs, decline);
                    MessageHelper.sendMessageToChannelWithButtons(nonActivePlayer.getCorrectChannel(),
                        ident + " use buttons to resolve So Ata, the Yssaril commander. ", buttons);
                }
            }
            List<String> pns = new ArrayList<>(player.getPromissoryNotesInPlayArea());
            for (String pn : pns) {
                Player pnOwner = game.getPNOwner(pn);
                if (pnOwner == null || !pnOwner.isRealPlayer()
                    || !pnOwner.getFaction().equalsIgnoreCase(nonActivePlayer.getFaction())) {
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if (pnModel.getText().contains("return this card")
                    && pnModel.getText().contains("you activate a system that contains")
                    && FoWHelper.playerHasUnitsInSystem(pnOwner, activeSystem)) {
                    if (justChecking) {
                        if (!game.isFowMode()) {
                            MessageHelper.sendMessageToChannel(channel,
                                "Warning: you would trigger the return of a PN (" + pnModel.getName() + ")");
                        }
                        numberOfAbilities++;
                    } else {
                        player.removePromissoryNote(pn);
                        nonActivePlayer.setPromissoryNote(pn);
                        PNInfo.sendPromissoryNoteInfo(game, nonActivePlayer, false);
                        PNInfo.sendPromissoryNoteInfo(game, player, false);
                        MessageHelper.sendMessageToChannel(channel,
                            nonActivePlayer.getFactionEmoji() + " " + pnModel.getName() + " was returned");
                        if (pn.endsWith("_an") && nonActivePlayer.hasLeaderUnlocked("bentorcommander")) {
                            player.setCommoditiesTotal(player.getCommoditiesTotal() - 1);
                        }
                    }

                }
            }
        }
        return numberOfAbilities;
    }

    public static boolean checkForTechSkips(Game game, String planetName) {
        boolean techPresent = false;
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (!planet.getTechSpecialities().isEmpty()) {
            techPresent = true;
        }
        return techPresent;
    }

    public static String getTechSkipAttachments(Game game, String planetName) {
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        if (tile == null) {
            BotLogger.log("Couldn't find tile for " + planetName + " in game " + game.getName());
            return "none";
        }
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        if (unitHolder == null) {
            BotLogger.log("Couldn't find unitholder for " + planetName + " in game " + game.getName());
            return "none";
        }
        Set<String> tokenList = unitHolder.getTokenList();
        if (CollectionUtils.containsAny(tokenList, "attachment_warfare.png", "attachment_cybernetic.png",
            "attachment_biotic.png", "attachment_propulsion.png")) {
            String type = "warfare";
            if (tokenList.contains("attachment_" + type + ".png")) {
                return type;
            }
            type = "cybernetic";
            if (tokenList.contains("attachment_" + type + ".png")) {
                return type;
            }
            type = "propulsion";
            if (tokenList.contains("attachment_" + type + ".png")) {
                return type;
            }
            type = "biotic";
            if (tokenList.contains("attachment_" + type + ".png")) {
                return type;
            }
        }
        return "none";
    }

    public static List<Button> getXxchaAgentReadyButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green("refresh_" + planet + "_" + player.getFaction(),
                "Ready " + Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Delete These Buttons"));
        return buttons;
    }

    public static List<Button> getAllTechsToReady(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getExhaustedTechs()) {
            buttons.add(Buttons.green("biostimsReady_tech_" + tech, "Ready " + Mapper.getTechs().get(tech).getName()));
        }
        return buttons;
    }

    public static void sendAllTechsNTechSkipPlanetsToReady(Game game, GenericInteractionCreateEvent event, Player player, boolean absol) {
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getExhaustedTechs()) {
            buttons.add(Buttons.green("biostimsReady_tech_" + tech, "Ready " + Mapper.getTechs().get(tech).getName()));
        }
        for (String planet : player.getExhaustedPlanets()) {
            if (absol || checkForTechSkips(game, planet)) {
                buttons.add(Buttons.green("biostimsReady_planet_" + planet,
                    "Ready " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Use buttons to select a planet or tech to ready", buttons);
    }

    public static void celdauriRedTech(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttonsToRemoveCC = new ArrayList<>();
        if (player.getStrategicCC() > 0) {
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event,
                Emojis.celdauri + Emojis.WarfareTech + "Emergency Mobilization Protocols");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                + " spent 1 strat CC to remove 1 CC from a system they have a space dock");
        }
        String finChecker = "FFCC_" + player.getFaction() + "_";
        for (Tile tile : getTilesWithYourCC(player, game, event)) {
            if (getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock).contains(tile)) {
                buttonsToRemoveCC.add(Buttons.green(
                    finChecker + "removeCCFromBoard_celdauriRedTech_"
                        + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Use buttons to select the tile you'd like to remove a CC from", buttonsToRemoveCC);
    }

    public static void sendAbsolX89NukeOptions(Game game, GenericInteractionCreateEvent event, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            buttons.add(
                Buttons.green("absolX89Nuke_" + planet, "Nuke " + Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Use buttons to select a planet to nuke", buttons);
    }

    public static List<Button> getPsychoTechPlanets(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            if (checkForTechSkips(game, planet)) {
                buttons.add(Buttons.green("psychoExhaust_" + planet, "Exhaust " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete Buttons"));
        return buttons;
    }

    @ButtonHandler("psychoExhaust_")
    public static void resolvePsychoExhaust(Game game, ButtonInteractionEvent event, Player player, String buttonID) {
        int oldTg = player.getTg();
        player.setTg(oldTg + 1);
        String planet = buttonID.split("_")[1];
        player.exhaustPlanet(planet);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji() + " exhausted " + Helper.getPlanetRepresentation(planet, game) + " and gained 1TG (" + oldTg + "->" + player.getTg() + ") using the Psychoarcheology tech");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        deleteTheOneButton(event);
    }

    @ButtonHandler("biostimsReady_")
    public static void bioStimsReady(Game game, GenericInteractionCreateEvent event, Player player, String buttonID) {
        buttonID = buttonID.replace("biostimsReady_", "");
        String last = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        if (buttonID.contains("tech_")) {
            last = buttonID.replace("tech_", "");
            player.refreshTech(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " readied tech: " + Mapper.getTech(last).getRepresentation(false));
            CommanderUnlockCheck.checkPlayer(player, "kolume");
        } else {
            player.refreshPlanet(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " readied planet: " + Helper.getPlanetRepresentation(last, game));
        }
        ButtonHelper.deleteMessage(event);
    }

    public static boolean isPlayerOverLimit(Game game, Player player) {
        if (player.hasAbility("crafty")) {
            return false;
        }
        int limit = getACLimit(game, player);
        return player.getAc() > limit;
    }

    public static int getACLimit(Game game, Player player) {
        if (player.hasAbility("crafty")) {
            return 999;
        }
        int limit = 7;
        if (ButtonHelper.isLawInPlay(game, "sanctions") && !game.isAbsolMode()) {
            limit = 3;
        }
        if (ButtonHelper.isLawInPlay(game, "absol_sanctions")) {
            limit = 3;
            if (game.getLawsInfo().get("absol_sanctions") != null
                && game.getLawsInfo().get("absol_sanctions").equalsIgnoreCase(player.getFaction())) {
                limit = 5;
            }
        }

        if (player.getTechs().contains("absol_nm")) {
            limit = limit + 3;
        }
        if (player.getRelics().contains("e6-g0_network")) {
            limit = limit + 2;
        }
        return limit;
    }

    public static void checkACLimit(Game game, GenericInteractionCreateEvent event, Player player) {
        int limit = getACLimit(game, player);
        if (isPlayerOverLimit(game, player)) {
            MessageChannel channel = game.getMainGameChannel();
            if (game.isFowMode()) {
                channel = player.getPrivateChannel();
            }
            String ident = player.getRepresentationUnfogged();
            MessageHelper.sendMessageToChannel(channel,
                ident + " you are exceeding the AC hand limit of " + limit
                    + ". Please discard down to the limit. Check your cards info thread for the blue discard buttons. ");
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                ident + " use buttons to discard", ACInfo.getDiscardActionCardButtons(player, false));
        }
    }

    public static void updateMap(Game game, GenericInteractionCreateEvent event) {
        updateMap(game, event, "");
    }

    @ButtonHandler("trade_primary")
    public static void tradePrimary(Game game, GenericInteractionCreateEvent event, Player player) {
        boolean reacted = false;
        if (event instanceof ButtonInteractionEvent e) {
            reacted = true;
            String msg = " gained 3" + Emojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(3) + " and replenished commodities (" + player.getCommodities() + " -> " + player.getCommoditiesTotal() + Emojis.comm + ")";
            ButtonHelper.addReaction(e, false, false, msg, "");
        }
        CommanderUnlockCheck.checkPlayer(player, "hacan");
        ButtonHelperAgents.resolveArtunoCheck(player, game, 3);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperStats.replenishComms(event, game, player, reacted);
    }

    public static void updateMap(Game game, GenericInteractionCreateEvent event, String message) {
        String threadName = game.getName() + "-bot-map-updates";
        List<ThreadChannel> threadChannels = game.getActionsChannel().getThreadChannels();
        MapRenderPipeline.render(game, event, DisplayType.all, fileUpload -> {
            boolean foundSomething = false;
            if (!game.isFowMode()) {
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        foundSomething = true;

                        List<Button> buttonsWeb = new ArrayList<>();
                        if (!game.isFowMode()) {
                            Button linkToWebsite = Button.link(
                                "https://ti4.westaddisonheavyindustries.com/game/" + game.getName(),
                                "Website View");
                            buttonsWeb.add(linkToWebsite);
                            buttonsWeb.add(Buttons.green("gameInfoButtons", "Player Info"));
                        }
                        buttonsWeb.add(Buttons.green("cardsInfo", "Cards Info"));
                        buttonsWeb.add(Buttons.blue("offerDeckButtons", "Show Decks"));
                        buttonsWeb.add(Buttons.gray("showGameAgain", "Show Game"));

                        MessageHelper.sendFileToChannelWithButtonsAfter(threadChannel_, fileUpload, message,
                            buttonsWeb);
                    }
                }
            } else {
                MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), fileUpload);
                foundSomething = true;
            }
            if (!foundSomething) {
                List<Button> buttonsWeb = new ArrayList<>();
                if (!game.isFowMode()) {
                    Button linkToWebsite = Button.link(
                        "https://ti4.westaddisonheavyindustries.com/game/" + game.getName(),
                        "Website View");
                    buttonsWeb.add(linkToWebsite);
                    buttonsWeb.add(Buttons.green("gameInfoButtons", "Player Info"));
                }
                buttonsWeb.add(Buttons.green("cardsInfo", "Cards Info"));
                buttonsWeb.add(Buttons.blue("offerDeckButtons", "Show Decks"));
                buttonsWeb.add(Buttons.gray("showGameAgain", "Show Game"));

                MessageHelper.sendFileToChannelWithButtonsAfter(event.getMessageChannel(), fileUpload, message,
                    buttonsWeb);
            }
        });
    }

    public static boolean nomadHeroAndDomOrbCheck(Player player, Game game, Tile tile) {
        if (game.isDominusOrb() || game.isL1Hero()) {
            return true;
        }
        return player.getLeader("nomadhero").map(Leader::isActive).orElse(false);
    }

    public static int getAllTilesWithAlphaNBetaNUnits(Player player, Game game) {
        int count = 0;
        for (Tile tile : game.getTileMap().values().stream().filter(t -> t.containsPlayersUnits(player)).toList()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)
                && FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition())) {
                count = count + 1;
            }
        }
        return count;
    }

    public static int getNumberOfGroundForces(Player player, UnitHolder uH) {
        int count = 0;
        for (UnitKey uk : uH.getUnits().keySet()) {
            UnitModel model = player.getUnitFromUnitKey(uk);
            if (model != null && model.getIsGroundForce()) {
                count += uH.getUnitCount(uk);
            }
        }
        return count;
    }

    public static int getNumberOfTilesPlayerIsInWithNoPlanets(Game game, Player player) {
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.getPlanetUnitHolders().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfUncontrolledNonLegendaryPlanets(Game game) {
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (Planet plan : tile.getPlanetUnitHolders()) {
                if (plan.getName().contains("mallice")) {
                    continue;
                }
                Planet planet = plan;
                if (planet.isLegendary()) {
                    continue;
                }
                boolean unowned = true;
                for (Player player : game.getRealPlayers()) {
                    if (player.getPlanets().contains(plan.getName())) {
                        unowned = false;
                        break;
                    }
                }
                if (unowned) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int getNumberOfNonHomeAnomaliesPlayerIsIn(Game game, Player player) {
        int count = 0;
        int asteroids = 0;
        int grav = 0;
        int nebula = 0;
        int totalNumber = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.isAnomaly()
                && !tile.isHomeSystem()) {
                if (tile.isGravityRift(game) && grav < 1) {
                    grav = 1;
                }
                if (tile.isNebula() && nebula < 1) {
                    nebula = 1;
                }
                if (tile.isAsteroidField() && asteroids < 1) {
                    asteroids = 1;
                }
                if (!tile.isGravityRift(game) && !tile.isNebula() && !tile.isAsteroidField()) {
                    count = 1;
                }

                totalNumber++;
            }
        }

        return Math.min(count + asteroids + grav + nebula, totalNumber);
    }

    public static int getNumberOfAsteroidsPlayerIsIn(Game game, Player player) {
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isAsteroidField() && FoWHelper.playerHasShipsInSystem(player, tile)) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfXTypePlanets(Player player, Game game, String type, boolean alliance) {
        int count = 0;
        List<String> planets = player.getPlanetsAllianceMode();
        if (!alliance) {
            planets = player.getPlanets();
        }
        for (String planet : planets) {
            Planet p = game.getPlanetsInfo().get(planet);
            if (p != null && p.getPlanetTypes().contains(type)) {
                count++;
            }
        }
        return count;
    }

    public static int checkHighestProductionSystem(Player player, Game game) {
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)
                && Helper.getProductionValue(player, game, tile, false) > count) {
                count = Helper.getProductionValue(player, game, tile, false);
            }
        }
        return count;
    }

    public static int checkHighestCostSystem(Player player, Game game) {
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)
                && checkValuesOfNonFighterShips(player, game, tile) > count) {
                count = checkValuesOfNonFighterShips(player, game, tile);
            }
        }
        return count;
    }

    public static int checkNumberNonFighterShips(Player player, Game game, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff")) {
                count = count + space.getUnits().get(unit);
            }
            if (removedUnit.getBaseType().equalsIgnoreCase("mech") && player.hasUnit("naaz_mech_space")) {
                count = count + space.getUnits().get(unit);
            }
        }
        return count;
    }

    public static int checkNumberShips(Player player, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
            if (removedUnit.getIsShip()) {
                count = count + space.getUnits().get(unit);
            }
        }
        return count;
    }

    public static int checkNumberNonFighterShipsWithoutSpaceCannon(Player player, Game game, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff") && removedUnit.getSpaceCannonDieCount() == 0) {
                count = count + space.getUnits().get(unit);
            }
        }
        return count;
    }

    public static int checkTypesOfNonFighterShips(Player player, Game game, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff")) {
                count = count + 1;
            }
        }
        return count;
    }

    public static int checkValuesOfNonFighterShips(Player player, Game game, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff")) {
                count = count + (int) removedUnit.getCost() * space.getUnits().get(unit);
            }
        }
        return count;
    }

    public static float checkValuesOfUnits(Player player, Game game, Tile tile, String type) {
        float count = 0;
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            for (UnitKey unit : uh.getUnits().keySet()) {
                if (!unit.getColor().equals(player.getColor())) {
                    continue;
                }
                if (player.getUnitsByAsyncID(unit.asyncID()).isEmpty()) {
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
                if (removedUnit.getIsShip() || removedUnit.getIsGroundForce()) {
                    if ((type.equalsIgnoreCase("ground") && removedUnit.getIsShip()) || (type.equalsIgnoreCase("space") && removedUnit.getIsGroundForce())) {
                        continue;
                    }
                    count = count + removedUnit.getCost() * uh.getUnits().get(unit);
                }
            }
        }
        return Math.round(count * 10) / (float) 10.0;
    }

    public static float checkUnitAbilityValuesOfUnits(Player player, Game game, Tile tile) {
        float count = 0;
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            for (UnitKey unit : uh.getUnits().keySet()) {
                if (!unit.getColor().equals(player.getColor())) {
                    continue;
                }
                if (player.getUnitsByAsyncID(unit.asyncID()).isEmpty()) {
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
                float hitChance = 0;
                if (removedUnit.getAfbDieCount(player, game) > 0) {
                    hitChance = (((float) 11.0 - removedUnit.getAfbHitsOn(player, game)) / 10);
                    if (game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander")) {
                        hitChance = 1 - ((1 - hitChance) * (1 - hitChance));
                    }
                    count = count + removedUnit.getAfbDieCount(player, game) * hitChance * uh.getUnits().get(unit);
                }
                if (removedUnit.getSpaceCannonDieCount() > 0) {
                    hitChance = (((float) 11.0 - removedUnit.getSpaceCannonHitsOn()) / 10);
                    if (game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander")) {
                        hitChance = 1 - ((1 - hitChance) * (1 - hitChance));
                    }
                    count = count + removedUnit.getSpaceCannonDieCount() * hitChance * uh.getUnits().get(unit);
                }
                if (removedUnit.getBombardDieCount() > 0) {
                    hitChance = (((float) 11.0 - removedUnit.getBombardHitsOn()) / 10);
                    if (game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander")) {
                        hitChance = 1 - ((1 - hitChance) * (1 - hitChance));
                    }
                    count = count + removedUnit.getBombardDieCount() * hitChance * uh.getUnits().get(unit);
                }
            }
        }
        return Math.round(count * 10) / (float) 10.0;
    }

    public static float checkCombatValuesOfUnits(Player player, Game game, Tile tile, String type) {
        float count = 0;
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            for (UnitKey unit : uh.getUnits().keySet()) {
                if (!unit.getColor().equals(player.getColor())) {
                    continue;
                }
                if (player.getUnitsByAsyncID(unit.asyncID()).isEmpty()) {
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
                float unrelententing = 0;
                if (player.hasAbility("unrelenting")) {
                    unrelententing = (float) 0.1;
                } else if (player.hasAbility("fragile")) {
                    unrelententing = (float) -0.1;
                }
                if (removedUnit.getIsShip() || removedUnit.getIsGroundForce()) {
                    if ((type.equalsIgnoreCase("ground") && removedUnit.getIsShip()) || (type.equalsIgnoreCase("space") && removedUnit.getIsGroundForce())) {
                        continue;
                    }
                    count = count + removedUnit.getCombatDieCount()
                        * (((float) 11.0 - removedUnit.getCombatHitsOn()) / 10 + unrelententing)
                        * uh.getUnits().get(unit);
                }
            }
        }
        return Math.round(count * 10) / (float) 10.0;
    }

    public static int checkHPOfUnits(Player player, Game game, Tile tile, String type) {
        int count = 0;
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            for (UnitKey unit : uh.getUnits().keySet()) {
                if (!unit.getColor().equals(player.getColor())) {
                    continue;
                }
                if (player.getUnitsByAsyncID(unit.asyncID()).isEmpty()) {
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
                if (removedUnit.getIsShip() || removedUnit.getIsGroundForce()) {
                    if ((type.equalsIgnoreCase("ground") && removedUnit.getIsShip()) || (type.equalsIgnoreCase("space") && removedUnit.getIsGroundForce())) {
                        continue;
                    }
                    int sustain = 0;
                    if (removedUnit.getSustainDamage()) {
                        sustain = 1;
                        if (player.hasTech("nes")) {
                            sustain = 2;
                        }
                    }
                    int damagedUnits = 0;
                    if (uh.getUnitDamage() != null && uh.getUnitDamage().get(unit) != null) {
                        damagedUnits = uh.getUnitDamage().get(unit);
                    }
                    int totalUnits = uh.getUnits().get(unit);
                    totalUnits = totalUnits - damagedUnits;
                    count = count + uh.getUnits().get(unit);
                    count = count + sustain * totalUnits;
                }
            }
        }
        return count;
    }

    public static int howManyDifferentDebtPlayerHas(Player player) {
        int count = 0;
        for (String color : player.getDebtTokens().keySet()) {
            if (player.getDebtTokens().get(color) > 0) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfPlanetsWithStructuresNotInHS(Player player, Game game) {
        int count = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Tile tile = game.getTileFromPlanet(planet);
            if (tile != null && tile.isHomeSystem(game)) {
                continue;
            }
            Planet p = (Planet) getUnitHolderFromPlanetName(planet, game);
            if (p != null && (p.getUnitCount(UnitType.Spacedock, player.getColor()) > 0
                || p.getUnitCount(UnitType.Pds, player.getColor()) > 0
                || (p.getUnitCount(UnitType.Mech, player.getColor()) > 0 && player.hasAbility("byssus")))) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfSpacedocksNotInOrAdjacentHS(Player player, Game game) {
        int count = 0;
        Tile hs = player.getHomeSystemTile();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }

            if (game.getTileFromPlanet(planet) == hs
                || FoWHelper.getAdjacentTiles(game, hs.getPosition(), player, false)
                    .contains(game.getTileFromPlanet(planet).getPosition())) {
                continue;
            }
            Planet p = (Planet) getUnitHolderFromPlanetName(planet, game);
            if (p != null && p.getUnitCount(UnitType.Spacedock, player.getColor()) > 0) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfSystemsWithShipsNotAdjacentToHS(Player player, Game game) {
        int count = 0;
        Tile hs = player.getHomeSystemTile();
        if (hs == null) {
            BotLogger.log("not finding a HS for " + player.getFaction() + " in " + game.getName());
            return 0;
        }
        String hsPos = hs.getPosition();
        for (Tile tile : game.getTileMap().values()) {
            if (tile == hs) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(player, tile)
                && !FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false).contains(hsPos)) {
                count++;
            }
        }
        return count;
    }

    public static int getAmountOfSpecificUnitsOnPlanets(Player player, Game game, String unit) {
        int num = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    UnitKey unitID = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
                    if (planet.getUnits().containsKey(unitID)) {
                        num = num + planet.getUnits().get(unitID);
                    }
                }
            }
        }
        return num;
    }

    public static List<String> getPlanetsWithSpecificUnit(Player player, Game game, Tile tile, String unit) {
        List<String> planetsWithUnit = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet) {
                if (planet.getUnits()
                    .containsKey(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor()))) {
                    planetsWithUnit.add(planet.getName());
                }
            }
        }
        return planetsWithUnit;
    }

    public static void doButtonsForSleepers(Player player, Game game, Tile tile, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        if (!player.hasAbility("awaken")) {
            return;
        }
        for (String planet : tile.getPlanetsWithSleeperTokens()) {
            List<Button> planetsWithSleepers = new ArrayList<>();
            planetsWithSleepers.add(Buttons.green(finChecker + "replaceSleeperWith_pds_" + planet,
                "Replace a Sleeper on " + planet + " with 1 PDS."));
            if (getNumberOfUnitsOnTheBoard(game, player, "mech") < 4 && player.hasUnit("titans_mech")) {
                planetsWithSleepers.add(Buttons.green(finChecker + "replaceSleeperWith_mech_" + planet,
                    "Replace a Sleeper on " + planet + " with 1 mech and 1 infantry."));
            }
            planetsWithSleepers.add(Buttons.red("deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Use buttons to resolve the Sleeper token",
                planetsWithSleepers);
        }

    }

    public static List<Button> getButtonsForTurningPDSIntoFS(Player player, Game game, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> planetsWithPDS = new ArrayList<>();
        if (!player.hasUnit("titans_flagship")) {
            return planetsWithPDS;
        }
        if (getNumberOfUnitsOnTheBoard(game, player, "fs") < 1) {
            for (String planet : getPlanetsWithSpecificUnit(player, game, tile, "pds")) {
                planetsWithPDS.add(Buttons.green(finChecker + "replacePDSWithFS_" + planet,
                    "Replace PDS on " + planet + " with the Ouranos (the Ul flagship)."));
            }
        }
        planetsWithPDS.add(Buttons.red("deleteButtons", "Delete these buttons"));
        return planetsWithPDS;
    }

    public static List<Button> getButtonsForRemovingASleeper(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> planetsWithSleepers = new ArrayList<>();
        for (String planet : game.getAllPlanetsWithSleeperTokens()) {
            planetsWithSleepers.add(Buttons.green(finChecker + "removeSleeperFromPlanet_" + planet,
                "Remove the Sleeper on " + planet + "."));
        }
        planetsWithSleepers.add(Buttons.red("deleteButtons", "Delete these buttons"));
        return planetsWithSleepers;
    }

    public static void resolveTitanShenanigansOnActivation(Player player, Game game, Tile tile, ButtonInteractionEvent event) {
        List<Button> buttons = getButtonsForTurningPDSIntoFS(player, game, tile);
        if (buttons.size() > 1) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Use buttons to decide which PDS to replace with the Ouranos (the Ul flagship)", buttons);
        }
        doButtonsForSleepers(player, game, tile, event);
    }

    public static List<Player> getOtherPlayersWithShipsInTheSystem(Player player, Game game, Tile tile) {
        List<Player> playersWithShips = new ArrayList<>();
        for (Player p2 : game.getRealPlayersNDummies()) {
            if (p2 == player)
                continue;
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                playersWithShips.add(p2);
            }
        }
        return playersWithShips;
    }

    public static List<Player> getPlayersWithShipsInTheSystem(Game game, Tile tile) {
        List<Player> playersWithShips = new ArrayList<>();
        for (Player p2 : game.getRealPlayersNNeutral()) {
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                playersWithShips.add(p2);
            }
        }
        return playersWithShips;
    }

    public static List<Player> getOtherPlayersWithUnitsInTheSystem(Player player, Game game, Tile tile) {
        List<Player> playersWithShips = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player)
                continue;
            if (FoWHelper.playerHasUnitsInSystem(p2, tile)) {
                playersWithShips.add(p2);
            }
        }
        return playersWithShips;
    }

    public static List<Player> getPlayersWithUnitsInTheSystem(Game game, Tile tile) {
        List<Player> playersWithShips = new ArrayList<>();
        for (Player player : game.getRealPlayersNNeutral()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                playersWithShips.add(player);
            }
        }
        return playersWithShips;
    }

    public static List<Player> getPlayersWithUnitsOnAPlanet(Game game, Tile tile, String planet) {
        List<Player> playersWithShips = new ArrayList<>();
        for (Player p2 : game.getPlayers().values()) {
            if (FoWHelper.playerHasUnitsOnPlanet(p2, tile, planet)) {
                playersWithShips.add(p2);
            }
        }
        return playersWithShips;
    }

    public static List<Tile> getTilesWithYourCC(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Tile> tilesWithCC = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (AddCC.hasCC(event, player.getColor(), tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                tilesWithCC.add(tile);
            }
        }
        return tilesWithCC;
    }

    public static void resolveRemovingYourCC(Player player, Game game, GenericInteractionCreateEvent event, String buttonID) {
        buttonID = buttonID.replace("removeCCFromBoard_", "");
        String whatIsItFor = buttonID.split("_")[0];
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String tileRep = tile.getRepresentationForButtons(game, player);
        String ident = player.getFactionEmojiOrColor();
        String msg = ident + " removed CC from " + tileRep;
        if (whatIsItFor.contains("mahactAgent")) {
            String faction = whatIsItFor.replace("mahactAgent", "");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            player = game.getPlayerFromColorOrFaction(faction);
            msg = player.getRepresentationUnfogged() + " this is a notice that " + msg + " using "
                + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        }

        RemoveCC.removeCC(event, player.getColor(), tile, game);

        String finChecker = "FFCC_" + player.getFaction() + "_";
        if ("mahactCommander".equalsIgnoreCase(whatIsItFor)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " used Mahact Commander and reduced their tactic CCs from "
                + player.getTacticalCC() + " to " + (player.getTacticalCC() - 1) + ". This ends their turn, leaving a window open for end of turn abilities.");
            player.setTacticalCC(player.getTacticalCC() - 1);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> conclusionButtons = new ArrayList<>();
            Button endTurn = Buttons.red(finChecker + "turnEnd", "End Turn");
            conclusionButtons.add(endTurn);
            if (getEndOfTurnAbilities(player, game).size() > 1) {
                conclusionButtons.add(Buttons.blue("endOfTurnAbilities",
                    "Do End Of Turn Ability (" + (getEndOfTurnAbilities(player, game).size() - 1) + ")"));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn.",
                conclusionButtons);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        }
        if ("warfare".equalsIgnoreCase(whatIsItFor)) {
            List<Button> redistributeButton = new ArrayList<>();
            Button redistribute = Buttons.green("FFCC_" + player.getFaction() + "_" + "redistributeCCButtons",
                "Redistribute & Gain CCs");
            Button deleButton = Buttons.red("FFCC_" + player.getFaction() + "_" + "deleteButtons",
                "Delete These Buttons");
            redistributeButton.add(redistribute);
            redistributeButton.add(deleButton);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                player.getRepresentation() + " click this after picking up a CC.", redistributeButton);
        }
    }

    public static void resolveMahactMechAbilityUse(Player mahact, Player target, Game game, Tile tile, ButtonInteractionEvent event) {
        mahact.removeMahactCC(target.getColor());
        if (!game.isNaaluAgent()) {
            if (!game.getStoredValue("absolLux").isEmpty()) {
                target.setTacticalCC(target.getTacticalCC() + 1);
            }
            target.setTacticalCC(target.getTacticalCC() - 1);
            AddCC.addCC(event, target.getColor(), tile);

        }

        MessageHelper.sendMessageToChannel(mahact.getCorrectChannel(),
            mahact.getRepresentationUnfogged() + " the " + target.getColor()
                + " CC has been removed from your fleet pool");
        ButtonHelper.checkFleetInEveryTile(mahact, game, event);
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Buttons.red(target.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, game).size() > 1) {
            conclusionButtons.add(Buttons.blue("endOfTurnAbilities",
                "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, game).size() - 1) + ")"));
        }
        List<Button> buttons = getGainCCButtons(target);
        String trueIdentity = target.getRepresentationUnfogged();
        String message2 = trueIdentity + "! Your current CCs are " + target.getCCRepresentation()
            + ". Use buttons to gain CCs";
        game.setStoredValue("originalCCsFor" + target.getFaction(), target.getCCRepresentation());
        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), message2, buttons);
        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), target.getRepresentation(true,
            true)
            + " You've been hit by"
            + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "")
            + " the Mahact Starlancer mech ability. You gain 1 CC to any command pool. Then, use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        deleteMessage(event);
    }

    public static void resolveNullificationFieldUse(Player mahact, Player target, Game game, Tile tile, ButtonInteractionEvent event) {
        mahact.setStrategicCC(mahact.getStrategicCC() - 1);
        mahact.exhaustTech("nf");
        ButtonHelperCommanders.resolveMuaatCommanderCheck(mahact, game, event,
            Emojis.Xxcha + Emojis.CyberneticTech + "Nullification Field");
        if (!game.isNaaluAgent()) {
            if (!game.getStoredValue("absolLux").isEmpty()) {
                target.setTacticalCC(target.getTacticalCC() + 1);
            }
            target.setTacticalCC(target.getTacticalCC() - 1);
            AddCC.addCC(event, target.getColor(), tile);
        }
        MessageHelper.sendMessageToChannel(mahact.getCorrectChannel(),
            mahact.getRepresentationUnfogged() + " you have spent a strategy CC");
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Buttons.red(target.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, game).size() > 1) {
            conclusionButtons.add(Buttons.blue("endOfTurnAbilities",
                "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, game).size() - 1) + ")"));
        }

        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), target
            .getRepresentationUnfogged()
            + " You've been hit by"
            + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "")
            + " *Nullification Field*. 1 CC has been placed from your tactic pool in the system and your turn has been ended. Use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        deleteMessage(event);

    }

    @ButtonHandler("ministerOfPeace")
    public static void resolveMinisterOfPeace(Player minister, Game game, ButtonInteractionEvent event) {
        Player target = game.getActivePlayer();

        if (target == null || target == minister) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Target player not found");
            return;
        }
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Active system not found");
            return;
        }
        boolean success = game.removeLaw(game.getLaws().get("minister_peace"));
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Minister of Peace Law removed");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
            return;
        }

        if (!game.isNaaluAgent()) {
            if (!AddCC.hasCC(target, tile)) {
                if (!game.getStoredValue("absolLux").isEmpty()) {
                    target.setTacticalCC(target.getTacticalCC() + 1);
                }
                target.setTacticalCC(target.getTacticalCC() - 1);
                AddCC.addCC(event, target.getColor(), tile);
            }
        }
        MessageHelper.sendMessageToChannel(minister.getCorrectChannel(), minister.getRepresentationUnfogged() + " you have used the Minister of Peace agenda");
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Buttons.red(target.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, game).size() > 1) {
            conclusionButtons.add(Buttons.blue("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, game).size() - 1) + ")"));
        }

        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), target.getRepresentationUnfogged()
            + " You've been hit by" + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "")
            + " *Minister of Peace*. 1 CC has been placed from your tactic pool in the system and your turn has been ended. Use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        deleteTheOneButton(event);

    }

    public static int checkNetGain(Player player, String ccs) {
        int netGain;
        int oldTactic = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/") + 1);
        int oldFleet = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/") + 1);
        int oldStrat = Integer.parseInt(ccs);

        netGain = (player.getTacticalCC() - oldTactic) + (player.getFleetCC() - oldFleet)
            + (player.getStrategicCC() - oldStrat);
        return netGain;
    }

    public static void resetCCs(Player player, String ccs) {
        int oldTactic = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/") + 1);
        int oldFleet = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/") + 1);
        int oldStrat = Integer.parseInt(ccs);
        player.setTacticalCC(oldTactic);
        player.setStrategicCC(oldStrat);
        player.setFleetCC(oldFleet);

    }

    public static List<Button> getButtonsToRemoveYourCC(Player player, Game game, GenericInteractionCreateEvent event,
        String whatIsItFor) {
        List<Button> buttonsToRemoveCC = new ArrayList<>();
        String finChecker = "FFCC_" + player.getFaction() + "_";
        for (Tile tile : getTilesWithYourCC(player, game, event)) {
            if (whatIsItFor.contains("kjal")) {
                String pos = whatIsItFor.split("_")[1];
                if (!pos.equalsIgnoreCase(tile.getPosition())
                    && !FoWHelper.getAdjacentTiles(game, pos, player, false).contains(tile.getPosition())) {
                    continue;
                }
            }
            buttonsToRemoveCC.add(
                Buttons.green(
                    finChecker + "removeCCFromBoard_" + whatIsItFor.replace("_", "") + "_" + tile.getPosition(),
                    "Remove CC from " + tile.getRepresentationForButtons(game, player)));
        }
        return buttonsToRemoveCC;
    }

    public static List<Button> getButtonsToSwitchWithAllianceMembers(Player player, Game game, boolean fromButton) {
        List<Button> buttonsToRemoveCC = new ArrayList<>();
        for (Player player2 : game.getRealPlayers()) {
            if (player.getAllianceMembers().contains(player2.getFaction())) {
                buttonsToRemoveCC.add(
                    Buttons.green("swapToFaction_" + player2.getFaction(), "Swap to " + player2.getFaction()).withEmoji(Emoji.fromFormatted(player2.getFactionEmoji())));
            }
        }
        if (fromButton) {
            buttonsToRemoveCC.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        }

        return buttonsToRemoveCC;
    }

    public static List<Button> getButtonsToExploreAllPlanets(Player player, Game game) {
        return getButtonsToExploreAllPlanets(player, game, false);
    }

    public static List<Button> getButtonsToExploreAllPlanets(Player player, Game game, boolean onlyReady) {
        List<Button> buttons = new ArrayList<>();
        for (String plan : player.getPlanetsAllianceMode()) {
            if (onlyReady && player.getExhaustedPlanets().contains(plan)) {
                continue;
            }
            Planet planetUnit = game.getPlanetsInfo().get(plan);
            Planet planetReal = planetUnit;
            if (planetReal != null && planetReal.getOriginalPlanetType() != null) {
                List<Button> planetButtons = getPlanetExplorationButtons(game, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static List<Button> getButtonsToExploreReadiedPlanets(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String plan : player.getPlanetsAllianceMode()) {
            Planet planetReal = game.getPlanetsInfo().get(plan);
            if (planetReal != null && planetReal.getOriginalPlanetType() != null
                && !player.getExhaustedPlanets().contains(planetReal.getName())) {
                List<Button> planetButtons = getPlanetExplorationButtons(game, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static List<Button> getButtonsForAgentSelection(Game game, String agent) {
        return AgendaHelper.getPlayerOutcomeButtons(game, null, "exhaustAgent_" + agent, null);
    }

    @ButtonHandler("deleteMessage_") // deleteMessage_{Optional String to send to the event channel after}
    public static void deleteMessage(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent bevent) {
            bevent.getMessage();
            bevent.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            bevent.getButton();
            String message = bevent.getButton().getId().replace("deleteMessage_", "");
            if (!message.isEmpty()) {
                // MessageHelper.sendMessageToEventChannel(event, message);
            }
        }
    }

    @ButtonHandler("editMessage_") // editMessage_{Optional String to edit the message to}
    public static void editMessage(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent bevent) {
            bevent.getMessage();
            bevent.getButton();
            String message = bevent.getButton().getId().replace("editMessage_", "");
            if (!message.isEmpty()) {
                // MessageHelper.sendMessageToEventChannel(event, message);
            }
            // bevent.editMessage(message).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static void deleteAllButtons(ButtonInteractionEvent event) {
        if (event == null) {
            return;
        } else {
            event.getMessage();
        }
        event.editComponents(Collections.emptyList()).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void deleteTheOneButton(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent bevent) {
            bevent.getMessage();
            deleteTheOneButton(bevent, bevent.getButton().getId(), true);
        }
    }

    public static void deleteButtonsWithPartialID(GenericInteractionCreateEvent event, String partialID) {
        if (event instanceof ButtonInteractionEvent bevent) {
            boolean containsRealButton = false;
            List<Button> buttons = new ArrayList<>(bevent.getMessage().getButtons());
            List<Button> newButtons = new ArrayList<>();
            for (Button button : buttons) {
                if (button.getId().contains(partialID)) {
                    // skip
                } else {
                    if (!button.getId().contains("deleteButtons") && !button.getId().contains("ultimateUndo")) {
                        containsRealButton = true;
                    }
                    newButtons.add(button);
                }
            }
            if (containsRealButton) {
                String msgText = bevent.getMessage().getContentRaw();
                if (StringUtils.isBlank(msgText)) msgText = "*edited*";
                MessageHelper.editMessageWithButtons(bevent, bevent.getMessage().getContentRaw(), newButtons);
            } else {
                ButtonHelper.deleteMessage(bevent);
            }
        }
    }

    public static void deleteTheOneButton(ButtonInteractionEvent event, String buttonID, boolean deleteMsg) {
        if (event == null) {
            return;
        } else {
            event.getMessage();
        }
        String exhaustedMessage = event.getMessage().getContentRaw();
        if ("".equalsIgnoreCase(exhaustedMessage)) {
            exhaustedMessage = "Updated";
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        int buttons = 0;
        String id = "br";
        String id2 = "br";
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            List<ItemComponent> buttonRow2 = new ArrayList<>(buttonRow);
            int buttonIndex = buttonRow.indexOf(event.getButton());
            int counter = 0;
            for (ItemComponent item : buttonRow2) {
                if (item instanceof Button b) {
                    if (b.getId().equalsIgnoreCase(buttonID)) {
                        buttonIndex = counter;
                    }
                    counter++;
                }
            }

            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (!buttonRow.isEmpty()) {

                buttons = buttons + buttonRow.size();
                if (buttonRow.get(0) instanceof Button butt) {
                    id = butt.getId();
                    if (buttonRow.size() == 2) {
                        if (buttonRow.get(1) instanceof Button butt2) {
                            id2 = butt2.getId();
                        }

                    }
                }
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        if (!actionRow2.isEmpty() && deleteMsg) {
            if (exhaustedMessage.contains("buttons to do an end of turn ability") && buttons == 1) {
                deleteMessage(event);
            } else {
                if ((buttons == 1 && id.contains("deleteButtons")) || (buttons == 1 && id.contains("ultimateUndo"))
                    || (buttons == 2 && id.contains("deleteButtons") && id2.contains("ultimateUndo"))) {
                    deleteMessage(event);
                } else {
                    event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue(Consumers.nop(), BotLogger::catchRestError);
                }
            }
        } else {
            if (deleteMsg) {
                deleteMessage(event);
            } else if (!actionRow2.isEmpty()) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
    }

    public static void findOrCreateThreadWithMessage(Game game, String threadName, String message) {
        TextChannel channel = game.getMainGameChannel();
        Helper.checkThreadLimitAndArchive(game.getGuild());
        TextChannel textChannel = channel;
        // Use existing thread, if it exists
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                MessageHelper.sendMessageToChannel(threadChannel_, message);
                return;
            }
        }
        String finalThreadName = threadName;
        List<ThreadChannel> hiddenThreadChannels = textChannel.retrieveArchivedPublicThreadChannels().complete();
        for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                MessageHelper.sendMessageToChannel(threadChannel_, message);
                return;
            }
        }
        String msg = "New Thread for " + threadName;

        channel.sendMessage(msg).queue(m -> {
            ThreadChannel.AutoArchiveDuration duration = ThreadChannel.AutoArchiveDuration.TIME_3_DAYS;
            if (finalThreadName.contains("undo-log"))
                duration = ThreadChannel.AutoArchiveDuration.TIME_1_HOUR;

            ThreadChannelAction threadChannel = textChannel.createThreadChannel(finalThreadName, m.getId());
            threadChannel = threadChannel.setAutoArchiveDuration(duration);
            threadChannel.queue(tc -> MessageHelper.sendMessageToChannel(tc, message + game.getPing()));
        });
    }

    public static void saveButtons(ButtonInteractionEvent event, Game game, Player player) {
        game.setSavedButtons(new ArrayList<>());
        String exhaustedMessage = event.getMessage().getContentRaw();
        if ("".equalsIgnoreCase(exhaustedMessage)) {
            exhaustedMessage = "Updated";
        }
        game.setSavedChannelID(event.getMessageChannel().getId());
        game.setSavedMessage(exhaustedMessage);
        List<Button> buttons = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            for (ItemComponent but : buttonRow) {
                Button button = (Button) but;
                if (button != null) {
                    buttons.add(button);
                }
            }
        }

        for (Button button : buttons) {
            if (button.getId() == null || button.getId().contains("ultimateUndo")) {
                continue;
            }
            String builder = player.getFaction() + ";" + button.getId() + ";" + button.getLabel() + ";"
                + button.getStyle();
            if (button.getEmoji() != null && !"".equalsIgnoreCase(button.getEmoji().toString())) {
                builder = builder + ";" + button.getEmoji().toString();
            }
            game.saveButton(builder);
        }
    }

    public static List<Button> getSavedButtons(Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String buttonString : game.getSavedButtons()) {
            int x = 0;
            if (game.getPlayerFromColorOrFaction(buttonString.split(";")[x]) != null) {
                x = 1;
            }
            String id = buttonString.split(";")[x];
            String label = buttonString.split(";")[x + 1];
            if (label.isEmpty()) {
                label = "Edited";
            }
            String style = buttonString.split(";")[x + 2].toLowerCase();
            String emoji = "";
            if (StringUtils.countMatches(buttonString, ";") > x + 2) {
                emoji = buttonString.split(";")[x + 3];
                String name = StringUtils.substringBetween(emoji, ":", "(");
                String emojiID = StringUtils.substringBetween(emoji, "=", ")");
                emoji = "<:" + name + ":" + emojiID + ">";
            }
            if ("success".equalsIgnoreCase(style)) {
                if (!emoji.isEmpty()) {
                    buttons.add(Buttons.green(id, label, emoji));
                } else {
                    buttons.add(Buttons.green(id, label));
                }
            } else if ("danger".equalsIgnoreCase(style)) {
                if (!emoji.isEmpty()) {
                    buttons.add(Buttons.red(id, label, emoji));
                } else {
                    buttons.add(Buttons.red(id, label));
                }
            } else if ("secondary".equalsIgnoreCase(style)) {
                if (!emoji.isEmpty()) {
                    buttons.add(Buttons.gray(id, label, emoji));
                } else {
                    buttons.add(Buttons.gray(id, label));
                }
            } else {
                if (!emoji.isEmpty()) {
                    buttons.add(Buttons.blue(id, label, emoji));
                } else {
                    buttons.add(Buttons.blue(id, label));
                }
            }
        }
        return buttons;
    }

    public static boolean doesPlayerOwnAPlanetInThisSystem(Tile tile, Player player, Game game) {
        for (String planet : player.getPlanets()) {
            Tile t2 = null;
            try {
                t2 = game.getTileFromPlanet(planet);
            } catch (Error ignored) {

            }
            if (t2 != null && t2.getPosition().equalsIgnoreCase(tile.getPosition())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTileLegendary(Tile tile, Game game) {
        for (UnitHolder planet : tile.getUnitHolders().values()) {
            if (planet instanceof Planet planetHolder) {
                if (planetHolder.isLegendary()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isPlanetLegendaryOrTechSkip(String planetName, Game game) {
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, game);
        Planet planetHolder = (Planet) unitHolder;
        if (planetHolder == null)
            return false;
        return planetHolder.isLegendary() || checkForTechSkips(game, planetName);
    }

    public static boolean isPlanetTechSkip(String planetName, Game game) {
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, game);
        Planet planetHolder = (Planet) unitHolder;
        if (planetHolder == null)
            return false;
        return checkForTechSkips(game, planetName);
    }

    public static boolean isPlanetLegendaryOrHome(String planetName, Game game, boolean onlyIncludeYourHome, Player p1) {
        if (planetName.toLowerCase().contains("custodia")) {
            return true;
        }
        PlanetModel planetModel = Mapper.getPlanet(planetName);
        if (planetModel != null && planetModel.isLegendary()) {
            return true;
        }
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, game);
        Planet planetHolder = (Planet) unitHolder;
        Tile tile = game.getTileFromPlanet(planetName);
        if (planetHolder == null || tile == null)
            return false;

        boolean hasAbility = planetHolder.isLegendary();
        if (tile.isHomeSystem()) {
            if (onlyIncludeYourHome && p1 != null && p1.getPlayerStatsAnchorPosition() != null) {
                if (game.getTileFromPlanet(planetName).getPosition()
                    .equalsIgnoreCase(p1.getPlayerStatsAnchorPosition())) {
                    return true;
                }
                if ("ghost".equalsIgnoreCase(p1.getFaction()) && "creuss".equalsIgnoreCase(planetName)) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return hasAbility;
    }

    public static int checkFleetInEveryTile(Player player, Game game, GenericInteractionCreateEvent event) {
        int highest = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                highest = Math.max(highest, checkFleetAndCapacity(player, game, tile, event));
            }
        }
        Helper.isCCCountCorrect(event, game, player.getColor());
        return highest;
    }

    public static int checkFleetAndCapacity(Player player, Game game, Tile tile, GenericInteractionCreateEvent event) {
        if (tile.getRepresentation() == null || "null".equalsIgnoreCase(tile.getRepresentation())) {
            return 0;
        }
        if (tile.getRepresentation().toLowerCase().contains("nombox")) {
            return 0;
        }
        if (!game.isCcNPlasticLimit()) {
            return 0;
        }
        int armadaValue = 0;
        if (player == null) {
            return 0;
        }
        if (player.hasAbility("armada")) {
            armadaValue = 2;
        }
        if (player.hasTech("dsghotg") && tile == player.getHomeSystemTile()) {
            armadaValue = armadaValue + 3;
        }
        int fleetCap = (player.getFleetCC()
            + armadaValue
            + player.getMahactCC().size()
            + tile.getFleetSupplyBonusForPlayer(player)) * 2; // fleetCap is double to more easily deal with half-capacity, e.g., Naalu Fighter II
        if (player.getLeader("letnevhero").map(Leader::isActive).orElse(false)) {
            fleetCap += 1000;
        }
        int capacity = 0;
        int numInfNFightersNMechs = 0;
        int numOfCapitalShips = 0;
        int fightersIgnored = 0;
        int numFighter2s = 0;
        int numFighter2sFleet = 0;
        boolean capacityViolated = false;
        boolean fleetSupplyViolated = false;

        for (UnitHolder capChecker : tile.getUnitHolders().values()) {
            if (capChecker.getUnitCount(UnitType.CabalSpacedock, player.getColor()) > 0) {
                String colorID = Mapper.getColorID(player.getColor());
                UnitKey csdKey = Mapper.getUnitKey("csd", colorID);
                UnitKey sdKey = Mapper.getUnitKey("sd", colorID);
                capChecker.removeUnit(csdKey, 1);
                capChecker.addUnit(sdKey, 1);
                BotLogger.log("Removing csd in game " + game.getName());
                // new RemoveUnits().unitParsing(event, player.getColor(), tile, "csd
                // "+capChecker.getName(), game);
                // new AddUnits().unitParsing(event, player.getColor(), tile, "sd
                // "+capChecker.getName(), game);
            }
            Map<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(capChecker, player);
            for (UnitModel unit : unitsByQuantity.keySet()) {
                if ("space".equalsIgnoreCase(capChecker.getName())) {
                    capacity += unit.getCapacityValue() * unitsByQuantity.get(unit);
                }
                // System.out.println(unit.getBaseType());
                if ("spacedock".equalsIgnoreCase(unit.getBaseType())
                    && !"space".equalsIgnoreCase(capChecker.getName())) {
                    if (player.ownsUnit("cabal_spacedock")) {
                        fightersIgnored += 6;
                    } else if (player.ownsUnit("cabal_spacedock2")) {
                        fightersIgnored += 12;
                    } else {
                        if (!player.hasUnit("mykomentori_spacedock") && !player.hasUnit("mykomentori_spacedock2")) {
                            fightersIgnored += 3;
                        }

                    }
                }
            }
            for (Player p2 : game.getRealPlayers()) {
                if (player.getAllianceMembers().contains(p2.getFaction())) {
                    Map<UnitModel, Integer> unitsByQuantity2 = CombatHelper.GetAllUnits(capChecker, p2);
                    for (UnitModel unit : unitsByQuantity2.keySet()) {
                        if ("space".equalsIgnoreCase(capChecker.getName())) {
                            capacity += unit.getCapacityValue() * unitsByQuantity2.get(unit);
                        }
                        // System.out.println(unit.getBaseType());
                        if ("spacedock".equalsIgnoreCase(unit.getBaseType())
                            && !"space".equalsIgnoreCase(capChecker.getName())) {
                            if (p2.ownsUnit("cabal_spacedock")) {
                                fightersIgnored += 6;
                            } else if (p2.ownsUnit("cabal_spacedock2")) {
                                fightersIgnored += 12;
                            } else {
                                if (!p2.hasUnit("mykomentori_spacedock") && !p2.hasUnit("mykomentori_spacedock2")) {
                                    fightersIgnored += 3;
                                }

                            }
                        }
                    }
                }
            }
            if (capChecker.getUnitCount(UnitType.PlenaryOrbital, player.getColor()) > 0) {
                fightersIgnored += 8;
            }
        }
        // System.out.println(fightersIgnored);
        UnitHolder combatOnHolder = tile.getUnitHolders().get("space");
        List<String> unitTypesCounted = new ArrayList<>();
        Map<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(combatOnHolder, player);
        for (UnitModel unit : unitsByQuantity.keySet()) {
            if (!unitTypesCounted.contains(unit.getBaseType())) {
                if ("fighter".equalsIgnoreCase(unit.getBaseType()) || "infantry".equalsIgnoreCase(unit.getBaseType())
                    || "mech".equalsIgnoreCase(unit.getBaseType())) {

                    if ("fighter".equalsIgnoreCase(unit.getBaseType()) && player.hasFF2Tech()) {
                        numFighter2s += unitsByQuantity.get(unit) - fightersIgnored;
                        if (numFighter2s < 0) {
                            numFighter2s = 0;
                        }
                    }
                    if ("fighter".equalsIgnoreCase(unit.getBaseType())) {
                        int numCountedFighters = unit.getCapacityUsed() * unitsByQuantity.get(unit) - fightersIgnored;
                        if (numCountedFighters < 0) {
                            numCountedFighters = 0;
                        }
                        numInfNFightersNMechs += numCountedFighters;
                    } else {
                        numInfNFightersNMechs += unit.getCapacityUsed() * unitsByQuantity.get(unit);
                    }
                    unitTypesCounted.add(unit.getBaseType());

                } else {
                    if (unit.getIsShip()) {
                        if (player.hasAbility("capital_fleet") && unit.getBaseType().contains("destroyer")) {
                            numOfCapitalShips += unitsByQuantity.get(unit);
                        } else {
                            numOfCapitalShips += unitsByQuantity.get(unit) * 2;
                        }
                        unitTypesCounted.add(unit.getBaseType());
                    }
                }
            }

        }
        if (numOfCapitalShips > fleetCap) {
            fleetSupplyViolated = true;
        }
        if (capacity > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "vayleriancommander")
            && tile.getPosition().equals(game.getActiveSystem())
            && player == game.getActivePlayer()) {
            capacity = capacity + 2;
        }
        if (numInfNFightersNMechs > capacity) {
            if (numInfNFightersNMechs - numFighter2s > capacity) {
                capacityViolated = true;
            } else {
                numFighter2s = numInfNFightersNMechs - capacity;
                if (player.hasTech("hcf2")) {
                    numFighter2sFleet = numFighter2s;
                } else {
                    numFighter2sFleet = numFighter2s * 2;
                }
                if (numFighter2sFleet + numOfCapitalShips > fleetCap) {
                    fleetSupplyViolated = true;
                }
            }
        }
        if (numOfCapitalShips > 8 && !fleetSupplyViolated) {
            CommanderUnlockCheck.checkPlayer(player, "letnev");
        }
        if (player.hasAbility("flotilla")) {
            int numInf = tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player.getColor());
            if (numInf > ((numOfCapitalShips
                + tile.getUnitHolders().get("space").getUnitCount(UnitType.Destroyer, player.getColor())) / 2)) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation()
                        + " reminder that your Flotilla ability says you can't have more infantry than non-fighter ships in the space area of a system. You seem to be violating this in "
                        + tile.getRepresentationForButtons(game, player));
            }
        }
        String message = player.getRepresentationUnfogged();
        if (fleetSupplyViolated) {
            message += " You are violating fleet supply in tile " + tile.getRepresentation()
                + ". Specifically, you have " + fleetCap / 2
                + " fleet supply in this system, and you currently are filling "
                + (numFighter2sFleet + numOfCapitalShips + 1) / 2
                + " of that. ";
        }
        if (capacityViolated) {
            message += " You are violating carrying capacity in tile " + tile.getRepresentation()
                + ". Specifically, you have " + capacity
                + " capacity, and you are trying to carry "
                + (numInfNFightersNMechs - numFighter2s) + " things";
        }
        // System.out.printf("%d %d %d %d%n", fleetCap, numOfCapitalShips, capacity,
        // numInfNFightersNMechs);
        if (capacityViolated || fleetSupplyViolated) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.blue("getDamageButtons_" + tile.getPosition() + "_remove",
                "Remove units in " + tile.getRepresentationForButtons(game, player)));
            buttons.add(Buttons.red("deleteButtons",
                "Dismiss These Buttons"));

            FileUpload systemWithContext = new TileGenerator(game, event, null, 0, tile.getPosition()).createFileUpload();
            MessageHelper.sendFileToChannelWithButtonsAfter(player.getCorrectChannel(), systemWithContext, message, buttons);

        }
        return (numFighter2sFleet + numOfCapitalShips + 1) / 2;
    }

    public static List<Tile> getAllTilesWithProduction(Game game, Player player, GenericInteractionCreateEvent event) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder capChecker : tile.getUnitHolders().values()) {
                Map<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(capChecker, player);
                for (UnitModel unit : unitsByQuantity.keySet()) {
                    if (unit.getProductionValue() > 0) {
                        if (!tiles.contains(tile)) {
                            tiles.add(tile);
                        }
                    }
                }
            }
        }
        return tiles;
    }

    public static List<String> getAllPlanetsAdjacentToTileNotOwnedByPlayer(Tile tile, Game game, Player player) {
        List<String> planets = new ArrayList<>();
        for (String pos2 : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false)) {
            Tile tile2 = game.getTileByPosition(pos2);
            for (UnitHolder planetUnit2 : tile2.getUnitHolders().values()) {
                if ("space".equalsIgnoreCase(planetUnit2.getName())) {
                    continue;
                }
                Planet planetReal2 = (Planet) planetUnit2;
                String planet2 = planetReal2.getName();
                if (!player.getPlanetsAllianceMode().contains(planet2)) {
                    planets.add(planet2);
                }
            }
        }
        return planets;
    }

    public static List<Button> customRexLegendary(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        Tile rex = game.getMecatolTile();
        List<String> planetsToCheck = getAllPlanetsAdjacentToTileNotOwnedByPlayer(rex, game, player);
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            for (String planet2 : p2.getPlanetsAllianceMode()) {
                PlanetModel mod = Mapper.getPlanet(planet2);
                if (mod.getLegendaryAbilityName() != null && !mod.getLegendaryAbilityName().isEmpty()
                    && !planetsToCheck.contains(planet2)) {
                    planetsToCheck.add(planet2);
                }
            }
        }
        for (String planet : planetsToCheck) {
            Planet planetUnit2 = game.getPlanetsInfo().get(planet);
            if (planetUnit2 != null) {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    int numMechs = 0;
                    int numInf = 0;
                    String colorID = Mapper.getColorID(p2.getColor());
                    UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
                    UnitKey infKey = Mapper.getUnitKey("gf", colorID);
                    if (planetUnit2.getUnits() != null) {
                        if (planetUnit2.getUnits().get(mechKey) != null) {
                            numMechs = planetUnit2.getUnits().get(mechKey);
                        }
                        if (planetUnit2.getUnits().get(infKey) != null) {
                            numInf = planetUnit2.getUnits().get(infKey);
                        }
                    }
                    String planetId2 = planetUnit2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planetId2, game);
                    if (numInf > 0) {
                        buttons.add(Buttons.green("specialRex_" + planet + "_" + p2.getFaction() + "_infantry",
                            "Remove 1 infantry from " + planetRepresentation2));
                    }
                    if (numMechs > 0) {
                        buttons.add(Buttons.blue("specialRex_" + planet + "_" + p2.getFaction() + "_mech",
                            "Remove 1 mech from " + planetRepresentation2));
                    }
                }
            }
        }
        return buttons;
    }

    @ButtonHandler("buttonID.startsWith")
    public static void resolveSpecialRex(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null)
            return;

        String mechOrInf = buttonID.split("_")[3];
        String msg = player.getFactionEmojiOrColor() + " used the special Mecatol Rex power to remove 1 " + mechOrInf + " on " + Helper.getPlanetRepresentation(planet, game);
        new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(planet), "1 " + mechOrInf + " " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        deleteMessage(event);
    }

    public static List<Button> getEchoAvailableSystems(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPlanetUnitHolders().isEmpty()) {
                buttons.add(Buttons.green("echoPlaceFrontier_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    @ButtonHandler("echoPlaceFrontier_")
    public static void resolveEchoPlaceFrontier(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        AddToken.addToken(event, tile, Constants.FRONTIER, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji() + " placed a frontier token in "
            + tile.getRepresentationForButtons(game, player));
        deleteMessage(event);
    }

    public static List<Button> getEndOfTurnAbilities(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> endButtons = new ArrayList<>();

        List<String> implementedLegendaryPlanets = List.of(
            "mallice", "mirage", "hopesend", "primor", // PoK
            "silence", "tarrock", "prism", "echo", "domna"); // DS

        for (String planet : implementedLegendaryPlanets) {
            String prettyPlanet = Mapper.getPlanet(planet).getName();
            if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
                endButtons.add(Buttons.green(finChecker + "planetAbilityExhaust_" + planet,
                    "Use " + prettyPlanet + " Ability"));
            }
        }

        if (player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")) {
            endButtons.add(Buttons.red(finChecker + "exhaustTech_pi", "Exhaust Predictive Intelligence"));
        }
        if (player.getTechs().contains("absol_pi") && !player.getExhaustedTechs().contains("absol_pi")) {
            endButtons.add(Buttons.red(finChecker + "exhaustTech_absol_pi", "Exhaust Predictive Intelligence"));
        }
        if (!player.hasAbility("arms_dealers")) {
            for (String shipOrder : getPlayersShipOrders(player)) {
                if (!Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "dreadnought",
                    "placeOneNDone_skipbuild", null).isEmpty()) {
                    endButtons.add(Buttons.green(finChecker + "resolveShipOrder_" + shipOrder,
                        "Use " + Mapper.getRelic(shipOrder).getName()));
                }
            }
        }
        if (player.getTechs().contains("bs") && !player.getExhaustedTechs().contains("bs")) {
            endButtons.add(Buttons.green(finChecker + "exhaustTech_bs", "Exhaust Bio-Stims"));
        }
        // dsceldr
        if (player.getTechs().contains("dsceldr") && !player.getExhaustedTechs().contains("dsceldr")
            && player.getStrategicCC() > 0) {
            endButtons.add(
                Buttons.green(finChecker + "exhaustTech_dsceldr", "Exhaust Emergency Mobilization Protocols"));
        }
        if (player.getTechs().contains("absol_bs") && !player.getExhaustedTechs().contains("absol_bs")) {
            endButtons.add(Buttons.green(finChecker + "exhaustTech_absol_bs", "Exhaust Bio-Stims"));
        }
        if (player.getTechs().contains("miltymod_hm") && !player.getExhaustedTechs().contains("miltymod_hm")) {
            endButtons.add(Buttons.green(finChecker + "exhaustTech_miltymod_hm", "Exhaust Hyper Metabolism"));
        }
        if (player.getTechs().contains("absol_hm") && !player.getExhaustedTechs().contains("absol_hm")) {
            endButtons.add(Buttons.green(finChecker + "exhaustTech_absol_hm", "Exhaust Hyper Metabolism"));
        }
        if (player.getTechs().contains("absol_nm") && !player.getExhaustedTechs().contains("absol_nm")) {
            endButtons.add(Buttons.green(finChecker + "exhaustTech_absol_nm", "Exhaust Neural Motivator"));
        }
        if (player.getTechs().contains("absol_pa") && !player.getReadiedPlanets().isEmpty()
            && !player.getActionCards().isEmpty()) {
            endButtons.add(Buttons.green(finChecker + "useTech_absol_pa", "Use Psychoarchaeology"));
        }
        if (player.hasUnexhaustedLeader("naazagent")) {
            endButtons.add(Buttons.green(finChecker + "exhaustAgent_naazagent",
                "Use Naaz-Rokha Agents", Emojis.Naaz));
        }
        if (player.hasUnexhaustedLeader("cheiranagent")
            && !ButtonHelperAgents.getCheiranAgentTiles(player, game).isEmpty()) {
            endButtons.add(
                Buttons.green(finChecker + "exhaustAgent_cheiranagent_" + player.getFaction(),
                    "Use Cheiran Agent", Emojis.cheiran));
        }

        if (player.hasUnexhaustedLeader("freesystemsagent") && !player.getReadiedPlanets().isEmpty()
            && !ButtonHelperAgents.getAvailableLegendaryAbilities(game).isEmpty()) {
            endButtons.add(Buttons.green(finChecker + "exhaustAgent_freesystemsagent_" + player.getFaction(),
                "Use Free Systems Agent", Emojis.freesystems));
        }
        if (player.hasRelic("absol_tyrantslament") && !player.hasUnit("tyrantslament")) {
            endButtons.add(Buttons.green("deployTyrant", "Deploy The Tyrant's Lament", Emojis.Absol));
        }

        if (player.hasUnexhaustedLeader("lizhoagent")) {
            endButtons.add(Buttons.green(finChecker + "exhaustAgent_lizhoagent",
                "Use Li-Zho Agent", Emojis.lizho));
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "ravencommander")) {
            endButtons.add(Buttons.green(finChecker + "ravenMigration", "Use Migration", Emojis.raven));
        }

        endButtons.add(Buttons.red("deleteButtons", "Delete these buttons"));
        return endButtons;
    }

    public static List<String> getPlayersShipOrders(Player player) {
        List<String> shipOrders = new ArrayList<>();
        for (String relic : player.getRelics()) {
            if (relic.toLowerCase().contains("axisorder") && !player.getExhaustedRelics().contains(relic)) {
                shipOrders.add(relic);
            }
        }
        return shipOrders;
    }

    public static List<String> getPlayersStarCharts(Player player) {
        List<String> shipOrders = new ArrayList<>();
        for (String relic : player.getRelics()) {
            if (relic.toLowerCase().contains("starchart")) {
                shipOrders.add(relic);
            }
        }
        return shipOrders;
    }

    public static void starChartStep0(Game game, Player player, List<String> newTileIDs) {
        List<Button> buttons = new ArrayList<>();
        for (String newTileID : newTileIDs) {
            TileModel tile = TileHelper.getTileById(newTileID);
            buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "starChartsStep1_" + newTileID,
                tile.getName()));

        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " choose the tile you want to add to the board", buttons);
    }

    public static void detTileAdditionStep1(Game game, Player player, String newTileID) {
        List<Button> buttons = new ArrayList<>();
        TileModel tile = TileHelper.getTileById(newTileID);
        buttons.add(Buttons.green("detTileAdditionStep2_" + newTileID, "Next to only 1 tile"));
        buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "starChartsStep1_" + newTileID,
            "Next to 2 tiles"));
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " you are placing "
            + tile.getName() + ". Will this tile be adjacent to 1 other tile or 2?", buttons);
    }

    @ButtonHandler("detTileAdditionStep2_")
    public static void detTileAdditionStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        deleteMessage(event);
        String newTileID = buttonID.split("_")[1];
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isEdgeOfBoard(game) && tile.getPosition().length() > 2
                && FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "detTileAdditionStep3_" + newTileID
                    + "_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " choose an edge tile that the new tile will be adjacent too", buttons);
    }

    @ButtonHandler("detTileAdditionStep3_")
    public static void detTileAdditionStep3(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        deleteMessage(event);
        String newTileID = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        String pos = buttonID.split("_")[2];
        List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(pos);
        List<String> adjacentToSomethingElse = new ArrayList<>();
        for (String pos3 : directlyAdjacentTiles) {
            if (game.getTileByPosition(pos3) != null) {
                adjacentToSomethingElse.addAll(PositionMapper.getAdjacentTilePositions(pos3));
            }
        }
        for (String pos3 : directlyAdjacentTiles) {
            if (game.getTileByPosition(pos3) == null && !adjacentToSomethingElse.contains(pos3)) {
                buttons.add(Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "detTileAdditionStep4_" + newTileID + "_" + pos3, pos3));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " select the tile position where the tile should go", buttons);
    }

    @ButtonHandler("detTileAdditionStep4_")
    public static void detTileAdditionStep4(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        deleteMessage(event);
        String newTileID = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Tile tile = new Tile(newTileID, pos);
        game.setTile(tile);
        if (tile.getPlanetUnitHolders().isEmpty()) {
            AddToken.addToken(event, tile, Constants.FRONTIER, game);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " added the tile " + tile.getRepresentationForButtons(game, player));
    }

    public static void starChartStep1(Game game, Player player, String newTileID) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isEdgeOfBoard(game) && tile.getPosition().length() > 2
                && (game.isDiscordantStarsMode() || FoWHelper.playerHasShipsInSystem(player, tile))) {
                buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "starChartsStep2_" + newTileID + "_"
                    + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " choose an edge tile that the new tile will be adjacent too", buttons);
    }

    @ButtonHandler("starChartsStep2_")
    public static void starChartStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        deleteMessage(event);
        String newTileID = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(pos);
        for (String pos2 : directlyAdjacentTiles) {
            Tile tile = game.getTileByPosition(pos2);
            if (tile != null && tile.isEdgeOfBoard(game)) {
                buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "starChartsStep3_" + newTileID + "_"
                    + tile.getPosition() + "_" + pos, tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " choose another tile that the new tile will be adjacent too", buttons);
    }

    @ButtonHandler("starChartsStep3_")
    public static void starChartStep3(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        deleteMessage(event);
        String newTileID = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        String pos2 = buttonID.split("_")[3];
        List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(pos);
        List<String> directlyAdjacentTiles2 = PositionMapper.getAdjacentTilePositions(pos2);
        String inBoth = "";
        for (String pos3 : directlyAdjacentTiles) {
            if (directlyAdjacentTiles2.contains(pos3) && game.getTileByPosition(pos3) == null) {
                inBoth = pos3;
            }
        }
        if (inBoth.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " could not find the correct location, sorry.");
        } else {
            Tile tile = new Tile(newTileID, inBoth);
            game.setTile(tile);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " added the tile " + tile.getRepresentationForButtons(game, player));
            if (tile.getPlanetUnitHolders().isEmpty()) {
                AddToken.addToken(event, tile, Constants.FRONTIER, game);
            }
        }
    }

    @ButtonHandler("confirmSecondAction")
    public static void confirmSecondAction(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation() + " is using an ability to take another action", TurnStart.getStartOfTurnButtons(player, game, true, event, true));
    }

    @ButtonHandler("addToken_")
    public static void addTokenToTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        // addtoken_(tokenname)_(pos)_(planet?)
        String regex = "addToken_" + RegexHelper.tokenRegex() + "_" + RegexHelper.posRegex(game)
            + RegexHelper.optional("_" + RegexHelper.unitHolderRegex(game, "planet"));
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String token = matcher.group("token");
            String pos = matcher.group("pos");
            String planet = matcher.group("planet");

            Tile tile = game.getTileByPosition(pos);
            if (planet == null) {
                AddToken.addToken(event, tile, token, game);
            } else {
                tile.addToken(token, planet);
            }
        }
    }

    public static void checkForPrePassing(Game game, Player player) {
        game.setStoredValue("Pre Pass " + player.getFaction(), "");
        boolean hadAnyUnplayedSCs = false;
        for (Integer SC : player.getSCs()) {
            if (!game.getPlayedSCs().contains(SC)) {
                hadAnyUnplayedSCs = true;
            }
        }
        if (player.getTacticalCC() == 0 && !hadAnyUnplayedSCs && !player.isPassed()) {
            String msg = player.getRepresentation()
                + " you have the option to pre-pass, which means on your next turn, the bot automatically passes for you, no matter what happens. This is entirely optional and reversable";
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolvePreassignment_Pre Pass " + player.getFaction(), "Pass on Next Turn"));
            scButtons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, scButtons);
        }
    }

    public static int getKyroHeroSC(Game game) {
        if (game.getStoredValue("kyroHeroSC").isEmpty()) {
            return 1000;
        } else {
            return Integer.parseInt(game.getStoredValue("kyroHeroSC"));
        }
    }

    public static List<Button> getPossibleRings(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        Tile centerTile = game.getTileByPosition("000");
        if (centerTile != null) {
            Button rex = Buttons.green(finChecker + "ringTile_000",
                centerTile.getRepresentationForButtons(game, player));
            if (!AddCC.hasCC(player, centerTile)) {
                ringButtons.add(rex);
            }
        }
        int rings = game.getRingCount();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)) {
            if (AddCC.hasCC(player, tile)) {
                continue;
            }
            ringButtons.add(Buttons.green(finChecker + "ringTile_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)).withEmoji(Emoji.fromFormatted(Emojis.spacedock)));
        }
        for (int x = 1; x < rings + 1; x++) {
            Button ringX = Buttons.green(finChecker + "ring_" + x, "Ring #" + x);
            ringButtons.add(ringX);
        }
        Button corners = Buttons.green(finChecker + "ring_corners", "Corners");
        ringButtons.add(corners);
        return ringButtons;
    }

    public static List<Button> getTileInARing(Player player, Game game, String buttonID,
        GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        String ringNum = buttonID.replace("ring_", "");

        if ("corners".equalsIgnoreCase(ringNum)) {
            List<String> cornerPositions = List.of("tl", "tr", "bl", "br");
            for (String pos : cornerPositions) {
                Tile t = game.getTileByPosition(pos);
                if (t != null && !AddCC.hasCC(event, player.getColor(), t)
                    && (!game.isNaaluAgent() || !t.isHomeSystem())) {
                    Button corners = Buttons.green(finChecker + "ringTile_" + pos,
                        t.getRepresentationForButtons(game, player));
                    ringButtons.add(corners);
                }
            }
        } else {
            int ringN;
            if (ringNum.contains("_")) {
                ringN = Integer.parseInt(ringNum.substring(0, ringNum.indexOf("_")));
            } else {
                ringN = Integer.parseInt(ringNum);
            }
            int totalTiles = ringN * 6;
            if (ringNum.contains("_")) {
                String side = ringNum.substring(ringNum.lastIndexOf("_") + 1);
                if ("left".equalsIgnoreCase(side)) {
                    for (int x = totalTiles / 2; x < totalTiles + 1; x++) {
                        String pos = ringN + "" + x;
                        Tile tile = game.getTileByPosition(pos);
                        if (tile != null && !tile.getRepresentationForButtons(game, player).contains("Hyperlane")
                            && !AddCC.hasCC(event, player.getColor(), tile)
                            && (!game.isNaaluAgent() || !tile.isHomeSystem()
                                || "17".equalsIgnoreCase(tile.getTileID()))) {
                            Button corners = Buttons.green(finChecker + "ringTile_" + pos,
                                tile.getRepresentationForButtons(game, player));
                            ringButtons.add(corners);
                        }
                    }
                    String pos = ringN + "01";
                    Tile tile = game.getTileByPosition(pos);
                    if (tile != null && !tile.getRepresentationForButtons(game, player).contains("Hyperlane")
                        && !AddCC.hasCC(event, player.getColor(), tile)
                        && (!game.isNaaluAgent() || !tile.isHomeSystem()
                            || "17".equalsIgnoreCase(tile.getTileID()))) {
                        Button corners = Buttons.green(finChecker + "ringTile_" + pos,
                            tile.getRepresentationForButtons(game, player));
                        ringButtons.add(corners);
                    }
                } else {
                    for (int x = 1; x < (totalTiles / 2) + 1; x++) {
                        String pos = ringN + "" + x;
                        if (x < 10) {
                            pos = ringN + "0" + x;
                        }
                        Tile tile = game.getTileByPosition(pos);
                        if (tile != null && !tile.getRepresentationForButtons(game, player).contains("Hyperlane")
                            && !AddCC.hasCC(event, player.getColor(), tile)
                            && (!game.isNaaluAgent() || !tile.isHomeSystem())) {
                            Button corners = Buttons.green(finChecker + "ringTile_" + pos,
                                tile.getRepresentationForButtons(game, player));
                            ringButtons.add(corners);
                        }
                    }
                }
            } else {
                if (ringN < 5) {
                    for (int x = 1; x < totalTiles + 1; x++) {
                        String pos = ringN + "" + x;
                        if (x < 10) {
                            pos = ringN + "0" + x;
                        }
                        Tile tile = game.getTileByPosition(pos);
                        if (tile != null && !tile.getRepresentationForButtons(game, player).contains("Hyperlane")
                            && !AddCC.hasCC(event, player.getColor(), tile)
                            && (!game.isNaaluAgent() || !tile.isHomeSystem())) {
                            Button corners = Buttons.green(finChecker + "ringTile_" + pos,
                                tile.getRepresentationForButtons(game, player));
                            ringButtons.add(corners);
                        }
                    }
                } else {
                    Button ringLeft = Buttons.green(finChecker + "ring_" + ringN + "_left", "Left Half");
                    ringButtons.add(ringLeft);
                    Button ringRight = Buttons.green(finChecker + "ring_" + ringN + "_right", "Right Half");
                    ringButtons.add(ringRight);
                }
            }
        }
        ringButtons.add(Buttons.red("ChooseDifferentDestination", "Get a different ring"));

        return ringButtons;
    }

    public static int getNumberOfUnitUpgrades(Player player) {
        int count = 0;
        List<String> types = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (techM.isUnitUpgrade()) {
                if (!types.contains(techM.getBaseUpgrade().orElse("bleh"))) {
                    count++;
                    types.add(tech);
                }

            }
        }
        return count;
    }

    public static int getNumberOfCertainTypeOfTech(Player player, TechnologyType type) {
        int count = 0;
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (techM.getTypes().contains(type)) {
                count++;
            }
        }
        return count;
    }

    public static void exploreDET(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());

        if (player.hasAbility("reclamation")) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (Constants.MECATOLS.contains(uH.getName())
                    && game.getStoredValue("planetsTakenThisRound").contains(uH.getName())) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "sd mr, pds mr", game);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " Due to the reclamation ability, 1 PDS and 1 space dock have been added to Mecatol Rex. This is optional though.");
                }
            }
        }
        if (doesPlayerHaveFSHere("lanefir_flagship", player, tile)) {
            List<Button> button2 = scanlinkResolution(player, game, event);
            if (!button2.isEmpty()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                    + "Due to the Memory of Dusk (the Lanefir flagship), you may explore a planet you control in the system.");
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Explore a Planet", button2);
            }
        }
        if (player.hasAbility("secret_maps")) {
            String msg = player.getRepresentation()
                + " you may use your secret maps ability to explore a planet with production that you did not explore this turn.";
            List<Button> buttons = new ArrayList<>();
            for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
                if ("space".equalsIgnoreCase(planetUnit.getName())) {
                    continue;
                }
                Planet planetReal = (Planet) planetUnit;
                String planet = planetReal.getName();
                if (planetReal.getOriginalPlanetType() != null && player.getPlanetsAllianceMode().contains(planet)
                    && Helper.getProductionValueOfUnitHolder(player, game, tile, planetUnit) > 0
                    && !game.getStoredValue(player.getFaction() + "planetsExplored")
                        .contains(planetUnit.getName() + "*")) {
                    List<Button> planetButtons = getPlanetExplorationButtons(game, planetReal, player);
                    buttons.addAll(planetButtons);
                }
            }
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
            }
        }
        if (player.hasUnit("winnu_mech")) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0
                    && game.getStoredValue("planetsTakenThisRound").contains(uH.getName())) {
                    String planet = uH.getName();
                    Button sdButton = Buttons.green("winnuStructure_sd_" + planet,
                        "Place 1 space dock on " + Helper.getPlanetRepresentation(planet, game));
                    sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                    Button pdsButton = Buttons.green("winnuStructure_pds_" + planet,
                        "Place 1 PDS on " + Helper.getPlanetRepresentation(planet, game));
                    pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
                    Button tgButton = Buttons.red("deleteButtons", "Delete Buttons");
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(sdButton);
                    buttons.add(pdsButton);
                    buttons.add(tgButton);
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " Use buttons to place structures equal to the amount of mechs you have",
                        buttons);
                }
            }
        }
        if (player.hasUnit("kollecc_mech")) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                    List<Button> buttons = new ArrayList<>();
                    String planet = uH.getName();
                    Button sdButton = Buttons.green("kolleccMechCapture_" + planet + "_mech",
                        "Capture 1 mech on " + Helper.getPlanetRepresentation(planet, game));
                    sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.mech));
                    buttons.add(sdButton);
                    if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                        Button pdsButton = Buttons.green("kolleccMechCapture_" + planet + "_infantry",
                            "Capture 1 infantry on " + Helper.getPlanetRepresentation(planet, game));
                        pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                        buttons.add(pdsButton);
                    }
                    Button tgButton = Buttons.red("deleteButtons", "Delete Buttons");
                    buttons.add(tgButton);
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " Use buttons to resolve capturing up to 2 ground forces on each planet with your mechs",
                        buttons);
                }
            }
        }
        if (!FoWHelper.playerHasShipsInSystem(player, tile)) {
            return;
        }
        if (player.hasTech("det")
            && tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
            if (player.hasAbility("voidsailors")) {
                String cardID1 = game.drawExplore(Constants.FRONTIER);
                String cardID2 = game.drawExplore(Constants.FRONTIER);
                ExploreModel card1 = Mapper.getExplore(cardID1);
                ExploreModel card2 = Mapper.getExplore(cardID2);
                String name1 = card1.getName();
                String name2 = card2.getName();
                Button resolveExplore1 = Buttons.green(
                    "resFrontier_" + cardID1 + "_" + tile.getPosition() + "_" + cardID2, "Choose " + name1);
                Button resolveExplore2 = Buttons.green(
                    "resFrontier_" + cardID2 + "_" + tile.getPosition() + "_" + cardID1, "Choose " + name2);
                List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                // code to draw 2 explores and get their names
                // Send Buttons to decide which one to explore
                String message = player.getRepresentationUnfogged() + " Please decide which card to resolve.";

                if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {

                    String pF = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Using Voidsailors,  " + pF
                        + " found a " + name1 + " and a " + name2 + " in " + tile.getRepresentation());

                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Found a " + name1 + " and a " + name2 + " in " + tile.getRepresentation());
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                String msg2 = "As a reminder of their text, the card abilities read as: \n";
                msg2 = msg2 + name1 + ": " + card1.getText() + "\n";
                msg2 = msg2 + name2 + ": " + card2.getText() + "\n";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
            } else if (player.hasUnexhaustedLeader("lanefiragent")) {
                String cardID = game.drawExplore(Constants.FRONTIER);
                ExploreModel card = Mapper.getExplore(cardID);
                String name1 = card.getName();
                Button resolveExplore1 = Buttons.green(
                    "lanefirAgentRes_Decline_frontier_" + cardID + "_" + tile.getPosition(), "Choose " + name1);
                Button resolveExplore2 = Buttons.green("lanefirAgentRes_Accept_frontier_" + tile.getPosition(),
                    "Use Lanefir Agent");
                List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                String message = player.getRepresentationUnfogged()
                    + " You have " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Vassa Hagi , the Lanefir"
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                    + " agent, and thus may decline this explore to draw another one instead.";
                if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                    String pF = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                        pF + " found a " + name1 + " in " + tile.getRepresentation());
                } else {
                    String pF = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        pF + "Found a " + name1 + " and in " + tile.getRepresentation());
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                String msg2 = "As a reminder of the text, the card reads as: \n";
                msg2 = msg2 + name1 + ": " + card.getText() + "\n";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
            } else {
                new ExploreFrontier().expFront(event, tile, game, player);
            }

            if (player.hasAbility("migrant_fleet")) {
                String msg3 = player.getRepresentation()
                    + " after you resolve the frontier explore, you may use your migrant explorers ability to explore a planet you control in an adjacent system.";
                List<Button> buttons = new ArrayList<>();
                for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
                    Tile tile2 = game.getTileByPosition(pos);
                    for (Planet uH : tile2.getPlanetUnitHolders()) {
                        Planet planetReal = uH;
                        String planet = planetReal.getName();
                        if (planetReal.getOriginalPlanetType() != null
                            && player.getPlanetsAllianceMode().contains(planet)) {
                            List<Button> planetButtons = getPlanetExplorationButtons(game, planetReal, player);
                            buttons.addAll(planetButtons);
                        }

                    }
                }
                if (!buttons.isEmpty()) {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg3, buttons);
                }
            }

        }
    }

    @ButtonHandler("sendTradeHolder_")
    public static void sendTradeHolderSomething(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String tgOrDebt = buttonID.split("_")[1];
        Player tradeHolder = null;
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(5)) {
                tradeHolder = p2;
                break;
            }
        }
        if (buttonID.split("_").length > 2) {
            tradeHolder = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        }
        if (tradeHolder == null) {
            BotLogger.log(event, "`ButtonHelper.sendTradeHolderSomething` tradeHolder was **null**");
            return;
        }
        String msg = player.getRepresentation() + " sent 1 " + tgOrDebt + " to " + tradeHolder.getRepresentation();
        if ("tg".equalsIgnoreCase(tgOrDebt)) {
            TransactionHelper.checkTransactionLegality(game, player, tradeHolder);
            if (player.getTg() > 0) {
                tradeHolder.setTg(tradeHolder.getTg() + 1);
                player.setTg(player.getTg() - 1);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " you had no TGs to send, so no TGs were sent.");
                return;
            }
        } else {
            SendDebt.sendDebt(player, tradeHolder, 1);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

    }

    public static boolean doesPlanetHaveAttachmentTechSkip(Tile tile, String planet) {
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        return unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.WARFARE)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.CYBERNETIC)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.BIOTIC)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath("encryptionkey")) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.PROPULSION));
    }

    @ButtonHandler("absolsdn_")
    public static void resolveAbsolScanlink(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        if (buttonID.contains("Decline")) {
            String drawColor = buttonID.split("_")[2];
            String cardID = buttonID.split("_")[3];
            String planetName = buttonID.split("_")[4];
            Tile tile = game.getTileFromPlanet(planetName);
            String messageText = player.getRepresentation() + " explored " + Emojis.getEmojiFromDiscord(drawColor) + "Planet "
                + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " *(tile " + tile.getPosition() + ")*:";
            ExploreSubcommandData.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
            if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && game.getPhaseOfGame().contains("agenda")) {
                PlanetRefresh.doAction(player, planetName, game);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Planet has been refreshed because of Quaxdol Junitas, the Florzen Commander.");
                AgendaHelper.listVoteCount(game, game.getMainGameChannel());
            }
            if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
                UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
                game.getTileFromPlanet(planetName).getUnitHolders().get(planetName).addUnit(infKey, 1);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Added 1 infantry to planet because of Master Halbert, the Lanefir Commander.");
            }
            if (player.hasTech("dslaner")) {
                player.setAtsCount(player.getAtsCount() + 1);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getRepresentation() + " Put 1 commodity on ATS Armaments");
            }
        } else {
            int oldTg = player.getTg();
            player.setTg(oldTg + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getRepresentation()
                    + " used Scanlink Drone Network to decline explore and gained 1TG (TGs went from "
                    + oldTg + "->" + player.getTg() + ")");
            String planetID = buttonID.split("_")[2];
            if (player.hasAbility("awaken") && !game.getAllPlanetsWithSleeperTokens().contains(planetID)
                && player.getPlanets().contains(planetID)) {
                Button placeSleeper = Buttons.green("putSleeperOnPlanet_" + planetID, "Put Sleeper on " + planetID, Emojis.Sleeper);
                Button decline = Buttons.red("deleteButtons", "Decline To Put a Sleeper Down");
                List<Button> buttons = List.of(placeSleeper, decline);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    "Can Do Sleeper Things", buttons);
            }
        }
        deleteMessage(event);
    }

    public static List<Button> getAbsolOrbitalButtons(Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planet = planetReal.getName();
            if (player.getPlanetsAllianceMode().contains(planet)
                && planetUnit.getUnitCount(UnitType.Spacedock, player.getColor()) < 1) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, game);
                buttons.add(Buttons.green("addAbsolOrbital_" + game.getActiveSystem() + "_" + planetId, planetRepresentation, Emojis.Absol));
            }
        }
        return buttons;
    }

    @ButtonHandler("addAbsolOrbital_")
    public static void addAbsolOrbital(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder uH = tile.getUnitHolders().get(buttonID.split("_")[2]);
        new AddUnits().unitParsing(event, player.getColor(), tile, "plenaryorbital " + uH.getName(), game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji()
            + " added an Plenary Orbital to " + Mapper.getPlanet(uH.getName()).getName());
        player.addOwnedUnitByID("plenaryorbital");
        deleteMessage(event);
    }

    public static List<Button> scanlinkResolution(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planet = planetReal.getName();
            if (planetReal.getOriginalPlanetType() != null && player.getPlanetsAllianceMode().contains(planet)
                && FoWHelper.playerHasUnitsOnPlanet(player, tile, planet)) {
                List<Button> planetButtons = getPlanetExplorationButtons(game, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static List<Button> lanefirFSResolution(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planet = planetReal.getName();
            if (planetReal.getOriginalPlanetType() != null && player.getPlanetsAllianceMode().contains(planet)) {
                List<Button> planetButtons = getPlanetExplorationButtons(game, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static List<Button> getPlanetExplorationButtons(Game game, Planet planet, Player player) {
        if (planet == null || game == null)
            return null;

        String planetId = planet.getName();
        String planetRepresentation = Helper.getPlanetRepresentation(planetId, game);
        List<Button> buttons = new ArrayList<>();
        Set<String> explorationTraits = new HashSet<>(planet.getPlanetTypes());

        if (player.hasAbility("black_markets") && (explorationTraits.contains("cultural")
            || explorationTraits.contains("industrial") || explorationTraits.contains("hazardous"))) {
            Set<String> traits = getTypesOfPlanetPlayerHas(game, player);
            explorationTraits.addAll(traits);
        }

        for (String trait : explorationTraits) {
            if (List.of("cultural", "industrial", "hazardous").contains(trait)) {
                String buttonId = "movedNExplored_filler_" + planetId + "_" + trait;
                String buttonMessage = "Explore " + planetRepresentation
                    + (explorationTraits.size() > 1 ? " as " + trait : "");
                Emoji emoji = Emoji.fromFormatted(Emojis.getEmojiFromDiscord(trait));
                Button button = Buttons.gray(buttonId, buttonMessage).withEmoji(emoji);
                buttons.add(button);
            }
        }
        return buttons;
    }

    public static void resolveEmpyCommanderCheck(Player player, Game game, Tile tile,
        GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2 != player && AddCC.hasCC(event, p2.getColor(), tile)
                && game.playerHasLeaderUnlockedOrAlliance(p2, "empyreancommander")) {
                MessageChannel channel = game.getMainGameChannel();
                if (game.isFowMode()) {
                    channel = p2.getPrivateChannel();
                }
                RemoveCC.removeCC(event, p2.getColor(), tile, game);
                String message = p2.getRepresentationUnfogged()
                    + " due to having Xuange, the Empyrean commander, the CC you had in the active system has been removed. Reminder that this is optional but was done automatically.";
                MessageHelper.sendMessageToChannel(channel, message);
            }
        }
    }

    public static List<Tile> getTilesWithShipsInTheSystem(Player player, Game game) {
        List<Tile> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                buttons.add(tile);
            }
        }
        return buttons;
    }

    public static List<Tile> getTilesWithTrapsInTheSystem(Game game) {
        List<Tile> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                for (String id : planet.getTokenList()) {
                    if (id.contains("attachment_lizhotrap") && !buttons.contains(tile)) {
                        buttons.add(tile);
                        break;
                    }
                }
            }
        }
        return buttons;
    }

    public static List<Tile> getTilesForCheiranHero(Player player, Game game) {
        List<Tile> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                if (planet.getUnitCount(UnitType.Spacedock, player.getColor()) > 0
                    || planet.getUnitCount(UnitType.Pds, player.getColor()) > 0
                    || (planet.getUnitCount(UnitType.Mech, player.getColor()) > 0 && player.hasAbility("byssus"))) {
                    buttons.add(tile);
                    break;
                }
            }
        }
        return buttons;
    }

    public static List<Button> getTilesToModify(Player player, Game game) {
        return getTilesWithUnitsForModifyUnitsButton(player, game, "genericModify", true);
    }

    public static List<Button> getDomnaStepOneTiles(Player player, Game game) {
        return getTilesWithShipsForAction(player, game, "domnaStepOne", false);
    }

    public static List<Button> getTilesWithUnitsForAction(Player player, Game game, String action, boolean includeDelete) {
        Predicate<Tile> hasPlayerUnits = tile -> tile.containsPlayersUnits(player);
        return getTilesWithPredicateForAction(player, game, action, hasPlayerUnits, includeDelete);
    }

    public static List<Button> getTilesWithShipsForAction(Player player, Game game, String action, boolean includeDelete) {
        Predicate<Tile> hasPlayerShips = tile -> tile.containsPlayersUnitsWithModelCondition(player, UnitModel::getIsShip);
        return getTilesWithPredicateForAction(player, game, action, hasPlayerShips, includeDelete);
    }

    public static List<Button> getAllTilesToModify(Player player, Game game, String action, boolean includeDelete) {
        Predicate<Tile> tRue = tile -> true;
        return getTilesWithPredicateForAction(player, game, action, tRue, includeDelete);
    }

    public static List<Button> getTilesWithUnitsForModifyUnitsButton(Player player, Game game, String action, boolean includeDelete) {
        Predicate<Tile> hasPlayerUnits = tile -> tile.containsPlayersUnits(player);
        List<Button> buttons = new ArrayList<>(16);
        buttons.addAll(getTilesWithPredicateForAction(player, game, action, hasPlayerUnits, includeDelete));
        buttons.add(Buttons.green("modifyUnitsAllTiles" + "deleteThisMessage", "Show All Tiles"));
        return buttons;
    }

    public static List<Button> getTilesWithPredicateForAction(Player player, Game game, String action, Predicate<Tile> predicate, boolean includeDelete) {
        String finChecker = player.finChecker();
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            Tile tile = tileEntry.getValue();
            if (predicate.negate().test(tile))
                continue;

            String buttonID = finChecker + action + "_" + tileEntry.getKey();
            Button validTile = Buttons.green(buttonID, tile.getRepresentationForButtons(game, player));
            buttons.add(validTile);
        }
        if (includeDelete) {
            Button deleteButtons = Buttons.red(finChecker + "deleteButtons", "Delete these buttons");
            buttons.add(deleteButtons);
        }
        return buttons;
    }

    public static Set<String> getTypeOfPlanet(Game game, String planet) {
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        if (unitHolder == null)
            return new HashSet<>();
        Planet planetReal = unitHolder;
        return planetReal.getPlanetTypes();
    }

    public static void offerBuildOrRemove(Player player, Game game, GenericInteractionCreateEvent event, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Button buildButton = Buttons.green(finChecker + "genericBuild_" + tile.getPosition(),
            "Build in " + tile.getRepresentationForButtons(game, player));
        buttons.add(buildButton);
        Button remove = Buttons.red(finChecker + "getDamageButtons_" + tile.getPosition(),
            "Remove or damage units in " + tile.getRepresentationForButtons(game, player));
        buttons.add(remove);
        Button validTile2 = Buttons.gray(finChecker + "deleteButtons", "Delete these buttons");
        buttons.add(validTile2);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Choose to either add units (build) or remove them", buttons);
    }

    @ButtonHandler("combatRoll_")
    public static void resolveCombatRoll(Player player, Game game, GenericInteractionCreateEvent event, String buttonID) {
        String[] idInfo = buttonID.split("_");
        String pos = idInfo[1];
        String unitHolderName = idInfo[2];
        CombatRollType rollType = CombatRollType.combatround;
        if (idInfo.length > 3) {
            String rollTypeString = idInfo[3];
            switch (rollTypeString) {
                case "afb" -> rollType = CombatRollType.AFB;
                case "bombardment" -> rollType = CombatRollType.bombardment;
                case "spacecannonoffence" -> rollType = CombatRollType.SpaceCannonOffence;
                case "spacecannondefence" -> rollType = CombatRollType.SpaceCannonDefence;
                default -> {
                }
            }
        }
        CombatRoll.secondHalfOfCombatRoll(player, game, event, game.getTileByPosition(pos), unitHolderName, rollType);
        if (buttonID.contains("bombardment") && ButtonHelper.isLawInPlay(game, "conventions")) {
            boolean relevant = false;
            for (UnitHolder unitHolder : game.getTileByPosition(pos).getPlanetUnitHolders()) {
                String planet = unitHolder.getName();
                if (ButtonHelper.getTypeOfPlanet(game, planet).contains("cultural")) {
                    relevant = true;
                }
            }
            if (relevant) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "This is a reminder that Conventions of War is in play, so bombardment of cultural planets is illegal.");
            }
        }
        if (buttonID.contains("bombard")) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    public static List<Button> getTilesToMoveFrom(Player player, Game game, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tileEntry.getValue())
                && (!AddCC.hasCC(event, player.getColor(), tileEntry.getValue())
                    || nomadHeroAndDomOrbCheck(player, game, tileEntry.getValue()))) {
                Tile tile = tileEntry.getValue();
                Button validTile = Buttons.green(finChecker + "tacticalMoveFrom_" + tileEntry.getKey(), tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }

        if (player.hasUnexhaustedLeader("saaragent")) {
            Button saarButton = Buttons.gray("exhaustAgent_saaragent", "Use Saar Agent", Emojis.Saar);
            buttons.add(saarButton);
        }

        if (player.hasRelic("dominusorb")) {
            Button domButton = Buttons.gray("dominusOrb", "Purge Dominus Orb", Emojis.Relic);
            buttons.add(domButton);
        }

        if (player.hasRelicReady("absol_luxarchtreatise")) {
            Button domButton = Buttons.gray("exhaustRelic_absol_luxarchtreatise", "Exhaust Luxarch Treatise", Emojis.Relic);
            buttons.add(domButton);
        }

        if (player.hasUnexhaustedLeader("ghostagent")
            && FoWHelper.doesTileHaveWHs(game, game.getActiveSystem())) {
            Button ghostButton = Buttons.gray("exhaustAgent_ghostagent", "Use Creuss Agent", Emojis.Ghost);
            buttons.add(ghostButton);
        }
        if (player.hasTech("as") && FoWHelper.isTileAdjacentToAnAnomaly(game, game.getActiveSystem(), player)) {
            buttons.add(Buttons.gray("declareUse_Aetherstream", "Declare Aetherstream", Emojis.Empyrean));
        }
        if (player.hasTech("baldrick_gd")) {
            buttons.add(Buttons.gray("exhaustTech_baldrick_gd", "Exhaust Gravity Drive", Emojis.IgnisAurora));
        }
        if (player.hasTech("baldrick_lwd")) {
            buttons.add(Buttons.gray("exhaustTech_baldrick_lwd", "Exhaust Light/Wave Deflector", Emojis.IgnisAurora));
        }
        if (player.getTechs().contains("dsgledb")) {
            buttons.add(Buttons.green(finChecker + "declareUse_Lightning", "Declare Lightning Drives", Emojis.gledge));
        }
        if (player.getTechs().contains("dsvadeb") && !player.getExhaustedTechs().contains("dsvadeb")) {
            buttons.add(Buttons.green(finChecker + "exhaustTech_dsvadeb", "Exhaust Midas Turbine", Emojis.vaden));
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "vayleriancommander")) {
            Button ghostButton = Buttons.gray("declareUse_Vaylerian Commander", "Use Vaylerian Commander", Emojis.vaylerian);
            buttons.add(ghostButton);
        }
        if (player.hasLeaderUnlocked("vaylerianhero")) {
            Button sardakkH = Buttons.blue(finChecker + "purgeVaylerianHero", "Use Vaylerian Hero", Emojis.vaylerian);
            buttons.add(sardakkH);
        }
        if (player.ownsUnit("ghost_mech") && getNumberOfUnitsOnTheBoard(game, player, "mech") > 0) {
            Button ghostButton = Buttons.gray("creussMechStep1_", "Use Ghost Mech", Emojis.Ghost);
            buttons.add(ghostButton);
        }
        if ((player.ownsUnit("nivyn_mech") && getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)
            .contains(game.getTileByPosition(game.getActiveSystem()))) || player.ownsUnit("nivyn_mech2")) {
            Button ghostButton = Buttons.gray("nivynMechStep1_", "Use Nivyn Mech", Emojis.nivyn);
            buttons.add(ghostButton);
        }
        if (player.hasTech("dslihb") && !game.getTileByPosition(game.getActiveSystem()).isHomeSystem()) {
            Button ghostButton = Buttons.gray("exhaustTech_dslihb", "Exhaust Wraith Engine", Emojis.lizho);
            buttons.add(ghostButton);
        }
        String planet = "eko";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            buttons.add(Buttons.gray(finChecker + "planetAbilityExhaust_" + planet,
                "Use Eko's Ability To Ignore Anomalies"));
        }

        Button validTile = Buttons.red(finChecker + "concludeMove_" + game.getActiveSystem(), "Done moving");
        buttons.add(validTile);
        Button validTile2 = Buttons.blue(finChecker + "ChooseDifferentDestination", "Activate a different system");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> moveAndGetLandingTroopsButtons(Player player, Game game, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";

        List<Button> buttons = new ArrayList<>();
        Map<String, Integer> displacedUnits = game.getMovedUnitsFromCurrentActivation();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (!game.getMovedUnitsFromCurrentActivation().isEmpty()) {
            tile = MoveUnits.flipMallice(event, tile, game);
        }

        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Mallice");
            return buttons;
        }

        if (!game.isNaaluAgent() && !game.isL1Hero() && !AddCC.hasCC(event, player.getColor(), tile)
            && game.getStoredValue("vaylerianHeroActive").isEmpty()) {
            if (!game.getStoredValue("absolLux").isEmpty()) {
                player.setTacticalCC(player.getTacticalCC() + 1);
            }
            int cc = player.getTacticalCC();
            cc -= 1;
            player.setTacticalCC(cc);
            AddCC.addCC(event, player.getColor(), tile, true);
        }
        String thingToAdd = "box";
        for (String unit : displacedUnits.keySet()) {
            int amount = displacedUnits.get(unit);
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
            }
            if ("box".equalsIgnoreCase(thingToAdd)) {
                thingToAdd = amount + " " + unit;
            } else {
                thingToAdd = thingToAdd + ", " + amount + " " + unit;
            }
        }
        if (!"box".equalsIgnoreCase(thingToAdd)) {
            new AddUnits().unitParsing(event, player.getColor(), tile, thingToAdd, game);
        }
        for (String unit : displacedUnits.keySet()) {
            int amount = displacedUnits.get(unit);
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
                String colorID = Mapper.getColorID(player.getColor());
                UnitKey unitID = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), colorID);
                tile.addUnitDamage("space", unitID, amount);
            }
        }

        game.resetCurrentMovedUnitsFrom1TacticalAction();
        String colorID = Mapper.getColorID(player.getColor());
        UnitType inf = UnitType.Infantry;
        UnitType mech = UnitType.Mech;
        UnitType ff = UnitType.Fighter;
        UnitType fs = UnitType.Flagship;

        for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
            String name = unitHolder.getName();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            Set<String> tokenList = unitHolder.getTokenList();
            boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
            if (unitHolder instanceof Planet planet && !containsDMZ) {
                int limit;

                if (tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnitCount(inf, colorID) > 0) {
                    limit = tile.getUnitHolders().get("space").getUnitCount(inf, colorID);
                    for (int x = 1; x <= Math.min(2, limit); x++) {
                        String id = finChecker + "landUnits_" + tile.getPosition() + "_" + x + "infantry_" + representation;
                        String label = "Land " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        buttons.add(Buttons.red(id, label, Emojis.infantry));
                    }
                }
                if (planet.getUnitCount(inf, player) > 0 || planet.getUnitCount(mech, player) > 0) {
                    if (player.hasUnexhaustedLeader("dihmohnagent")) {
                        String id = "exhaustAgent_dihmohnagent_" + unitHolder.getName();
                        String label = "Use Dih-Mohn Agent on " + Helper.getPlanetRepresentation(unitHolder.getName(), game);
                        buttons.add(Buttons.green(id, label, Emojis.dihmohn));
                    }
                }
                if (player.hasUnit("tnelis_mech") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4
                    && tile.getUnitHolders().get("space").getUnitCount(UnitType.Destroyer, player) > 0) {
                    String id = "tnelisDeploy_" + unitHolder.getName();
                    String label = "Deploy Mech on " + Helper.getPlanetRepresentation(unitHolder.getName(), game);
                    buttons.add(Buttons.green(id, label, Emojis.tnelis));
                }
                if (planet.getUnitCount(inf, colorID) > 0) {
                    limit = planet.getUnitCount(inf, colorID);
                    for (int x = 1; x <= Math.min(2, limit); x++) {
                        String id = finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "infantry_" + representation;
                        String label = "Undo Landing of " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Buttons.gray(id, label, Emojis.infantry);
                        buttons.add(validTile2);
                    }
                }
                UnitHolder spaceUH = tile.getUnitHolders().get("space");
                if (tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnitCount(mech, colorID) > 0) {
                    UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), colorID);
                    limit = tile.getUnitHolders().get("space").getUnitCount(mech, colorID);
                    int totalUnits = limit;
                    int damagedUnits = 0;
                    if (spaceUH.getUnitDamage() != null && spaceUH.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = spaceUH.getUnitDamage().get(unitKey);
                    }
                    limit = damagedUnits;
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "landUnits_" + tile.getPosition() + "_" + x + "mechdamaged_"
                            + representation;
                        String buttonText = "Land " + x + " Damaged Mech" + (x == 1 ? "" : "s") + " on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Buttons.blue(buttonID, buttonText, Emojis.mech);
                        buttons.add(validTile2);
                    }
                    limit = totalUnits - damagedUnits;
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "landUnits_" + tile.getPosition() + "_" + x + "mech_"
                            + representation;
                        String buttonText = "Land " + x + " Mech" + (x == 1 ? "" : "s") + " on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Buttons.blue(buttonID, buttonText, Emojis.mech);
                        buttons.add(validTile2);
                    }
                }
                if (player.hasUnit("naalu_flagship") && tile.getUnitHolders().get("space").getUnits() != null
                    && tile.getUnitHolders().get("space").getUnitCount(fs, colorID) > 0
                    && tile.getUnitHolders().get("space").getUnitCount(ff, colorID) > 0) {
                    limit = tile.getUnitHolders().get("space").getUnitCount(ff, colorID);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "landUnits_" + tile.getPosition() + "_" + x + "ff_"
                            + representation;
                        String buttonText = "Land " + x + " Fighter" + (x == 1 ? "" : "s") + " on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Buttons.blue(buttonID, buttonText, Emojis.fighter);
                        buttons.add(validTile2);
                    }
                }

                if (planet.getUnitCount(mech, colorID) > 0) {
                    UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), colorID);
                    limit = planet.getUnitCount(mech, colorID);
                    int totalUnits = limit;
                    int damagedUnits = 0;
                    if (planet.getUnitDamage() != null && planet.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = planet.getUnitDamage().get(unitKey);
                    }
                    limit = damagedUnits;

                    for (int x = 1; x <= limit; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "mechdamaged_"
                            + representation;
                        String buttonText = "Undo Landing of " + x + " Damaged Mech" + (x == 1 ? "" : "s") + " on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Buttons.gray(buttonID, buttonText, Emojis.mech);
                        buttons.add(validTile2);
                    }
                    limit = totalUnits - damagedUnits;
                    for (int x = 1; x <= limit; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "mech_"
                            + representation;
                        String buttonText = "Undo Landing of " + x + " Mech" + (x == 1 ? "" : "s") + " on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Buttons.gray(buttonID, buttonText, Emojis.mech);
                        buttons.add(validTile2);
                    }
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")) {
            buttons.addAll(ButtonHelperCommanders.getSardakkCommanderButtons(game, player, event));
        }

        if (player.getPromissoryNotes().containsKey("ragh")) {
            buttons.addAll(ButtonHelperFactionSpecific.getRaghsCallButtons(player, game, tile));
        }
        if (!game.getStoredValue("possiblyUsedRift").isEmpty()) {
            Button rift = Buttons.green(finChecker + "getRiftButtons_" + tile.getPosition(), "Units traveled through rift", Emojis.GravityRift);
            buttons.add(rift);
        }
        if (player.hasAbility("combat_drones") && FoWHelper.playerHasFightersInSystem(player, tile)) {
            Button combatDrones = Buttons.blue(finChecker + "combatDrones", "Use Combat Drones Ability", Emojis.mirveda);
            buttons.add(combatDrones);
        }
        if (player.hasAbility("shroud_of_lith")
            && ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game).size() > 1) {
            buttons.add(Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", Emojis.kollecc));
            buttons.add(Buttons.gray("refreshLandingButtons", "Refresh Landing Buttons", Emojis.kollecc));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")) {
            Button combatDrones = Buttons.blue(finChecker + "offerMirvedaCommander", "Use Mirveda Commander", Emojis.mirveda);
            buttons.add(combatDrones);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "ghostcommander")) {
            Button ghostC = Buttons.blue(finChecker + "placeGhostCommanderFF_" + tile.getPosition(), "Place fighter with Creuss Commander", Emojis.Ghost);
            buttons.add(ghostC);
        }
        if (!tile.getPlanetUnitHolders().isEmpty()
            && game.playerHasLeaderUnlockedOrAlliance(player, "khraskcommander")) {
            Button ghostC = Buttons.blue(finChecker + "placeKhraskCommanderInf_" + tile.getPosition(), "Place infantry with Khrask Commander", Emojis.khrask);
            buttons.add(ghostC);
        }
        if (player.hasUnexhaustedLeader("nokaragent") && FoWHelper.playerHasShipsInSystem(player, tile)) {
            Button chaos = Buttons.gray("exhaustAgent_nokaragent_" + player.getFaction(), "Use Nokar Agent to place 1 destroyer", Emojis.nokar);
            buttons.add(chaos);
        }
        if (player.hasUnexhaustedLeader("tnelisagent") && FoWHelper.playerHasShipsInSystem(player, tile)
            && FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
            Button chaos = Buttons.gray("exhaustAgent_tnelisagent_" + player.getFaction(),
                "Use Tnelis Agent", Emojis.tnelis);
            buttons.add(chaos);
        }
        if (player.hasUnexhaustedLeader("zelianagent")
            && tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
            Button chaos = Buttons.gray("exhaustAgent_zelianagent_" + player.getFaction(), "Use Zelian Agent Yourself", Emojis.zelian);
            buttons.add(chaos);
        }
        if (player.hasLeaderUnlocked("muaathero") && !tile.isMecatol() && !tile.isHomeSystem()
            && getTilesOfPlayersSpecificUnits(game, player, UnitType.Warsun).contains(tile)) {
            Button muaatH = Buttons.blue(finChecker + "novaSeed_" + tile.getPosition(), "Nova Seed This Tile", Emojis.Muaat);
            buttons.add(muaatH);
        }
        if (player.hasLeaderUnlocked("zelianhero") && !tile.isMecatol()
            && getTilesOfUnitsWithBombard(player, game).contains(tile)) {
            Button zelianH = Buttons.blue(finChecker + "celestialImpact_" + tile.getPosition(), "Celestial Impact This Tile", Emojis.zelian);
            buttons.add(zelianH);
        }
        if (player.hasLeaderUnlocked("sardakkhero") && !tile.getPlanetUnitHolders().isEmpty()) {
            Button sardakkH = Buttons.blue(finChecker + "purgeSardakkHero", "Use N'orr Hero", Emojis.Sardakk);
            buttons.add(sardakkH);
        }
        if (player.hasLeaderUnlocked("rohdhnahero")) {
            Button sardakkH = Buttons.blue(finChecker + "purgeRohdhnaHero", "Use Roh'Dhna Hero", Emojis.rohdhna);
            buttons.add(sardakkH);
        }
        if (tile.getUnitHolders().size() > 1 && getTilesOfUnitsWithBombard(player, game).contains(tile)) {
            if (tile.getUnitHolders().size() > 2) {
                buttons.add(Buttons.gray(
                    "bombardConfirm_combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                    "Roll Bombardment"));
            } else {
                buttons.add(
                    Buttons.gray("combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                        "Roll Bombardment"));
            }

        }
        Button concludeMove = Buttons.red(finChecker + "doneLanding_" + tile.getPosition(), "Done landing troops");
        buttons.add(concludeMove);
        CommanderUnlockCheck.checkPlayer(player, "naaz", "empyrean", "ghost");

        return buttons;
    }

    public static String putInfWithMechsForStarforge(String pos, String successMessage, Game game, Player player, ButtonInteractionEvent event) {
        Set<String> tiles = FoWHelper.getAdjacentTiles(game, pos, player, true);
        tiles.add(pos);
        StringBuilder successMessageBuilder = new StringBuilder(successMessage);
        for (String tilePos : tiles) {
            Tile tile = game.getTileByPosition(tilePos);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {

                String colorID = Mapper.getColorID(player.getColor());
                UnitType mech = UnitType.Mech;
                if (unitHolder.getUnits() != null) {
                    if (unitHolder.getUnitCount(mech, colorID) > 0) {
                        int numMechs = unitHolder.getUnitCount(mech, colorID);
                        String planetName = "";
                        if (!"space".equalsIgnoreCase(unitHolder.getName())) {
                            planetName = " " + unitHolder.getName();
                        }
                        new AddUnits().unitParsing(event, player.getColor(), tile, numMechs + " infantry" + planetName,
                            game);

                        successMessageBuilder.append("\n").append(player.getFactionEmoji()).append(" placed ").append(numMechs)
                            .append(" ").append(Emojis.infantry)
                            .append(" with the mechs in ")
                            .append(tile.getRepresentationForButtons(game, player));
                    }
                }
            }
        }
        successMessage = successMessageBuilder.toString();

        return successMessage;

    }

    public static List<Button> landAndGetBuildButtons(Player player, Game game, ButtonInteractionEvent event,
        Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        game.resetCurrentMovedUnitsFrom1System();
        if (Helper.getProductionValue(player, game, tile, false) > 0 || (player.hasTech("iihq") && tile.getTileID().equalsIgnoreCase("18"))) {
            Button buildButton = Buttons.green(finChecker + "tacticalActionBuild_" + game.getActiveSystem(),
                "Build in this system (" + Helper.getProductionValue(player, game, tile, false) + " PRODUCTION Value)");
            buttons.add(buildButton);
        }
        if (!game.getStoredValue("possiblyUsedRift").isEmpty()) {
            Button rift = Buttons.green(finChecker + "getRiftButtons_" + tile.getPosition(), "Units traveled through rift", Emojis.GravityRift);
            buttons.add(rift);
        }
        if (player.hasUnexhaustedLeader("sardakkagent")) {
            buttons.addAll(ButtonHelperAgents.getSardakkAgentButtons(game, player));
        }
        if (player.hasUnexhaustedLeader("nomadagentmercer")) {
            buttons.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(game, player));
        }
        if (player.hasAbility("shroud_of_lith")
            && ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game).size() > 1) {
            buttons.add(Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", Emojis.kollecc));
        }
        Button concludeMove = Buttons.red(finChecker + "doneWithTacticalAction", "Conclude tactical action");
        buttons.add(concludeMove);
        return buttons;
    }

    public static void resolveTransitDiodesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            buttons.add(Buttons.green("transitDiodes_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Done resolving Transit Diodes"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation() + " use buttons to choose the planet you want to move troops to", buttons);
    }

    @ButtonHandler("transitDiodes_")
    public static void resolveTransitDiodesStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = getButtonsForMovingGroundForcesToAPlanet(game, buttonID.split("_")[1], player);
        deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation() + " use buttons to choose the troops you want to move to "
                + Helper.getPlanetRepresentation(buttonID.split("_")[1], game),
            buttons);
    }

    public static List<Button> getButtonsForMovingGroundForcesToAPlanet(Game game, String planetName, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                    if (uH instanceof Planet) {
                        buttons.add(Buttons.green(
                            "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName()
                                + "_infantry",
                            "Move Infantry from " + Helper.getPlanetRepresentation(uH.getName(), game) + " to "
                                + Helper.getPlanetRepresentation(planetName, game)));
                    } else {
                        buttons.add(Buttons.green(
                            "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName()
                                + "_infantry",
                            "Move Infantry from space of " + tile.getRepresentation() + " to "
                                + Helper.getPlanetRepresentation(planetName, game)));
                    }
                }
                if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                    if (uH instanceof Planet) {
                        buttons.add(Buttons.green(
                            "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_mech",
                            "Move Mech from " + Helper.getPlanetRepresentation(uH.getName(), game) + " to "
                                + Helper.getPlanetRepresentation(planetName, game)));
                    } else {
                        buttons.add(Buttons.green(
                            "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_mech",
                            "Move Mech from space of " + tile.getRepresentation() + " to "
                                + Helper.getPlanetRepresentation(planetName, game)));
                    }
                }
                if (player.hasUnit("titans_pds") || player.hasTech("ht2")) {
                    if (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0) {
                        if (uH instanceof Planet) {
                            buttons.add(Buttons.green(
                                "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_pds",
                                "Move PDS from " + Helper.getPlanetRepresentation(uH.getName(), game) + " to "
                                    + Helper.getPlanetRepresentation(planetName, game)));
                        } else {
                            buttons.add(Buttons.green(
                                "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_pds",
                                "Move PDS from space of " + tile.getRepresentation() + " to "
                                    + Helper.getPlanetRepresentation(planetName, game)));
                        }
                    }
                }
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done moving to this planet"));
        return buttons;
    }

    public static UnitHolder getUnitHolderFromPlanetName(String planetName, Game game) {
        Tile tile = game.getTileFromPlanet(AliasHandler.resolvePlanet(planetName.toLowerCase()));
        if (tile == null) {
            return null;
        }
        return tile.getUnitHolders().get(AliasHandler.resolvePlanet(planetName.toLowerCase()));
    }

    public static String buildMessageFromDisplacedUnits(Game game, boolean landing, Player player, String moveOrRemove, Tile tile) {
        String message;
        Map<String, Integer> displacedUnits = game.getCurrentMovedUnitsFrom1System();
        String prefix = " > " + player.getFactionEmoji();

        StringBuilder messageBuilder = new StringBuilder();
        for (String unit : displacedUnits.keySet()) {
            int amount = displacedUnits.get(unit);
            String damagedMsg = "";
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
                damagedMsg = " damaged ";
            }
            String planet = null;
            if (unit.contains("_")) {
                planet = unit.substring(unit.lastIndexOf("_") + 1);
                unit = unit.replace("_" + planet, "");
            }
            if (landing) {
                messageBuilder.append(prefix).append(" Landed ").append(amount).append(" ").append(damagedMsg)
                    .append(Emojis.getEmojiFromDiscord(unit.toLowerCase()));
                if (planet == null) {
                    messageBuilder.append("\n");
                } else {
                    messageBuilder.append(" on the planet ")
                        .append(Helper.getPlanetRepresentation(planet.toLowerCase(), game)).append("\n");
                }
            } else {
                messageBuilder.append(prefix).append(" ").append(moveOrRemove).append("d ").append(amount).append(" ")
                    .append(damagedMsg).append(Emojis.getEmojiFromDiscord(unit.toLowerCase()));
                if (planet == null) {
                    Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
                    UnitModel uni = player.getUnitByBaseType(unit);
                    if (activeSystem != null && uni != null && uni.getIsShip()
                        && !uni.getBaseType().equalsIgnoreCase("fighter")) {
                        int distance = CheckDistance.getDistanceBetweenTwoTiles(game, player, tile.getPosition(),
                            game.getActiveSystem(), true);
                        int riftDistance = CheckDistance.getDistanceBetweenTwoTiles(game, player, tile.getPosition(),
                            game.getActiveSystem(), false);
                        int moveValue = uni.getMoveValue();
                        if (tile.isNebula() && !player.hasAbility("voidborn") && !player.hasTech("absol_amd")) {
                            moveValue = 1;
                        }
                        if (player.hasTech("as")
                            && FoWHelper.isTileAdjacentToAnAnomaly(game, game.getActiveSystem(), player)) {
                            moveValue++;
                        }
                        if (player.hasAbility("slipstream")
                            && (FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition())
                                || tile == player.getHomeSystemTile())) {
                            moveValue++;
                        }
                        if (!game.getStoredValue("crucibleBoost").isEmpty()) {
                            moveValue = moveValue + 1;
                        }
                        if (!game.getStoredValue("flankspeedBoost").isEmpty()) {
                            moveValue = moveValue + 1;
                        }
                        if (!game.getStoredValue("baldrickGDboost").isEmpty()) {
                            moveValue = moveValue + 1;
                        }

                        if (distance > moveValue && distance < 90) {
                            if (player.hasTech("gd")) {
                                messageBuilder.append(" (Distance exceeds move value (").append(distance).append(" > ").append(moveValue).append("), used gravity drive)");
                            } else {
                                messageBuilder.append(" (Distance exceeds move value (").append(distance).append(" > ").append(moveValue).append("), **did not have gravity drive**)");
                            }
                            if (player.getTechs().contains("dsgledb")) {
                                messageBuilder.append(" (did have lightning drives for +1 if not transporting)");
                            }
                            if (riftDistance < distance) {
                                messageBuilder.append(" (gravity rifts along a path could add +").append(distance - riftDistance).append(" movement if used)");
                                game.setStoredValue("possiblyUsedRift", "yes");
                            }

                        }
                        if (riftDistance < distance) {
                            game.setStoredValue("possiblyUsedRift", "yes");
                        }
                        if (player.hasAbility("celestial_guides")) {
                            game.setStoredValue("possiblyUsedRift", "");
                        }
                    }
                    messageBuilder.append("\n");
                } else {
                    messageBuilder.append(" from the planet ")
                        .append(Helper.getPlanetRepresentation(planet.toLowerCase(), game)).append("\n");
                }
            }

        }
        message = messageBuilder.toString();
        if ("".equalsIgnoreCase(message)) {
            message = "Nothing moved.";
        }
        return message;
    }

    public static List<LayoutComponent> turnButtonListIntoActionRowList(List<Button> buttons) {
        List<LayoutComponent> list = new ArrayList<>();
        List<ItemComponent> buttonRow = new ArrayList<>();
        for (Button button : buttons) {
            if (buttonRow.size() == 5) {
                list.add(ActionRow.of(buttonRow));
                buttonRow = new ArrayList<>();
            }
            if (buttons.size() < 26 || !button.getId().contains("_2")) {
                buttonRow.add(button);
            }
        }
        if (!buttonRow.isEmpty()) {
            list.add(ActionRow.of(buttonRow));
        }

        return list;
    }

    @ButtonHandler("unflipMallice")
    public static void unflipMallice(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();

        if (player.getPlanets().contains("mallice")) {
            player.removePlanet("mallice");
        }
        Tile tile = game.getTileFromPlanet("mallice");

        if (tile != null && "82b".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);
            String planetTileName = AliasHandler.resolveTile("82a");
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return;
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Unflipped mallice");
        }
    }

    @ButtonHandler("addIonStorm_")
    public static void addIonStorm(Game game, String buttonID, ButtonInteractionEvent event, Player player) {
        String pos = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        Tile tile = game.getTileByPosition(pos);
        if (buttonID.contains("alpha")) {
            String tokenFilename = Mapper.getTokenID("ionalpha");
            tile.addToken(tokenFilename, Constants.SPACE);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Added ionstorm alpha to " + tile.getRepresentation());

        } else {
            String tokenFilename = Mapper.getTokenID("ionbeta");
            tile.addToken(tokenFilename, Constants.SPACE);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Added ionstorm beta to " + tile.getRepresentation());
        }
        deleteMessage(event);
        CommanderUnlockCheck.checkPlayer(player, "ghost");
    }

    public static void checkForIonStorm(Game game, Tile tile, Player player) {
        String tokenFilenameAlpha = Mapper.getTokenID("ionalpha");
        UnitHolder space = tile.getUnitHolders().get("space");
        String tokenFilename = Mapper.getTokenID("ionbeta");
        if (space.getTokenList().contains(tokenFilenameAlpha) || space.getTokenList().contains(tokenFilename)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("flipIonStorm_" + tile.getPosition(), "Flip Ion Storm"));
            buttons.add(Buttons.red("deleteButtons", "Not Used"));
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " if you used the Ion Storm please press button to flip it", buttons);
        }
    }

    @ButtonHandler("flipIonStorm_")
    public static void flipIonStorm(Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        Tile tile = game.getTileByPosition(pos);
        String tokenFilenameAlpha = Mapper.getTokenID("ionalpha");
        UnitHolder space = tile.getUnitHolders().get("space");
        String tokenFilename = Mapper.getTokenID("ionbeta");
        if (space.getTokenList().contains(tokenFilenameAlpha)) {
            tile.addToken(tokenFilename, Constants.SPACE);
            tile.removeToken(tokenFilenameAlpha, "space");
        } else {
            tile.removeToken(tokenFilename, Constants.SPACE);
            tile.addToken(tokenFilenameAlpha, "space");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Flipped ionstorm in " + tile.getRepresentation());
        deleteTheOneButton(event);
    }

    public static List<Tile> getAllWormholeTiles(Game game) {
        List<Tile> wormholes = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.doesTileHaveWHs(game, tile.getPosition())) {
                wormholes.add(tile);
            }
        }
        return wormholes;
    }

    public static List<Button> getButtonsForRemovingAllUnitsInSystem(Player player, Game game, Tile tile) {
        return getButtonsForRemovingAllUnitsInSystem(player, game, tile, "combat");
    }

    public static List<Button> getButtonsForRemovingAllUnitsInSystem(Player player, Game game, Tile tile, String type) {
        String finChecker = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();
        game.setStoredValue(player.getFaction() + "latestAssignHits", type);
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String representation = planetRepresentations.get(entry.getKey());
            if (representation == null) {
                representation = entry.getKey();
            }
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet) { // Ground
                if ((type.equalsIgnoreCase("spacecombat") || type.equalsIgnoreCase("assaultcannoncombat"))
                    && !ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, tile)) {
                    continue;
                }
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                        continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null)
                        continue;

                    UnitKey unitKey = unitEntry.getKey();
                    String unitName = unitKey.unitName();

                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                    }
                    int totalUnits = unitEntry.getValue() - damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x < 3; x++) {
                        String buttonID = finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "_" + representation;
                        String buttonText = "Remove " + x + " " + unitModel.getBaseType() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        buttons.add(Buttons.red(buttonID, buttonText, unitModel.getUnitEmoji()));

                        if (unitModel.getSustainDamage() && !type.equalsIgnoreCase("assaultcannoncombat")) {
                            buttonID = finChecker + "assignDamage_" + tile.getPosition() + "_" + x + unitName + "_" + representation;
                            buttonText = "Sustain " + x + " " + unitModel.getBaseType() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                            buttons.add(Buttons.gray(buttonID, buttonText, unitModel.getUnitEmoji()));
                        }
                    }
                    for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                        String buttonID = finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "_" + representation + "damaged";
                        String buttonText = "Remove " + x + " damaged " + unitModel.getBaseType() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        buttons.add(Buttons.red(buttonID, buttonText, unitModel.getUnitEmoji()));
                    }
                }
            } else { // Space
                if (type.equalsIgnoreCase("groundcombat")) {
                    continue;
                }
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                        continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null)
                        continue;
                    UnitKey key = unitEntry.getKey();
                    String unitName = key.unitName();
                    int totalUnits = unitEntry.getValue();
                    int damagedUnits = 0;
                    if (type.equalsIgnoreCase("assaultcannoncombat") && key.getUnitType() == UnitType.Fighter) {
                        continue;
                    }
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    totalUnits = totalUnits - damagedUnits;

                    for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                        buttons.add(Buttons.red(
                            finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "damaged",
                            "Remove " + x + " damaged " + unitModel.getBaseType(),
                            unitModel.getUnitEmoji()));
                    }

                    for (int x = 1; x < totalUnits + 1 && x < 3; x++) {
                        buttons.add(Buttons.red(
                            finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName,
                            "Remove " + x + " " + unitModel.getBaseType(),
                            unitModel.getUnitEmoji()));
                    }
                    if (totalUnits > 0 && !type.equalsIgnoreCase("assaultcannoncombat") && unitCanSustainDamage(game, player, tile, unitName)) {
                        buttons.add(Buttons.gray(
                            finChecker + "assignDamage_" + tile.getPosition() + "_" + 1 + unitName,
                            "Sustain " + 1 + " " + unitModel.getBaseType(),
                            unitModel.getUnitEmoji()));
                    }
                }
            }
        }

        buttons.add(Buttons.gray(finChecker + "assignHits_" + tile.getPosition() + "_AllShips", "Remove all Ships"));
        buttons.add(Buttons.gray(finChecker + "assignHits_" + tile.getPosition() + "_All", "Remove all units"));
        buttons.add(Buttons.blue("deleteButtons", "Done removing/sustaining units"));
        return buttons;
    }

    private static boolean unitCanSustainDamage(Game game, Player player, Tile tile, String unitBaseType) {
        UnitModel unitModel = player.getUnitByBaseType(unitBaseType);
        return "dreadnought".equalsIgnoreCase(unitBaseType)
            || "lady".equalsIgnoreCase(unitBaseType)
            || "cavalry".equalsIgnoreCase(unitBaseType)
            || "flagship".equalsIgnoreCase(unitBaseType)
            || ("cruiser".equalsIgnoreCase(unitBaseType) && unitModel.getSustainDamage())
            || ("carrier".equalsIgnoreCase(unitBaseType) && unitModel.getSustainDamage())
            || ("warsun".equalsIgnoreCase(unitBaseType) && !ButtonHelper.isLawInPlay(game, "schematics"))
            || ("mech".equalsIgnoreCase(unitBaseType) && !game.getLaws().containsKey("articles_war") && player.getUnitsOwned().contains("nomad_mech"))
            || ("mech".equalsIgnoreCase(unitBaseType) && doesPlayerHaveFSHere("nekro_flagship", player, tile))
            || (!player.isActivePlayer() && game.playerHasLeaderUnlockedOrAlliance(player, "mortheuscommander") && !List.of("fighter", "infantry", "mech").contains(unitBaseType.toLowerCase()));
    }

    @ButtonHandler("startThalnos_")
    public static void resolveThalnosStart(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        game.resetThalnosUnits();
        String unitHolderName = buttonID.split("_")[2];
        game.setStoredValue("thalnosInitialHolder", unitHolderName);
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>(getButtonsForRollingThalnos(player, game, tile, tile.getUnitHolders().get(unitHolderName)));
        if ("space".equalsIgnoreCase(unitHolderName)
            && doesPlayerHaveFSHere("nekro_flagship", player, tile)) {
            buttons = new ArrayList<>();
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                buttons.addAll(getButtonsForRollingThalnos(player, game, tile, uH));
            }
        }
        buttons.add(Buttons.blue("rollThalnos_" + tile.getPosition() + "_" + unitHolderName, "Roll Now"));
        buttons.add(Buttons.red("deleteButtons", "Don't roll anything"));
        deleteTheOneButton(event);
        String message = player.getRepresentation()
            + " select the units for which you wish to reroll. Units that fail and did not have extra rolls will be automatically removed";
        MessageHelper.sendMessageToChannel(event.getChannel(), message, buttons);
    }

    @ButtonHandler("setForThalnos_")
    public static void resolveSetForThalnos(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHolderName = game.getStoredValue("thalnosInitialHolder");
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>(getButtonsForRollingThalnos(player, game, tile, tile.getUnitHolders().get(unitHolderName)));
        if ("space".equalsIgnoreCase(unitHolderName)
            && doesPlayerHaveFSHere("nekro_flagship", player, tile)) {
            buttons = new ArrayList<>();
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                buttons.addAll(getButtonsForRollingThalnos(player, game, tile, uH));
            }
        }
        buttons.add(Buttons.blue("rollThalnos_" + tile.getPosition() + "_" + unitHolderName, "Roll Now"));
        buttons.add(Buttons.red("deleteButtons", "Don't roll anything"));
        String id = buttonID.replace("setForThalnos_", "");
        game.setSpecificThalnosUnit(id, game.getSpecificThalnosUnit(id) + 1);

        StringBuilder message = new StringBuilder(player.getRepresentation()
            + " select the units for which you wish to reroll. Units that fail and did not have extra rolls will be automatically removed\n"
            +
            "Currently you are rerolling: \n");
        String damaged = "";
        for (String unit : game.getThalnosUnits().keySet()) {
            String rep = unit.split("_")[2];
            if (rep.contains("damaged")) {
                damaged = "damaged ";
                rep = rep.replace("damaged", "");
            }
            message.append(player.getFactionEmoji()).append(" ").append(game.getSpecificThalnosUnit(unit)).append(" ").append(damaged).append(rep).append("\n");
        }
        List<Button> systemButtons = buttons;
        event.getMessage().editMessage(message.toString())
            .setComponents(turnButtonListIntoActionRowList(systemButtons))
            .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("rollThalnos_")
    public static void resolveRollForThalnos(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHolderName = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);

        String sb = "";
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        if (combatOnHolder == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Cannot find the planet " + unitHolderName + " on tile " + tile.getPosition());
            return;
        }
        CombatRollType rollType = CombatRollType.combatround;
        Map<UnitModel, Integer> playerUnitsByQuantity = CombatHelper.GetUnitsInCombat(tile, combatOnHolder, player,
            event, rollType, game);
        List<UnitModel> units = new ArrayList<>(playerUnitsByQuantity.keySet());
        for (UnitModel unitModel : units) {
            playerUnitsByQuantity.put(unitModel, 0);
            for (String thalnosUnit : game.getThalnosUnits().keySet()) {
                int amount = game.getSpecificThalnosUnit(thalnosUnit);
                String unitName = unitModel.getAsyncId();
                thalnosUnit = thalnosUnit.split("_")[2].replace("damaged", "");
                if (thalnosUnit.equals(unitName)) {
                    playerUnitsByQuantity.put(unitModel, amount + playerUnitsByQuantity.get(unitModel));
                }
            }
            if (playerUnitsByQuantity.get(unitModel) == 0) {
                playerUnitsByQuantity.remove(unitModel);
            }
        }

        if (playerUnitsByQuantity.isEmpty()) {
            // String fightingOnUnitHolderName = unitHolderName;
            // if (!unitHolderName.equalsIgnoreCase(Constants.SPACE)) {
            //     fightingOnUnitHolderName = Helper.getPlanetRepresentation(unitHolderName, game);
            // }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There were no units selected to reroll");
            return;
        }
        game.setStoredValue("thalnosPlusOne", "true");
        List<UnitHolder> combatHoldersForOpponent = new ArrayList<>(List.of(combatOnHolder));
        Player opponent = CombatHelper.GetOpponent(player, combatHoldersForOpponent, game);
        if (opponent == null) {
            opponent = player;
        }
        Map<UnitModel, Integer> opponentUnitsByQuantity = CombatHelper.GetUnitsInCombat(tile, combatOnHolder, opponent,
            event, rollType, game);

        TileModel tileModel = TileHelper.getTileById(tile.getTileID());
        List<NamedCombatModifierModel> modifiers = CombatModHelper.GetModifiers(player, opponent,
            playerUnitsByQuantity, tileModel, game, rollType, Constants.COMBAT_MODIFIERS);

        List<NamedCombatModifierModel> extraRolls = CombatModHelper.GetModifiers(player, opponent,
            playerUnitsByQuantity, tileModel, game, rollType, Constants.COMBAT_EXTRA_ROLLS);

        // Check for temp mods
        CombatTempModHelper.EnsureValidTempMods(player, tileModel, combatOnHolder);
        CombatTempModHelper.InitializeNewTempMods(player, tileModel, combatOnHolder);
        List<NamedCombatModifierModel> tempMods = new ArrayList<>(
            CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(player, tileModel,
                combatOnHolder, false, rollType));
        List<NamedCombatModifierModel> tempOpponentMods = new ArrayList<>();
        tempOpponentMods = CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(opponent, tileModel,
            combatOnHolder, true, rollType);
        tempMods.addAll(tempOpponentMods);

        String message = CombatMessageHelper.displayCombatSummary(player, tile, combatOnHolder, rollType);
        message += CombatHelper.RollForUnits(playerUnitsByQuantity, opponentUnitsByQuantity, extraRolls, modifiers,
            tempMods, player, opponent, game, rollType, event, tile);
        String hits = StringUtils.substringAfter(message, "Total hits ");
        hits = hits.split(" ")[0].replace("*", "");
        int h = Integer.parseInt(hits);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        if (!game.isFowMode() && combatOnHolder instanceof Planet && h > 0 && opponent != player) {
            String msg = opponent.getRepresentationUnfogged() + " you may autoassign "
                + h + " hit" + (h == 1 ? "" : "s") + ".";
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Buttons.green(finChecker + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
        } else {
            if (!game.isFowMode() && opponent != player) {
                String msg = "\n" + opponent.getRepresentation(true, true, true, true) + " you suffered " + h
                    + " hit" + (h == 1 ? "" : "s");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                List<Button> buttons = new ArrayList<>();
                if (h > 0) {
                    int round = 0;
                    String combatName = "combatRoundTracker" + opponent.getFaction() + tile.getPosition()
                        + combatOnHolder.getName();
                    if (game.getStoredValue(combatName).isEmpty()) {
                        round = 1;
                    } else {
                        round = Integer.parseInt(game.getStoredValue(combatName)) + 1;
                    }
                    int round2 = 0;
                    String combatName2 = "combatRoundTracker" + player.getFaction() + tile.getPosition()
                        + combatOnHolder.getName();
                    if (game.getStoredValue(combatName2).isEmpty()) {
                        round2 = 1;
                    } else {
                        round2 = Integer.parseInt(game.getStoredValue(combatName2));
                    }
                    if (round2 > round) {
                        buttons.add(Buttons.blue("combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(),
                            "Roll Dice For Combat Round #" + round));
                    }
                    String finChecker = "FFCC_" + opponent.getFaction() + "_";
                    buttons.add(Buttons.green(finChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                        "Auto-assign Hit" + (h == 1 ? "" : "s")));
                    buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                        "Manually Assign Hit" + (h == 1 ? "" : "s")));
                    buttons.add(Buttons.gray(finChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + h,
                        "Cancel a Hit"));

                    String msg2 = opponent.getFactionEmoji()
                        + " may automatically assign " + (h == 1 ? "the hit" : "hits")
                        + ". The hit" + (h == 1 ? "" : "s") + " would be assigned in the following way:\n\n"
                        + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
                }
            }
        }
        game.setStoredValue("thalnosPlusOne", "false");
        deleteMessage(event);
    }

    public static List<Button> getButtonsForRollingThalnos(Player player, Game game, Tile tile, UnitHolder unitHolder) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        if (unitHolder instanceof Planet) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null)
                    continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = unitKey.unitName();
                if (!unitModel.getIsGroundForce()) {
                    continue;
                }
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                int totalUnits = unitEntry.getValue() - damagedUnits;
                EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                totalUnits = totalUnits - game
                    .getSpecificThalnosUnit(tile.getPosition() + "_" + unitHolder.getName() + "_" + unitName);
                if (totalUnits > 0) {
                    String buttonID = finChecker + "setForThalnos_" + tile.getPosition() + "_" + unitHolder.getName()
                        + "_" + unitName;
                    String buttonText = "Roll 1 " + unitModel.getBaseType() + " from "
                        + Helper.getPlanetRepresentation(unitHolder.getName(), game);
                    Button validTile2 = Buttons.red(buttonID, buttonText);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                damagedUnits = damagedUnits - game.getSpecificThalnosUnit(
                    tile.getPosition() + "_" + unitHolder.getName() + "_" + unitName + "damaged");
                if (damagedUnits > 0) {
                    String buttonID = finChecker + "setForThalnos_" + tile.getPosition() + "_" + unitHolder.getName()
                        + "_" + unitName + "damaged";
                    String buttonText = "Roll 1 damaged " + unitModel.getBaseType() + " from "
                        + Helper.getPlanetRepresentation(unitHolder.getName(), game);
                    Button validTile2 = Buttons.red(buttonID, buttonText);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
            }
        } else {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null)
                    continue;
                UnitKey key = unitEntry.getKey();
                String unitName = key.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(key);
                }
                totalUnits = totalUnits - damagedUnits;
                EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                damagedUnits = damagedUnits - game.getSpecificThalnosUnit(
                    tile.getPosition() + "_" + unitHolder.getName() + "_" + unitName + "damaged");
                if (damagedUnits > 0) {
                    Button validTile2 = Buttons.red(
                        finChecker + "setForThalnos_" + tile.getPosition() + "_" + unitHolder.getName() + "_"
                            + unitName + "damaged",
                        "Roll 1 damaged " + unitModel.getBaseType());
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                totalUnits = totalUnits - game
                    .getSpecificThalnosUnit(tile.getPosition() + "_" + unitHolder.getName() + "_" + unitName);
                if (totalUnits > 0) {
                    Button validTile2 = Buttons.red(finChecker + "setForThalnos_" + tile.getPosition() + "_"
                        + unitHolder.getName() + "_" + unitName, "Roll 1 " + unitModel.getBaseType());
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
            }
        }
        return buttons;
    }

    public static List<Button> getUserSetupButtons(Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            String userId = player.getUserID();
            if (player.isNotRealPlayer() || player.getSo() < 1) {
                buttons.add(Buttons.green("setupStep1_" + userId, player.getUserName()));
            }
        }
        return buttons;
    }

    public static void setUpFrankenFactions(Game game, GenericInteractionCreateEvent event) {
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        int x = 1;
        for (Player player : players) {
            if (x < 9) {
                switch (x) {
                    case 1 -> Setup.secondHalfOfPlayerSetup(player, game, "black", "franken1", "201", event, false);
                    case 2 -> Setup.secondHalfOfPlayerSetup(player, game, "green", "franken2", "202", event, false);
                    case 3 -> Setup.secondHalfOfPlayerSetup(player, game, "purple", "franken3", "203", event, false);
                    case 4 -> Setup.secondHalfOfPlayerSetup(player, game, "orange", "franken4", "204", event, false);
                    case 5 -> Setup.secondHalfOfPlayerSetup(player, game, "pink", "franken5", "205", event, false);
                    case 6 -> Setup.secondHalfOfPlayerSetup(player, game, "yellow", "franken6", "206", event, false);
                    case 7 -> Setup.secondHalfOfPlayerSetup(player, game, "red", "franken7", "207", event, false);
                    case 8 -> Setup.secondHalfOfPlayerSetup(player, game, "blue", "franken8", "208", event, false);
                    default -> {

                    }
                }
            }
            x++;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "You have all been set up as franken factions. These have similar zombie emojis as their default faction icon. You should personalize yours with /franken set_faction_icon. You may use any emoji the bot may use.");
    }

    public static List<Button> getFactionSetupButtons(Game game, String buttonID) {
        String userId = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        List<FactionModel> factionsOnMap = Mapper.getFactions().stream()
            .filter(f -> game.getTile(f.getHomeSystem()) != null)
            .filter(f -> game.getPlayerFromColorOrFaction(f.getAlias()) == null)
            .toList();
        List<FactionModel> allFactions = Mapper.getFactions().stream()
            .filter(f -> game.isDiscordantStarsMode() ? f.getSource().isDs() : f.getSource().isOfficial())
            .filter(f -> game.getPlayerFromColorOrFaction(f.getAlias()) == null)
            .sorted((f1, f2) -> factionsOnMap.contains(f1) ? (factionsOnMap.contains(f2) ? 0 : -1) : (factionsOnMap.contains(f2) ? 1 : 0))
            .toList();

        Set<String> factionsComplete = new HashSet<>();
        for (FactionModel faction : allFactions) {
            String factionId = faction.getAlias();
            if (game.getPlayerFromColorOrFaction(factionId) == null) {
                String name = faction.getFactionName();
                if (factionId.contains("keleres")) {
                    factionId = "keleres";
                    name = "The Council Keleres";
                }
                if (factionsComplete.contains(factionId))
                    continue;
                Emoji factionEmoji = Emoji.fromFormatted(Emojis.getFactionIconFromDiscord(factionId));
                buttons.add(Buttons.green("setupStep2_" + userId + "_" + factionId, name).withEmoji(factionEmoji));
            }

            factionsComplete.add(factionId);
        }
        return buttons;
    }

    public static boolean isNumeric(String strNum) {
        if (strNum == null)
            return false;
        try {
            Integer.parseInt(strNum);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    public static void cloneGame(GenericInteractionCreateEvent event, Game game) {
        String name = game.getName();
        GameSaveLoadManager.saveGame(game, event);
        String newName = name + "clone";
        Guild guild = game.getGuild();
        Role gameRole = null;
        String gameName = newName;
        String gameFunName = game.getCustomName();
        String newChatChannelName = gameName + "-" + gameFunName;
        String newActionsChannelName = gameName + Constants.ACTIONS_CHANNEL_SUFFIX;
        String newBotThreadName = gameName + Constants.BOT_CHANNEL_SUFFIX;

        long permission = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();

        gameRole = guild.createRole()
            .setName(newName)
            .setMentionable(true)
            .complete();
        for (Player player : game.getRealPlayers()) {
            Member member = guild.getMemberById(player.getUserID());
            if (member != null) {
                guild.addRoleToMember(member, gameRole).complete();
            }
        }
        Category category = game.getMainGameChannel().getParentCategory();
        long gameRoleID = gameRole.getIdLong();
        // CREATE TABLETALK CHANNEL
        TextChannel chatChannel = guild.createTextChannel(newChatChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();

        // CREATE ACTIONS CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();

        //String undoFileToRestorePath = game.getName() + "_" + 1 + ".txt";
        //File undoFileToRestore = new File(Storage.getMapUndoDirectory(), undoFileToRestorePath);

        File originalMapFile = Storage.getGameFile(game.getName() + Constants.TXT);

        File mapUndoDirectory = Storage.getGameUndoDirectory();
        if (!mapUndoDirectory.exists()) {
            return;
        }

        String mapName = game.getName();
        String mapNameForUndoStart = mapName + "_";
        String[] mapUndoFiles = mapUndoDirectory.list((dir, name2) -> name2.startsWith(mapNameForUndoStart));
        if (mapUndoFiles != null && mapUndoFiles.length > 0) {
            try {
                List<Integer> numbers = Arrays.stream(mapUndoFiles)
                    .map(fileName -> fileName.replace(mapNameForUndoStart, ""))
                    .map(fileName -> fileName.replace(Constants.TXT, ""))
                    .map(Integer::parseInt).toList();
                int maxNumber = numbers.isEmpty() ? 0
                    : numbers.stream().mapToInt(value -> value)
                        .max().orElseThrow(NoSuchElementException::new);

                File mapUndoStorage = Storage.getGameUndoStorage(mapName + "_" + maxNumber + Constants.TXT);
                CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
                Files.copy(mapUndoStorage.toPath(), originalMapFile.toPath(), options);
                Game gameToRestore = GameSaveLoadManager.loadGame(originalMapFile);
                gameToRestore.setTableTalkChannelID(chatChannel.getId());
                gameToRestore.setMainChannelID(actionsChannel.getId());
                gameToRestore.setName(newName);
                gameToRestore.shuffleDecks();
                GameManager.addGame(gameToRestore);
                // CREATE BOT/MAP THREAD
                ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName)
                    .complete();
                gameToRestore.setBotMapUpdatesThreadID(botThread.getId());
                for (Player player : gameToRestore.getRealPlayers()) {
                    player.setCardsInfoThreadID(null);
                }
                GameSaveLoadManager.saveGame(gameToRestore, event);
            } catch (Exception ignored) {

            }

        }

    }

    public static List<Button> getColorSetupButtons(Game game, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        List<ColorModel> unusedColors = game.getUnusedColors();
        unusedColors = ColourHelper.sortColours(factionId, unusedColors);
        for (ColorModel color : unusedColors) {
            String colorName = color.getName();
            Emoji colorEmoji = color.getEmoji();
            String step3id = "setupStep3_" + userId + "_" + factionId + "_" + colorName;
            buttons.add(Buttons.green(step3id, colorName).withEmoji(colorEmoji));
        }
        return buttons;
    }

    public static void offerPlayerSetupButtons(MessageChannel channel, Game game) {
        Helper.checkThreadLimitAndArchive(game.getGuild());

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("startPlayerSetup", "Setup a Player"));
        String message = "After setting up the map, you may use this button instead of /player setup if you wish.";
        for (Player player : game.getPlayers().values()) {
            try {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() + message, buttons);
            } catch (Exception e) {
                BotLogger.log("Failing to set up player cards info threads in " + game.getName(), e);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
    }

    public static void offerRedTapeButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String poS : game.getPublicObjectives1Peakable()) {
            buttons.add(Buttons.green("cutTape_" + poS, Mapper.getPublicObjective(poS).getName()));
        }
        for (String poS : game.getPublicObjectives2Peakable()) {
            buttons.add(Buttons.green("cutTape_" + poS, Mapper.getPublicObjective(poS).getName()));
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation()
                + "Choose an objective to make scorable. Reminder that in a normal game you can't choose a stage 2 to make scorable until after round 3 is over",
            buttons);
    }

    @ButtonHandler("cutTape_")
    public static void cutTape(Game game, String buttonID, ButtonInteractionEvent event) {
        String poID = buttonID.replace("cutTape_", "");
        int location = 1;
        deleteMessage(event);
        List<String> po1s = new ArrayList<>(game.getPublicObjectives1Peakable());
        for (String poS : po1s) {
            if (poS.equalsIgnoreCase(poID)) {
                game.swapStage1(1, location);
                Map.Entry<String, Integer> objective = game.revealStage1();
                PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    game.getPing() + " **Stage 1 Public Objective Now Scorable**");
                game.getMainGameChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
                return;
            }
            location++;
        }
        location = 1;
        List<String> po2s = new ArrayList<>(game.getPublicObjectives2Peakable());
        for (String poS : po2s) {
            if (poS.equalsIgnoreCase(poID)) {
                game.swapStage2(1, location);
                Map.Entry<String, Integer> objective = game.revealStage2();
                PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    game.getPing() + " **Stage 2 Public Objective Now Scorable**");
                game.getMainGameChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
                return;
            }
            location++;
        }
    }

    @ButtonHandler("getHomebrewButtons")
    public static void offerHomeBrewButtons(Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        game.setHomebrew(false);
        buttons.add(Buttons.green("setupHomebrew_444", "4 stage 1s, 4 stage 2s, 4 secrets, 12 VP"));
        buttons.add(Buttons.green("setupHomebrew_absolRelicsNAgendas", "Absol Relics And Agendas", Emojis.Absol));
        buttons.add(Buttons.green("setupHomebrew_absolTechsNMechs", "Absol Techs and Mechs", Emojis.Absol));
        buttons.add(Buttons.green("setupHomebrew_dsfactions", "Discordant Stars Factions", Emojis.DiscordantStars));
        buttons.add(Buttons.green("setupHomebrew_dsexplores", "US Explores/Relics/ACs", Emojis.UnchartedSpace));
        buttons.add(Buttons.green("setupHomebrew_acDeck2", "Action Cards Deck 2", Emojis.ActionDeck2));
        buttons.add(Buttons.green("setupHomebrew_456", "5 stage 1s, 6 stage 2s, 4 secrets, 14 VP"));
        buttons.add(Buttons.green("setupHomebrew_redTape", "Red Tape"));
        buttons.add(Buttons.green("setupHomebrew_removeSupports", "Remove Supports"));
        buttons.add(Buttons.green("setupHomebrew_homebrewSCs", "Homebrew SCs"));
        buttons.add(Buttons.red("deleteButtons", "Done With Buttons"));
        deleteMessage(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Game has been marked as homebrew, chose which homebrew you'd like in the game, or press Done With Buttons",
            buttons);
    }

    @ButtonHandler("setupHomebrew_")
    public static void setUpHomebrew(Game game, ButtonInteractionEvent event, String buttonID) {
        deleteTheOneButton(event);
        String type = buttonID.split("_")[1];

        switch (type) {
            case "444" -> {
                game.setMaxSOCountPerPlayer(4);
                game.setUpPeakableObjectives(4, 1);
                game.setUpPeakableObjectives(4, 2);
                game.setVp(12);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set up 4/4/4");
            }
            case "removeSupports" -> {
                for (Player p2 : game.getRealPlayers()) {
                    p2.removeOwnedPromissoryNoteByID(p2.getColor() + "_sftt");
                    p2.removePromissoryNote(p2.getColor() + "_sftt");
                }
                game.setStoredValue("removeSupports", "true");
            }
            case "456" -> {
                game.setMaxSOCountPerPlayer(4);
                game.setUpPeakableObjectives(5, 1);
                game.setUpPeakableObjectives(6, 2);
                game.setVp(14);
                game.setStoredValue("homebrewMode", "456");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set up 4/5/6/14VP");
            }
            case "absolRelicsNAgendas" -> {
                game.setAbsolMode(true);
                game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"));
                if (game.isDiscordantStarsMode() && game.getRelicDeckID().contains("ds")) {
                    game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol_ds"));
                } else {
                    game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol"));
                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set the relics and agendas to Absol stuff");
            }
            case "absolTechsNMechs" -> {
                game.setAbsolMode(true);
                if (game.isDiscordantStarsMode()) {
                    game.setTechnologyDeckID("techs_ds_absol");
                } else {
                    game.setTechnologyDeckID("techs_absol");
                }
                game.swapInVariantUnits("absol");
                game.swapInVariantTechs();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set the techs & mechs to Absol stuff");
            }
            case "dsexplores" -> {
                game.setDiscordantStarsMode(true);
                game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_DS"));
                game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"));
                if (game.isAbsolMode()) {
                    if (game.getTechnologyDeckID().contains("absol")) {
                        game.setTechnologyDeckID("techs_ds_absol");
                    }
                    if (game.getRelicDeckID().contains("absol")) {
                        game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol_ds"));
                    }

                } else {
                    game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_ds"));
                    game.setTechnologyDeckID("techs_ds");

                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set the explores/ACs/relics to DS stuff");
            }
            case "acDeck2" -> {
                game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_deck_2"));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set the AC deck to AC Deck 2");
            }
            case "dsfactions" -> {
                game.setDiscordantStarsMode(true);
                if (game.getTechnologyDeckID().contains("absol")) {
                    game.setTechnologyDeckID("techs_ds_absol");
                } else {
                    game.setTechnologyDeckID("techs_ds");
                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set game to DS mode. Only includes factions and planets unless you also click/clicked the DS Explores button");
            }
            case "homebrewSCs" -> {
                game.setHomebrewSCMode(true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to homebrew strategy card mode");
            }
            case "redTape" -> {
                game.setRedTapeMode(true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to red tape mode");
            }
        }
    }

    @ButtonHandler("startPlayerSetup")
    public static void resolveSetupStep0(Player player, Game game, ButtonInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentation() + "Please tell the bot which user you are setting up",
            getUserSetupButtons(game));
    }

    public static List<Button> getGainAndLoseCCButtons(Player player) {
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        buttons.removeIf(b -> !b.getId().startsWith("increase_")); // remove the wiring buttons
        buttons.addAll(ButtonHelper.getLoseCCButtons(player)); // add the redistro buttons
        return buttons;
    }

    public static List<Button> getGainCCButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_tactic_cc", "Gain 1 Tactic CC"));
        buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_fleet_cc", "Gain 1 Fleet CC"));
        buttons.add(
            Buttons.green(player.getFinsFactionCheckerPrefix() + "increase_strategy_cc", "Gain 1 Strategy CC"));
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Done Gaining CCs"));
        buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "resetCCs", "Reset CCs"));
        player.getGame().setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        return buttons;
    }

    public static List<Button> getLoseCCButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "decrease_tactic_cc", "Lose 1 Tactic CC"));
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "decrease_fleet_cc", "Lose 1 Fleet CC"));
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "decrease_strategy_cc", "Lose 1 Strategy CC"));
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Done Gaining CCs"));
        buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "resetCCs", "Reset CCs"));
        player.getGame().setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        return buttons;
    }

    @ButtonHandler("setupStep1_")
    public static void resolveSetupStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        if (game.isTestBetaFeaturesMode()) {
            SelectFaction.offerFactionSelectionMenu(event);
            return;
        }

        String userId = buttonID.split("_")[1];
        deleteMessage(event);
        List<Button> buttons = getFactionSetupButtons(game, buttonID);
        List<Button> newButtons = new ArrayList<>();
        int maxBefore = -1;
        long numberOfHomes = Mapper.getFactions().stream()
            .filter(f -> game.getTile(f.getHomeSystem()) != null)
            .filter(f -> game.getPlayerFromColorOrFaction(f.getAlias()) == null)
            .count();
        if (numberOfHomes <= 0) {
            numberOfHomes = 22;
        } else {
            numberOfHomes = numberOfHomes - 1;
        }

        for (int x = 0; x < buttons.size(); x++) {
            if (x <= maxBefore + numberOfHomes) {
                newButtons.add(buttons.get(x));
            }
        }
        newButtons.add(Buttons.gray("setupStep2_" + userId + "_" + (maxBefore + numberOfHomes) + "!", "Get more factions"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the desired faction", newButtons);
    }

    @ButtonHandler("setupStep2_")
    public static void resolveSetupStep2(Player player, Game game, GenericInteractionCreateEvent event, String buttonID) {
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        if (event instanceof ButtonInteractionEvent) {
            ((ComponentInteraction) event).getMessage().delete().queue();
        }
        if (factionId.contains("!")) {
            List<Button> buttons = getFactionSetupButtons(game, buttonID);
            List<Button> newButtons = new ArrayList<>();
            int maxBefore = Integer.parseInt(factionId.replace("!", ""));
            for (int x = 0; x < buttons.size(); x++) {
                if (x > maxBefore && x < (maxBefore + 23)) {
                    newButtons.add(buttons.get(x));
                }
            }
            int additionalMax = Math.min(buttons.size(), maxBefore + 22);
            if (additionalMax != buttons.size()) {
                newButtons.add(
                    Buttons.gray("setupStep2_" + userId + "_" + (maxBefore + 22) + "!", "Get more factions"));
            } else {
                newButtons.add(Buttons.gray("setupStep2_" + userId + "_-1!", "Go back"));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Please tell the bot the desired faction", newButtons);
            return;
        }
        if ("keleres".equalsIgnoreCase(factionId)) {
            List<Button> newButtons = new ArrayList<>();
            newButtons.add(Buttons.green("setupStep2_" + userId + "_keleresa", "Keleres Argent", Emojis.Argent));
            newButtons.add(Buttons.green("setupStep2_" + userId + "_keleresm", "Keleres Mentak", Emojis.Mentak));
            newButtons.add(Buttons.green("setupStep2_" + userId + "_keleresx", "Keleres Xxcha", Emojis.Xxcha));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Please tell the bot which flavor of keleres you are", newButtons);
            return;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Setting up as faction: " + Mapper.getFaction(factionId).getFactionName());
        offerColorSetupButtons(game, event, buttonID, userId, factionId);
    }

    private static void offerColorSetupButtons(Game game, GenericInteractionCreateEvent event, String buttonID, String userId, String factionId) {
        List<Button> buttons = getColorSetupButtons(game, buttonID);
        List<Button> newButtons = new ArrayList<>();
        int maxBefore = -1;
        for (int x = 0; x < buttons.size(); x++) {
            if (x < maxBefore + 23) {
                newButtons.add(buttons.get(x));
            }
        }
        newButtons.add(Buttons.gray("setupStep3_" + userId + "_" + factionId + "_" + (maxBefore + 22) + "!", "Get more colors"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please tell the bot the desired player color", newButtons);
    }

    public static List<Button> getSpeakerSetupButtons(Game game, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        String pos = buttonID.split("_")[4];
        buttons.add(Buttons.green("setupStep5_" + userId + "_" + factionId + "_" + color + "_" + pos + "_yes",
            "Yes, setting up speaker"));
        buttons.add(Buttons.green("setupStep5_" + userId + "_" + factionId + "_" + color + "_" + pos + "_no", "No"));
        return buttons;
    }

    @ButtonHandler("setupStep3_")
    public static void resolveSetupStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        deleteMessage(event);
        if (color.contains("!")) {
            List<Button> buttons = getColorSetupButtons(game, buttonID);
            List<Button> newButtons = new ArrayList<>();
            int maxBefore = Integer.parseInt(color.replace("!", ""));
            for (int x = 0; x < buttons.size(); x++) {
                if (x > maxBefore && x < (maxBefore + 23)) {
                    newButtons.add(buttons.get(x));
                }
            }
            int additionalMax = Math.min(buttons.size(), maxBefore + 22);
            if (additionalMax != buttons.size()) {
                newButtons.add(Buttons.gray("setupStep3_" + userId + "_" + factionId + "_" + (maxBefore + 22) + "!", "Get more colors"));
            } else {
                newButtons.add(Buttons.gray("setupStep3_" + userId + "_" + factionId + "_-1!", "Go back"));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the desired color", newButtons);
            return;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Setting up as color: " + color);

        List<Button> buttons = new ArrayList<>();

        for (Tile tile : game.getTileMap().values()) {
            FactionModel fModel = Mapper.getFaction(factionId);
            if (fModel.getHomeSystem().equalsIgnoreCase(tile.getTileID())) {
                resolveSetupStep4And5(game, event,
                    "setupStep4_" + userId + "_" + factionId + "_" + color + "_" + tile.getPosition());
                return;
            }
            if (tile.isHomeSystem()) {

                String rep = tile.getRepresentation();
                if (rep == null || rep.isEmpty()) {
                    rep = tile.getTileID() + "(" + tile.getPosition() + ")";
                }
                buttons.add(Buttons.green(
                    "setupStep4_" + userId + "_" + factionId + "_" + color + "_" + tile.getPosition(), rep));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            "Please tell the bot the home system location", buttons);

    }

    @ButtonHandler("setupStep4_")
    @ButtonHandler("setupStep5_")
    public static void resolveSetupStep4And5(Game game, ButtonInteractionEvent event, String buttonID) {
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        String pos = buttonID.split("_")[4];
        Player speaker = null;
        Player player = game.getPlayer(userId);
        if (game.getPlayer(game.getSpeakerUserID()) != null) {
            speaker = game.getPlayers().get(game.getSpeakerUserID());
        }
        if (game.getPlayerFromColorOrFaction(color) != null)
            color = player.getNextAvailableColour();
        if (buttonID.split("_").length == 6 || speaker != null) {
            if (speaker != null) {
                Setup.secondHalfOfPlayerSetup(player, game, color, factionId, pos, event, false);
            } else {
                Setup.secondHalfOfPlayerSetup(player, game, color, factionId, pos, event,
                    "yes".equalsIgnoreCase(buttonID.split("_")[5]));
            }
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                "Please tell the bot if the player is the speaker", getSpeakerSetupButtons(game, buttonID));
        }
        deleteMessage(event);
    }

    /**
     * Check all colors in the active game and print out errors and possible
     * solutions if any have too low of a luminance variation
     */
    public static void resolveSetupColorChecker(Game game) {
        @Data
        class Collision {
            Player p1, p2;
            double contrast;

            Collision(Player p1, Player p2, double contrast) {
                this.p1 = p1;
                this.p2 = p2;
                this.contrast = contrast;
            }
        }

        List<Player> players = game.getRealPlayers();
        List<Collision> issues = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Player p1 = players.get(i);
            ColorModel c1 = Mapper.getColor(p1.getColor());
            for (int j = i + 1; j < players.size(); j++) {
                Player p2 = players.get(j);
                ColorModel c2 = Mapper.getColor(p2.getColor());

                double contrast = c1.contrastWith(c2);
                if (contrast < 2.5) {
                    Collision e1 = new Collision(p1, p2, contrast);
                    issues.add(e1);
                }
            }
        }

        if (issues.isEmpty())
            return;

        StringBuilder sb = new StringBuilder(
            "### The following pairs of players have colors with a low contrast value:\n");
        for (Collision issue : issues) {
            sb.append("> ").append(issue.p1.getRepresentation(false, false)).append(" & ")
                .append(issue.p2.getRepresentation(false, false)).append("  -> ");
            sb.append("Ratio = 1:").append(issue.contrast);
            if (issue.contrast < 2) {
                sb.append("(very bad!)");
            }
            sb.append("\n");
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb.toString());
    }

    public static String getUnitHolderRep(UnitHolder unitHolder, Tile tile, Game game) {
        String name = unitHolder.getName();
        if ("space".equalsIgnoreCase(name)) {
            name = "Space Area of " + tile.getRepresentation();
        } else {
            if (unitHolder instanceof Planet) {
                name = Helper.getPlanetRepresentation(name, game);
            }
        }
        return name;
    }

    public static Set<Tile> getTilesOfUnitsWithProduction(Player player, Game game) {
        Set<Tile> tilesWithProduction = game.getTileMap().values().stream()
            .filter(tile -> tile.containsPlayersUnitsWithModelCondition(player,
                unit -> unit.getProductionValue() > 0))
            .collect(Collectors.toSet());
        if (player.hasAbility("voidmaker")) {
            for (Tile t : game.getTileMap().values()) {
                if (t.containsPlayersUnitsWithModelCondition(player, UnitModel::getIsShip)
                    && t.getPlanetUnitHolders().isEmpty()) {
                    tilesWithProduction.add(t);
                }
            }
        }
        if (player.hasUnit("ghoti_flagship")) {
            tilesWithProduction.addAll(getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship));
        }
        if (player.hasTech("mr") || player.hasTech("absol_mr")) {
            List<Tile> tilesWithNovaAndUnits = game.getTileMap().values().stream()
                .filter(Tile::isSupernova)
                .filter(tile -> tile.containsPlayersUnits(player))
                .toList();
            tilesWithProduction.addAll(tilesWithNovaAndUnits);
        }
        if (player.hasTech("iihq") && player.controlsMecatol(false)) {
            Tile mr = game.getMecatolTile();
            tilesWithProduction.add(mr);
        }
        return tilesWithProduction;
    }

    public static void increasePingCounter(Game reference, String playerID) {
        int count = 0;
        if (reference.getStoredValue("pingsFor" + playerID).isEmpty()) {
            count = 1;
        } else {
            count = Integer.parseInt(reference.getStoredValue("pingsFor" + playerID)) + 1;
        }
        reference.setStoredValue("pingsFor" + playerID, "" + count);
    }

    public static List<Tile> getTilesOfUnitsWithBombard(Player player, Game game) {
        return game.getTileMap().values().stream()
            .filter(tile -> tile.containsPlayersUnitsWithModelCondition(player,
                unit -> unit.getBombardDieCount() > 0))
            .toList();
    }

    public static List<Button> getButtonsForStellar(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tilesWithBombard = getTilesOfUnitsWithBombard(player, game);
        Set<String> adjacentTiles = FoWHelper.getAdjacentTilesAndNotThisTile(game,
            tilesWithBombard.getFirst().getPosition(), player, false);
        for (Tile tile : tilesWithBombard) {
            adjacentTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false));
        }
        for (String pos : adjacentTiles) {
            Tile tile = game.getTileByPosition(pos);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    if (!player.getPlanetsAllianceMode().contains(planet.getName())
                        && !isPlanetLegendaryOrHome(unitHolder.getName(), game, false, player)
                        && !Constants.MECATOLS.contains(planet.getName())) {
                        buttons.add(Buttons.green(finChecker + "stellarConvert_" + planet.getName(),
                            "Stellar Convert " + Helper.getPlanetRepresentation(planet.getName(), game)));
                    }
                }
            }
        }
        return buttons;
    }

    public static int getNumberOfGravRiftsPlayerIsIn(Player player, Game game) {
        return (int) game.getTileMap().values().stream()
            .filter(tile -> tile.isGravityRift(game) && tile.containsPlayersUnits(player)).count();
    }

    public static List<Button> getButtonsForRepairingUnitsInASystem(Player player, Game game, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();

            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null)
                    continue;

                UnitKey key = unitEntry.getKey();
                String unitName = key.unitName();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(key);
                }

                EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                for (int x = 1; x < damagedUnits + 1 && x < 3; x++) {
                    String buttonID = finChecker + "repairDamage_" + tile.getPosition() + "_" + x + unitName;
                    String buttonText = "Repair " + x + " damaged " + unitModel.getBaseType();
                    if (unitHolder instanceof Planet) {
                        buttonID += "_" + representation;
                        buttonText += " from "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                    }
                    Button validTile3 = Buttons.green(buttonID, buttonText);
                    validTile3 = validTile3.withEmoji(emoji);
                    buttons.add(validTile3);
                }
            }
        }
        Button concludeMove = Buttons.blue("deleteButtons", "Done repairing units");
        buttons.add(concludeMove);
        return buttons;
    }

    public static void showFeatureType(GenericInteractionCreateEvent event, Game game, DisplayType feature) {
        MapRenderPipeline.render(game, event, feature,
            fileUpload -> MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), fileUpload));
    }

    public static List<Player> tileHasPDS2Cover(Player player, Game game, String tilePos) {
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tilePos, player, false, true);
        for (Player p2 : game.getRealPlayers()) {
            adjTiles.addAll(FoWHelper.getAdjacentTiles(game, tilePos, p2, false, true));
        }
        List<Player> playersWithPds2 = new ArrayList<>();
        if (FoWHelper.otherPlayersHaveShipsInSystem(player, game.getTileByPosition(tilePos), game)
            && player.hasAbility("starfall_gunnery")
            && checkNumberNonFighterShipsWithoutSpaceCannon(player, game, game.getTileByPosition(tilePos)) > 0) {
            playersWithPds2.add(player);
        }
        for (String adjTilePos : adjTiles) {
            Tile adjTile = game.getTileByPosition(adjTilePos);
            if (adjTile == null) {
                BotLogger.log("`ButtonHelper.tileHasPDS2Cover` Game: " + game.getName() + " Tile: " + tilePos
                    + " has a null adjacent tile: `" + adjTilePos + "` within: `" + adjTiles + "`");
                continue;
            }
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                if (tilePos.equalsIgnoreCase(adjTilePos) && Constants.MECATOLS.contains(unitHolder.getName())) {
                    for (Player p2 : game.getRealPlayers()) {
                        if (p2.controlsMecatol(false) && p2.getTechs().contains("iihq")
                            && !playersWithPds2.contains(p2)) {
                            playersWithPds2.add(p2);
                        }
                    }
                }
                for (Map.Entry<UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                    if (unitEntry.getValue() == 0) {
                        continue;
                    }

                    UnitKey unitKey = unitEntry.getKey();
                    Player owningPlayer = game.getPlayerByColorID(unitKey.getColorID()).orElse(null);
                    if (owningPlayer == null || playersWithPds2.contains(owningPlayer) || !FoWHelper
                        .getAdjacentTiles(game, tilePos, owningPlayer, false, true).contains(adjTilePos)) {
                        continue;
                    }

                    UnitModel model = owningPlayer.getUnitFromUnitKey(unitKey);
                    if (model == null || (model.getId().equalsIgnoreCase("xxcha_mech")
                        && ButtonHelper.isLawInPlay(game, "articles_war"))) {
                        continue;
                    }
                    if (model.getSpaceCannonDieCount() > 0 && (model.getDeepSpaceCannon() || tilePos.equalsIgnoreCase(adjTilePos) || game.playerHasLeaderUnlockedOrAlliance(owningPlayer, "mirvedacommander"))) {
                        if (owningPlayer == player || player.getAllianceMembers().contains(owningPlayer.getFaction())) {
                            if (FoWHelper.otherPlayersHaveShipsInSystem(player, game.getTileByPosition(tilePos), game)) {
                                playersWithPds2.add(owningPlayer);
                            }
                        } else {
                            playersWithPds2.add(owningPlayer);
                        }
                    }
                }
            }
        }
        return playersWithPds2;
    }

    public static void sendEBSWarning(Player player, Game game, String tilePos) {
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tilePos, player, false);
        for (String adjTilePos : adjTiles) {
            Tile adjTile = game.getTileByPosition(adjTilePos);
            if (adjTile == null) {
                BotLogger.log("`ButtonHelper.tileHasPDS2Cover` Game: " + game.getName() + " Tile: " + tilePos
                    + " has a null adjacent tile: `" + adjTilePos + "` within: `" + adjTiles + "`");
                continue;
            }
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                for (Map.Entry<UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                    if (unitEntry.getValue() == 0) {
                        continue;
                    }

                    UnitKey unitKey = unitEntry.getKey();
                    Player owningPlayer = game.getPlayerByColorID(unitKey.getColorID()).orElse(null);
                    if (owningPlayer == null || owningPlayer == player) {
                        continue;
                    }

                    UnitModel model = owningPlayer.getUnitFromUnitKey(unitKey);
                    if (owningPlayer.getActionCards().containsKey("experimental") && model != null
                        && "spacedock".equalsIgnoreCase(model.getBaseType())) {
                        MessageHelper.sendMessageToChannel(owningPlayer.getCardsInfoThread(),
                            owningPlayer.getRepresentation()
                                + " this is a reminder that this is the window to play Experimental Battlestation");
                        return;
                    }
                }
            }
        }

    }

    public static void fixRelics(Game game) {
        for (Player player : game.getPlayers().values()) {
            if (player != null && player.getRelics() != null) {
                List<String> rels = new ArrayList<>(player.getRelics());
                for (String relic : rels) {
                    if (relic.contains("extra")) {
                        player.removeRelic(relic);
                        relic = relic.replace("extra1", "");
                        relic = relic.replace("extra2", "");
                        player.addRelic(relic);
                    }
                }
            }
        }
    }

    public static void fixAllianceMembers(Game game) {
        for (Player player : game.getRealPlayers()) {
            player.setAllianceMembers("");
        }
    }

    public static void startMyTurn(GenericInteractionCreateEvent event, Game game, Player player) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);
        String msg;
        String msgExtra = "";
        Player privatePlayer = player;

        // INFORM FIRST PLAYER IS UP FOR ACTION
        if (player != null) {
            msgExtra += "# " + player.getRepresentation() + " is up for an action";
            game.updateActivePlayer(player);
            if (game.isFowMode()) {
                FoWHelper.pingAllPlayersWithFullStats(game, event, player, "started turn");
            }
            ButtonHelperFactionSpecific.resolveMilitarySupportCheck(player, game);
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, game);

            game.setPhaseOfGame("action");
        }

        msg = "";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (isFowPrivateGame) {
            if (privatePlayer == null) {
                BotLogger.log(event, "`ButtonHelper.startMyTurn` privatePlayer is null");
                return;
            }
            msgExtra = privatePlayer.getRepresentationUnfogged() + " UP NEXT";
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, game, event, msgExtra, fail, success);
            game.updateActivePlayer(privatePlayer);

            MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(),
                msgExtra + "\n Use Buttons to do turn.",
                TurnStart.getStartOfTurnButtons(privatePlayer, game, false, event));

            if (privatePlayer.getGenSynthesisInfantry() > 0) {
                if (!getPlaceStatusInfButtons(game, privatePlayer).isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(),
                        "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry()
                            + " infantry left to revive.",
                        getPlaceStatusInfButtons(game, privatePlayer));
                } else {
                    privatePlayer.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer
                        .getRepresentation()
                        + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether");

                }
            }

        } else {
            if (!msgExtra.isEmpty()) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msgExtra);
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    "\n Use Buttons to do turn.",
                    TurnStart.getStartOfTurnButtons(privatePlayer, game, false, event));

                if (privatePlayer.getGenSynthesisInfantry() > 0) {
                    if (!getPlaceStatusInfButtons(game, privatePlayer).isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(),
                            "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry()
                                + " infantry left to revive.",
                            getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer
                            .getRepresentation()
                            + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");

                    }
                }
            }
        }
    }

    @ButtonHandler("startArbiter")
    public static void resolveImperialArbiter(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " decided to use the Imperial Arbiter Law to swap SCs with someone");
        game.removeLaw("arbiter");
        List<Button> buttons = ButtonHelperFactionSpecific.getSwapSCButtons(game, "imperialarbiter", player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " choose who you want to swap SCs with",
            buttons);
        deleteMessage(event);
    }

    public static List<Button> getMawButtons() {
        List<Button> playerButtons = new ArrayList<>();
        playerButtons.add(Buttons.green("resolveMaw", "Purge Maw of Worlds"));
        playerButtons.add(Buttons.red("deleteButtons", "Decline"));
        return playerButtons;
    }

    public static List<Button> getCrownButtons() {
        List<Button> playerButtons = new ArrayList<>();
        playerButtons.add(Buttons.green("resolveCrownOfE", "Purge Crown"));
        playerButtons.add(Buttons.red("deleteButtons", "Decline"));
        return playerButtons;
    }

    @ButtonHandler("resolveMaw")
    public static void resolveMaw(Game game, Player player, ButtonInteractionEvent event) {
        player.removeRelic("mawofworlds");
        player.removeExhaustedRelic("mawofworlds");
        for (String planet : player.getPlanets()) {
            player.exhaustPlanet(planet);
        }
        game.setComponentAction(true);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " purged Maw Of Worlds.");
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), player.getRepresentation() + " Use the button to get a tech", Buttons.GET_A_FREE_TECH);
        deleteMessage(event);
    }

    @ButtonHandler("endTurnWhenAllReactedTo_")
    public static void endTurnWhenAllReacted(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String sc = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
            game.getPing() + " the active player has elected to move the game along after everyone has resolved "
                + Helper.getSCName(Integer.parseInt(sc), game) + ". Please resolve it as soon as possible so the game may progress.");
        game.setTemporaryPingDisable(true);
        game.setStoredValue("endTurnWhenSCFinished", sc + player.getFaction());
        ButtonHelper.deleteTheOneButton(event);
        ButtonHelper.deleteTheOneButton(event, "fleetLogWhenAllReactedTo_" + sc, true);
    }

    @ButtonHandler("fleetLogWhenAllReactedTo_")
    public static void fleetLogWhenAllReacted(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String sc = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
            game.getPing() + " the active player has elected to move the game along after everyone has resolved "
                + Helper.getSCName(Integer.parseInt(sc), game) + ". Please resolve it as soon as possible so the game may progress.");
        game.setTemporaryPingDisable(true);
        game.setStoredValue("fleetLogWhenSCFinished", sc + player.getFaction());
        ButtonHelper.deleteTheOneButton(event);
        ButtonHelper.deleteTheOneButton(event, "endTurnWhenAllReactedTo_" + sc, true);
    }

    @ButtonHandler("moveAlongAfterAllHaveReactedToAC_")
    public static void moveAlonAfterAC(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String ac = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
            game.getPing() + " the active player has elected to move the game along after everyone has said no sabo to "
                + ac + ". Please respond as soon as possible so the game may progress.");
        game.setTemporaryPingDisable(true);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("resolveTwilightMirror")
    public static void resolveTwilightMirror(Game game, Player player, ButtonInteractionEvent event) {
        player.removeRelic("twilight_mirror");
        player.removeExhaustedRelic("twilight_mirror");
        for (String planet : player.getPlanets()) {
            player.exhaustPlanet(planet);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " purged Twilight Mirror to take one action.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation()
                + " Use the button to do an action. It is advised you avoid the end turn button at the end of it, and just delete it. ",
            TurnStart.getStartOfTurnButtons(player, game, false, event));
        game.updateActivePlayer(player);
        deleteMessage(event);
    }

    @ButtonHandler("resolveCrownOfE")
    public static void resolveCrownOfE(Game game, Player player, ButtonInteractionEvent event) {
        if (player.hasRelic("absol_emphidia")) {
            player.removeRelic("absol_emphidia");
            player.removeExhaustedRelic("absol_emphidia");
        }
        if (player.hasRelic("emphidia")) {
            player.removeRelic("emphidia");
            player.removeExhaustedRelic("emphidia");
        }
        Integer poIndex = game.addCustomPO("Crown of Emphidia", 1);
        game.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " scored Crown of Emphidia");
        deleteMessage(event);
        Helper.checkEndGame(game, player);
    }

    public static boolean isPlayerNew(Game gameOG, Player player) {
        Map<String, Game> mapList = GameManager.getGameNameToGame();
        for (Game game : mapList.values()) {
            if (!game.getName().equalsIgnoreCase(gameOG.getName())) {
                for (Player player2 : game.getRealPlayers()) {
                    if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean anyoneHaveInPlayArea(Game game, String pnID) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPromissoryNotesInPlayArea().contains(pnID)) {
                return true;
            }
        }
        return false;
    }

    @ButtonHandler("resolvePreassignment_")
    public static void resolvePreAssignment(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String messageID = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " successfully preset " + messageID;
        String part2 = player.getFaction();
        if (game.getStoredValue(messageID) != null
            && !game.getStoredValue(messageID).isEmpty()) {
            part2 = game.getStoredValue(messageID) + "_" + player.getFaction();
        }
        if (StringUtils.countMatches(buttonID, "_") > 1) {
            part2 = part2 + "_" + buttonID.split("_")[2];
            msg += " on " + buttonID.split("_")[2];
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        game.setStoredValue(messageID, part2);
        deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("removePreset_" + messageID, "Remove The Preset"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation() + " you may use this button to undo the preset. Ignore it otherwise.",
            buttons);
        if ("Public Disgrace".equalsIgnoreCase(messageID)) {
            String msg2 = player.getRepresentation()
                + " Additionally, you may set Public Disgrace to only trigger on a particular person. Choose to do so if you wish, or decline, or ignore this.";
            List<Button> buttons2 = new ArrayList<>();
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (!game.isFowMode()) {
                    buttons2.add(Buttons.gray("resolvePreassignment_Public Disgrace Only_" + p2.getFaction(),
                        p2.getFaction()));
                } else {
                    buttons2.add(Buttons.gray("resolvePreassignment_Public Disgrace Only_" + p2.getFaction(),
                        p2.getColor()));
                }
            }
            buttons2.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons2);
        }
    }

    @ButtonHandler("removePreset_")
    public static void resolveRemovalOfPreAssignment(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String messageID = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " successfully removed the preset for " + messageID;
        String part2 = player.getFaction();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (game.getStoredValue(messageID) != null) {
            game.setStoredValue(messageID, game.getStoredValue(messageID).replace(part2, ""));
        }
        deleteMessage(event);
    }

    public static void addReaction(ButtonInteractionEvent event, boolean skipReaction, boolean sendPublic,
        String message, String additionalMessage) {
        if (event == null)
            return;

        String userID = event.getUser().getId();
        Game game = GameManager.getUserActiveGame(userID);
        if (game == null) {
            event.getChannel().sendMessage("Unable to determine active game.").queue();
            return;
        }
        Player player = CommandHelper.getPlayerFromGame(game, event.getMember(), userID);
        if (player == null || !player.isRealPlayer()) {
            event.getChannel().sendMessage("You're not an active player of the game").queue();
            return;
        }
        // String playerFaction = player.getFaction();
        Guild guild = event.getGuild();
        if (guild == null) {
            event.getChannel().sendMessage("Could not find server Emojis").queue();
            return;
        }
        Map<String, Emoji> emojiMap = ButtonListener.emoteMap.get(guild);
        List<RichCustomEmoji> emojis = guild.getEmojis();
        if (emojiMap != null && emojiMap.size() != emojis.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emoji emoji : emojis) {
                emojiMap.put(emoji.getName().toLowerCase(), emoji);
            }
        }

        Message mainMessage = event.getInteraction().getMessage();
        Emoji emojiToUse = Helper.getPlayerEmoji(game, player, mainMessage);
        String messageId = mainMessage.getId();

        if (!skipReaction) {
            if (event.getMessageChannel() instanceof ThreadChannel) {

                game.getActionsChannel().addReactionById(event.getChannel().getId(), emojiToUse).queue();
            }

            event.getChannel().addReactionById(messageId, emojiToUse).queue(Consumers.nop(), BotLogger::catchRestError);
            if (game.getStoredValue(messageId) != null) {
                if (!game.getStoredValue(messageId).contains(player.getFaction())) {
                    game.setStoredValue(messageId,
                        game.getStoredValue(messageId) + "_" + player.getFaction());
                }
            } else {
                game.setStoredValue(messageId, player.getFaction());
            }

            UnfiledButtonHandlers.checkForAllReactions(event, game);
            if (message == null || message.isEmpty()) {
                return;
            }
        }

        String text = player.getRepresentation();
        if ("Not Following".equalsIgnoreCase(message))
            text = player.getRepresentation(false, false);
        text = text + " " + message;
        if (game.isFowMode() && sendPublic) {
            text = message;
        } else if (game.isFowMode()) {
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
        }

        if (additionalMessage != null && !additionalMessage.isEmpty()) {
            text += game.getPing() + " " + additionalMessage;
        }

        if (game.isFowMode() && !sendPublic) {
            MessageHelper.sendPrivateMessageToPlayer(player, game, text);
            return;
        }

        MessageHelper.sendMessageToChannel(Helper.getThreadChannelIfExists(event), text);
    }

    public static void addReaction(Player player, boolean skipReaction, boolean sendPublic, String message, String additionalMessage, String messageID, Game game) {
        Guild guild = game.getGuild();
        if (guild == null)
            return;

        Map<String, Emoji> emojiMap = ButtonListener.emoteMap.get(guild);
        List<RichCustomEmoji> emojis = guild.getEmojis();
        if (emojiMap != null && emojiMap.size() != emojis.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emoji emoji : emojis) {
                emojiMap.put(emoji.getName().toLowerCase(), emoji);
            }
        }

        try {
            game.getMainGameChannel().retrieveMessageById(messageID).queue(mainMessage -> {
                Emoji emojiToUse = Helper.getPlayerEmoji(game, player, mainMessage);
                String messageId = mainMessage.getId();

                if (!skipReaction) {
                    game.getMainGameChannel().addReactionById(messageId, emojiToUse).queue();
                    if (game.getStoredValue(messageId) != null) {
                        if (!game.getStoredValue(messageId).contains(player.getFaction())) {
                            game.setStoredValue(messageId,
                                game.getStoredValue(messageId) + "_" + player.getFaction());
                        }
                    } else {
                        game.setStoredValue(messageId, player.getFaction());
                    }
                    UnfiledButtonHandlers.checkForAllReactions(messageId, game);
                    if (message == null || message.isEmpty()) {
                        return;
                    }
                }

                String text = player.getRepresentation() + " " + message;
                if (game.isFowMode() && sendPublic) {
                    text = message;
                } else if (game.isFowMode()) {
                    text = "(You) " + emojiToUse.getFormatted() + " " + message;
                }

                if (additionalMessage != null && !additionalMessage.isEmpty()) {
                    text += game.getPing() + " " + additionalMessage;
                }

                if (game.isFowMode() && !sendPublic) {
                    MessageHelper.sendPrivateMessageToPlayer(player, game, text);
                }
            }, BotLogger::catchRestError);
        } catch (Throwable e) {
            game.removeMessageIDForSabo(messageID);
        }
    }

    public static Tile getTileOfPlanetWithNoTrait(Player player, Game game) {
        List<String> fakePlanets = new ArrayList<>(List.of("custodiavigilia", "ghoti"));
        List<String> ignoredPlanets = new ArrayList<>(Constants.MECATOLS);
        ignoredPlanets.addAll(fakePlanets);

        for (String planet : player.getPlanets()) {
            Planet unitHolder = game.getPlanetsInfo().get(planet.toLowerCase());
            Planet planetReal = unitHolder;
            boolean oneOfThree = planetReal != null
                && List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (!ignoredPlanets.contains(planet.toLowerCase()) && !oneOfThree) {
                return game.getTileFromPlanet(planet);
            }
        }
        return null;
    }

    public static String getListOfStuffAvailableToSpend(Player player, Game game) {
        return getListOfStuffAvailableToSpend(player, game, false);
    }

    public static String getListOfStuffAvailableToSpend(Player player, Game game, boolean production) {
        StringBuilder youCanSpend = new StringBuilder("You have available to you to spend: ");
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        if (planets.isEmpty()) {
            youCanSpend.append(" No Ready Planets ");
        } else {
            for (String planet : planets) {
                youCanSpend.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)).append(", ");
            }
        }
        if (!game.getPhaseOfGame().contains("agenda")) {
            youCanSpend.append("and ").append(player.getTg()).append(Emojis.tg).append(" TG").append(player.getTg() == 1 ? "" : "s");
        }
        if (production) {
            if (player.hasTech("st")) {
                youCanSpend.append(". You also have " + Emojis.CyberneticTech + "Sarween Tools");
            }
            if (player.hasTechReady("aida")) {
                youCanSpend.append(". You also have " + Emojis.WarfareTech + "AIDEV for ").append(ButtonHelper.getNumberOfUnitUpgrades(player)).append(" resources");
            }
        }
        return youCanSpend.toString();
    }

    public static List<Tile> getTilesOfPlayersSpecificUnits(Game game, Player p1, UnitType... type) {
        List<UnitType> unitTypes = new ArrayList<>();
        Collections.addAll(unitTypes, type);

        return game.getTileMap().values().stream()
            .filter(t -> t.containsPlayersUnitsWithKeyCondition(p1, unit -> unitTypes.contains(unit.getUnitType())))
            .toList();
    }

    public static int getNumberOfUnitsOnTheBoard(Game game, Player p1, String unit) {
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), p1.getColor());
        return getNumberOfUnitsOnTheBoard(game, unitKey, true);
    }

    public static int getNumberOfUnitsOnTheBoard(Game game, Player p1, String unit, boolean countPrison) {
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), p1.getColor());
        return getNumberOfUnitsOnTheBoard(game, unitKey, countPrison);
    }

    public static int getNumberOfUnitsOnTheBoard(Game game, UnitKey unitKey) {
        List<UnitHolder> unitHolders = new ArrayList<>(game.getTileMap().values().stream()
            .flatMap(t -> t.getUnitHolders().values().stream()).toList());
        unitHolders.addAll(game.getRealPlayers().stream()
            .flatMap(p -> p.getNomboxTile().getUnitHolders().values().stream()).toList());

        return unitHolders.stream()
            .flatMap(uh -> uh.getUnits().entrySet().stream())
            .filter(e -> e.getKey().equals(unitKey)).mapToInt(e -> Optional.ofNullable(e.getValue()).orElse(0))
            .sum();
    }

    public static int getNumberOfUnitsOnTheBoard(Game game, UnitKey unitKey, boolean countPrison) {
        List<UnitHolder> unitHolders = new ArrayList<>(game.getTileMap().values().stream()
            .flatMap(t -> t.getUnitHolders().values().stream()).toList());
        if (countPrison) {
            unitHolders.addAll(game.getRealPlayers().stream()
                .flatMap(p -> p.getNomboxTile().getUnitHolders().values().stream()).toList());
        }

        return unitHolders.stream()
            .flatMap(uh -> uh.getUnits().entrySet().stream())
            .filter(e -> e.getKey().equals(unitKey)).mapToInt(e -> Optional.ofNullable(e.getValue()).orElse(0))
            .sum();
    }

    @ButtonHandler("diplo_")
    public static void resolveDiploPrimary(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        if (type.toLowerCase().contains("mahact")) {
            String color2 = type.replace("mahact", "");
            Player mahactP = game.getPlayerFromColorOrFaction(color2);
            if (mahactP == null) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not find mahact player");
                return;
            }
            Tile tile = game.getTileByPosition(planet);
            AddCC.addCC(event, color2, tile);
            Helper.isCCCountCorrect(event, game, color2);
            for (String color : mahactP.getMahactCC()) {
                if (Mapper.isValidColor(color) && !color.equalsIgnoreCase(player.getColor())) {
                    AddCC.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, game, color);
                }
            }
            String message = player.getFactionEmoji() + " chose to use the mahact PN in the tile " + tile.getRepresentation();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        } else {
            if (!DiploSystem.diploSystem(event, game, player, planet.toLowerCase())) {
                return;
            }
            String message = player.getFactionEmoji() + " chose to diplo the system containing "
                + Helper.getPlanetRepresentation(planet, game);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        deleteMessage(event);
    }

    @ButtonHandler("acquireATechWithSC")
    public static void acquireATechWithSC(Player player, Game game, ButtonInteractionEvent event) {
        acquireATechWithResources(player, game, event, true);
    }

    @ButtonHandler("acquireATech")
    public static void acquireATech(Player player, Game game, ButtonInteractionEvent event) {
        acquireATechWithResources(player, game, event, false);
    }

    private static void acquireATechWithResources(Player player, Game game, ButtonInteractionEvent event, boolean sc) {
        acquireATech(player, game, event, sc,
            Set.of(Constants.PROPULSION, Constants.BIOTIC, Constants.CYBERNETIC, Constants.WARFARE, Constants.UNIT),
            "res");
    }

    @ButtonHandler("acquireAUnitTechWithInf")
    public static void acquireAUnitTechWithInf(Player player, Game game, ButtonInteractionEvent event) {
        acquireATech(player, game, event, false, Set.of(Constants.UNIT), "inf");
    }

    private static void acquireATech(Player player, Game game, ButtonInteractionEvent event, boolean sc, final String payType) {
        acquireATech(player, game, event, sc,
            Set.of(Constants.PROPULSION, Constants.BIOTIC, Constants.CYBERNETIC, Constants.WARFARE, Constants.UNIT),
            payType);
    }

    private static void acquireATech(Player player, Game game, ButtonInteractionEvent event, boolean sc, final Set<String> techTypes, final String payType) {
        String finsFactionCheckerPrefix = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();
        if (sc) {
            game.setComponentAction(false);
            boolean used = ButtonHelperSCs.addUsedSCPlayer(event.getMessageId(), game, player, event, "");
            StrategyCardModel scModel = game.getStrategyCardModelByName("technology").orElse(null);
            if (!used && scModel != null && scModel.usesAutomationForSCID("pok7technology")
                && !player.getFollowedSCs().contains(scModel.getInitiative())) {
                int scNum = scModel.getInitiative();
                player.addFollowedSC(scNum, event);
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Tech");
                }
                String message = ButtonHelperSCs.deductCC(player, event);
                addReaction(event, false, false, message, "");
            }
        } else {
            game.setComponentAction(true);
        }
        String techPrefix = finsFactionCheckerPrefix + "getAllTechOfType_";
        for (String type : techTypes) {
            switch (type) {
                case Constants.PROPULSION -> buttons.add(Buttons.blue(techPrefix + "propulsion", "Get a Blue Tech", Emojis.PropulsionTech));
                case Constants.BIOTIC -> buttons.add(Buttons.green(techPrefix + "biotic", "Get a Green Tech", Emojis.BioticTech));
                case Constants.CYBERNETIC -> buttons.add(Buttons.gray(techPrefix + "cybernetic", "Get a Yellow Tech", Emojis.CyberneticTech));
                case Constants.WARFARE -> buttons.add(Buttons.red(techPrefix + "warfare", "Get a Red Tech", Emojis.WarfareTech));
                case Constants.UNIT -> buttons.add(Buttons.gray(techPrefix + "unitupgrade", "Get a Unit Upgrade Tech", Emojis.UnitUpgradeTech));
            }
        }

        ButtonHelperCommanders.yinCommanderSummary(player, game);
        String message = player.getRepresentation() + " What type of tech would you want?";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    public static void sendMessageToRightStratThread(Player player, Game game, String message, String stratName) {
        sendMessageToRightStratThread(player, game, message, stratName, null);
    }

    public static String getStratName(int sc) {
        return switch (sc) {
            case 1 -> "leadership";
            case 2 -> "diplomacy";
            case 3 -> "politics";
            case 4 -> "construction";
            case 5 -> "trade";
            case 6 -> "warfare";
            case 7 -> "technology";
            case 8 -> "imperial";
            default -> "action";
        };
    }

    public static void sendMessageToRightStratThread(Player player, Game game, String message, String stratName, @Nullable List<Button> buttons) {
        List<ThreadChannel> threadChannels = game.getActionsChannel().getThreadChannels();
        String threadName = game.getName() + "-round-" + game.getRound() + "-" + stratName;
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (game.getName().equalsIgnoreCase("pbd1000") || game.getName().equalsIgnoreCase("pbd100two")) {
                if (!threadChannel_.getMembers().contains(game.getGuild().getMemberById(player.getUserID()))) {
                    continue;
                }
            }
            if ((threadChannel_.getName().startsWith(threadName)
                || threadChannel_.getName().equals(threadName + "WinnuHero"))
                && (!"technology".equalsIgnoreCase(stratName) || !game.isComponentAction())) {
                MessageHelper.sendMessageToChannelWithButtons(threadChannel_, message, buttons);
                return;
            }
        }
        if (player != null) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(game.getActionsChannel(), message, buttons);
        }
    }

    public static void offerCodexButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        Button codex1 = Buttons.green("codexCardPick_1", "Card #1");
        Button codex2 = Buttons.green("codexCardPick_2", "Card #2");
        Button codex3 = Buttons.green("codexCardPick_3", "Card #3");
        String message = "Use buttons to select cards from the discard";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
            List.of(codex1, codex2, codex3));
    }

    @ButtonHandler("sarMechStep1_")
    public static void resolveSARMechStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String tilePos = buttonID.split("_")[1];
        String warfareOrNot = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(tilePos);
        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            if (player.getPlanetsAllianceMode().contains(uH.getName())) {
                buttons.add(Buttons.green("sarMechStep2_" + uH.getName() + "_" + warfareOrNot,
                    "Place mech on " + Helper.getPlanetRepresentation(uH.getName(), game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " choose the planet to drop 1 mech on", buttons);
        deleteTheOneButton(event);
    }

    @ButtonHandler("sarMechStep2_")
    public static void resolveSARMechStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String warfareOrNot = buttonID.split("_")[2];
        String msg1 = player.getFactionEmoji() + " exhausted Self-Assembly Routines to place 1 mech on "
            + Helper.getPlanetRepresentation(planet, game);
        player.exhaustTech("sar");
        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "mech " + planet, game);
        deleteMessage(event);
        sendMessageToRightStratThread(player, game, msg1, warfareOrNot);
    }

    public static String resolveACDraw(Player p2, Game game, GenericInteractionCreateEvent event) {
        String message = "";
        if (p2.hasAbility("scheming")) {
            game.drawActionCard(p2.getUserID());
            game.drawActionCard(p2.getUserID());
            message = p2.getFactionEmoji() + " Drew 2 ACs with Scheming. Please discard 1 AC with the blue buttons";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                p2.getRepresentationUnfogged() + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(p2, false));
        } else if (p2.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, p2, 1);
            message = p2.getFactionEmoji() + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(p2.getUserID());
            message = p2.getFactionEmoji() + " Drew 1 AC";
            ACInfo.sendActionCardInfo(game, p2, event);
        }
        return message;
    }

    public static int getNumberOfStarCharts(Player player) {
        int count = 0;
        for (String relic : player.getRelics()) {
            if (relic.contains("starchart")) {
                count++;
            }
        }
        return count;
    }

    public static void purge2StarCharters(Player player) {
        List<String> relics = new ArrayList<>(player.getRelics());
        int count = 0;
        for (String relic : relics) {
            if (relic.contains("starchart") && count < 2) {
                count++;
                player.removeRelic(relic);
            }
        }
    }

    public static void offerSpeakerButtons(Game game, Player player) {
        String assignSpeakerMessage = "Please, before you draw your action cards or look at agendas, click a faction below to assign Speaker " + Emojis.SpeakerToken;
        List<Button> assignSpeakerActionRow = getAssignSpeakerButtons(game);
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), assignSpeakerMessage, assignSpeakerActionRow);
    }

    private static List<Button> getAssignSpeakerButtons(Game game) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            if (player.isRealPlayer() && !player.getUserID().equals(game.getSpeakerUserID())) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isValidFaction(faction)) {
                    Button button = Buttons.gray("assignSpeaker_" + faction, null, player.getFactionEmoji());
                    assignSpeakerButtons.add(button);
                }
            }
        }
        return assignSpeakerButtons;
    }
}
