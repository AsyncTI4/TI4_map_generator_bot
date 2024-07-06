package ti4.helpers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.ButtonListener;
import ti4.buttons.Buttons;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.agenda.ShowDiscardedAgendas;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowDiscardActionCards;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.ShowUnScoredSOs;
import ti4.commands.combat.CombatRoll;
import ti4.commands.ds.DrawBlueBackTile;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpInfo;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.explore.SendFragments;
import ti4.commands.explore.ShowRemainingRelics;
import ti4.commands.game.GameCreate;
import ti4.commands.game.GameEnd;
import ti4.commands.leaders.ExhaustLeader;
import ti4.commands.leaders.HeroPlay;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.ClearDebt;
import ti4.commands.player.SendDebt;
import ti4.commands.player.Setup;
import ti4.commands.player.TurnStart;
import ti4.commands.special.CheckDistance;
import ti4.commands.special.DiploSystem;
import ti4.commands.special.StellarConverter;
import ti4.commands.status.Cleanup;
import ti4.commands.status.ListTurnOrder;
import ti4.commands.tech.TechShowDeck;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.uncategorized.CardsInfo;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.MapGenerator;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
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
import ti4.model.LeaderModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.RelicModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.selections.selectmenus.SelectFaction;

public class ButtonHelper {

    public static void offerEveryoneTitlePossibilities(Game game) {
        for (Player player : game.getRealPlayers()) {
            String msg = player.getRepresentation()
                + " you have the opportunity to anonymously bestow one title on someone else in this game. Titles are just for fun, and have no real significance, but could a nice way to take something away from this game. Feel free to not. If you choose to, its a 2 button process. First select the title, then the player you want to bestow it upon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("bestowTitleStep1_Life Of The Table", "Life Of The Table"));
            buttons.add(Button.success("bestowTitleStep1_Fun To Be Around", "Fun To Be Around"));
            buttons.add(Button.success("bestowTitleStep1_Trustworthy To A Fault", "Trustworthy To A Fault"));
            buttons.add(Button.success("bestowTitleStep1_You Made The Game Better", "You Made The Game Better"));
            buttons.add(Button.success("bestowTitleStep1_A Kind Soul", "A Kind Soul"));
            buttons.add(Button.success("bestowTitleStep1_A Good Ally", "A Good Ally"));
            buttons.add(Button.success("bestowTitleStep1_A Mahact Puppet Master", "A Mahact Puppet Master"));

            buttons.add(Button.primary("bestowTitleStep1_Lightning Fast", "Lightning Fast"));
            buttons.add(Button.primary("bestowTitleStep1_Fortune Favored", "Fortune Favored"));
            buttons.add(Button.primary("bestowTitleStep1_Possesses Cursed Dice", "Possesses Cursed Dice"));
            buttons.add(Button.primary("bestowTitleStep1_A Worthy Opponent", "A Worthy Opponent"));
            buttons.add(Button.primary("bestowTitleStep1_A Brilliant Tactician", "A Brilliant Tactician"));
            buttons.add(Button.primary("bestowTitleStep1_A Master Diplomat", "A Master Diplomat"));
            buttons.add(Button.primary("bestowTitleStep1_Hard To Kill", "Hard To Kill"));

            buttons.add(Button.danger("bestowTitleStep1_A Sneaky One", "A Sneaky One"));
            buttons.add(Button.danger("bestowTitleStep1_You Made Me Mad", "You Made Me Mad"));
            buttons.add(Button.danger("bestowTitleStep1_A Vuil'Raith In Xxcha Clothing", "A Vuil'Raith In Xxcha Clothing"));
            buttons.add(Button.danger("bestowTitleStep1_Space Risker", "Space Risker"));
            buttons.add(Button.danger("bestowTitleStep1_A Warlord", "A Warlord"));
            buttons.add(Button.danger("bestowTitleStep1_Traitor", "Traitor"));

            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Tiles here", buttons);
        }
    }

    public static void resolveBestowTitleStep1(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String title = buttonID.split("_")[1];
        String msg = player.getRepresentation() + " choose the player you wish to give the title of " + title;
        List<Button> buttons = new ArrayList<>();
        for (Player player2 : game.getRealPlayersNDummies()) {
            if (player2 == player) {
                continue;
            }
            buttons.add(Button.success("bestowTitleStep2_" + title + "_" + player2.getFaction(),
                player2.getFactionModel().getFactionName() + " (" + player2.getUserName() + ")"));
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
        deleteMessage(event);
    }

    public static void resolveBestowTitleStep2(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String title = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String msg = p2.getRepresentation() + " someone has chosen to give you the title of '" + title + "'";
        String titles = game.getStoredValue("TitlesFor" + p2.getUserID());
        if (titles.isEmpty()) {
            game.setStoredValue("TitlesFor" + p2.getUserID(), title);
        } else {
            game.setStoredValue("TitlesFor" + p2.getUserID(), titles + "_" + title);
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Successfully bestowed title");
        MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), msg);
        deleteMessage(event);
    }

    public static void pickACardFromDiscardStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String acStringID : game.getDiscardActionCards().keySet()) {
            buttons.add(Button.success("pickFromDiscard_" + acStringID, Mapper.getActionCard(acStringID).getName()));
        }
        buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
        if (buttons.size() > 25) {
            buttons.add(25, Button.danger("deleteButtons_", "Delete These Buttons"));
        }
        if (buttons.size() > 50) {
            buttons.add(50, Button.danger("deleteButtons_2", "Delete These Buttons"));
        }
        if (buttons.size() > 75) {
            buttons.add(75, Button.danger("deleteButtons_3", "Delete These Buttons"));
        }
        String msg = player.getRepresentation(true, true) + " use buttons to grab an AC from the discard";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void pickACardFromDiscardStep2(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        deleteMessage(event);
        String acID = buttonID.replace("pickFromDiscard_", "");
        boolean picked = game.pickActionCard(player.getUserID(), game.getDiscardActionCards().get(acID));
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String msg2 = player.getRepresentation(true, true) + " grabbed " + Mapper.getActionCard(acID).getName()
            + " from the discard";
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), msg2);

        ACInfo.sendActionCardInfo(game, player, event);
        if (player.hasAbility("autonetic_memory")) {
            String message = player.getRepresentation(true, true)
                + " if you did not just use the codex to get that ac, please discard an AC due to your cybernetic madness ability";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message,
                ACInfo.getDiscardActionCardButtons(game, player, false));
        }
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
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), rollInfantryRevival(player));
            }
        }
    }

    public static List<Button> getDacxiveButtons(String planet, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "dacxive_" + planet, "Resolve Dacxive"));
        buttons.add(Button.danger(player.getFinsFactionCheckerPrefix() + "deleteButtons", "No Dacxive"));
        return buttons;
    }

    public static List<Button> getForcedPNSendButtons(Game game, Player player, Player p1) {
        List<Button> stuffToTransButtons = new ArrayList<>();
        for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
            if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)
                || (player.getAbilities().contains("hubris") && pnShortHand.endsWith("an"))) {
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
            Player owner = game.getPNOwner(pnShortHand);
            Button transact;
            if (game.isFoWMode()) {
                transact = Button.success(
                    "naaluHeroSend_" + player.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand),
                    owner.getColor() + " " + promissoryNote.getName());
            } else {
                transact = Button
                    .success(
                        "naaluHeroSend_" + player.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand),
                        promissoryNote.getName())
                    .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
            }
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }

    public static boolean canTheseTwoTransact(Game game, Player player, Player player2) {
        return player == player2 || !"action".equalsIgnoreCase(game.getCurrentPhase())
            || player.hasAbility("guild_ships") || player.getPromissoryNotes().containsKey("convoys")
            || player2.getPromissoryNotes().containsKey("convoys") || player2.hasAbility("guild_ships")
            || player2.getNeighbouringPlayers().contains(player)
            || player.getNeighbouringPlayers().contains(player2);
    }

    public static void checkTransactionLegality(Game game, Player player, Player player2) {
        if (!canTheseTwoTransact(game, player, player2)) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentation(true, true)
                    + " this is a friendly reminder that you are not neighbors with " + player2.getColor());
        }
        if (player.hasAbility("policy_the_people_control") && !"action".equalsIgnoreCase(game.getCurrentPhase())) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                player.getRepresentation(true, true)
                    + " this is a friendly reminder that you cannot transact during the agenda phase due to the control policy");
        }
        if (player2.hasAbility("policy_the_people_control") && !"action".equalsIgnoreCase(game.getCurrentPhase())) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                player.getRepresentation(true, true)
                    + " this is a friendly reminder that " + player2.getColor()
                    + " cannot transact during the agenda phase due to their control policy");
        }
    }

    public static void riftUnitButton(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String rest = buttonID.replace("riftUnit_", "").toLowerCase();
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unit = rest;
        for (int x = 0; x < amount; x++) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                ident + " " + riftUnit(unit, tile, game, event, player, null));
        }
        String message = event.getMessage().getContentRaw();
        List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, game, tile);
        event.getMessage().editMessage(message)
            .setComponents(turnButtonListIntoActionRowList(systemButtons)).queue();
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
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                ident + " " + riftUnit(unit, tile, game, event, player, null));
        }
        deleteMessage(event);
    }

    public static void riftAllUnitsButton(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String pos = buttonID.replace("riftAllUnits_", "").toLowerCase();
        riftAllUnitsInASystem(pos, event, game, player, ident, null);
    }

    public static void riftAllUnitsInASystem(String pos, ButtonInteractionEvent event, Game game, Player player,
        String ident, Player cabal) {
        Tile tile = game.getTileByPosition(pos);

        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
            }
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (!(unitHolder instanceof Planet planet)) {
                Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
                for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                        continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null)
                        continue;

                    UnitKey key = unitEntry.getKey();
                    if (key.getUnitType() == UnitType.Infantry
                        || key.getUnitType() == UnitType.Mech
                        || (!player.hasFF2Tech() && key.getUnitType() == UnitType.Fighter)
                        || (cabal != null && (key.getUnitType() == UnitType.Fighter
                            || key.getUnitType() == UnitType.Spacedock))) {
                        continue;
                    }

                    int totalUnits = unitEntry.getValue();
                    String unitKey = unitModel.getAsyncId();
                    unitKey = getUnitName(unitKey);
                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    for (int x = 1; x < damagedUnits + 1; x++) {
                        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                            ident + " " + riftUnit(unitKey + "damaged", tile, game, event, player, cabal));
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1; x++) {
                        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                            ident + " " + riftUnit(unitKey, tile, game, event, player, cabal));
                    }
                }
            }
        }
        if (cabal == null) {
            String message = event.getMessage().getContentRaw();
            List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, game, tile);
            event.getMessage().editMessage(message)
                .setComponents(turnButtonListIntoActionRowList(systemButtons)).queue();
        } else {
            List<ActionRow> actionRow2 = new ArrayList<>();
            String exhaustedMessage = event.getMessage().getContentRaw();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            if ("".equalsIgnoreCase(exhaustedMessage)) {
                exhaustedMessage = "Rift";
            }
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            } else {
                deleteMessage(event);
            }
        }

    }

    public static String riftUnit(String unit, Tile tile, Game game, GenericInteractionCreateEvent event, Player player,
        Player cabal) {
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        Die d1 = new Die(4);
        String msg = Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " rolled a " + d1.getResult();
        if (damaged) {
            msg = "A damaged " + msg;
        }
        if (d1.isSuccess()) {
            msg = msg + " and survived. May you always be so lucky.";
        } else {
            UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
            new RemoveUnits().removeStuff(event, tile, 1, "space", key, player.getColor(), damaged, game);
            msg = msg + " and failed. Condolences for your loss.";
            if (cabal != null && cabal != player
                && !ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(player, game, cabal)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, 1, unit, event);
            }
        }

        return msg;
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
        String msg = Emojis.infantry + " rolled a " + d1.getResult();
        if (player.hasTech("cl2")) {
            msg = Emojis.infantry + " died";

        }
        if (d1.isSuccess() || player.hasTech("cl2")) {
            msg = msg
                + " and revived. You will be prompted to place them on a planet in your HS at the start of your next turn.";
            player.setStasisInfantry(player.getStasisInfantry() + 1);
        } else {
            msg = msg + " and failed. No revival";
        }
        return getIdent(player) + " " + msg;
    }

    public static void rollMykoMechRevival(Game game, Player player) {
        Die d1 = new Die(6);
        String msg = Emojis.mech + " rolled a " + d1.getResult();
        if (d1.isSuccess()) {
            msg = msg
                + " and revived. You will be prompted to replace an infantry with a mech at the start of your turn.";
            ButtonHelperFactionSpecific.increaseMykoMech(game);
        } else {
            msg = msg + " and failed. No revival";
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), getIdent(player) + " " + msg);
    }

    public static void placeInfantryFromRevival(Game game, ButtonInteractionEvent event, Player player,
        String buttonID) {
        String planet = buttonID.split("_")[1];
        String amount;
        if (StringUtils.countMatches(buttonID, "_") > 1) {
            amount = buttonID.split("_")[2];
        } else {
            amount = "1";
        }

        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, amount + " inf " + planet, game);
        player.setStasisInfantry(player.getStasisInfantry() - Integer.parseInt(amount));
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
            getIdent(player) + " Placed " + amount + " infantry on "
                + Helper.getPlanetRepresentation(planet, game) + ". You have "
                + player.getStasisInfantry() + " infantry left to revive.");
        if (player.getStasisInfantry() == 0) {
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
                return getCorrectChannel(player, game);
            }
        }
        List<ThreadChannel> threadChannels = game.getMainGameChannel().getThreadChannels();
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                return threadChannel_;
            }
        }
        return getCorrectChannel(player, game);
    }

    public static Set<String> getTypesOfPlanetPlayerHas(Game game, Player player) {
        Set<String> types = new HashSet<>();
        for (String planet : player.getPlanets()) {
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
        if (player.getStasisInfantry() == 0) {
            return buttons;
        }
        Tile tile = player.getHomeSystemTile();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                if (player.getPlanets().contains(unitHolder.getName())) {
                    buttons.add(Button.success("statusInfRevival_" + unitHolder.getName() + "_1",
                        "Place 1 infantry on " + Helper.getPlanetRepresentation(unitHolder.getName(), game)));
                    if (player.getStasisInfantry() > 1) {
                        buttons.add(Button.success(
                            "statusInfRevival_" + unitHolder.getName() + "_" + player.getStasisInfantry(),
                            "Place " + player.getStasisInfantry() + " infantry on "
                                + Helper.getPlanetRepresentation(unitHolder.getName(), game)));

                    }
                }

            }
        }
        if (player.ownsUnit("cymiae_infantry2")) {
            buttons = new ArrayList<>();
            for (String planet : player.getPlanets()) {
                if (game.getTileFromPlanet(planet) != null) {
                    buttons.add(Button.success("statusInfRevival_" + planet + "_1",
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
        if (player.getTg() > 0 || (game.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
            && !whatIsItFor.contains("inf"))) {
            Button lost1TG = Button.danger("reduceTG_1_" + whatIsItFor, "Spend 1 TG");
            buttons.add(lost1TG);
        }
        if (player.getTg() > 1) {
            Button lost2TG = Button.danger("reduceTG_2_" + whatIsItFor, "Spend 2 TGs");
            buttons.add(lost2TG);
        }
        if (player.getTg() > 2) {
            Button lost3TG = Button.danger("reduceTG_3_" + whatIsItFor, "Spend 3 TGs");
            buttons.add(lost3TG);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 0) {
            Button lost1C = Button.danger("reduceComm_1_" + whatIsItFor, "Spend 1 comm");
            buttons.add(lost1C);
        }
        if (player.hasUnexhaustedLeader("olradinagent")) {
            Button hacanButton = Button
                .secondary("exhaustAgent_olradinagent_" + player.getFaction(),
                    "Use Olradin Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.olradin));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 1) {
            Button lost2C = Button.danger("reduceComm_2_" + whatIsItFor, "Spend 2 comms");
            buttons.add(lost2C);
        }
        if (player.getNomboxTile().getUnitHolders().get("space").getUnits().size() > 0 && !whatIsItFor.contains("inf")
            && !whatIsItFor.contains("both") && (player.hasAbility("devour") || player.hasAbility("riftmeld"))) {
            Button release = Button.secondary("getReleaseButtons", "Release captured units")
                .withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("cabal")));
            buttons.add(release);
        }
        if (player.hasUnexhaustedLeader("khraskagent")
            && (whatIsItFor.contains("inf") || whatIsItFor.contains("both"))) {
            Button release = Button.secondary("exhaustAgent_khraskagent_" + player.getFaction(),
                "Use Khrask Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("khrask")));
            buttons.add(release);
        }
        if (player.hasAbility("diplomats") && ButtonHelperAbilities.getDiplomatButtons(game, player).size() > 0) {
            Button release = Button.secondary("getDiplomatsButtons", "Use Diplomats Ability")
                .withEmoji(Emoji.fromFormatted(Emojis.freesystems));
            buttons.add(release);
        }
        buttons.add(Button.secondary("resetSpend_" + whatIsItFor, "Reset Spent Planets and Tgs"));

        return buttons;
    }

    public static List<Player> getPlayersWhoHaveNoSC(Player player, Game game) {
        List<Player> playersWhoDontHaveSC = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().size() > 0 || p2 == player) {
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
                if (game.isFoWMode()) {
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
            UnitHolder unitHolder = getUnitHolderFromPlanetName(planet, game);
            if (unitHolder == null) {
                BotLogger.log("Null unitholder for planet " + planet);
                continue;
            }
            Set<String> tokenList = unitHolder.getTokenList();
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
                buttons.add(Button.secondary(
                    "resolveAlliancePlanetTrade_" + planet + "_" + receiver.getFaction() + "_" + refreshed,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        return buttons;
    }

    public static void resolveAllianceMemberPlanetTrade(Player p1, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String dmzPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        String exhausted = buttonID.split("_")[3];
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(p1, game),
                "Could not resolve second player, please resolve manually.");
            return;
        }
        UnitHolder oriPlanet = getUnitHolderFromPlanetName(dmzPlanet, game);
        PlanetAdd.doAction(p2, dmzPlanet, game, event, true);
        if (!"exhausted".equalsIgnoreCase(exhausted)) {
            p2.refreshPlanet(dmzPlanet);
        }
        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = getIdentOrColor(p1, game);
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(dmzPlanet, game) + " to "
            + getIdentOrColor(p2, game);
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        goAgainButtons.add(Button.success("demandSomething_" + p2.getColor(), "Expect something in return"));
        if (game.isFoWMode() || !game.getWhetherNewTransactionMethod()) {
            if (game.isFoWMode()) {
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

    public static void resolveDMZTrade(Player p1, Game game, ButtonInteractionEvent event, String buttonID) {
        String dmzPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(p1, game),
                "Could not resolve second player, please resolve manually.");
            return;
        }
        UnitHolder oriPlanet = getUnitHolderFromPlanetName(dmzPlanet, game);
        PlanetAdd.doAction(p2, dmzPlanet, game, event, false);
        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = getIdentOrColor(p1, game);
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(dmzPlanet, game) + " to "
            + getIdentOrColor(p2, game);
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        goAgainButtons.add(Button.success("demandSomething_" + p2.getColor(), "Expect something in return"));

        if (game.isFoWMode() || !game.getWhetherNewTransactionMethod()) {
            if (game.isFoWMode()) {
                MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
                MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
                MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
        }
        deleteMessage(event);
        MessageHelper.sendMessageToChannel(getCorrectChannel(p2, game),
            p2.getRepresentation(true, true) + " you got traded the DMZ");

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

    public static void getTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String ident = getIdent(player);
        boolean paymentRequired = !buttonID.contains("__noPay");
        buttonID = buttonID.replace("__noPay", "");

        String techID = StringUtils.substringAfter(buttonID, "getTech_");
        techID = AliasHandler.resolveTech(techID);
        if (!Mapper.isValidTech(techID)) {
            BotLogger.log(event, "`ButtonHelper.getTech` Invalid TechID in 'getTech_' Button: " + techID);
            return;
        }
        TechnologyModel techM = Mapper.getTech(techID);
        StringBuilder message = new StringBuilder(ident).append(" Acquired The Tech ")
            .append(techM.getRepresentation(false));

        if (techM.getRequirements().isPresent() && techM.getRequirements().get().length() > 1) {
            if (player.getLeaderIDs().contains("zealotscommander") && !player.hasLeaderUnlocked("zealotscommander")) {
                commanderUnlockCheck(player, game, "zealots", event);
            }
        }
        player.addTech(techID);
        if (techM.isUnitUpgrade()) {
            if (player.hasUnexhaustedLeader("mirvedaagent") && player.getStrategicCC() > 0) {
                List<Button> buttons = new ArrayList<>();
                Button hacanButton = Button
                    .secondary("exhaustAgent_mirvedaagent_" + player.getFaction(),
                        "Use Mirveda Agent")
                    .withEmoji(Emoji.fromFormatted(Emojis.mirveda));
                buttons.add(hacanButton);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player
                    .getRepresentation(true, true)
                    + " you can use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Logic Machina (Mirveda Agent) to spend a CC and research a tech of the same color as a prereq of the tech you just got",
                    buttons);
            }
            if (player.hasAbility("obsessive_designs") && paymentRequired
                && "action".equalsIgnoreCase(game.getCurrentPhase())) {
                String msg = player.getRepresentation()
                    + " due to your obsessive designs ability, you can use your space dock at home PRODUCTION ability to build units of the type you just upgraded, reducing the total cost by 2. ";
                String generalMsg = getIdentOrColor(player, game)
                    + " has an opportunity to use their obsessive designs ability to build " + techM.getName()
                    + " at home";
                List<Button> buttons;
                Tile tile = game.getTile(AliasHandler.resolveTile(player.getFaction()));
                if (player.hasAbility("mobile_command")
                    && getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship).size() > 0) {
                    tile = getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship).get(0);
                }
                if (tile == null) {
                    tile = player.getHomeSystemTile();
                }
                if (tile == null) {
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find a HS, sorry bro");
                }
                buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "obsessivedesigns", "place");
                int val = Helper.getProductionValue(player, game, tile, true);
                String message2 = msg + getListOfStuffAvailableToSpend(player, game) + "\n"
                    + "You have " + val + " PRODUCTION value in this system";
                if (val > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
                    message2 = message2
                        + ". You also have cabal commander which allows you to produce 2 ff/inf that dont count towards production limit";
                }
                if (val > 0 && isPlayerElected(game, player, "prophecy")) {
                    message2 = message2
                        + "Reminder that you have prophecy of Ixth and should produce 2 fighters if you want to keep it. Its removal is not automated";
                }
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), generalMsg);
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), message2);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Produce Units", buttons);

            }
        }
        if (player.hasUnexhaustedLeader("zealotsagent")) {
            List<Button> buttons = new ArrayList<>();
            Button hacanButton = Button
                .secondary("exhaustAgent_zealotsagent_" + player.getFaction(),
                    "Use Zealots Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.zealots));
            buttons.add(hacanButton);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentation(true, true)
                    + " you can use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Priestess Tuh (Zealots Agent) to produce 1 ship at home or in a system where you have a tech skip planet",
                buttons);
        }

        ButtonHelperFactionSpecific.resolveResearchAgreementCheck(player, techID, game);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, game);
        if ("iihq".equalsIgnoreCase(techID)) {
            message.append("\n Automatically added the Custodia Vigilia planet");
        }
        if ("cm".equalsIgnoreCase(techID) && game.getActivePlayerID() != null
            && game.getActivePlayerID().equalsIgnoreCase(player.getUserID()) && !player.getSCs().contains(7)) {
            if (!game.isFoWMode()) {
                try {
                    if (game.getLatestTransactionMsg() != null && !"".equals(game.getLatestTransactionMsg())) {
                        game.getMainGameChannel().deleteMessageById(game.getLatestTransactionMsg()).queue();
                        game.setLatestTransactionMsg("");
                    }
                } catch (Exception e) {
                    // Block of code to handle errors
                }
            }
            String text = "" + player.getRepresentation(true, true) + " UP NEXT";
            String buttonText = "Use buttons to do your turn. ";
            List<Button> buttons = TurnStart.getStartOfTurnButtons(player, game, true, event);
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), text);
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), buttonText, buttons);
        }
        if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
            commanderUnlockCheck(player, game, "jolnar", event);
        }
        if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
            commanderUnlockCheck(player, game, "nekro", event);
        }
        if (player.getLeaderIDs().contains("mirvedacommander") && !player.hasLeaderUnlocked("mirvedacommander")) {
            commanderUnlockCheck(player, game, "mirveda", event);
        }
        if (player.getLeaderIDs().contains("dihmohncommander") && !player.hasLeaderUnlocked("dihmohncommander")) {
            commanderUnlockCheck(player, game, "dihmohn", event);
        }

        if (game.getComponentAction() || !"action".equalsIgnoreCase(game.getCurrentPhase())) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), message.toString());
        } else {
            sendMessageToRightStratThread(player, game, message.toString(), "technology");
            String key = "TechForRound" + game.getRound() + player.getFaction();
            if (game.getStoredValue(key).isEmpty()) {
                game.setStoredValue(key, techID);
            } else {
                game.setStoredValue(key, game.getStoredValue(key) + "." + techID);
            }
            postTechSummary(game);
        }
        if (paymentRequired) {
            payForTech(game, player, event, techID);

        } else {
            if (player.hasLeader("zealotshero") && player.getLeader("zealotshero").get().isActive()) {
                if (game.getStoredValue("zealotsHeroTechs").isEmpty()) {
                    game.setStoredValue("zealotsHeroTechs", techID);
                } else {
                    game.setStoredValue("zealotsHeroTechs",
                        game.getStoredValue("zealotsHeroTechs") + "-" + techID);
                }
            }
        }
        if (player.hasUnit("augers_mech") && getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                player.getFactionEmoji()
                    + " has the opportunity to deploy an Augur mech on a legendary planet or planet with a tech skip");
            String message2 = player.getRepresentation(true, true)
                + " Use buttons to drop a mech on a legendary planet or planet with a tech skip";
            List<Button> buttons2 = new ArrayList<>(
                Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message2, buttons2);
        }

        deleteMessage(event);
    }

    public static void postTechSummary(Game game) {
        if (game.isFoWMode() || game.getTableTalkChannel() == null || !game.getStoredValue("TechSummaryRound" + game.getRound()).isEmpty() || game.isHomeBrewSCMode()) {
            return;
        }
        String msg = "**__Tech Summary For Round " + game.getRound() + "__**\n";
        for (Player player : game.getRealPlayers()) {
            if (!player.hasFollowedSC(7)) {
                return;
            }
            String key = "TechForRound" + game.getRound() + player.getFaction();
            msg = msg + player.getFactionEmoji() + ":";
            String key2 = "RAForRound" + game.getRound() + player.getFaction();
            if (!game.getStoredValue(key2).isEmpty()) {
                msg = msg + "(From RA: ";
                if (game.getStoredValue(key2).contains(".")) {
                    for (String tech : game.getStoredValue(key2).split("\\.")) {
                        msg = msg + " " + Mapper.getTech(tech).getNameRepresentation();
                    }

                } else {
                    msg = msg + " " + Mapper.getTech(game.getStoredValue(key2)).getNameRepresentation();
                }
                msg = msg + ")";
            }
            if (!game.getStoredValue(key).isEmpty()) {
                if (game.getStoredValue(key).contains(".")) {
                    String tech1 = StringUtils.substringBefore(game.getStoredValue(key), ".");
                    String tech2 = StringUtils.substringAfter(game.getStoredValue(key), ".");
                    msg = msg + " " + Mapper.getTech(tech1).getNameRepresentation();
                    for (String tech2Plus : tech2.split("\\.")) {
                        msg = msg + "and " + Mapper.getTech(tech2Plus).getNameRepresentation();
                    }

                } else {
                    msg = msg + " " + Mapper.getTech(game.getStoredValue(key)).getNameRepresentation();
                }
                msg = msg + "\n";
            } else {
                msg = msg + " Did not follow for tech\n";
            }
        }
        String key2 = "TechForRound" + game.getRound() + "Counter";
        if (game.getStoredValue(key2).equalsIgnoreCase("0")) {
            MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), msg);
            game.setStoredValue("TechSummaryRound" + game.getRound(), "yes");
        } else {
            if (game.getStoredValue(key2).isEmpty()) {
                game.setStoredValue(key2, "6");
            }
        }
    }

    public static void payForTech(Game game, Player player, ButtonInteractionEvent event, String tech) {
        String trueIdentity = player.getRepresentation(true, true);
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
        List<Button> buttons = getExhaustButtonsWithTG(game, player, "res" + "tech");
        TechnologyModel techM = Mapper.getTechs().get(AliasHandler.resolveTech(tech));
        if (techM.isUnitUpgrade() && player.hasTechReady("aida")) {
            Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
            buttons.add(aiDEVButton);
        }
        if (!techM.isUnitUpgrade() && player.hasAbility("iconoclasm")) {

            for (int x = 1; x < player.getCrf() + 1; x++) {
                Button transact = Button.primary("purge_Frags_CRF_" + x,
                    "Purge Cultural Fragments (" + x + ")");
                buttons.add(transact);
            }

            for (int x = 1; (x < player.getIrf() + 1 && x < 4); x++) {
                Button transact = Button.success("purge_Frags_IRF_" + x,
                    "Purge Industrial Fragments (" + x + ")");
                buttons.add(transact);
            }

            for (int x = 1; (x < player.getHrf() + 1 && x < 4); x++) {
                Button transact = Button.danger("purge_Frags_HRF_" + x,
                    "Purge Hazardous Fragments (" + x + ")");
                buttons.add(transact);
            }

            for (int x = 1; x < player.getUrf() + 1; x++) {
                Button transact = Button.secondary("purge_Frags_URF_" + x,
                    "Purge Frontier Fragments (" + x + ")");
                buttons.add(transact);
            }

        }
        if (player.hasTechReady("is")) {
            Button aiDEVButton = Button.secondary("exhaustTech_is", "Exhaust Inheritance Systems");
            buttons.add(aiDEVButton);
        }
        if (player.hasRelicReady("prophetstears")) {
            Button pT1 = Button.danger("prophetsTears_AC", "Exhaust Prophets Tears for AC");
            buttons.add(pT1);
            Button pT2 = Button.danger("prophetsTears_TechSkip", "Exhaust Prophets Tears for Tech Skip");
            buttons.add(pT2);
        }
        if (player.hasExternalAccessToLeader("jolnaragent") || player.hasUnexhaustedLeader("jolnaragent")) {
            Button pT2 = Button.secondary("exhaustAgent_jolnaragent",
                "Use Jol-Nar Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Jolnar));
            buttons.add(pT2);
        }
        if (player.hasUnexhaustedLeader("veldyragent")) {
            Button winnuButton = Button
                .danger("exhaustAgent_veldyragent_" + player.getFaction(),
                    "Use Veldyr Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.veldyr));
            buttons.add(winnuButton);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
            Button pT2 = Button.secondary("yinCommanderStep1_", "Remove Inf Via Yin Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.Yin));
            buttons.add(pT2);
        }
        Button doneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }
        if (ButtonHelper.isLawInPlay(game, "revolution")) {
            MessageHelper.sendMessageToChannelWithButton(getCorrectChannel(player, game),
                player.getRepresentation()
                    + " Due to the anti-intellectual revolution law, you now have to kill a non-fighter ship if you researched the tech you just acquired",
                Button.secondary("getModifyTiles", "Modify Units"));
        }
    }

    public static void forceARefresh(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String msg = getIdent(player) + " forced " + getIdentOrColor(p2, game) + " to refresh";
        String msg2 = p2.getRepresentation(true, true) + " the trade holder has forced you to refresh";
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), msg);
        MessageHelper.sendMessageToChannel(getCorrectChannel(p2, game), msg2);
        deleteTheOneButton(event);
        if (!p2.getFollowedSCs().contains(5)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(p2, 5, game, event);
        }
        p2.addFollowedSC(5, event);
        ButtonHelperStats.replenishComms(event, game, p2, true);
    }

    public static List<Button> getForcedRefreshButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFoWMode()) {
                buttons.add(Button.secondary("forceARefresh_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("forceARefresh_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        buttons.add(Button.danger("deleteButtons", "Done Resolving"));
        return buttons;
    }

    public static void resolveTACheck(Game game, Player player, GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getFaction().equalsIgnoreCase(player.getFaction())) {
                continue;
            }
            if (p2.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("useTA_" + player.getColor(), "Use TA"));
                buttons.add(Button.danger("deleteButtons", "Decline to use TA"));
                String message = p2.getRepresentation(true, true)
                    + " a player who's TA you hold has refreshed their comms, would you like to play the TA?";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, buttons);
            }
        }
    }

    public static void offerDeckButtons(Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("showDeck_frontier", "Frontier").withEmoji(Emoji.fromFormatted(Emojis.Frontier)));
        buttons.add(Buttons.blue("showDeck_cultural", "Cultural").withEmoji(Emoji.fromFormatted(Emojis.Cultural)));
        buttons.add(Buttons.red("showDeck_hazardous", "Hazardous").withEmoji(Emoji.fromFormatted(Emojis.Hazardous)));
        buttons.add(Buttons.green("showDeck_industrial", "Industrial").withEmoji(Emoji.fromFormatted(Emojis.Industrial)));
        buttons.add(Buttons.gray("showDeck_all", "All Explores"));
        buttons.add(Buttons.blue("showDeck_propulsion", "Propulsion Techs").withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech)));
        buttons.add(Buttons.red("showDeck_warfare", "Warfare Techs").withEmoji(Emoji.fromFormatted(Emojis.WarfareTech)));
        buttons.add(Buttons.gray("showDeck_cybernetic", "Cybernetic Techs").withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech)));
        buttons.add(Buttons.green("showDeck_biotic", "Biotic Techs").withEmoji(Emoji.fromFormatted(Emojis.BioticTech)));
        buttons.add(Buttons.gray("showDeck_ac", "AC Discards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
        buttons.add(Buttons.gray("showDeck_unplayedAC", "Unplayed ACs").withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
        buttons.add(Buttons.gray("showDeck_agenda", "Agenda Discards").withEmoji(Emoji.fromFormatted(Emojis.Agenda)));
        buttons.add(Buttons.gray("showDeck_relic", "Relics").withEmoji(Emoji.fromFormatted(Emojis.Relic)));
        buttons.add(Buttons.gray("showDeck_unscoredSO", "Unscored SOs").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
        buttons.add(Buttons.gray("showObjInfo_both", "All Revealed Objectives in Game").withEmoji(Emoji.fromFormatted(Emojis.Public1)));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Pick a deck to show:", buttons);
    }

    public static void resolveDeckChoice(Game game, ButtonInteractionEvent event, String buttonID, Player player) {
        String deck = buttonID.replace("showDeck_", "");
        switch (deck) {
            case "ac" -> ShowDiscardActionCards.showDiscard(game, event);
            case "agenda" -> ShowDiscardedAgendas.showDiscards(game, event);
            case "relic" -> ShowRemainingRelics.showRemaining(event, false, game, player);
            case "unscoredSO" -> ShowUnScoredSOs.showUnscored(game, event);
            case "unplayedAC" -> showUnplayedACs(game, event);
            case Constants.PROPULSION, Constants.WARFARE, Constants.CYBERNETIC, Constants.BIOTIC -> TechShowDeck.displayTechDeck(game, event, deck);
            case Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER, "all" -> {
                List<String> types = new ArrayList<>();
                String msg = "You can click this button to get the full text";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("showTextOfDeck_" + deck, "Show full text"));
                buttons.add(Button.danger("deleteButtons", "No Thanks"));
                if ("all".equalsIgnoreCase(deck)) { // Show all explores
                    types.add(Constants.CULTURAL);
                    types.add(Constants.INDUSTRIAL);
                    types.add(Constants.HAZARDOUS);
                    types.add(Constants.FRONTIER);
                } else {
                    types.add(deck);
                }
                ExpInfo.secondHalfOfExpInfo(types, event, player, game, false);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            }
            default -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Deck Button Not Implemented: " + deck);
        }
        deleteMessage(event);
    }

    public static void resolveShowFullTextDeckChoice(Game game, ButtonInteractionEvent event, String buttonID, Player player) {
        String type = buttonID.split("_")[1];
        List<String> types = new ArrayList<>();
        if ("all".equalsIgnoreCase(type)) {
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
            ExpInfo.secondHalfOfExpInfo(types, event, player, game, false, true);
        } else {
            types.add(type);
            ExpInfo.secondHalfOfExpInfo(types, event, player, game, false, true);
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
        for (String law : game.getLaws().keySet()) {
            if (lawID.equalsIgnoreCase(law)) {
                if (game.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())
                    || game.getLawsInfo().get(law).equalsIgnoreCase(player.getColor())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void drawStatusACs(Game game, Player player, ButtonInteractionEvent event) {
        if (game.getACDrawStatusInfo().contains(player.getFaction())) {
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
            message = getIdent(player) + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(player.getUserID());
            if (player.hasTech("nm")) {
                message = " Neural motivator has been accounted for.";
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

        // if (player.getRelics().contains("absol_codex")) {
        // amount = amount + 1;
        // game.drawActionCard(player.getUserID());
        // message = message + " Absol Codex has been accounted for.";
        // }

        StringBuilder messageBuilder = new StringBuilder(message);
        if (isPlayerElected(game, player, "minister_policy") && !player.hasAbility("scheming")) {
            messageBuilder.append(
                " Minister of Policy has been accounted for. If this AC is political stability, you cannot play it at this time. ");
            game.drawActionCard(player.getUserID());
            amount = amount + 1;
        }
        message = messageBuilder.toString();

        if (!player.hasAbility("autonetic_memory")) {
            message = "Drew " + amount + " AC." + message;
        }

        ACInfo.sendActionCardInfo(game, player, event);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            commanderUnlockCheck(player, game, "yssaril", event);
        }
        if (player.hasAbility("scheming")) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentation(true, true) + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(game, player, false));
        }

        addReaction(event, true, false, message, "");
        checkACLimit(game, event, player);
        game.setACDrawStatusInfo(game.getACDrawStatusInfo() + "_" + player.getFaction());
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
                    if (game.isFoWMode()) {
                        channel = player.getPrivateChannel();
                    }
                    int numOfNeighbors = player.getNeighbourCount();
                    String message = player.getRepresentation(true, true)
                        + " Minister of Commerce triggered, your tgs have increased due to your " +
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
        boolean justChecking, GenericInteractionCreateEvent event) {
        int numberOfAbilities = 0;
        if (game.getL1Hero()) {
            return 0;
        }
        String activePlayerident = player.getRepresentation();
        MessageChannel channel = game.getActionsChannel();

        Player ghostPlayer = Helper.getPlayerFromUnit(game, "ghost_mech");
        if (!game.isFoWMode() && ghostPlayer != null && ghostPlayer != player
            && getNumberOfUnitsOnTheBoard(game, ghostPlayer, "mech", false) > 0
            && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                "This is a reminder that if you are moving via creuss wormhole, you should first pause and check if the creuss player wants to use their mech to move that wormhole. ");
        }
        if (!game.isFoWMode() && ButtonHelper.isLawInPlay(game, "minister_peace")) {
            if (FoWHelper.otherPlayersHaveUnitsInSystem(player, activeSystem, game)) {
                for (Player p2 : game.getRealPlayers()) {
                    if (isPlayerElected(game, p2, "minister_peace")) {
                        if (p2 != player) {
                            List<Button> buttons2 = new ArrayList<>();
                            Button hacanButton = Button.secondary("ministerOfPeace", "Use Minister of Peace")
                                .withEmoji(Emoji.fromFormatted(Emojis.Agenda));
                            buttons2.add(hacanButton);
                            MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(),
                                "Reminder you can use minister of peace", buttons2);
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                "Reminder you should really check in with the minister of peace if this activation has the possibility of being relevant. If you proceed over their window, a rollback may be required");
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
            if (game.isFoWMode()) {
                channel = nonActivePlayer.getPrivateChannel();
            }
            String fincheckerForNonActive = "FFCC_" + nonActivePlayer.getFaction() + "_";
            String ident = nonActivePlayer.getRepresentation(true, true);
            // eres
            if (nonActivePlayer.getTechs().contains("ers")
                && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger eres.");
                    }
                    numberOfAbilities++;
                } else {
                    int cTG = nonActivePlayer.getTg();
                    nonActivePlayer.setTg(cTG + 4);
                    MessageHelper.sendMessageToChannel(channel,
                        ident + " gained 4 tg (" + cTG + "->" + nonActivePlayer.getTg() + ")");
                    ButtonHelperAgents.resolveArtunoCheck(nonActivePlayer, game, 4);
                    ButtonHelperAbilities.pillageCheck(nonActivePlayer, game);
                }
            }
            // keleres_fs
            if (nonActivePlayer.hasUnit("keleres_flagship") && activeSystem.getUnitHolders().get("space")
                .getUnitCount(UnitType.Flagship, nonActivePlayer.getColor()) > 0) {
                if (justChecking) {
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel,
                            "Warning: you would have to pay 2 influence to activate this system due to Keleres FS");
                    }
                    numberOfAbilities++;
                } else {
                    List<Button> buttons = getExhaustButtonsWithTG(game, player, "inf");
                    Button doneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    MessageHelper.sendMessageToChannel(channel,
                        activePlayerident + " you must pay 2 influence due to Keleres FS");
                    MessageHelper.sendMessageToChannelWithButtons(channel,
                        "Click the names of the planets you wish to exhaust", buttons);
                }
            }
            // neuroglaive
            if (nonActivePlayer.getTechs().contains("ng")
                && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger neuroglaive");
                    }
                    numberOfAbilities++;
                } else {
                    int cTG = player.getFleetCC();
                    player.setFleetCC(cTG - 1);
                    if (game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, ident + " you triggered neuroglaive");
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, activePlayerident
                        + " lost 1 fleet CC due to neuroglaive (" + cTG + "->" + player.getFleetCC() + ")");
                    checkFleetInEveryTile(player, game, event);
                }
            }
            if (FoWHelper.playerHasUnitsInSystem(nonActivePlayer, activeSystem)) {
                if (nonActivePlayer.getActionCards().containsKey("fsb")) {
                    MessageHelper.sendMessageToChannel(nonActivePlayer.getCardsInfoThread(),
                        nonActivePlayer.getRepresentation()
                            + " Reminder that you have forward supply base and this is the window for it");
                }
                if (nonActivePlayer.getPromissoryNotes().containsKey(player.getColor() + "_cf")) {
                    MessageHelper.sendMessageToChannel(nonActivePlayer.getCardsInfoThread(), nonActivePlayer
                        .getRepresentation()
                        + " Reminder that you have the active players ceasefire and this is the window for it");
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
                        reverseButtons.add(Button.success(
                            Constants.AC_PLAY_FROM_HAND + nonActivePlayer.getActionCards().get(key)
                                + "_counterstroke_" + activeSystem.getPosition(),
                            "Counterstroke in " + activeSystem.getRepresentationForButtons(game, nonActivePlayer)));
                    }
                    reverseButtons.add(Button.danger("deleteButtons", "Decline"));
                    String cyberMessage = nonActivePlayer.getRepresentation(true, true)
                        + " reminder that you can use counterstroke in "
                        + activeSystem.getRepresentationForButtons(game, nonActivePlayer);
                    MessageHelper.sendMessageToChannelWithButtons(nonActivePlayer.getCardsInfoThread(),
                        cyberMessage, reverseButtons);
                }
            }
            if (nonActivePlayer.ownsUnit("nivyn_mech")
                && getTilesOfPlayersSpecificUnits(game, nonActivePlayer, UnitType.Mech).contains(activeSystem)) {
                Button nivynButton = Button.secondary("nivynMechStep1_", "Use Nivyn Mech")
                    .withEmoji(Emoji.fromFormatted(Emojis.nivyn));
                Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Wound");
                List<Button> buttons = List.of(nivynButton, decline);
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, game),
                    ident + " use buttons to resolve potential wound ", buttons);
            }
            if (game.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "arboreccommander")
                && Helper.getProductionValue(nonActivePlayer, game, activeSystem, false) > 0) {
                if (justChecking) {
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger the arborec commander");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(
                        fincheckerForNonActive + "arboCommanderBuild_" + activeSystem.getPosition(),
                        "Build 1 Unit");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, game),
                        ident + " use buttons to resolve Arborec commander ", buttons);
                }
            }
            if (nonActivePlayer.hasLeaderUnlocked("celdaurihero")
                && FoWHelper.playerHasPlanetsInSystem(nonActivePlayer, activeSystem)
                && !activeSystem.isMecatol()) {
                if (justChecking) {
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel,
                            "Warning: you would trigger a chance to play celdauri Hero");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(
                        fincheckerForNonActive + "purgeCeldauriHero_" + activeSystem.getPosition(),
                        "Use Celdauri Hero");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Hero");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, game),
                        ident + " use buttons to decide if you want to use Celdauri Hero", buttons);
                }
            }
            if (nonActivePlayer.hasUnit("mahact_mech") && nonActivePlayer.hasMechInSystem(activeSystem)
                && nonActivePlayer.getMahactCC().contains(player.getColor())
                && !ButtonHelper.isLawInPlay(game, "articles_war")) {
                if (justChecking) {
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel,
                            "Warning: you would trigger an opportunity for a mahact mech trigger");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(
                        fincheckerForNonActive + "mahactMechHit_" + activeSystem.getPosition() + "_"
                            + player.getColor(),
                        "Return " + player.getColor() + " CC and end their turn");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline To Use Mech");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, game),
                        ident + " use buttons to resolve Mahact mech ability ", buttons);
                }
            }
            if (nonActivePlayer.hasTechReady("nf") && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)
                && nonActivePlayer.getStrategicCC() > 0) {
                if (justChecking) {
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel,
                            "Warning: you would trigger an opportunity for nullification field to trigger");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(
                        fincheckerForNonActive + "nullificationField_" + activeSystem.getPosition() + "_"
                            + player.getColor(),
                        "Spend Strat CC and end their turn");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons",
                        "Decline To Use Nullification Field");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(channel,
                        ident + " use buttons to resolve Nullfication field ", buttons);
                }
            }
            if (game.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "yssarilcommander")
                && FoWHelper.playerHasUnitsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger yssaril commander");
                    }
                    numberOfAbilities++;
                } else {
                    Button lookAtACs = Button.success(
                        fincheckerForNonActive + "yssarilcommander_ac_" + player.getFaction(),
                        "Look at ACs (" + player.getAc() + ")");
                    Button lookAtPNs = Button.success(
                        fincheckerForNonActive + "yssarilcommander_pn_" + player.getFaction(),
                        "Look at PNs (" + player.getPnCount() + ")");
                    Button lookAtSOs = Button.success(
                        fincheckerForNonActive + "yssarilcommander_so_" + player.getFaction(),
                        "Look at SOs (" + (player.getSo()) + ")");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(lookAtACs, lookAtPNs, lookAtSOs, decline);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, game),
                        ident + " use buttons to resolve Yssaril commander ", buttons);
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
                        if (!game.isFoWMode()) {
                            MessageHelper.sendMessageToChannel(channel,
                                "Warning: you would trigger the return of a PN (" + pnModel.getName() + ")");
                        }
                        numberOfAbilities++;
                    } else {
                        player.removePromissoryNote(pn);
                        nonActivePlayer.setPromissoryNote(pn);
                        PNInfo.sendPromissoryNoteInfo(game, nonActivePlayer, false);
                        PNInfo.sendPromissoryNoteInfo(game, player, false);
                        MessageHelper.sendMessageToChannel(channel, nonActivePlayer.getFactionEmoji() + " " + pnModel.getName() + " was returned");
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
        if (planet.getTechSpecialities().size() > 0) {
            techPresent = true;
        }
        return techPresent;
    }

    public static String getTechSkipAttachments(Game game, String planetName) {
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        if (tile == null) {
            BotLogger.log("Couldnt find tile for " + planetName + " in game " + game.getName());
            return "none";
        }
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        if (unitHolder == null) {
            BotLogger.log("Couldnt find unitholder for " + planetName + " in game " + game.getName());
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
            buttons.add(Button.success("refresh_" + planet + "_" + player.getFaction(),
                "Ready " + Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Button.danger("deleteButtons_spitItOut", "Delete These Buttons"));
        return buttons;
    }

    public static List<Button> getAllTechsToReady(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getExhaustedTechs()) {
            buttons.add(Button.success("biostimsReady_tech_" + tech, "Ready " + Mapper.getTechs().get(tech).getName()));
        }
        return buttons;
    }

    public static void sendAllTechsNTechSkipPlanetsToReady(Game game, GenericInteractionCreateEvent event,
        Player player, boolean absol) {
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getExhaustedTechs()) {
            buttons.add(Button.success("biostimsReady_tech_" + tech, "Ready " + Mapper.getTechs().get(tech).getName()));
        }
        for (String planet : player.getExhaustedPlanets()) {
            if (absol || checkForTechSkips(game, planet)) {
                buttons.add(Button.success("biostimsReady_planet_" + planet,
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
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, Emojis.celdauri + Emojis.WarfareTech + "Emergency Mobilization Protocols");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " spent a strat cc to remove a CC from a place they have a dock");
        }
        String finChecker = "FFCC_" + player.getFaction() + "_";
        for (Tile tile : getTilesWithYourCC(player, game, event)) {
            if (getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock).contains(tile)) {
                buttonsToRemoveCC.add(Button.success(
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
                Button.success("absolX89Nuke_" + planet, "Nuke " + Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Use buttons to select a planet to nuke", buttons);
    }

    public static List<Button> getPsychoTechPlanets(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            if (checkForTechSkips(game, planet)) {
                buttons.add(Button.success("psychoExhaust_" + planet,
                    "Exhaust " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete Buttons"));
        return buttons;
    }

    public static void resolvePsychoExhaust(Game game, ButtonInteractionEvent event, Player player, String buttonID) {
        int oldTg = player.getTg();
        player.setTg(oldTg + 1);
        String planet = buttonID.split("_")[1];
        player.exhaustPlanet(planet);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            getIdent(player) + " exhausted " + Helper.getPlanetRepresentation(planet, game)
                + " and gained 1tg (" + oldTg + "->" + player.getTg() + ") using the psychoarcheology tech");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        deleteTheOneButton(event);
    }

    public static void bioStimsReady(Game game, GenericInteractionCreateEvent event, Player player, String buttonID) {
        buttonID = buttonID.replace("biostimsReady_", "");
        String last = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        if (buttonID.contains("tech_")) {
            last = buttonID.replace("tech_", "");
            player.refreshTech(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getRepresentation() + " readied tech: " + Mapper.getTech(last).getRepresentation(false));
            if (player.getLeaderIDs().contains("kolumecommander") && !player.hasLeaderUnlocked("kolumecommander")) {
                commanderUnlockCheck(player, game, "kolume", event);
            }
        } else {
            player.refreshPlanet(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                + " readied planet: " + Helper.getPlanetRepresentation(last, game));
        }
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
            if (game.getLawsInfo().get("absol_sanctions") != null && game.getLawsInfo().get("absol_sanctions").equalsIgnoreCase(player.getFaction())) {
                limit = 5;
            }
        }
        if (player.getRelics().contains("absol_codex")) {
            limit = limit + 5;
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
            if (game.isFoWMode()) {
                channel = player.getPrivateChannel();
            }
            String ident = player.getRepresentation(true, true);
            MessageHelper.sendMessageToChannel(channel,
                ident + " you are exceeding the AC hand limit of " + limit
                    + ". Please discard down to the limit. Check your cards info thread for the blue discard buttons. ");
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                ident + " use buttons to discard", ACInfo.getDiscardActionCardButtons(game, player, false));
        }
    }

    public static void updateMap(Game game, GenericInteractionCreateEvent event) {
        updateMap(game, event, "");
    }

    public static void tradePrimary(Game game, GenericInteractionCreateEvent event, Player player) {
        if (!player.getSCs().contains(5) || player.getFollowedSCs().contains(5)) {
            return;
        }
        int tg = player.getTg();
        player.setTg(tg + 3);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 3);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "hacan", event);

        boolean reacted = false;
        ButtonHelperAbilities.pillageCheck(player, game);
        if (event instanceof ButtonInteractionEvent e) {
            reacted = true;
            String msg = " gained 3" + Emojis.getTGorNomadCoinEmoji(game) + " and replenished commodities (" + player.getCommoditiesTotal() + Emojis.comm + ")";
            ButtonHelper.addReaction(e, false, false, msg, "");
        }
        ButtonHelperStats.replenishComms(event, game, player, reacted);
    }

    public static void updateMap(Game game, GenericInteractionCreateEvent event, String message) {
        String threadName = game.getName() + "-bot-map-updates";
        List<ThreadChannel> threadChannels = game.getActionsChannel().getThreadChannels();
        MapGenerator.saveImage(game, DisplayType.all, event)
            .thenApply(fileUpload -> {
                boolean foundSomething = false;
                if (!game.isFoWMode()) {
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            foundSomething = true;

                            List<Button> buttonsWeb = new ArrayList<>();
                            if (!game.isFoWMode()) {
                                Button linkToWebsite = Button.link(
                                    "https://ti4.westaddisonheavyindustries.com/game/" + game.getName(),
                                    "Website View");
                                buttonsWeb.add(linkToWebsite);
                                buttonsWeb.add(Button.success("gameInfoButtons", "Player Info"));
                            }
                            buttonsWeb.add(Button.success("cardsInfo", "Cards Info"));
                            buttonsWeb.add(Button.primary("offerDeckButtons", "Show Decks"));
                            buttonsWeb.add(Button.secondary("showGameAgain", "Show Game"));

                            MessageHelper.sendFileToChannelWithButtonsAfter(threadChannel_, fileUpload, message, buttonsWeb);
                        }
                    }
                } else {
                    MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), fileUpload);
                    foundSomething = true;
                }
                if (!foundSomething) {
                    List<Button> buttonsWeb = new ArrayList<>();
                    if (!game.isFoWMode()) {
                        Button linkToWebsite = Button.link(
                            "https://ti4.westaddisonheavyindustries.com/game/" + game.getName(),
                            "Website View");
                        buttonsWeb.add(linkToWebsite);
                        buttonsWeb.add(Button.success("gameInfoButtons", "Player Info"));
                    }
                    buttonsWeb.add(Button.success("cardsInfo", "Cards Info"));
                    buttonsWeb.add(Button.primary("offerDeckButtons", "Show Decks"));
                    buttonsWeb.add(Button.secondary("showGameAgain", "Show Game"));

                    MessageHelper.sendFileToChannelWithButtonsAfter(event.getMessageChannel(), fileUpload, message, buttonsWeb);
                }
                return fileUpload;
            });
    }

    public static boolean nomadHeroAndDomOrbCheck(Player player, Game game, Tile tile) {
        if (game.getDominusOrbStatus() || game.getL1Hero()) {
            return true;
        }
        return player.getLeader("nomadhero").map(Leader::isActive).orElse(false);
    }

    public static int getAllTilesWithAlphaNBetaNUnits(Player player, Game game) {
        game.getTileMap().values().stream().filter(t -> t.containsPlayersUnits(player));
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)
                && FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition())) {
                count = count + 1;
            }
        }
        return count;
    }

    public static int getNumberOfGroundForces(Player player, UnitHolder uH) {
        int count = uH.getUnitCount(UnitType.Mech, player) + uH.getUnitCount(UnitType.Infantry, player);
        if (player.hasUnit("titans_pds") || player.hasUnit("titans_pds2")) {
            count = count + uH.getUnitCount(UnitType.Pds, player);
        }
        return count;
    }

    public static int getNumberOfTilesPlayerIsInWithNoPlanets(Game game, Player player) {
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.getPlanetUnitHolders().size() == 0) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfUncontrolledNonLegendaryPlanets(Game game) {
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder plan : tile.getPlanetUnitHolders()) {
                if (plan.getName().contains("mallice")) {
                    continue;
                }
                Planet planet = (Planet) plan;
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
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.isAnomaly()
                && !tile.isHomeSystem()) {
                if (tile.isGravityRift(game)) {
                    grav = 1;
                } else if (tile.isNebula()) {
                    nebula = 1;
                } else if (tile.isAsteroidField()) {
                    asteroids = 1;
                } else {
                    count = 1;
                }
            }
        }
        return count + asteroids + grav + nebula;
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

    public static int getNumberOfXTypePlanets(Player player, Game game, String type) {
        int count = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
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
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff")) {
                count = count + space.getUnits().get(unit);
            }
        }
        return count;
    }

    public static int checkNumberShips(Player player, Game game, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
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
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
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
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
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
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff")) {
                count = count + (int) removedUnit.getCost() * space.getUnits().get(unit);
            }
        }
        return count;
    }

    public static float checkValuesOfUnits(Player player, Game game, Tile tile) {
        float count = 0;
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            for (UnitKey unit : uh.getUnits().keySet()) {
                if (!unit.getColor().equals(player.getColor())) {
                    continue;
                }
                if (player.getUnitsByAsyncID(unit.asyncID()).size() == 0) {
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                if (removedUnit.getIsShip() || removedUnit.getIsGroundForce()) {
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
                if (player.getUnitsByAsyncID(unit.asyncID()).size() == 0) {
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
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

    public static float checkCombatValuesOfUnits(Player player, Game game, Tile tile) {
        float count = 0;
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            for (UnitKey unit : uh.getUnits().keySet()) {
                if (!unit.getColor().equals(player.getColor())) {
                    continue;
                }
                if (player.getUnitsByAsyncID(unit.asyncID()).size() == 0) {
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                float unrelententing = 0;
                if (player.hasAbility("unrelenting")) {
                    unrelententing = (float) 0.1;
                } else if (player.hasAbility("fragile")) {
                    unrelententing = (float) -0.1;
                }
                if (removedUnit.getIsShip() || removedUnit.getIsGroundForce()) {
                    count = count + removedUnit.getCombatDieCount()
                        * (((float) 11.0 - removedUnit.getCombatHitsOn()) / 10 + unrelententing)
                        * uh.getUnits().get(unit);
                }
            }
        }
        return Math.round(count * 10) / (float) 10.0;
    }

    public static int checkHPOfUnits(Player player, Game game, Tile tile) {
        int count = 0;
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            for (UnitKey unit : uh.getUnits().keySet()) {
                if (!unit.getColor().equals(player.getColor())) {
                    continue;
                }
                if (player.getUnitsByAsyncID(unit.asyncID()).size() == 0) {
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                if (removedUnit.getIsShip() || removedUnit.getIsGroundForce()) {
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
            if (game.getTileFromPlanet(planet).isHomeSystem()) {
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

    public static void fullCommanderUnlockCheck(Player player, Game game, String faction,
        GenericInteractionCreateEvent event) {
        if (player.getLeaderIDs().contains(faction + "commander") && !player.hasLeaderUnlocked(faction + "commander")) {
            commanderUnlockCheck(player, game, faction, event);
        }
    }

    public static void commanderUnlockCheck(Player player, Game game, String faction, GenericInteractionCreateEvent event) {
        boolean shouldBeUnlocked = false;
        switch (faction) {
            case "axis" -> {
                if (ButtonHelperAbilities.getNumberOfDifferentAxisOrdersBought(player, game) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "rohdhna" -> {
                if (checkHighestProductionSystem(player, game) > 6) {
                    shouldBeUnlocked = true;
                }
            }
            case "freesystems" -> {
                if (getNumberOfUncontrolledNonLegendaryPlanets(game) < 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "mortheus" -> {
                if (getNumberOfSystemsWithShipsNotAdjacentToHS(player, game) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "celdauri" -> {
                if (getNumberOfSpacedocksNotInOrAdjacentHS(player, game) > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "cheiran" -> {
                if (getNumberOfPlanetsWithStructuresNotInHS(player, game) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "vaden" -> {
                if (howManyDifferentDebtPlayerHas(player) > (game.getRealPlayers().size() / 2) - 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "gledge" -> {
                if (checkHighestCostSystem(player, game) > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "olradin" -> {
                if (getNumberOfXTypePlanets(player, game, "industrial") > 0
                    && getNumberOfXTypePlanets(player, game, "cultural") > 0
                    && getNumberOfXTypePlanets(player, game, "hazardous") > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "vaylerian" -> {
                if (getNumberOfXTypePlanets(player, game, "industrial") > 2
                    || getNumberOfXTypePlanets(player, game, "cultural") > 2
                    || getNumberOfXTypePlanets(player, game, "hazardous") > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "ghoti" -> {
                if (getNumberOfTilesPlayerIsInWithNoPlanets(game, player) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nivyn" -> {
                if (getNumberOfNonHomeAnomaliesPlayerIsIn(game, player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "zelian" -> {
                if (getNumberOfAsteroidsPlayerIsIn(game, player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "yssaril" -> {
                if (player.getActionCards().size() > 7
                    || (player.getExhaustedTechs().contains("mi") && player.getActionCards().size() > 6)) {
                    shouldBeUnlocked = true;
                }
            }
            case "kjalengard" -> {
                if (ButtonHelperAgents.getGloryTokenTiles(game).size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "kolume" -> {
                shouldBeUnlocked = true;

            }
            case "veldyr" -> {
                if (ButtonHelperFactionSpecific.getPlayersWithBranchOffices(game, player).size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "mirveda" -> {
                if (getNumberOfUnitUpgrades(player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "dihmohn" -> {
                if (getNumberOfUnitUpgrades(player) > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "kollecc" -> {
                if (player.getCrf() + player.getHrf() + player.getIrf() + player.getUrf() > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "bentor" -> {
                if (player.getNumberOfBluePrints() > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "edyn" -> {
                if (game.getLaws().size() > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "lizho" -> {
                if (player.getTrapCardsPlanets().size() > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "zealots" -> shouldBeUnlocked = true;
            case "yin" -> shouldBeUnlocked = true;
            case "florzen" -> shouldBeUnlocked = true;
            case "letnev" -> shouldBeUnlocked = true;
            case "kortali" -> shouldBeUnlocked = true;
            case "augers" -> shouldBeUnlocked = true;
            case "hacan" -> {
                if (player.getTg() > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "mykomentori" -> {
                if (player.getCommodities() > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "sardakk" -> {
                if (player.getPlanets().size() > 6) {
                    shouldBeUnlocked = true;
                }
            }
            case "ghost" -> {
                if (getAllTilesWithAlphaNBetaNUnits(player, game) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "sol" -> {
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources = resources + Helper.getPlanetResources(planet, game);
                }
                if (resources > 11) {
                    shouldBeUnlocked = true;
                }
            }
            case "xxcha" -> {
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources = resources + Helper.getPlanetInfluence(planet, game);
                }
                if (resources > 11) {
                    shouldBeUnlocked = true;
                }
            }
            case "mentak" -> {
                if (getNumberOfUnitsOnTheBoard(game, player, "cruiser", false) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "ghemina" -> {
                if ((getNumberOfUnitsOnTheBoard(game, player, "flagship", false)
                    + getNumberOfUnitsOnTheBoard(game, player, "lady", false)) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "tnelis" -> {
                if (getNumberOfUnitsOnTheBoard(game, player, "destroyer", false) > 5) {
                    shouldBeUnlocked = true;
                }
            }
            case "cymiae" -> {
                if (getNumberOfUnitsOnTheBoard(game, player, "infantry", false) > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "kyro" -> {
                if (getNumberOfUnitsOnTheBoard(game, player, "infantry", false) > 5
                    && getNumberOfUnitsOnTheBoard(game, player, "fighter", false) > 5) {
                    shouldBeUnlocked = true;
                }
            }
            case "l1z1x" -> {
                if (getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "argent" -> {
                int num = getNumberOfUnitsOnTheBoard(game, player, "pds", false)
                    + getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false)
                    + getNumberOfUnitsOnTheBoard(game, player, "destroyer", false);
                if (num > 5) {
                    shouldBeUnlocked = true;
                }
            }
            case "titans" -> {
                int num = getNumberOfUnitsOnTheBoard(game, player, "pds")
                    + getNumberOfUnitsOnTheBoard(game, player, "spacedock");
                if (num > 4) {
                    shouldBeUnlocked = true;
                }
            }
            case "cabal" -> {
                int num = getNumberOfGravRiftsPlayerIsIn(player, game);
                if (num > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nekro" -> {
                int count = 2;
                if (player.hasTech("vax")) {
                    count++;
                }
                if (player.hasTech("vay")) {
                    count++;
                }
                if (player.getTechs().size() > count) {
                    shouldBeUnlocked = true;
                }
            }
            case "jolnar" -> {
                if (player.getTechs().size() > 7) {
                    shouldBeUnlocked = true;
                }
            }
            case "saar" -> {
                if (getNumberOfUnitsOnTheBoard(game, player, "spacedock") > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "naaz" -> {
                if (getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech).size() > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nomad" -> {
                if (player.getSoScored() > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "mahact" -> {
                if (player.getMahactCC().size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "empyrean" -> {
                if (player.getNeighbourCount() > (game.getRealPlayers().size() - 2)) {
                    shouldBeUnlocked = true;
                }
            }
            case "muaat" -> shouldBeUnlocked = true;
            case "winnu" -> shouldBeUnlocked = true;
            case "naalu" -> {
                Tile rex = game.getMecatolTile();
                if (rex != null) {
                    for (String tilePos : FoWHelper.getAdjacentTiles(game, rex.getPosition(), player, false)) {
                        Tile tile = game.getTileByPosition(tilePos);
                        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                            if (unitHolder.getUnitCount(UnitType.Mech, player.getColor()) > 0
                                || unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                                shouldBeUnlocked = true;
                            }
                        }
                    }
                }
            }
            case "keleres" -> shouldBeUnlocked = true;
            case "arborec" -> {
                int num = getAmountOfSpecificUnitsOnPlanets(player, game, "infantry")
                    + getAmountOfSpecificUnitsOnPlanets(player, game, "mech");
                if (num > 11) {
                    shouldBeUnlocked = true;
                }
            }
            case "lanefir" -> {
                if (game.getNumberOfPurgedFragments() > 6) {
                    shouldBeUnlocked = true;
                }
            }
            // missing: yin, ghost, naalu, letnev
        }
        if (shouldBeUnlocked) {
            UnlockLeader.unlockLeader(faction + "commander", game, player);
        }
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
            planetsWithSleepers.add(Button.success(finChecker + "replaceSleeperWith_pds_" + planet,
                "Replace sleeper on " + planet + " with a pds."));
            if (getNumberOfUnitsOnTheBoard(game, player, "mech") < 4 && player.hasUnit("titans_mech")) {
                planetsWithSleepers.add(Button.success(finChecker + "replaceSleeperWith_mech_" + planet,
                    "Replace sleeper on " + planet + " with a mech and an infantry."));
            }
            planetsWithSleepers.add(Button.danger("deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to resolve sleeper",
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
                planetsWithPDS.add(Button.success(finChecker + "replacePDSWithFS_" + planet,
                    "Replace pds on " + planet + " with your flagship."));
            }
        }
        planetsWithPDS.add(Button.danger("deleteButtons", "Delete these buttons"));
        return planetsWithPDS;
    }

    public static List<Button> getButtonsForRemovingASleeper(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> planetsWithSleepers = new ArrayList<>();
        for (String planet : game.getAllPlanetsWithSleeperTokens()) {
            planetsWithSleepers.add(Button.success(finChecker + "removeSleeperFromPlanet_" + planet,
                "Remove the sleeper on " + planet + "."));
        }
        planetsWithSleepers.add(Button.danger("deleteButtons", "Delete these buttons"));
        return planetsWithSleepers;
    }

    public static void resolveTitanShenanigansOnActivation(Player player, Game game, Tile tile,
        ButtonInteractionEvent event) {
        List<Button> buttons = getButtonsForTurningPDSIntoFS(player, game, tile);
        if (buttons.size() > 1) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Use buttons to decide which pds to replace with your flagship", buttons);
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

    public static void resolveRemovingYourCC(Player player, Game game, GenericInteractionCreateEvent event,
        String buttonID) {
        buttonID = buttonID.replace("removeCCFromBoard_", "");
        String whatIsItFor = buttonID.split("_")[0];
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String tileRep = tile.getRepresentationForButtons(game, player);
        String ident = getIdentOrColor(player, game);
        String msg = ident + " removed CC from " + tileRep;
        if (whatIsItFor.contains("mahactAgent")) {
            String faction = whatIsItFor.replace("mahactAgent", "");
            if (player != null) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), msg);
            }
            player = game.getPlayerFromColorOrFaction(faction);
            msg = player.getRepresentation(true, true) + " this is a notice that " + msg + " using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Jae Mir Kan (Mahact Agent)";
        }

        if (player == null)
            return;
        RemoveCC.removeCC(event, player.getColor(), tile, game);

        String finChecker = "FFCC_" + player.getFaction() + "_";
        if ("mahactCommander".equalsIgnoreCase(whatIsItFor)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + "reduced their tactic CCs from "
                + player.getTacticalCC() + " to " + (player.getTacticalCC() - 1));
            player.setTacticalCC(player.getTacticalCC() - 1);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> conclusionButtons = new ArrayList<>();
            Button endTurn = Button.danger(finChecker + "turnEnd", "End Turn");
            conclusionButtons.add(endTurn);
            if (getEndOfTurnAbilities(player, game).size() > 1) {
                conclusionButtons.add(Button.primary("endOfTurnAbilities",
                    "Do End Of Turn Ability (" + (getEndOfTurnAbilities(player, game).size() - 1) + ")"));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn.",
                conclusionButtons);
        } else {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), msg);
        }
        if ("warfare".equalsIgnoreCase(whatIsItFor)) {
            List<Button> redistributeButton = new ArrayList<>();
            Button redistribute = Button.success("FFCC_" + player.getFaction() + "_" + "redistributeCCButtons",
                "Redistribute & Gain CCs");
            Button deleButton = Button.danger("FFCC_" + player.getFaction() + "_" + "deleteButtons",
                "Delete These Buttons");
            redistributeButton.add(redistribute);
            redistributeButton.add(deleButton);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                player.getRepresentation() + " click this after picking up a CC.", redistributeButton);
        }
    }

    public static void resolveMahactMechAbilityUse(Player mahact, Player target, Game game, Tile tile,
        ButtonInteractionEvent event) {
        mahact.removeMahactCC(target.getColor());
        if (!game.getNaaluAgent()) {
            if (!game.getStoredValue("absolLux").isEmpty()) {
                target.setTacticalCC(target.getTacticalCC() + 1);
            }
            target.setTacticalCC(target.getTacticalCC() - 1);
            AddCC.addCC(event, target.getColor(), tile);

        }

        MessageHelper.sendMessageToChannel(getCorrectChannel(mahact, game),
            mahact.getRepresentation(true, true) + " the " + target.getColor()
                + " CC has been removed from your fleet pool");
        ButtonHelper.checkFleetInEveryTile(mahact, game, event);
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Button.danger(target.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, game).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities",
                "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, game).size() - 1) + ")"));
        }
        List<Button> buttons = getGainCCButtons(target);
        String trueIdentity = target.getRepresentation(true, true);
        String message2 = trueIdentity + "! Your current CCs are " + target.getCCRepresentation()
            + ". Use buttons to gain CCs";
        game.setStoredValue("originalCCsFor" + target.getFaction(), target.getCCRepresentation());
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(target, game), message2, buttons);
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(target, game), target.getRepresentation(true,
            true)
            + " You've been hit by"
            + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "")
            + " the Mahact Starlancer mech ability. You gain a CC to any command pool. Then, use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        deleteMessage(event);
    }

    public static void resolveNullificationFieldUse(Player mahact, Player target, Game game, Tile tile, ButtonInteractionEvent event) {
        mahact.setStrategicCC(mahact.getStrategicCC() - 1);
        mahact.exhaustTech("nf");
        ButtonHelperCommanders.resolveMuaatCommanderCheck(mahact, game, event, Emojis.Xxcha + Emojis.CyberneticTech + "Nullification Field");
        if (!game.getNaaluAgent()) {
            if (!game.getStoredValue("absolLux").isEmpty()) {
                target.setTacticalCC(target.getTacticalCC() + 1);
            }
            target.setTacticalCC(target.getTacticalCC() - 1);
            AddCC.addCC(event, target.getColor(), tile);
        }
        MessageHelper.sendMessageToChannel(mahact.getCorrectChannel(), mahact.getRepresentation(true, true) + " you have spent a strategy CC");
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Button.danger(target.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, game).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, game).size() - 1) + ")"));
        }

        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), target
            .getRepresentation(true, true)
            + " You've been hit by"
            + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "")
            + " *Nullification Field*. A CC has been placed from your tactics in the system and your turn has been ended. Use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        deleteMessage(event);

    }

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

        if (!game.getNaaluAgent()) {
            if (!AddCC.hasCC(target, tile)) {
                if (!game.getStoredValue("absolLux").isEmpty()) {
                    target.setTacticalCC(target.getTacticalCC() + 1);
                }
                target.setTacticalCC(target.getTacticalCC() - 1);
                AddCC.addCC(event, target.getColor(), tile);
            }
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(minister, game),
            minister.getRepresentation(true, true) + " you have used the Minister of Peace agenda");
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Button.danger(target.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, game).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities",
                "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, game).size() - 1) + ")"));
        }

        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(target, game), target
            .getRepresentation(true, true)
            + " You've been hit by"
            + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "")
            + " *Minister of Peace*. A CC has been placed from your tactics in the system and your turn has been ended. Use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        deleteTheOneButton(event);

    }

    public static int checkNetGain(Player player, String ccs) {
        int netgain;
        int oldTactic = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/") + 1);
        int oldFleet = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/") + 1);
        int oldStrat = Integer.parseInt(ccs);

        netgain = (player.getTacticalCC() - oldTactic) + (player.getFleetCC() - oldFleet)
            + (player.getStrategicCC() - oldStrat);
        return netgain;
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
                Button.success(
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
                    Button.success("swapToFaction_" + player2.getFaction(), "Swap to " + player2.getFaction())
                        .withEmoji(Emoji.fromFormatted(player2.getFactionEmoji())));
            }
        }
        if (fromButton) {
            buttonsToRemoveCC.add(Button.danger("deleteButtons", "Delete These Buttons"));
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

    public static void deleteMessage(GenericInteractionCreateEvent event) {
        if (event != null && event instanceof ButtonInteractionEvent bevent)
            bevent.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void deleteAllButtons(ButtonInteractionEvent event) {
        if (event == null) return;
        event.editComponents(Collections.emptyList()).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void deleteTheOneButton(ButtonInteractionEvent event) {
        deleteTheOneButton(event, true);
    }

    public static void deleteTheOneButton(ButtonInteractionEvent event, boolean deleteMsg) {
        if (event == null) return;
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
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {

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
        if (actionRow2.size() > 0 && deleteMsg) {
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
            } else if (actionRow2.size() > 0) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
    }

    public static void findOrCreateThreadWithMessage(Game game, String threadName, String message) {
        MessageChannel channel = game.getMainGameChannel();
        Helper.checkThreadLimitAndArchive(game.getGuild());
        TextChannel textChannel = (TextChannel) channel;
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
            if (finalThreadName.contains("undo-log")) duration = ThreadChannel.AutoArchiveDuration.TIME_1_HOUR;

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
            if (label.length() < 1) {
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
                if (emoji.length() > 0) {
                    buttons.add(Button.success(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.success(id, label));
                }
            } else if ("danger".equalsIgnoreCase(style)) {
                if (emoji.length() > 0) {
                    buttons.add(Button.danger(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.danger(id, label));
                }
            } else if ("secondary".equalsIgnoreCase(style)) {
                if (emoji.length() > 0) {
                    buttons.add(Button.secondary(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.secondary(id, label));
                }
            } else {
                if (emoji.length() > 0) {
                    buttons.add(Button.primary(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.primary(id, label));
                }
            }
        }
        return buttons;
    }

    public static void resolveWarForgeRuins(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String mech = buttonID.split("_")[2];
        String message = "";
        boolean failed;
        message = message + mechOrInfCheck(planet, game, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            if ("mech".equalsIgnoreCase(mech)) {
                new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet),
                    "mech " + planet, game);
                message = message + "Placed mech on" + Mapper.getPlanet(planet).getName();
            } else {
                new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet),
                    "2 infantry " + planet, game);
                message = message + "Placed 2 infantry on" + Mapper.getPlanet(planet).getName();
            }
            addReaction(event, false, false, message, "");
            deleteMessage(event);
        } else {
            addReaction(event, false, false, message, "");
        }
    }

    public static void resolveSeedySpace(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[2];
        String acOrAgent = buttonID.split("_")[1];
        String message = "";
        boolean failed;
        message = message + mechOrInfCheck(planet, game, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            if ("ac".equalsIgnoreCase(acOrAgent)) {
                if (player.hasAbility("scheming")) {
                    game.drawActionCard(player.getUserID());
                    game.drawActionCard(player.getUserID());
                    message = getIdent(player) + " Drew 2 AC With Scheming. Please Discard An AC with the blue buttons";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentation(true, true) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(game, player, false));
                } else {
                    game.drawActionCard(player.getUserID());
                    message = getIdent(player) + " Drew 1 AC";
                    ACInfo.sendActionCardInfo(game, player, event);
                }
                if (player.getLeaderIDs().contains("yssarilcommander")
                    && !player.hasLeaderUnlocked("yssarilcommander")) {
                    commanderUnlockCheck(player, game, "yssaril", event);
                }
            } else {
                Leader playerLeader = player.getLeader(acOrAgent).orElse(null);
                if (playerLeader == null) {
                    return;
                }
                RefreshLeader.refreshLeader(player, playerLeader, game);
                message = message + " Refreshed " + Mapper.getLeader(acOrAgent).getName();
            }
            addReaction(event, false, false, message, "");
            deleteMessage(event);
        } else {
            addReaction(event, false, false, message, "");
        }
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

    public static boolean isPlanetLegendaryOrHome(String planetName, Game game, boolean onlyIncludeYourHome,
        Player p1) {
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

    public static void checkFleetInEveryTile(Player player, Game game, GenericInteractionCreateEvent event) {
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                checkFleetAndCapacity(player, game, tile, event);
            }
        }
        Helper.isCCCountCorrect(event, game, player.getColor());
    }

    public static void checkFleetAndCapacity(Player player, Game game, Tile tile, GenericInteractionCreateEvent event) {
        if (tile.getRepresentation() == null || "null".equalsIgnoreCase(tile.getRepresentation())) {
            return;
        }
        if (tile.getRepresentation().toLowerCase().contains("nombox")) {
            return;
        }
        if (!game.getCCNPlasticLimit()) {
            return;
        }
        int armadaValue = 0;
        if (player == null) {
            return;
        }
        if (player.hasAbility("armada")) {
            armadaValue = 2;
        }
        if (player.hasTech("dsghotg") && tile == player.getHomeSystemTile()) {
            armadaValue = armadaValue + 3;
        }
        int fleetCap = (player.getFleetCC() + armadaValue + player.getMahactCC().size()) * 2;
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
                fleetCap = fleetCap + 4;
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
            if (player.getLeaderIDs().contains("letnevcommander") && !player.hasLeaderUnlocked("letnevcommander")) {
                commanderUnlockCheck(player, game, "letnev", event);
            }
        }
        if (player.hasAbility("flotilla")) {
            int numInf = tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player.getColor());
            if (numInf > ((numOfCapitalShips
                + tile.getUnitHolders().get("space").getUnitCount(UnitType.Destroyer, player.getColor())) / 2)) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                    player.getRepresentation()
                        + " reminder that your flotilla ability says you cant have more infantry than non-fighter ships in the space area of a system. You seem to be violating this in "
                        + tile.getRepresentationForButtons(game, player));
            }
        }
        String message = player.getRepresentation(true, true);
        if (fleetSupplyViolated) {
            message += " You are violating fleet supply in tile " + tile.getRepresentation()
                + ". Specifically, you have " + fleetCap / 2
                + " fleet supply, and that you currently are filling " + (numFighter2sFleet + numOfCapitalShips + 1) / 2
                + " of that. ";
        }
        if (capacityViolated) {
            message += " You are violating carrying capacity in tile " + tile.getRepresentation()
                + ". Specifically, you have " + capacity
                + " capacity, and you are trying to carry "
                + (numInfNFightersNMechs - numFighter2s) + " things";
        }
        //System.out.printf("%d %d %d %d%n", fleetCap, numOfCapitalShips, capacity, numInfNFightersNMechs);
        if (capacityViolated || fleetSupplyViolated) {
            Button remove = Button.danger("getDamageButtons_" + tile.getPosition() + "_remove",
                "Remove units in " + tile.getRepresentationForButtons(game, player));
            MessageHelper.sendMessageToChannelWithButton(getCorrectChannel(player, game), message, remove);
        }
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
                if (mod.getLegendaryAbilityName() != null && !"".equals(mod.getLegendaryAbilityName())
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
                        buttons.add(Button.success("specialRex_" + planet + "_" + p2.getFaction() + "_infantry",
                            "Remove 1 infantry from " + planetRepresentation2));
                    }
                    if (numMechs > 0) {
                        buttons.add(Button.primary("specialRex_" + planet + "_" + p2.getFaction() + "_mech",
                            "Remove 1 mech from " + planetRepresentation2));
                    }
                }
            }
        }
        return buttons;
    }

    public static void resolveSpecialRex(Player player, Game game, String buttonID, String ident,
        ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null)
            return;

        String mechOrInf = buttonID.split("_")[3];
        String msg = ident + " used the special Mecatol Rex power to remove 1 " + mechOrInf + " on "
            + Helper.getPlanetRepresentation(planet, game);
        new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(planet),
            "1 " + mechOrInf + " " + planet, game);
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), msg);
        deleteMessage(event);
    }

    public static List<Button> getEchoAvailableSystems(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPlanetUnitHolders().size() == 0) {
                buttons.add(Button.success("echoPlaceFrontier_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    public static void resolveEchoPlaceFrontier(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        AddToken.addToken(event, tile, Constants.FRONTIER, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), getIdent(player) + " placed a frontier token in "
            + tile.getRepresentationForButtons(game, player));
        deleteMessage(event);
    }

    public static List<Button> getEndOfTurnAbilities(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> endButtons = new ArrayList<>();

        List<String> implementedLegendaryPlanets = List.of(
            "mallice", "mirage", "hopesend", "primor", //PoK
            "silence", "tarrock", "prism", "echo", "domna"); //DS

        for (String planet : implementedLegendaryPlanets) {
            String prettyPlanet = Mapper.getPlanet(planet).getName();
            if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
                endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use " + prettyPlanet + " Ability"));
            }
        }

        if (player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")) {
            endButtons.add(Button.danger(finChecker + "exhaustTech_pi", "Exhaust Predictive Intelligence"));
        }
        if (!player.hasAbility("arms_dealers")) {
            for (String shipOrder : getPlayersShipOrders(player)) {
                if (Helper.getTileWithShipsNTokenPlaceUnitButtons(player, game, "dreadnought",
                    "placeOneNDone_skipbuild", null).size() > 0) {
                    endButtons.add(Button.success(finChecker + "resolveShipOrder_" + shipOrder,
                        "Use " + Mapper.getRelic(shipOrder).getName()));
                }
            }
        }
        if (player.getTechs().contains("bs") && !player.getExhaustedTechs().contains("bs")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_bs", "Exhaust Bio-Stims"));
        }
        //dsceldr
        if (player.getTechs().contains("dsceldr") && !player.getExhaustedTechs().contains("dsceldr") && player.getStrategicCC() > 0) {
            endButtons.add(Button.success(finChecker + "exhaustTech_dsceldr", "Exhaust Emergency Mobilization Protocols"));
        }
        if (player.getTechs().contains("absol_bs") && !player.getExhaustedTechs().contains("absol_bs")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_absol_bs", "Exhaust Absol Bio-Stims"));
        }
        if (player.getTechs().contains("miltymod_hm") && !player.getExhaustedTechs().contains("miltymod_hm")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_miltymod_hm", "Exhaust Hyper Metabolism"));
        }
        if (player.getTechs().contains("absol_hm") && !player.getExhaustedTechs().contains("absol_hm")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_absol_hm", "Exhaust Hyper Metabolism"));
        }
        if (player.getTechs().contains("absol_pa") && !player.getReadiedPlanets().isEmpty()
            && !player.getActionCards().isEmpty()) {
            endButtons.add(Button.success(finChecker + "useTech_absol_pa", "Use Psychoarchaeology"));
        }
        if (player.hasUnexhaustedLeader("naazagent")) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_naazagent",
                "Use Naaz-Rokha Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Naaz)));
        }
        if (player.hasUnexhaustedLeader("cheiranagent")
            && ButtonHelperAgents.getCheiranAgentTiles(player, game).size() > 0) {
            endButtons.add(
                Button.success(finChecker + "exhaustAgent_cheiranagent_" + player.getFaction(),
                    "Use Cheiran Agent")
                    .withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
        }

        if (player.hasUnexhaustedLeader("freesystemsagent") && player.getReadiedPlanets().size() > 0
            && ButtonHelperAgents.getAvailableLegendaryAbilities(game).size() > 0) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_freesystemsagent_" + player.getFaction(),
                "Use Free Systems Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.freesystems)));
        }
        if (player.hasRelic("absol_tyrantslament") && !player.hasUnit("tyrantslament")) {
            endButtons.add(Button.success("deployTyrant", "Deploy The Tyrant's Lament")
                .withEmoji(Emoji.fromFormatted(Emojis.Absol)));
        }

        if (player.hasUnexhaustedLeader("lizhoagent")) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_lizhoagent",
                "Use Li-zho Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.lizho)));
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "ravencommander")) {
            endButtons.add(Button.success(finChecker + "ravenMigration", "Use Migration").withEmoji(Emoji.fromFormatted(Emojis.raven)));
        }

        endButtons.add(Button.danger("deleteButtons", "Delete these buttons"));
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
            TileModel tile = TileHelper.getTile(newTileID);
            buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "starChartsStep1_" + newTileID, tile.getName()));

        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " choose the tile you want to add to the board", buttons);
    }

    public static void detTileAdditionStep1(Game game, Player player, String newTileID) {
        List<Button> buttons = new ArrayList<>();
        TileModel tile = TileHelper.getTile(newTileID);
        buttons.add(Button.success("detTileAdditionStep2_" + newTileID, "Next to only 1 tile"));
        buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "starChartsStep1_" + newTileID, "Next to 2 tiles"));
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " you are placing " + tile.getName() + ". Will this tile be adjacent to 1 other tile or 2?", buttons);
    }

    public static void detTileAdditionStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        deleteMessage(event);
        String newTileID = buttonID.split("_")[1];
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isEdgeOfBoard(game) && tile.getPosition().length() > 2 && FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "detTileAdditionStep3_" + newTileID + "_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " choose an edge tile that the new tile will be adjacent too", buttons);
    }

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
                buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "detTileAdditionStep4_" + newTileID + "_" + pos3, pos3));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " select the tile position where the tile should go", buttons);
    }

    public static void detTileAdditionStep4(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        deleteMessage(event);
        String newTileID = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Tile tile = new Tile(newTileID, pos);
        game.setTile(tile);
        if (tile.getPlanetUnitHolders().isEmpty()) {
            AddToken.addToken(event, tile, Constants.FRONTIER, game);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " added the tile " + tile.getRepresentationForButtons(game, player));
    }

    public static void starChartStep1(Game game, Player player, String newTileID) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isEdgeOfBoard(game) && tile.getPosition().length() > 2 && (game.isDiscordantStarsMode() || FoWHelper.playerHasShipsInSystem(player, tile))) {
                buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "starChartsStep2_" + newTileID + "_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " choose an edge tile that the new tile will be adjacent too", buttons);
    }

    public static void starChartStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        deleteMessage(event);
        String newTileID = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(pos);
        for (String pos2 : directlyAdjacentTiles) {
            Tile tile = game.getTileByPosition(pos2);
            if (tile != null && tile.isEdgeOfBoard(game)) {
                buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "starChartsStep3_" + newTileID + "_" + tile.getPosition() + "_" + pos, tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " choose another tile that the new tile will be adjacent too", buttons);
    }

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
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " could not find the correct location, sorry.");
        } else {
            Tile tile = new Tile(newTileID, inBoth);
            game.setTile(tile);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " added the tile " + tile.getRepresentationForButtons(game, player));
            if (tile.getPlanetUnitHolders().isEmpty()) {
                AddToken.addToken(event, tile, Constants.FRONTIER, game);
            }
        }
    }

    public static void addTokenToTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        //addtoken_(tokenname)_(pos)_(planet?)
        String regex = "addToken_" + RegexHelper.tokenRegex() + "_" + RegexHelper.posRegex(game) + RegexHelper.optional("_" + RegexHelper.unitHolderRegex(game, "planet"));
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
            scButtons.add(Button.success("resolvePreassignment_Pre Pass " + player.getFaction(), "Pass on Next Turn"));
            scButtons.add(Button.danger("deleteButtons", "Decline"));
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
            Button rex = Button.success(finChecker + "ringTile_000",
                centerTile.getRepresentationForButtons(game, player));
            ringButtons.add(rex);
        }
        int rings = game.getRingCount();
        for (int x = 1; x < rings + 1; x++) {
            Button ringX = Button.success(finChecker + "ring_" + x, "Ring #" + x);
            ringButtons.add(ringX);
        }
        Button corners = Button.success(finChecker + "ring_corners", "Corners");
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
                if (t != null && !AddCC.hasCC(event, player.getColor(), t) && (!game.getNaaluAgent() || !t.isHomeSystem())) {
                    Button corners = Button.success(finChecker + "ringTile_" + pos, t.getRepresentationForButtons(game, player));
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
                            && (!game.getNaaluAgent() || !tile.isHomeSystem()
                                || "17".equalsIgnoreCase(tile.getTileID()))) {
                            Button corners = Button.success(finChecker + "ringTile_" + pos,
                                tile.getRepresentationForButtons(game, player));
                            ringButtons.add(corners);
                        }
                    }
                    String pos = ringN + "01";
                    Tile tile = game.getTileByPosition(pos);
                    if (tile != null && !tile.getRepresentationForButtons(game, player).contains("Hyperlane")
                        && !AddCC.hasCC(event, player.getColor(), tile)
                        && (!game.getNaaluAgent() || !tile.isHomeSystem()
                            || "17".equalsIgnoreCase(tile.getTileID()))) {
                        Button corners = Button.success(finChecker + "ringTile_" + pos,
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
                            && (!game.getNaaluAgent() || !tile.isHomeSystem())) {
                            Button corners = Button.success(finChecker + "ringTile_" + pos,
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
                            && (!game.getNaaluAgent() || !tile.isHomeSystem())) {
                            Button corners = Button.success(finChecker + "ringTile_" + pos,
                                tile.getRepresentationForButtons(game, player));
                            ringButtons.add(corners);
                        }
                    }
                } else {
                    Button ringLeft = Button.success(finChecker + "ring_" + ringN + "_left", "Left Half");
                    ringButtons.add(ringLeft);
                    Button ringRight = Button.success(finChecker + "ring_" + ringN + "_right", "Right Half");
                    ringButtons.add(ringRight);
                }
            }
        }
        ringButtons.add(Button.danger("ChooseDifferentDestination", "Get a different ring"));

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
                    MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
                        player.getRepresentation(true, true)
                            + " Due to the reclamation ability, A pds and SD have been added to Mecatol Rex. This is optional though.");
                }
            }
        }
        if (doesPlayerHaveFSHere("lanefir_flagship", player, tile)) {
            List<Button> button2 = scanlinkResolution(player, game, event);
            if (!button2.isEmpty()) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), player.getRepresentation() + "Due to lanefire FS, you can explore a planet you control in the system");
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), "Explore a planet", button2);
            }
        }
        if (player.hasAbility("secret_maps")) {
            String msg = player.getRepresentation()
                + " you can use your secret maps ability to explore a planet with production that you did not explore this turn";
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
            if (buttons.size() > 0) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), msg, buttons);
            }
        }
        if (player.hasUnit("winnu_mech")) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0
                    && game.getStoredValue("planetsTakenThisRound").contains(uH.getName())) {
                    String planet = uH.getName();
                    Button sdButton = Button.success("winnuStructure_sd_" + planet,
                        "Place A SD on " + Helper.getPlanetRepresentation(planet, game));
                    sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                    Button pdsButton = Button.success("winnuStructure_pds_" + planet,
                        "Place a PDS on " + Helper.getPlanetRepresentation(planet, game));
                    pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
                    Button tgButton = Button.danger("deleteButtons", "Delete Buttons");
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(sdButton);
                    buttons.add(pdsButton);
                    buttons.add(tgButton);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game),
                        player.getRepresentation(true, true)
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
                    Button sdButton = Button.success("kolleccMechCapture_" + planet + "_mech",
                        "Capture a mech on " + Helper.getPlanetRepresentation(planet, game));
                    sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.mech));
                    buttons.add(sdButton);
                    if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                        Button pdsButton = Button.success("kolleccMechCapture_" + planet + "_infantry",
                            "Capture an infantry on " + Helper.getPlanetRepresentation(planet, game));
                        pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                        buttons.add(pdsButton);
                    }
                    Button tgButton = Button.danger("deleteButtons", "Delete Buttons");
                    buttons.add(tgButton);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game),
                        player.getRepresentation(true, true)
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
                Button resolveExplore1 = Button.success(
                    "resFrontier_" + cardID1 + "_" + tile.getPosition() + "_" + cardID2, "Choose " + name1);
                Button resolveExplore2 = Button.success(
                    "resFrontier_" + cardID2 + "_" + tile.getPosition() + "_" + cardID1, "Choose " + name2);
                List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                // code to draw 2 explores and get their names
                // Send Buttons to decide which one to explore
                String message = player.getRepresentation(true, true) + " Please decide which card to resolve.";

                if (!game.isFoWMode() && event.getChannel() != game.getActionsChannel()) {

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
                Button resolveExplore1 = Button.success(
                    "lanefirAgentRes_Decline_frontier_" + cardID + "_" + tile.getPosition(), "Choose " + name1);
                Button resolveExplore2 = Button.success("lanefirAgentRes_Accept_frontier_" + tile.getPosition(),
                    "Use Lanefir Agent");
                List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                String message = player.getRepresentation(true, true)
                    + " You have " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Vassa Hagi (Lanefir Agent), and thus can decline this explore to draw another one instead.";
                if (!game.isFoWMode() && event.getChannel() != game.getActionsChannel()) {
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
                new ExpFrontier().expFront(event, tile, game, player);
            }

            if (player.hasAbility("migrant_fleet")) {
                String msg3 = player.getRepresentation()
                    + " after you resolve the frontier explore, you can use your migrant explorers ability to explore a planet you control in an adjacent system";
                List<Button> buttons = new ArrayList<>();
                for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
                    Tile tile2 = game.getTileByPosition(pos);
                    for (UnitHolder uH : tile2.getPlanetUnitHolders()) {
                        Planet planetReal = (Planet) uH;
                        String planet = planetReal.getName();
                        if (planetReal.getOriginalPlanetType() != null
                            && player.getPlanetsAllianceMode().contains(planet)) {
                            List<Button> planetButtons = getPlanetExplorationButtons(game, planetReal, player);
                            buttons.addAll(planetButtons);
                        }

                    }
                }
                if (buttons.size() > 0) {
                    MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), msg3, buttons);
                }
            }

        }
    }

    public static void sendTradeHolderSomething(Player player, Game game, String buttonID,
        ButtonInteractionEvent event) {
        String tgOrDebt = buttonID.split("_")[1];
        Player tradeHolder = null;
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(5)) {
                tradeHolder = p2;
                break;
            }
        }
        if (tradeHolder == null) {
            BotLogger.log(event, "`ButtonHelper.sendTradeHolderSomething` tradeHolder was **null**");
            return;
        }
        String msg = player.getRepresentation() + " sent 1 " + tgOrDebt + " to " + tradeHolder.getRepresentation();
        if ("tg".equalsIgnoreCase(tgOrDebt)) {
            checkTransactionLegality(game, player, tradeHolder);
            if (player.getTg() > 0) {
                tradeHolder.setTg(tradeHolder.getTg() + 1);
                player.setTg(player.getTg() - 1);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getRepresentation(true, true) + " you had no tg to send, no tg sent.");
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
            unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.PROPULSION));
    }

    public static void resolveAbsolScanlink(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        if (buttonID.contains("Decline")) {
            String drawColor = buttonID.split("_")[2];
            String cardID = buttonID.split("_")[3];
            String planetName = buttonID.split("_")[4];
            Tile tile = game.getTileFromPlanet(planetName);
            String messageText = player.getRepresentation() + " explored " +
                Emojis.getEmojiFromDiscord(drawColor) +
                "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tile.getPosition()
                + ")*:";
            ExploreSubcommandData.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
            if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && game.getCurrentPhase().contains("agenda")) {
                PlanetRefresh.doAction(player, planetName, game);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Planet has been refreshed because of Florzen Commander");
                ListVoteCount.turnOrder(event, game, game.getMainGameChannel());
            }
            if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
                UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
                game.getTileFromPlanet(planetName).getUnitHolders().get(planetName).addUnit(infKey, 1);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Added inf to planet because of Lanefir Commander");
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
                player.getRepresentation() + " used Absol Scanlink to decline explore and gained 1tg (tg went from "
                    + oldTg + "->" + player.getTg() + ")");
            String planetID = buttonID.split("_")[2];
            if (player.hasAbility("awaken") && !game.getAllPlanetsWithSleeperTokens().contains(planetID)
                && player.getPlanets().contains(planetID)) {
                Button placeSleeper = Button.success("putSleeperOnPlanet_" + planetID, "Put Sleeper on " + planetID)
                    .withEmoji(Emoji.fromFormatted(Emojis.Sleeper));
                Button decline = Button.danger("deleteButtons", "Decline To Put a Sleeper Down");
                List<Button> buttons = List.of(placeSleeper, decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Can Do Sleeper Things", buttons);
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
                buttons.add(Button
                    .success("addAbsolOrbital_" + game.getActiveSystem() + "_" + planetId,
                        planetRepresentation)
                    .withEmoji(Emoji.fromFormatted(Emojis.Absol)));
            }
        }
        return buttons;
    }

    public static void addAbsolOrbital(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder uH = tile.getUnitHolders().get(buttonID.split("_")[2]);
        new AddUnits().unitParsing(event, player.getColor(), tile, "plenaryorbital " + uH.getName(), game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji()
            + " added an absol plenary orbital to " + Mapper.getPlanet(uH.getName()).getName());
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

        if (player.hasAbility("black_markets") && (explorationTraits.contains("cultural") || explorationTraits.contains("industrial") || explorationTraits.contains("hazardous"))) {
            Set<String> traits = getTypesOfPlanetPlayerHas(game, player);
            explorationTraits.addAll(traits);
        }

        for (String trait : explorationTraits) {
            if (List.of("cultural", "industrial", "hazardous").contains(trait)) {
                String buttonId = "movedNExplored_filler_" + planetId + "_" + trait;
                String buttonMessage = "Explore " + planetRepresentation
                    + (explorationTraits.size() > 1 ? " as " + trait : "");
                Emoji emoji = Emoji.fromFormatted(Emojis.getEmojiFromDiscord(trait));
                Button button = Button.secondary(buttonId, buttonMessage).withEmoji(emoji);
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
                if (game.isFoWMode()) {
                    channel = p2.getPrivateChannel();
                }
                RemoveCC.removeCC(event, p2.getColor(), tile, game);
                String message = p2.getRepresentation(true, true)
                    + " due to having the empyrean commander, the CC you had in the active system has been removed. Reminder that this is optional but was done automatically";
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
        return getTilesWithUnitsForAction(player, game, "genericModify", true);
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

    public static List<Button> getTilesWithPredicateForAction(Player player, Game game, String action, Predicate<Tile> predicate, boolean includeDelete) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            Tile tile = tileEntry.getValue();
            if (predicate.negate().test(tile)) continue;

            String buttonID = finChecker + action + "_" + tileEntry.getKey();
            Button validTile = Button.success(buttonID, tile.getRepresentationForButtons(game, player));
            buttons.add(validTile);
        }
        if (includeDelete) {
            Button deleteButtons = Button.danger(finChecker + "deleteButtons", "Delete these buttons");
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
        Button buildButton = Button.success(finChecker + "genericBuild_" + tile.getPosition(),
            "Build in " + tile.getRepresentationForButtons(game, player));
        buttons.add(buildButton);
        Button remove = Button.danger(finChecker + "getDamageButtons_" + tile.getPosition(),
            "Remove or damage units in " + tile.getRepresentationForButtons(game, player));
        buttons.add(remove);
        Button validTile2 = Button.secondary(finChecker + "deleteButtons", "Delete these buttons");
        buttons.add(validTile2);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Choose to either add units (build) or remove them", buttons);
    }

    public static void resolveCombatRoll(Player player, Game game, GenericInteractionCreateEvent event,
        String buttonID) {
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
        new CombatRoll().secondHalfOfCombatRoll(player, game, event, game.getTileByPosition(pos),
            unitHolderName, rollType);
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
                    "This is a reminder that conventions of war is in play, so bombardment of cultural planets is illegal.");
            }
        }
    }

    /**
     * @deprecated use {@link Player#getCorrectChannel()} instead
     */
    @Deprecated
    public static MessageChannel getCorrectChannel(Player player, Game game) {
        return player.getCorrectChannel();
    }

    public static List<Button> getTilesToMoveFrom(Player player, Game game, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tileEntry.getValue())
                && (!AddCC.hasCC(event, player.getColor(), tileEntry.getValue())
                    || nomadHeroAndDomOrbCheck(player, game, tileEntry.getValue()))) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker + "tacticalMoveFrom_" + tileEntry.getKey(),
                    tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }

        if (player.hasUnexhaustedLeader("saaragent")) {
            Button saarButton = Button.secondary("exhaustAgent_saaragent",
                "Use Saar Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Saar));
            buttons.add(saarButton);
        }

        if (player.hasRelic("dominusorb")) {
            Button domButton = Button.secondary("dominusOrb", "Purge Dominus Orb");
            buttons.add(domButton);
        }

        if (player.hasRelicReady("absol_luxarchtreatise")) {
            Button domButton = Button.secondary("exhaustRelic_absol_luxarchtreatise", "Exhaust Luxarch Treatise");
            buttons.add(domButton);
        }

        if (player.hasUnexhaustedLeader("ghostagent")
            && FoWHelper.doesTileHaveWHs(game, game.getActiveSystem())) {
            Button ghostButton = Button.secondary("exhaustAgent_ghostagent",
                "Use Creuss Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostButton);
        }
        if (player.hasTech("as")
            && FoWHelper.isTileAdjacentToAnAnomaly(game, game.getActiveSystem(), player)) {
            Button ghostButton = Button.secondary("declareUse_Aetherstream", "Declare Aetherstream")
                .withEmoji(Emoji.fromFormatted(Emojis.Empyrean));
            buttons.add(ghostButton);
        }
        if (player.getTechs().contains("dsgledb")) {
            buttons.add(Button.success(finChecker + "declareUse_Lightning", "Declare Lightning Drives").withEmoji(Emoji.fromFormatted(Emojis.gledge)));
        }
        if (player.getTechs().contains("dsvadeb") && !player.getExhaustedTechs().contains("dsvadeb")) {
            buttons.add(Button.success(finChecker + "exhaustTech_dsvadeb", "Exhaust Midas Turbine").withEmoji(Emoji.fromFormatted(Emojis.vaden)));
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "vayleriancommander")) {
            Button ghostButton = Button.secondary("declareUse_Vaylerian Commander", "Use Vaylerian Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.vaylerian));
            buttons.add(ghostButton);
        }
        if (player.hasLeaderUnlocked("vaylerianhero")) {
            Button sardakkH = Button.primary(finChecker + "purgeVaylerianHero", "Use Vaylerian Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.vaylerian));
            buttons.add(sardakkH);
        }
        if (player.ownsUnit("ghost_mech") && getNumberOfUnitsOnTheBoard(game, player, "mech") > 0) {
            Button ghostButton = Button.secondary("creussMechStep1_", "Use Ghost Mech")
                .withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostButton);
        }
        if ((player.ownsUnit("nivyn_mech") && getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)
            .contains(game.getTileByPosition(game.getActiveSystem()))) || player.ownsUnit("nivyn_mech2")) {
            Button ghostButton = Button.secondary("nivynMechStep1_", "Use Nivyn Mech")
                .withEmoji(Emoji.fromFormatted(Emojis.nivyn));
            buttons.add(ghostButton);
        }
        if (player.hasTech("dslihb") && !game.getTileByPosition(game.getActiveSystem()).isHomeSystem()) {
            Button ghostButton = Button.secondary("exhaustTech_dslihb", "Exhaust Wraith Engine")
                .withEmoji(Emoji.fromFormatted(Emojis.lizho));
            buttons.add(ghostButton);
        }
        String planet = "eko";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            buttons.add(Button.secondary(finChecker + "planetAbilityExhaust_" + planet,
                "Use Eko's Ability To Ignore Anomalies"));
        }

        Button validTile = Button.danger(finChecker + "concludeMove_" + game.getActiveSystem(), "Done moving");
        buttons.add(validTile);
        Button validTile2 = Button.primary(finChecker + "ChooseDifferentDestination", "Activate a different system");
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

        if (!game.getNaaluAgent() && !game.getL1Hero() && !AddCC.hasCC(event, player.getColor(), tile)
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

                if (tile.getUnitHolders().get("space").getUnits() != null
                    && tile.getUnitHolders().get("space").getUnitCount(inf, colorID) > 0) {
                    limit = tile.getUnitHolders().get("space").getUnitCount(inf, colorID);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        Button validTile2 = Button
                            .danger(finChecker + "landUnits_" + tile.getPosition() + "_" + x + "infantry_"
                                + representation,
                                "Land " + x + " Infantry on "
                                    + Helper.getPlanetRepresentation(representation.toLowerCase(), game))
                            .withEmoji(Emoji.fromFormatted(Emojis.infantry));
                        buttons.add(validTile2);
                    }
                }
                if (planet.getUnitCount(inf, player) > 0 || planet.getUnitCount(mech, player) > 0) {
                    if (player.hasUnexhaustedLeader("dihmohnagent")) {
                        Button dihmohn = Button
                            .success("exhaustAgent_dihmohnagent_" + unitHolder.getName(),
                                "Use Dihmohn Agent on "
                                    + Helper.getPlanetRepresentation(unitHolder.getName(), game))
                            .withEmoji(Emoji.fromFormatted(Emojis.dihmohn));
                        buttons.add(dihmohn);
                    }
                }
                if (player.hasUnit("tnelis_mech") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4 && tile.getUnitHolders().get("space").getUnitCount(UnitType.Destroyer, player) > 0) {
                    Button tnelis = Button
                        .success("tnelisDeploy_" + unitHolder.getName(),
                            "Deploy Mech On "
                                + Helper.getPlanetRepresentation(unitHolder.getName(), game))
                        .withEmoji(Emoji.fromFormatted(Emojis.tnelis));
                    buttons.add(tnelis);
                }
                if (planet.getUnitCount(inf, colorID) > 0) {
                    limit = planet.getUnitCount(inf, colorID);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonId = finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "infantry_"
                            + representation;
                        String buttonText = "Undo Landing of " + x + " Infantry on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Button.secondary(buttonId, buttonText)
                            .withEmoji(Emoji.fromFormatted(Emojis.infantry));
                        buttons.add(validTile2);
                    }
                }
                UnitHolder spaceUH = tile.getUnitHolders().get("space");
                if (tile.getUnitHolders().get("space").getUnits() != null
                    && tile.getUnitHolders().get("space").getUnitCount(mech, colorID) > 0) {
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
                        String buttonText = "Land " + x + " Damaged Mech(s) on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Button.primary(buttonID, buttonText)
                            .withEmoji(Emoji.fromFormatted(Emojis.mech));
                        buttons.add(validTile2);
                    }
                    limit = totalUnits - damagedUnits;
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "landUnits_" + tile.getPosition() + "_" + x + "mech_"
                            + representation;
                        String buttonText = "Land " + x + " Mech(s) on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Button.primary(buttonID, buttonText)
                            .withEmoji(Emoji.fromFormatted(Emojis.mech));
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
                        String buttonText = "Land " + x + " Fighter(s) on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Button.primary(buttonID, buttonText)
                            .withEmoji(Emoji.fromFormatted(Emojis.fighter));
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
                        String buttonText = "Undo Landing of " + x + " Damaged Mech(s) on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Button.secondary(buttonID, buttonText)
                            .withEmoji(Emoji.fromFormatted(Emojis.mech));
                        buttons.add(validTile2);
                    }
                    limit = totalUnits - damagedUnits;
                    for (int x = 1; x <= limit; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "mech_"
                            + representation;
                        String buttonText = "Undo Landing of " + x + " Mech(s) on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Button.secondary(buttonID, buttonText)
                            .withEmoji(Emoji.fromFormatted(Emojis.mech));
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
        Button rift = Button.success(finChecker + "getRiftButtons_" + tile.getPosition(), "Units traveled through rift")
            .withEmoji(Emoji.fromFormatted(Emojis.GravityRift));
        buttons.add(rift);
        if (player.hasAbility("combat_drones") && FoWHelper.playerHasFightersInSystem(player, tile)) {
            Button combatDrones = Button.primary(finChecker + "combatDrones", "Use Combat Drones Ability")
                .withEmoji(Emoji.fromFormatted(Emojis.mirveda));
            buttons.add(combatDrones);
        }
        if (player.hasAbility("shroud_of_lith")
            && ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game).size() > 1) {
            buttons.add(Button.primary("shroudOfLithStart", "Use Shroud of Lith")
                .withEmoji(Emoji.fromFormatted(Emojis.kollecc)));
            buttons.add(Button.secondary("refreshLandingButtons", "Refresh Landing Buttons")
                .withEmoji(Emoji.fromFormatted(Emojis.kollecc)));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")) {
            Button combatDrones = Button.primary(finChecker + "offerMirvedaCommander", "Use Mirveda Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.mirveda));
            buttons.add(combatDrones);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "ghostcommander")) {
            Button ghostC = Button
                .primary(finChecker + "placeGhostCommanderFF_" + tile.getPosition(),
                    "Place fighter with Ghost Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostC);
        }
        if (tile.getPlanetUnitHolders().size() > 0
            && game.playerHasLeaderUnlockedOrAlliance(player, "khraskcommander")) {
            Button ghostC = Button
                .primary(finChecker + "placeKhraskCommanderInf_" + tile.getPosition(),
                    "Place infantry with Khrask Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.khrask));
            buttons.add(ghostC);
        }
        if (player.hasUnexhaustedLeader("nokaragent") && FoWHelper.playerHasShipsInSystem(player, tile)) {
            Button chaos = Button
                .secondary("exhaustAgent_nokaragent_" + player.getFaction(),
                    "Use Nokar Agent To Place A Destroyer")
                .withEmoji(Emoji.fromFormatted(Emojis.nokar));
            buttons.add(chaos);
        }
        if (player.hasUnexhaustedLeader("tnelisagent") && FoWHelper.playerHasShipsInSystem(player, tile)
            && FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
            Button chaos = Button.secondary("exhaustAgent_tnelisagent_" + player.getFaction(),
                "Use Tnelis Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.tnelis));
            buttons.add(chaos);
        }
        if (player.hasUnexhaustedLeader("zelianagent")
            && tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
            Button chaos = Button
                .secondary("exhaustAgent_zelianagent_" + player.getFaction(),
                    "Use Zelian Agent Yourself")
                .withEmoji(Emoji.fromFormatted(Emojis.zelian));
            buttons.add(chaos);
        }
        if (player.hasLeaderUnlocked("muaathero") && !tile.isMecatol() && !tile.isHomeSystem()
            && getTilesOfPlayersSpecificUnits(game, player, UnitType.Warsun).contains(tile)) {
            Button muaatH = Button.primary(finChecker + "novaSeed_" + tile.getPosition(), "Nova Seed This Tile")
                .withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            buttons.add(muaatH);
        }
        if (player.hasLeaderUnlocked("zelianhero") && !tile.isMecatol()
            && getTilesOfUnitsWithBombard(player, game).contains(tile)) {
            Button zelianH = Button
                .primary(finChecker + "celestialImpact_" + tile.getPosition(), "Celestial Impact This Tile")
                .withEmoji(Emoji.fromFormatted(Emojis.zelian));
            buttons.add(zelianH);
        }
        if (player.hasLeaderUnlocked("sardakkhero") && tile.getPlanetUnitHolders().size() > 0) {
            Button sardakkH = Button.primary(finChecker + "purgeSardakkHero", "Use Sardakk Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Sardakk));
            buttons.add(sardakkH);
        }
        if (player.hasLeaderUnlocked("rohdhnahero")) {
            Button sardakkH = Button.primary(finChecker + "purgeRohdhnaHero", "Use Rohdhna Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.rohdhna));
            buttons.add(sardakkH);
        }
        if (tile.getUnitHolders().size() > 1 && getTilesOfUnitsWithBombard(player, game).contains(tile)) {
            if (tile.getUnitHolders().size() > 2) {
                buttons.add(Button.secondary(
                    "bombardConfirm_combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                    "Roll Bombardment"));
            } else {
                buttons.add(
                    Button.secondary("combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                        "Roll Bombardment"));
            }

        }
        Button concludeMove = Button.secondary(finChecker + "doneLanding_" + tile.getPosition(), "Done landing troops");
        buttons.add(concludeMove);
        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            commanderUnlockCheck(player, game, "naaz", event);
        }
        if (player.getLeaderIDs().contains("empyreancommander") && !player.hasLeaderUnlocked("empyreancommander")) {
            commanderUnlockCheck(player, game, "empyrean", event);
        }
        if (player.getLeaderIDs().contains("ghostcommander") && !player.hasLeaderUnlocked("ghostcommander")) {
            commanderUnlockCheck(player, game, "ghost", event);
        }

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

                        successMessageBuilder.append("\n" + player.getFactionEmoji() + " placed ").append(numMechs).append(" ").append(Emojis.infantry)
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
        Button buildButton = Button.success(finChecker + "tacticalActionBuild_" + game.getActiveSystem(),
            "Build in this system (" + Helper.getProductionValue(player, game, tile, false) + " PRODUCTION Value)");
        buttons.add(buildButton);
        Button rift = Button.success(finChecker + "getRiftButtons_" + tile.getPosition(), "Units traveled through rift")
            .withEmoji(Emoji.fromFormatted(Emojis.GravityRift));
        buttons.add(rift);
        if (player.hasUnexhaustedLeader("sardakkagent")) {
            buttons.addAll(ButtonHelperAgents.getSardakkAgentButtons(game, player));
        }
        if (player.hasUnexhaustedLeader("nomadagentmercer")) {
            buttons.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(game, player));
        }
        if (player.hasAbility("shroud_of_lith")
            && ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game).size() > 1) {
            buttons.add(Button.primary("shroudOfLithStart", "Use Shroud of Lith")
                .withEmoji(Emoji.fromFormatted(Emojis.kollecc)));
        }
        Button concludeMove = Button.danger(finChecker + "doneWithTacticalAction",
            "Conclude tactical action");
        buttons.add(concludeMove);
        return buttons;
    }

    public static void resolveTransitDiodesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            buttons.add(Button.success("transitDiodes_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Button.danger("deleteButtons", "Done resolving transit diodes"));
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game),
            player.getRepresentation() + " use buttons to choose the planet you want to move troops to", buttons);
    }

    public static void resolveTransitDiodesStep2(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = getButtonsForMovingGroundForcesToAPlanet(game, buttonID.split("_")[1], player);
        deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game),
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
                        buttons.add(Button.success(
                            "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName()
                                + "_infantry",
                            "Move Infantry from " + Helper.getPlanetRepresentation(uH.getName(), game) + " to "
                                + Helper.getPlanetRepresentation(planetName, game)));
                    } else {
                        buttons.add(Button.success(
                            "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName()
                                + "_infantry",
                            "Move Infantry from space of " + tile.getRepresentation() + " to "
                                + Helper.getPlanetRepresentation(planetName, game)));
                    }
                }
                if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                    if (uH instanceof Planet) {
                        buttons.add(Button.success(
                            "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_mech",
                            "Move Mech from " + Helper.getPlanetRepresentation(uH.getName(), game) + " to "
                                + Helper.getPlanetRepresentation(planetName, game)));
                    } else {
                        buttons.add(Button.success(
                            "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_mech",
                            "Move Mech from space of " + tile.getRepresentation() + " to "
                                + Helper.getPlanetRepresentation(planetName, game)));
                    }
                }
                if (player.hasUnit("titans_pds") || player.hasTech("ht2")) {
                    if (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0) {
                        if (uH instanceof Planet) {
                            buttons.add(Button.success(
                                "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_pds",
                                "Move PDS from " + Helper.getPlanetRepresentation(uH.getName(), game) + " to "
                                    + Helper.getPlanetRepresentation(planetName, game)));
                        } else {
                            buttons.add(Button.success(
                                "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_pds",
                                "Move PDS from space of " + tile.getRepresentation() + " to "
                                    + Helper.getPlanetRepresentation(planetName, game)));
                        }
                    }
                }
            }
        }
        buttons.add(Button.danger("deleteButtons", "Done moving to this planet"));
        return buttons;
    }

    public static void offerSetAutoPassOnSaboButtons(Game game, Player player2) {
        List<Button> buttons = new ArrayList<>();
        int x = 1;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 2;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 4;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 6;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 8;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 16;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 24;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 36;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        buttons.add(Button.danger("deleteButtons", "Decline"));
        x = 0;
        buttons.add(Button.danger("setAutoPassMedian_" + x, "Turn off (if already on)"));
        if (player2 == null) {
            for (Player player : game.getRealPlayers()) {
                String message = player.getRepresentation(true, true)
                    + " you can choose to automatically pass on sabo's after a random amount of time if you don't have a sabo/instinct training/watcher mechs. How it works is you secretly set a median time (in hours) here, and then from now on when an AC is played, the bot will randomly react for you, 50% of the time being above that amount of time and 50% below. It's random so people cant derive much information from it. You are free to decline, noone will ever know either way, but if necessary you can change your time later with /player stats";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            }
        } else {
            Player player = player2;
            String message = player.getRepresentation(true, true)
                + " you can choose to automatically pass on sabo's after a random amount of time if you don't have a sabo/instinct training/watcher mechs. How it works is you secretly set a median time (in hours) here, and then from now on when an AC is played, the bot will randomly react for you, 50% of the time being above that amount of time and 50% below. It's random so people cant derive much information from it. You are free to decline, noone will ever know either way, but if necessary you can change your time later with /player stats";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        }
    }

    public static UnitHolder getUnitHolderFromPlanetName(String planetName, Game game) {
        Tile tile = game.getTileFromPlanet(AliasHandler.resolvePlanet(planetName.toLowerCase()));
        if (tile == null) {
            return null;
        }
        return tile.getUnitHolders().get(AliasHandler.resolvePlanet(planetName.toLowerCase()));
    }

    /**
     * @deprecated just use {@link Player#getFactionEmoji()} instead
     */
    @Deprecated
    public static String getIdent(Player player) {
        return player.getFactionEmoji();
    }

    public static String getIdentOrColor(Player player, Game game) {
        if (game.isFoWMode()) {
            return StringUtils.capitalize(player.getColor());
        }
        return player.getFactionEmoji();
    }

    public static String buildMessageFromDisplacedUnits(Game game, boolean landing, Player player,
        String moveOrRemove, Tile tile) {
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
                    if (activeSystem != null && uni != null && uni.getIsShip() && !uni.getBaseType().equalsIgnoreCase("fighter")) {
                        int distance = CheckDistance.getDistanceBetweenTwoTiles(game, player, tile.getPosition(), game.getActiveSystem());
                        int moveValue = uni.getMoveValue();
                        if (tile.isNebula() && !player.hasAbility("voidborn")) {
                            moveValue = 1;
                        }
                        if (player.hasTech("as")
                            && FoWHelper.isTileAdjacentToAnAnomaly(game, game.getActiveSystem(), player)) {
                            moveValue++;
                        }
                        if (player.hasAbility("slipstream") && (FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition()) || tile == player.getHomeSystemTile())) {
                            moveValue++;
                        }
                        if (tile.isGravityRift(game)) {
                            moveValue++;
                        }
                        if (!game.getStoredValue("crucibleBoost").isEmpty()) {
                            moveValue = moveValue + 2;
                        }
                        if (!game.getStoredValue("flankspeedBoost").isEmpty()) {
                            moveValue = moveValue + 1;
                        }

                        if (distance > moveValue && distance < 90) {
                            if (player.hasTech("gd")) {
                                messageBuilder.append(" (Distance exceeds move value (" + distance + " > " + moveValue + "), probably used gravity drive)");
                            } else {
                                messageBuilder.append(" (Distance exceeds move value (" + distance + " > " + moveValue + "), did not have gravity drive)");
                            }
                            if (player.getTechs().contains("dsgledb")) {
                                messageBuilder.append("(did have lightning drives for +1 if not transporting)");
                            }
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
        if (buttonRow.size() > 0) {
            list.add(ActionRow.of(buttonRow));
        }

        return list;
    }

    public static String getUnitName(String id) {
        return switch (id) {
            case "fs" -> "flagship";
            case "tyrantslament" -> "tyrantslament";
            case "ws" -> "warsun";
            case "gf" -> "infantry";
            case "mf" -> "mech";
            case "sd" -> "spacedock";
            case "csd" -> "cabalspacedock";
            case "pd" -> "pds";
            case "ff" -> "fighter";
            case "ca", "cr" -> "cruiser";
            case "dd" -> "destroyer";
            case "cv" -> "carrier";
            case "dn" -> "dreadnought";
            case "lady" -> "lady";
            case "plenaryorbital" -> "plenaryorbital";
            case "cavalry" -> "cavalry";
            default -> "";
        };
    }

    public static List<Button> getButtonsForRiftingUnitsInSystem(Player player, Game game, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();

            if (unitHolder instanceof Planet) {
            } else {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    UnitKey key = unitEntry.getKey();
                    if (!player.unitBelongsToPlayer(key))
                        continue;

                    UnitModel unitModel = player.getUnitFromUnitKey(key);
                    if (unitModel == null)
                        continue;

                    UnitType unitType = key.getUnitType();
                    if ((!game.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")
                        && (unitType == UnitType.Infantry || unitType == UnitType.Mech))
                        || (!player.hasFF2Tech() && unitType == UnitType.Fighter)) {
                        continue;
                    }

                    String asyncID = key.asyncID();
                    asyncID = getUnitName(asyncID);

                    int totalUnits = unitEntry.getValue();

                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                    for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Button.danger(
                            finChecker + "riftUnit_" + tile.getPosition() + "_" + x + asyncID + "damaged",
                            "Rift " + x + " damaged " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Button.danger(
                            finChecker + "riftUnit_" + tile.getPosition() + "_" + x + asyncID,
                            "Rift " + x + " " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            }
        }
        Button concludeMove;
        Button doAll;
        Button concludeMove1;

        doAll = Button.secondary(finChecker + "riftAllUnits_" + tile.getPosition(), "Rift all units");
        concludeMove1 = Button.primary("getDamageButtons_" + tile.getPosition() + "_remove", "Remove excess inf/ff");
        concludeMove = Button.danger("deleteButtons", "Done rifting units and removing excess capacity");

        buttons.add(doAll);
        buttons.add(concludeMove1);
        buttons.add(concludeMove);

        return buttons;
    }

    public static List<Button> getButtonsForAllUnitsInSystem(Player player, Game game, Tile tile, String moveOrRemove) {
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

            if (unitHolder instanceof Planet planet) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                        continue;
                    UnitKey unitKey = unitEntry.getKey();
                    representation = representation.replace(" ", "").toLowerCase().replace("'", "").replace("-", "");
                    if ((unitKey.getUnitType() == UnitType.Infantry || unitKey.getUnitType() == UnitType.Mech)) {
                        String unitName = getUnitName(unitKey.asyncID());
                        int totalUnits = unitEntry.getValue();
                        int damagedUnits = 0;
                        EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                        if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                            damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                        }
                        for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                            String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition()
                                + "_" + x + unitName + "damaged_" + representation;
                            String buttonText = moveOrRemove + " " + x + " damaged " + unitKey.unitName() + " from "
                                + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                            Button validTile2 = Button.danger(buttonID, buttonText).withEmoji(emoji);
                            buttons.add(validTile2);
                        }
                        totalUnits = totalUnits - damagedUnits;
                        for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                            String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition()
                                + "_" + x + unitName + "_" + representation;
                            String buttonText = moveOrRemove + " " + x + " " + unitKey.unitName() + " from "
                                + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                            Button validTile2 = Button.danger(buttonID, buttonText).withEmoji(emoji);
                            buttons.add(validTile2);
                        }
                    }
                }
            } else {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!(player.unitBelongsToPlayer(unitEntry.getKey())))
                        continue;

                    UnitKey unitKey = unitEntry.getKey();
                    String unitName = getUnitName(unitKey.asyncID());
                    // System.out.println(unitKey.asyncID());
                    int totalUnits = unitEntry.getValue();
                    int damagedUnits = 0;

                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                    }
                    EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                    for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                        String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_"
                            + x + unitName + "damaged";
                        String buttonText = moveOrRemove + " " + x + " damaged " + unitKey.unitName();
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_"
                            + x + unitName;
                        String buttonText = moveOrRemove + " " + x + " " + unitKey.unitName();
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            }

        }
        Button concludeMove;
        Button doAll;
        Button doAllShips;
        if ("Remove".equalsIgnoreCase(moveOrRemove)) {
            doAllShips = Button.secondary(
                finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_removeAllShips",
                "Remove all Ships");
            buttons.add(doAllShips);
            doAll = Button.secondary(
                finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_removeAll",
                "Remove all units");
            concludeMove = Button.primary(finChecker + "doneRemoving", "Done removing units");
        } else {
            doAll = Button.secondary(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_moveAll",
                "Move all units");
            concludeMove = Button.primary(finChecker + "doneWithOneSystem_" + tile.getPosition(),
                "Done moving units from this system");
            if (game.playerHasLeaderUnlockedOrAlliance(player, "tneliscommander")
                && game.getStoredValue("tnelisCommanderTracker").isEmpty()) {
                buttons.add(Button.primary("declareUse_Tnelis Commander_" + tile.getPosition(), "Use Tnelis Commander")
                    .withEmoji(Emoji.fromFormatted(Emojis.tnelis)));
            }
        }
        buttons.add(doAll);
        buttons.add(concludeMove);
        Map<String, Integer> displacedUnits = game.getCurrentMovedUnitsFrom1System();
        for (String unit : displacedUnits.keySet()) {
            String unitkey;
            String planet = "";
            String origUnit = unit;
            String damagedMsg = "";
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
                damagedMsg = " damaged ";
            }
            if (unit.contains("_")) {
                unitkey = unit.split("_")[0];
                planet = unit.split("_")[1];
            } else {
                unitkey = unit;
            }
            for (int x = 1; x < displacedUnits.get(origUnit) + 1; x++) {
                if (x > 2) {
                    break;
                }
                String blabel = "Undo move of " + x + " " + damagedMsg + unitkey;
                if (!"".equalsIgnoreCase(planet)) {
                    blabel = blabel + " from " + Helper.getPlanetRepresentation(planet.toLowerCase(), game);
                }
                Button validTile2 = Button.success(
                    finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x
                        + unit.toLowerCase().replace(" ", "").replace("'", "") + damagedMsg.replace(" ", "")
                        + "_reverse",
                    blabel)
                    .withEmoji(Emoji
                        .fromFormatted(Emojis.getEmojiFromDiscord(unitkey.toLowerCase().replace(" ", ""))));
                buttons.add(validTile2);
            }
        }
        if (displacedUnits.keySet().size() > 0) {
            Button validTile2 = Button.success(
                finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_reverseAll", "Undo all");
            buttons.add(validTile2);
        }
        return buttons;
    }

    public static void addIonStorm(Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        Tile tile = game.getTileByPosition(pos);
        if (buttonID.contains("alpha")) {
            String tokenFilename = Mapper.getTokenID("ionalpha");
            tile.addToken(tokenFilename, Constants.SPACE);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Added ionstorm alpha to " + tile.getRepresentation());

        } else {
            String tokenFilename = Mapper.getTokenID("ionbeta");
            tile.addToken(tokenFilename, Constants.SPACE);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Added ionstorm beta to " + tile.getRepresentation());
        }
        deleteMessage(event);
    }

    public static void checkForIonStorm(Game game, Tile tile, Player player) {
        String tokenFilenameAlpha = Mapper.getTokenID("ionalpha");
        UnitHolder space = tile.getUnitHolders().get("space");
        String tokenFilename = Mapper.getTokenID("ionbeta");
        if (space.getTokenList().contains(tokenFilenameAlpha) || space.getTokenList().contains(tokenFilename)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("flipIonStorm_" + tile.getPosition(), "Flip Ion Storm"));
            buttons.add(Button.danger("deleteButtons", "Not Used"));
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), player.getRepresentation() + " if you used the ion storm please press button to flip it", buttons);
        }
    }

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
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        game.setStoredValue(player.getFaction() + "latestAssignHits", type);
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet) {
                if ((type.equalsIgnoreCase("spacecombat") || type.equalsIgnoreCase("assaultcannon")) && !ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, tile)) {
                    continue;
                }
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                        continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null)
                        continue;

                    UnitKey unitKey = unitEntry.getKey();
                    String unitName = getUnitName(unitKey.asyncID());

                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                    }
                    int totalUnits = unitEntry.getValue() - damagedUnits;

                    EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                    for (int x = 1; x < totalUnits + 1 && x < 3; x++) {
                        String buttonID = finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "_"
                            + representation;
                        String buttonText = "Remove " + x + " " + unitModel.getBaseType() + " from "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);

                        if (unitModel.getSustainDamage()) {
                            buttonID = finChecker + "assignDamage_" + tile.getPosition() + "_" + x + unitName + "_"
                                + representation;
                            buttonText = "Sustain " + x + " " + unitModel.getBaseType() + " from "
                                + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                            Button validTile3 = Button.secondary(buttonID, buttonText);
                            validTile2 = validTile2.withEmoji(emoji);
                            buttons.add(validTile3);
                        }
                    }
                    for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                        String buttonID = finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "_"
                            + representation + "damaged";
                        String buttonText = "Remove " + x + " damaged " + unitModel.getBaseType() + " from "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            } else {
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
                    String unitName = getUnitName(key.asyncID());
                    int totalUnits = unitEntry.getValue();
                    int damagedUnits = 0;

                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    totalUnits = totalUnits - damagedUnits;

                    EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                    for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                        Button validTile2 = Button.danger(
                            finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "damaged",
                            "Remove " + x + " damaged " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    for (int x = 1; x < totalUnits + 1 && x < 3; x++) {
                        Button validTile2 = Button.danger(
                            finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName,
                            "Remove " + x + " " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }

                    if ((("mech".equalsIgnoreCase(unitName) && !game.getLaws().containsKey("articles_war")
                        && player.getUnitsOwned().contains("nomad_mech"))
                        || "dreadnought".equalsIgnoreCase(unitName)
                        || (player != game.getActivePlayer() && !"fighter".equalsIgnoreCase(unitName)
                            && !"mech".equalsIgnoreCase(unitName) && !"infantry".equalsIgnoreCase(unitName)
                            && game.playerHasLeaderUnlockedOrAlliance(player, "mortheuscommander"))
                        || ("warsun".equalsIgnoreCase(unitName) && !ButtonHelper.isLawInPlay(game, "schematics"))
                        || "lady".equalsIgnoreCase(unitName) || "cavalry".equalsIgnoreCase(unitName)
                        || "flagship".equalsIgnoreCase(unitName)
                        || ("mech".equalsIgnoreCase(unitName)
                            && doesPlayerHaveFSHere("nekro_flagship", player, tile))
                        || ("cruiser".equalsIgnoreCase(unitName) && player.hasTech("se2"))
                        || ("mech".equalsIgnoreCase(unitName) && ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, tile))
                        || ("carrier".equalsIgnoreCase(unitName) && player.hasTech("ac2"))) && totalUnits > 0) {
                        Button validTile2 = Button
                            .secondary(finChecker + "assignDamage_" + tile.getPosition() + "_" + 1 + unitName,
                                "Sustain " + 1 + " " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            }
        }
        Button doAllShips;
        doAllShips = Button.secondary(finChecker + "assignHits_" + tile.getPosition() + "_AllShips",
            "Remove all Ships");
        buttons.add(doAllShips);
        Button doAll = Button.secondary(finChecker + "assignHits_" + tile.getPosition() + "_All", "Remove all units");
        Button concludeMove = Button.primary("deleteButtons", "Done removing/sustaining units");
        buttons.add(doAll);
        buttons.add(concludeMove);
        return buttons;
    }

    public static void resolveThalnosStart(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        game.resetThalnosUnits();
        String unitHolderName = buttonID.split("_")[2];
        game.setStoredValue("thalnosInitialHolder", unitHolderName);
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        buttons.addAll(
            getButtonsForRollingThalnos(player, game, tile, tile.getUnitHolders().get(unitHolderName)));
        if ("space".equalsIgnoreCase(unitHolderName)
            && doesPlayerHaveFSHere("nekro_flagship", player, tile)) {
            buttons = new ArrayList<>();
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                buttons.addAll(getButtonsForRollingThalnos(player, game, tile, uH));
            }
        }
        buttons.add(Button.primary("rollThalnos_" + tile.getPosition() + "_" + unitHolderName, "Roll Now"));
        buttons.add(Button.danger("deleteButtons", "Dont roll anything"));
        deleteTheOneButton(event);
        String message = player.getRepresentation()
            + " select the units for which you wish to reroll. Units that fail and did not have extra rolls will be automatically removed";
        MessageHelper.sendMessageToChannel(event.getChannel(), message, buttons);
    }

    public static void resolveSetForThalnos(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHolderName = game.getStoredValue("thalnosInitialHolder");
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        buttons.addAll(
            getButtonsForRollingThalnos(player, game, tile, tile.getUnitHolders().get(unitHolderName)));
        if ("space".equalsIgnoreCase(unitHolderName)
            && doesPlayerHaveFSHere("nekro_flagship", player, tile)) {
            buttons = new ArrayList<>();
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                buttons.addAll(getButtonsForRollingThalnos(player, game, tile, uH));
            }
        }
        buttons.add(Button.primary("rollThalnos_" + tile.getPosition() + "_" + unitHolderName, "Roll Now"));
        buttons.add(Button.danger("deleteButtons", "Dont roll anything"));
        String id = buttonID.replace("setForThalnos_", "");
        game.setSpecificThalnosUnit(id, game.getSpecificThalnosUnit(id) + 1);

        String message = player.getRepresentation()
            + " select the units for which you wish to reroll. Units that fail and did not have extra rolls will be automatically removed\n"
            +
            "Currently you are rerolling: \n";
        String damaged = "";
        for (String unit : game.getThalnosUnits().keySet()) {
            String rep = unit.split("_")[2];
            if (rep.contains("damaged")) {
                damaged = "damaged ";
                rep = rep.replace("damaged", "");
            }
            message = message + player.getFactionEmoji() + " " + game.getSpecificThalnosUnit(unit) + " " + damaged
                + rep + "\n";
        }
        List<Button> systemButtons = buttons;
        event.getMessage().editMessage(message)
            .setComponents(turnButtonListIntoActionRowList(systemButtons))
            .queue(Consumers.nop(), BotLogger::catchRestError);
    }

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
        List<UnitModel> units = new ArrayList<>();
        units.addAll(playerUnitsByQuantity.keySet());
        for (UnitModel unit : units) {
            playerUnitsByQuantity.put(unit, 0);
            for (String thalnosUnit : game.getThalnosUnits().keySet()) {
                int amount = game.getSpecificThalnosUnit(thalnosUnit);
                String unitName = getUnitName(unit.getAsyncId());
                thalnosUnit = thalnosUnit.split("_")[2].replace("damaged", "");
                if (thalnosUnit.equals(unitName)) {
                    playerUnitsByQuantity.put(unit, amount + playerUnitsByQuantity.get(unit));
                }
            }
            if (playerUnitsByQuantity.get(unit) == 0) {
                playerUnitsByQuantity.remove(unit);
            }
        }

        if (playerUnitsByQuantity.isEmpty()) {
            String fightingOnUnitHolderName = unitHolderName;
            if (!unitHolderName.equalsIgnoreCase(Constants.SPACE)) {
                fightingOnUnitHolderName = Helper.getPlanetRepresentation(unitHolderName, game);
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "There were no units selected to reroll");
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

        TileModel tileModel = TileHelper.getAllTiles().get(tile.getTileID());
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
        if (opponent != null) {
            tempOpponentMods = CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(opponent, tileModel,
                combatOnHolder, true, rollType);
        }
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
        if (!game.isFoWMode() && rollType == CombatRollType.combatround && combatOnHolder instanceof Planet && h > 0
            && opponent != null && opponent != player) {
            String msg = opponent.getRepresentation(true, true) + " you can autoassign " + h + " hit(s)";
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Button.success(finChecker + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + h,
                "Auto-assign Hits"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
        } else {
            if (!game.isFoWMode() && rollType == CombatRollType.combatround && opponent != null
                && opponent != player) {
                String msg = "\n" + opponent.getRepresentation(true, true) + " you suffered " + h
                    + " hit(s)";
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
                        buttons.add(Button.primary("combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(),
                            "Roll Dice For Combat Round #" + round));
                    }
                    String finChecker = "FFCC_" + opponent.getFaction() + "_";
                    buttons.add(Button.success(finChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                        "Auto-assign Hits"));
                    buttons.add(Button.danger("getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                        "Manually Assign Hits"));
                    buttons.add(Button.secondary(finChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + h,
                        "Cancel a Hit"));

                    String msg2 = opponent.getFactionEmoji()
                        + " can automatically assign hits. The hits would be assigned in the following way:\n\n"
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
                String unitName = getUnitName(unitKey.asyncID());
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
                    Button validTile2 = Button.danger(buttonID, buttonText);
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
                    Button validTile2 = Button.danger(buttonID, buttonText);
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
                String unitName = getUnitName(key.asyncID());
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
                    Button validTile2 = Button.danger(
                        finChecker + "setForThalnos_" + tile.getPosition() + "_" + unitHolder.getName() + "_"
                            + unitName + "damaged",
                        "Roll 1 damaged " + unitModel.getBaseType());
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                totalUnits = totalUnits - game
                    .getSpecificThalnosUnit(tile.getPosition() + "_" + unitHolder.getName() + "_" + unitName);
                if (totalUnits > 0) {
                    Button validTile2 = Button.danger(finChecker + "setForThalnos_" + tile.getPosition() + "_"
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
                buttons.add(Button.success("setupStep1_" + userId, player.getUserName()));
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
            "You have all been set up as franken factions. These have similar zombie emojis as their default faction icon. You should personalize yours with /franken set_faction_icon. You can use any emoji the bot can use");
    }

    public static List<Button> getFactionSetupButtons(Game game, String buttonID) {
        String userId = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        List<FactionModel> factionsOnMap = Mapper.getFactions().stream()
            .filter(f -> game.getTile(f.getHomeSystem()) != null)
            .filter(f -> game.getPlayerFromColorOrFaction(f.getAlias()) == null)
            .toList();
        List<FactionModel> allFactions = Mapper.getFactions().stream()
            .filter(f -> game.isDiscordantStarsMode() ? f.getSource().isDs() : f.getSource().isPok())
            .filter(f -> game.getPlayerFromColorOrFaction(f.getAlias()) == null)
            .sorted((f1, f2) -> factionsOnMap.contains(f1) ? (factionsOnMap.contains(f2) ? 0 : -1)
                : (factionsOnMap.contains(f2) ? 1 : 0))
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
                buttons.add(Button.success("setupStep2_" + userId + "_" + factionId, name).withEmoji(factionEmoji));
            }

            factionsComplete.add(factionId);
        }
        return buttons;
    }

    public static boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            int d = Integer.parseInt(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public static void rematch(Game game, GenericInteractionCreateEvent event) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**Game: `" + game.getName() + "` has ended!**");
        game.setHasEnded(true);
        game.setEndedDate(new Date().getTime());
        GameSaveLoadManager.saveMap(game, event);
        String gameEndText = GameEnd.getGameEndText(game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), gameEndText);
        game.setAutoPing(false);
        game.setAutoPingSpacer(0);
        ButtonHelper.offerEveryoneTitlePossibilities(game);
        MapGenerator.saveImage(game, DisplayType.all, event)
            .thenAccept(fileUpload -> {
                StringBuilder message = new StringBuilder();
                for (String playerID : game.getRealPlayerIDs()) { // GET ALL PLAYER PINGS
                    Member member = event.getGuild().getMemberById(playerID);
                    if (member != null)
                        message.append(member.getAsMention());
                }
                message.append("\nPlease provide a summary of the game below:");
                if (!game.isFoWMode() && !AsyncTI4DiscordBot.guildPrimary
                    .getTextChannelsByName("the-pbd-chronicles", true).isEmpty()) {
                    TextChannel pbdChroniclesChannel = AsyncTI4DiscordBot.guildPrimary
                        .getTextChannelsByName("the-pbd-chronicles", true).get(0);
                    String channelMention = pbdChroniclesChannel == null ? "#the-pbd-chronicles"
                        : pbdChroniclesChannel.getAsMention();
                    if (pbdChroniclesChannel == null) {
                        BotLogger.log(event,
                            "`#the-pbd-chronicles` channel not found - `/game end` cannot post summary");
                        return;
                    }
                    if (!game.isFoWMode()) {
                        // INFORM PLAYERS
                        pbdChroniclesChannel.sendMessage(gameEndText).queue(m -> { // POST INITIAL MESSAGE
                            m.editMessageAttachments(fileUpload).queue(Consumers.nop(), BotLogger::catchRestError); // ADD MAP FILE TO MESSAGE
                            m.createThreadChannel(game.getName()).queueAfter(2, TimeUnit.SECONDS,
                                t -> {
                                    t.sendMessage(message.toString()).queue(null, (error) -> BotLogger.log("Failure to create Game End thread for **" + game.getName() + "** in PBD Chronicles:\n> " + error.getMessage()));
                                    String endOfGameSummary = "";

                                    for (int x = 1; x < game.getRound() + 1; x++) {
                                        String summary = "";
                                        for (Player player : game.getRealPlayers()) {
                                            if (!game.getStoredValue("endofround" + x + player.getFaction()).isEmpty()) {
                                                summary = summary + player.getFactionEmoji() + ": " + game.getStoredValue("endofround" + x + player.getFaction()).replace("666fin", ":").replace("667fin", ",") + "\n";
                                            }
                                        }
                                        if (!summary.isEmpty()) {
                                            summary = "**__Round " + x + " Secret Summary__**\n" + summary;
                                            endOfGameSummary = endOfGameSummary + summary;
                                        }
                                    }
                                    if (!endOfGameSummary.isEmpty()) {
                                        MessageHelper.sendMessageToChannel(t, endOfGameSummary);
                                    }
                                }); // CREATE THREAD AND POST FOLLOW UP
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Game summary has been posted in the " + channelMention + " channel: "
                                    + m.getJumpUrl());
                        }, BotLogger::catchRestError);
                    }
                }
            });
        secondHalfOfRematch(event, game);
    }

    public static void secondHalfOfRematch(GenericInteractionCreateEvent event, Game game) {
        String name = game.getName();
        int charValue = name.charAt(name.length() - 1);
        String present = name.substring(name.length() - 1);
        String next = String.valueOf((char) (charValue + 1));
        String newName = "";
        if (isNumeric(present)) {
            newName = name + "b";
        } else {
            newName = name.substring(0, name.length() - 1) + next;
        }

        Guild guild = game.getGuild();
        Role gameRole = null;
        if (guild != null) {
            for (Role role : guild.getRoles()) {
                if (game.getName().equals(role.getName().toLowerCase())) {
                    gameRole = role;
                }
            }
        }

        TextChannel tableTalkChannel = game.getTableTalkChannel();
        TextChannel actionsChannel = game.getMainGameChannel();
        if (gameRole != null) {
            gameRole.getManager().setName(newName).queue();
        } else {
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

            Set<Permission> allow = Set.of(Permission.MESSAGE_MANAGE, Permission.VIEW_CHANNEL);
            tableTalkChannel.getManager().putRolePermissionOverride(gameRole.getIdLong(), allow, null);
            actionsChannel.getManager().putRolePermissionOverride(gameRole.getIdLong(), allow, null);
        }

        // CLOSE THREADS IN CHANNELS
        if (tableTalkChannel != null) {
            for (ThreadChannel threadChannel : tableTalkChannel.getThreadChannels()) {
                threadChannel.getManager().setArchived(true).queue();
            }
            String newTableName = tableTalkChannel.getName().replace(name, newName);
            game.getTableTalkChannel().getManager().setName(newTableName).queue();
        }
        if (actionsChannel != null) {
            for (ThreadChannel threadChannel : actionsChannel.getThreadChannels()) {
                threadChannel.getManager().setArchived(true).queue();
            }
            game.getActionsChannel().getManager().setName(newName + "-actions").queue();
        }
        Member gameOwner = guild.getMemberById(game.getOwnerID());
        if (gameOwner == null) {
            for (Player player : game.getPlayers().values()) {
                gameOwner = guild.getMemberById(player.getUserID());
                break;
            }
        }
        Game newGame = GameCreate.createNewGame(event, newName, gameOwner);
        // ADD PLAYERS
        for (Player player : game.getPlayers().values()) {
            if (!player.getFaction().equals("neutral"))
                newGame.addPlayer(player.getUserID(), player.getUserName());
        }
        newGame.setPlayerCountForMap(newGame.getPlayers().values().size());
        newGame.setStrategyCardsPerPlayer(newGame.getSCList().size() / newGame.getPlayers().values().size());

        // CREATE CHANNELS
        String newGameName = game.getCustomName();
        Matcher alreadyRematch = Pattern.compile(" Rematch #" + RegexHelper.intRegex("num")).matcher(game.getCustomName());
        if (alreadyRematch.find()) {
            newGameName = newGameName.replace(alreadyRematch.group(), "");
            int prevMatch = Integer.parseInt(alreadyRematch.group("num"));
            newGameName = newGameName + " Rematch #" + (prevMatch + 1);
        } else {
            newGameName = newGameName + " Rematch #1";
        }
        newGame.setCustomName(newGameName);
        if (tableTalkChannel != null)
            newGame.setTableTalkChannelID(tableTalkChannel.getId());

        // CREATE ACTIONS CHANNEL AND CLEAR PINS
        String newBotThreadName = newName + Constants.BOT_CHANNEL_SUFFIX;
        newGame.setMainGameChannelID(actionsChannel.getId());
        actionsChannel.retrievePinnedMessages().queue(msgs -> {
            msgs.forEach(msg -> msg.unpin().queue());
        }, BotLogger::catchRestError);

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName)
            .complete();
        newGame.setBotMapUpdatesThreadID(botThread.getId());
        newGame.setUpPeakableObjectives(5, 1);
        newGame.setUpPeakableObjectives(5, 2);
        // INTRODUCTION TO TABLETALK CHANNEL
        String tabletalkGetStartedMessage = gameRole.getAsMention() + " - table talk channel\n" +
            "This channel is for typical over the table converstion, as you would over the table while playing the game in real life.\n"
            +
            "If this group has agreed to whispers (secret conversations), you can create private threads off this channel.\n"
            +
            "Typical things that go here are: general conversation, deal proposals, memes - everything that isn't either an actual action in the game or a bot command\n";
        MessageHelper.sendMessageToChannelAndPin(tableTalkChannel, tabletalkGetStartedMessage);

        // INTRODUCTION TO ACTIONS CHANNEL
        String actionsGetStartedMessage = gameRole.getAsMention() + " - actions channel\n" +
            "This channel is for taking actions in the game, primarily using buttons or the odd slash command.\n" +
            "Please keep this channel clear of any chat with other players. Ideally this channel is a nice clean ledger of what has physically happened in the game.\n";
        MessageHelper.sendMessageToChannelAndPin(actionsChannel, actionsGetStartedMessage);
        offerPlayerSetupButtons(actionsChannel, newGame);

        // INTRODUCTION TO BOT-MAP THREAD
        String botGetStartedMessage = gameRole.getAsMention() + " - bot/map channel\n" +
            "This channel is for bot slash commands and updating the map, to help keep the actions channel clean.\n"
            +
            "### __Use the following commands to get started:__\n" +
            "> `/map add_tile_list {mapString}`, replacing {mapString} with a TTPG map string\n" +
            "> `/player setup` to set player faction and color\n" +
            "> `/game setup` to set player count and additional options\n" +
            "> `/game set_order` to set the starting speaker order\n" +
            "\n" +
            "### __Other helpful commands:__\n" +
            "> `/game replace` to replace a player in the game with a new one\n";
        MessageHelper.sendMessageToChannelAndPin(botThread, botGetStartedMessage);
        MessageHelper.sendMessageToChannelAndPin(botThread,
            "Website Live Map: https://ti4.westaddisonheavyindustries.com/game/" + newName);
        //ButtonHelper.offerPlayerSetupButtons(actionsChannel, newGame);

        List<Button> buttons2 = new ArrayList<>();
        buttons2.add(Button.success("getHomebrewButtons", "Yes, have homebrew"));
        buttons2.add(Button.danger("deleteButtons", "No Homebrew"));
        MessageHelper.sendMessageToChannel(actionsChannel, "If you plan to have a supported homebrew mode in this game, please indicate so with these buttons", buttons2);
        GameSaveLoadManager.saveMap(newGame, event);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }
    }

    public static List<Button> getColorSetupButtons(Game game, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        List<ColorModel> unusedColors = game.getUnusedColors();
        for (ColorModel color : unusedColors) {
            String colorName = color.getName();
            Emoji colorEmoji = color.getEmoji();
            String step3id = "setupStep3_" + userId + "_" + factionId + "_" + colorName;
            buttons.add(Button.success(step3id, colorName).withEmoji(colorEmoji));
        }
        return buttons;
    }

    public static void offerPlayerSetupButtons(MessageChannel channel, Game game) {
        List<Button> buttons = new ArrayList<>();

        buttons.add(Button.success("startPlayerSetup", "Setup a Player"));
        for (Player player : game.getPlayers().values()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentation()
                    + "After setting up the map, you can use this button instead of /player setup if you wish",
                buttons);
        }
        MessageHelper.sendMessageToChannelWithButtons(channel,
            "After setting up the map, you can use this button instead of /player setup if you wish", buttons);
    }

    public static void offerRedTapButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String poS : game.getPublicObjectives1Peakable()) {
            buttons.add(Button.success("cutTape_" + poS, Mapper.getPublicObjective(poS).getName()));
        }
        for (String poS : game.getPublicObjectives2Peakable()) {
            buttons.add(Button.success("cutTape_" + poS, Mapper.getPublicObjective(poS).getName()));
        }

        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
            player.getRepresentation() + "Choose an objective to make scorable. Reminder that in a normal game you cant choose a stage 2 to make scorable until after round 3 is over",
            buttons);
    }

    public static void cutTape(Game game, String buttonID, ButtonInteractionEvent event) {
        String poID = buttonID.replace("cutTape_", "");
        int location = 1;
        deleteMessage(event);
        List<String> po1s = new ArrayList<>();
        po1s.addAll(game.getPublicObjectives1Peakable());
        for (String poS : po1s) {
            if (poS.equalsIgnoreCase(poID)) {
                game.swapStage1(1, location);
                Map.Entry<String, Integer> objective = game.revealStage1();
                PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), game.getPing() + " **Stage 1 Public Objective Now Scorable**");
                game.getMainGameChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
                return;
            }
            location++;
        }
        location = 1;
        List<String> po2s = new ArrayList<>();
        po2s.addAll(game.getPublicObjectives2Peakable());
        for (String poS : po2s) {
            if (poS.equalsIgnoreCase(poID)) {
                game.swapStage2(1, location);
                Map.Entry<String, Integer> objective = game.revealStage2();
                PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), game.getPing() + " **Stage 2 Public Objective Now Scorable**");
                game.getMainGameChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
                return;
            }
            location++;
        }
    }

    public static void offerHomeBrewButtons(Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        game.setHomeBrew(false);
        buttons.add(Button.success("setupHomebrew_444", "4 stage 1s, 4 stage 2s, 4 secrets, 12 VP"));
        buttons.add(Button.success("setupHomebrew_absolRelicsNAgendas", "Absol Relics And Agendas"));
        buttons.add(Button.success("setupHomebrew_absolTechsNMechs", "Absol Techs and Mechs"));
        buttons.add(Button.success("setupHomebrew_dsfactions", "Discordant Stars Factions"));
        buttons.add(Button.success("setupHomebrew_dsexplores", "DS Explores/Relics/ACs"));
        buttons.add(Button.success("setupHomebrew_acDeck2", "Action Cards Deck 2"));
        buttons.add(Button.success("setupHomebrew_456", "5 stage 1s, 6 stage 2s, 4 secrets, 14 VP"));
        buttons.add(Button.success("setupHomebrew_redTape", "Red Tape"));
        buttons.add(Button.success("setupHomebrew_removeSupports", "Remove Supports"));
        buttons.add(Button.success("setupHomebrew_homebrewSCs", "Homebrew SCs"));
        buttons.add(Button.danger("deleteButtons", "Done With Buttons"));
        deleteMessage(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Game has been marked as homebrew, chose which homebrew you'd like in the game, or press Done With Buttons",
            buttons);
    }

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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set the relics and agendas to absol stuff");
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set the techs n mechs to absol stuff");
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set the explores/ACs/relics to DS stuff");
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
                game.setHomeBrewSCMode(true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to homebrew SC mode");
            }
            case "redTape" -> {
                game.setRedTapeMode(true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to red tape mode");
            }
        }
    }

    public static void resolveSetupStep0(Player player, Game game, ButtonInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentation() + "Please tell the bot which user you are setting up",
            getUserSetupButtons(game));
    }

    public static List<Button> getGainCCButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "increase_tactic_cc", "Gain 1 Tactic CC"));
        buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "increase_fleet_cc", "Gain 1 Fleet CC"));
        buttons.add(
            Button.success(player.getFinsFactionCheckerPrefix() + "increase_strategy_cc", "Gain 1 Strategy CC"));
        buttons.add(Button.danger(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Done Gaining CCs"));
        buttons.add(Button.secondary(player.getFinsFactionCheckerPrefix() + "resetCCs",
            "Reset CCs"));
        player.getGame().setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        return buttons;
    }

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
        newButtons.add(Button.secondary("setupStep2_" + userId + "_" + (maxBefore + numberOfHomes) + "!",
            "Get more factions"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the desired faction",
            newButtons);
    }

    public static void resolveSetupStep2(Player player, Game game, GenericInteractionCreateEvent event,
        String buttonID) {
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
            newButtons
                .add(Button.secondary("setupStep2_" + userId + "_" + (maxBefore + 22) + "!", "Get more factions"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Please tell the bot the desired faction", newButtons);
            return;
        }
        if ("keleres".equalsIgnoreCase(factionId)) {
            List<Button> newButtons = new ArrayList<>();
            newButtons.add(Button.success("setupStep2_" + userId + "_keleresa", "Keleres Argent")
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("argent"))));
            newButtons.add(Button.success("setupStep2_" + userId + "_keleresm", "Keleres Mentak")
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mentak"))));
            newButtons.add(Button.success("setupStep2_" + userId + "_keleresx", "Keleres Xxcha")
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("xxcha"))));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Please tell the bot which flavor of keleres you are", newButtons);
            return;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Setting up as faction: " + Mapper.getFaction(factionId).getFactionName());
        offerColorSetupButtons(game, event, buttonID, userId, factionId);

    }

    private static void offerColorSetupButtons(Game game, GenericInteractionCreateEvent event, String buttonID,
        String userId, String factionId) {
        List<Button> buttons = getColorSetupButtons(game, buttonID);
        List<Button> newButtons = new ArrayList<>();
        int maxBefore = -1;
        for (int x = 0; x < buttons.size(); x++) {
            if (x < maxBefore + 23) {
                newButtons.add(buttons.get(x));
            }
        }
        newButtons.add(Button.secondary("setupStep3_" + userId + "_" + factionId + "_" + (maxBefore + 22) + "!",
            "Get more colors"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Please tell the bot the desired player color", newButtons);
    }

    public static List<Button> getSpeakerSetupButtons(Game game, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        String pos = buttonID.split("_")[4];
        buttons.add(Button.success("setupStep5_" + userId + "_" + factionId + "_" + color + "_" + pos + "_yes",
            "Yes, setting up speaker"));
        buttons.add(Button.success("setupStep5_" + userId + "_" + factionId + "_" + color + "_" + pos + "_no", "No"));
        return buttons;
    }

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
                newButtons.add(Button.secondary("setupStep3_" + userId + "_" + factionId + "_" + (maxBefore + 22) + "!",
                    "Get more colors"));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the desired color",
                newButtons);
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
                buttons.add(Button.success(
                    "setupStep4_" + userId + "_" + factionId + "_" + color + "_" + tile.getPosition(), rep));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            "Please tell the bot the home system location", buttons);

    }

    public static void resolveSetupStep4And5(Game game, ButtonInteractionEvent event, String buttonID) {
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        String pos = buttonID.split("_")[4];
        Player speaker = null;
        Player player = game.getPlayer(userId);
        if (game.getPlayer(game.getSpeaker()) != null) {
            speaker = game.getPlayers().get(game.getSpeaker());
        }
        if (game.getPlayerFromColorOrFaction(color) != null)
            color = player.getNextAvailableColour();
        if (buttonID.split("_").length == 6 || speaker != null) {
            if (speaker != null) {
                Setup.secondHalfOfPlayerSetup(player, game, color, factionId, pos, event, false);
            } else {
                Setup.secondHalfOfPlayerSetup(player, game, color, factionId, pos, event, "yes".equalsIgnoreCase(buttonID.split("_")[5]));
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
            for (int j = i + 1; j < players.size(); j++) {
                Player p2 = players.get(j);

                double contrast = colorContrast(p1.getColor(), p2.getColor());
                if (contrast < 4.5) {
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

    public static double colorContrast(String color1, String color2) {
        return Math.max(colorPrimaryContrast(color1, color2), colorSecondaryContrast(color1, color2));
    }

    private static double colorPrimaryContrast(String color1, String color2) {
        Color c1 = ColorModel.primaryColor(color1);
        Color c2 = ColorModel.primaryColor(color2);
        if (c1 == null || c2 == null)
            return 1;

        double l1 = relativeLuminance(c1);
        double l2 = relativeLuminance(c2);
        double contrast = contrastRatio(l1, l2);
        return contrast;
    }

    private static double colorSecondaryContrast(String color1, String color2) {
        Color c1 = ColorModel.secondaryColor(color1);
        Color c2 = ColorModel.secondaryColor(color2);

        // if there is no secondary color (not a split color), compare on the primary
        // color
        double l1 = c1 == null ? relativeLuminance(ColorModel.primaryColor(color1)) : relativeLuminance(c1);
        double l2 = c2 == null ? relativeLuminance(ColorModel.primaryColor(color2)) : relativeLuminance(c2);
        double contrast = contrastRatio(l1, l2);
        return contrast;
    }

    /**
     * For the sRGB colorspace, the relative luminance of a color is defined as
     * <p>
     * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
     * <p>
     * where R, G and B are defined as:
     * <p>
     * if XsRGB <= 0.03928 then X = XsRGB/12.92 else X = ((XsRGB+0.055)/1.055) ^ 2.4
     * <p>
     * -
     * <p>
     * and RsRGB, GsRGB, and BsRGB are defined as:
     * <p>
     * XsRGB = X8bit/255
     */
    private static double relativeLuminance(Color color) {
        if (color == null)
            return 0;
        double RsRGB = ((double) color.getRed()) / 255.0;
        double GsRGB = ((double) color.getGreen()) / 255.0;
        double BsRGB = ((double) color.getBlue()) / 255.0;

        double r = color.getRed() <= 10 ? RsRGB / 12.92 : Math.pow((RsRGB + 0.055) / 1.055, 2.4);
        double g = color.getGreen() <= 10 ? GsRGB / 12.92 : Math.pow((GsRGB + 0.055) / 1.055, 2.4);
        double b = color.getBlue() <= 10 ? BsRGB / 12.92 : Math.pow((BsRGB + 0.055) / 1.055, 2.4);

        return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
    }

    /**
     * To calculate the contrast ratio, the relative luminance of the lighter colour
     * (L1) is divided through the relative luminance of the darker colour (L2):
     * <p>
     * (L1 + 0.05) / (L2 + 0.05)
     * <p>
     * This results in a value ranging from 1:1 (no contrast at all) to 21:1 (the
     * highest possible contrast).
     *
     * @param L1 The lighter color (higher luminance)
     * @param L2 the darker color (lower luminance)
     * @return contrast ratio (1:x)
     */
    private static double contrastRatio(double L1, double L2) {
        if (L1 < L2)
            return contrastRatio(L2, L1);
        return (L1 + 0.05) / (L2 + 0.05);
    }

    public static void resolveStellar(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        StellarConverter.secondHalfOfStellar(game, buttonID.split("_")[1], event);
        deleteMessage(event);
    }

    public static String getUnitHolderRep(UnitHolder unitHolder, Tile tile, Game game) {
        String name = unitHolder.getName();
        if ("space".equalsIgnoreCase(name)) {
            name = "Space Area of " + tile.getRepresentation();
        } else {
            if (unitHolder instanceof Planet planet) {
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
                if (t.containsPlayersUnitsWithModelCondition(player, UnitModel::getIsShip) && t.getPlanetUnitHolders().isEmpty()) {
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
            tilesWithBombard.get(0).getPosition(), player, false);
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
                        buttons.add(Button.success(finChecker + "stellarConvert_" + planet.getName(),
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
                String unitName = getUnitName(key.asyncID());
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
                    Button validTile3 = Button.success(buttonID, buttonText);
                    validTile3 = validTile3.withEmoji(emoji);
                    buttons.add(validTile3);
                }
            }
        }
        Button concludeMove = Button.primary("deleteButtons", "Done repairing units");
        buttons.add(concludeMove);
        return buttons;
    }

    public static void showWormholes(GenericInteractionCreateEvent event, Game game) {
        game.setStoredValue("checkWHs", "yes");

        MapGenerator.saveImage(game, DisplayType.map, event, true)
            .thenAccept(fileUpload -> MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), fileUpload));
    }

    public static List<Player> tileHasPDS2Cover(Player player, Game game, String tilePos) {
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tilePos, player, false, true);
        for (Player p2 : game.getRealPlayers()) {
            adjTiles.addAll(FoWHelper.getAdjacentTiles(game, tilePos, p2, false, true));
        }
        List<Player> playersWithPds2 = new ArrayList<>();
        if (FoWHelper.otherPlayersHaveShipsInSystem(player, game.getTileByPosition(tilePos), game) && player.hasAbility("starfall_gunnery") && checkNumberNonFighterShipsWithoutSpaceCannon(player, game, game.getTileByPosition(tilePos)) > 0) {
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
                        if (p2.controlsMecatol(false) && p2.getTechs().contains("iihq") && !playersWithPds2.contains(p2)) {
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
                    if (owningPlayer == null || playersWithPds2.contains(owningPlayer) || !FoWHelper.getAdjacentTiles(game, tilePos, owningPlayer, false, true).contains(adjTilePos)) {
                        continue;
                    }

                    UnitModel model = owningPlayer.getUnitFromUnitKey(unitKey);
                    if (model == null || (model.getId().equalsIgnoreCase("xxcha_mech") && ButtonHelper.isLawInPlay(game, "articles_war"))) {
                        continue;
                    }
                    if (model != null && model.getSpaceCannonDieCount() > 0
                        && (model.getDeepSpaceCannon() || tilePos.equalsIgnoreCase(adjTilePos)
                            || game.playerHasLeaderUnlockedOrAlliance(owningPlayer, "mirvedacommander"))) {
                        if (owningPlayer == player) {
                            if (FoWHelper.otherPlayersHaveShipsInSystem(player, game.getTileByPosition(tilePos),
                                game)) {
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
                                + " this is a reminder that this is the window to play experimental battlestation");
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
            if (game.isFoWMode()) {
                FoWHelper.pingAllPlayersWithFullStats(game, event, player, "started turn");
            }
            ButtonHelperFactionSpecific.resolveMilitarySupportCheck(player, game);
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, game);

            game.setCurrentPhase("action");
        }

        msg = "";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (isFowPrivateGame) {
            if (privatePlayer == null) {
                BotLogger.log(event, "`ButtonHelper.startMyTurn` privatePlayer is null");
                return;
            }
            msgExtra = "" + privatePlayer.getRepresentation(true, true) + " UP NEXT";
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, game, event, msgExtra, fail, success);
            game.updateActivePlayer(privatePlayer);

            MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(),
                msgExtra + "\n Use Buttons to do turn.",
                TurnStart.getStartOfTurnButtons(privatePlayer, game, false, event));

            if (privatePlayer.getStasisInfantry() > 0) {
                if (getPlaceStatusInfButtons(game, privatePlayer).size() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, game),
                        "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.",
                        getPlaceStatusInfButtons(game, privatePlayer));
                } else {
                    privatePlayer.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(getCorrectChannel(privatePlayer, game), privatePlayer.getRepresentation()
                        + " You had infantry2 to be revived, but the bot couldnt find planets you own in your HS to place them, so per the rules they now disappear into the ether");

                }
            }

        } else {
            if (!msgExtra.isEmpty()) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msgExtra);
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    "\n Use Buttons to do turn.",
                    TurnStart.getStartOfTurnButtons(privatePlayer, game, false, event));
                if (privatePlayer == null) {
                    BotLogger.log(event, "`ButtonHelper.startMyTurn` privatePlayer is null");
                    return;
                }

                if (privatePlayer.getStasisInfantry() > 0) {
                    if (getPlaceStatusInfButtons(game, privatePlayer).size() > 0) {
                        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, game),
                            "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.",
                            getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(getCorrectChannel(privatePlayer, game), privatePlayer.getRepresentation()
                            + " You had infantry2 to be revived, but the bot couldnt find planets you own in your HS to place them, so per the rules they now disappear into the ether");

                    }
                }
            }
        }
    }

    public static void resolveImperialArbiter(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
            getIdent(player) + " decided to use the Imperial Arbiter Law to swap SCs with someone");
        game.removeLaw("arbiter");
        List<Button> buttons = ButtonHelperFactionSpecific.getSwapSCButtons(game, "imperialarbiter", player);
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game),
            player.getRepresentation(true, true) + " choose who you want to swap SCs with",
            buttons);
        deleteMessage(event);
    }

    // playerHasUnitsInSystem(player, tile);
    public static void startActionPhase(GenericInteractionCreateEvent event, Game game) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);
        String msg;
        game.setCurrentPhase("action");
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        Collection<Player> activePlayers = game.getPlayers().values().stream()
            .filter(Player::isRealPlayer)
            .toList();
        Player nextPlayer = null;
        int lowestSC = 100;
        for (Player p2 : game.getRealPlayers()) {
            ButtonHelperActionCards.checkForAssigningCoup(game, p2);
            if (game.getStoredValue("Play Naalu PN") != null
                && game.getStoredValue("Play Naalu PN").contains(p2.getFaction())) {
                if (!p2.getPromissoryNotesInPlayArea().contains("gift")
                    && p2.getPromissoryNotes().containsKey("gift")) {
                    resolvePNPlay("gift", p2, game, event);
                }
            }
        }
        for (Player player_ : activePlayers) {
            int playersLowestSC = player_.getLowestSC();
            String scNumberIfNaaluInPlay = game.getSCNumberIfNaaluInPlay(player_,
                Integer.toString(playersLowestSC));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                nextPlayer = player_; // no further processing, this player has the 0 token
                break;
            }
            if (playersLowestSC < lowestSC) {
                lowestSC = playersLowestSC;
                nextPlayer = player_;
            }
        }

        // INFORM FIRST PLAYER IS UP FOR ACTION
        if (nextPlayer != null) {
            msgExtra += " " + nextPlayer.getRepresentation() + " is up for an action";
            privatePlayer = nextPlayer;
            game.updateActivePlayer(nextPlayer);
            if (game.isFoWMode()) {
                FoWHelper.pingAllPlayersWithFullStats(game, event, nextPlayer, "started turn");
            }
            ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, game);

            game.setCurrentPhase("action");
        }

        msg = "";
        MessageHelper.sendMessageToChannel(getCorrectChannel(nextPlayer, game), msg);
        if (isFowPrivateGame) {
            msgExtra = "Start phase command run";
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, game, event, msgExtra, fail, success);
            if (privatePlayer == null)
                return;
            msgExtra = "" + privatePlayer.getRepresentation(true, true) + " UP NEXT";
            game.updateActivePlayer(privatePlayer);

            if (!allPicked) {
                game.setCurrentPhase("strategy");
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(),
                    "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, game, privatePlayer));
            } else {

                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(),
                    msgExtra + "\n Use Buttons to do turn.",
                    TurnStart.getStartOfTurnButtons(privatePlayer, game, false, event));

                if (privatePlayer.getStasisInfantry() > 0) {
                    if (getPlaceStatusInfButtons(game, privatePlayer).size() > 0) {
                        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, game),
                            "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.",
                            getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(getCorrectChannel(privatePlayer, game), privatePlayer.getRepresentation()
                            + " You had infantry2 to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether");

                    }
                }
            }

        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "All players have picked a strategy card.");
            if (game.getShowBanners()) {
                MapGenerator.drawPhaseBanner("action", game.getRound(), event);
            }
            ListTurnOrder.turnOrder(event, game);
            if (!msgExtra.isEmpty()) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msgExtra);
                if (privatePlayer == null) {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Could not find player.");
                    return;
                }
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    "\n Use Buttons to do turn.",
                    TurnStart.getStartOfTurnButtons(privatePlayer, game, false, event));

                if (privatePlayer.getStasisInfantry() > 0) {
                    if (getPlaceStatusInfButtons(game, privatePlayer).size() > 0) {
                        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, game),
                            "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.",
                            getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(getCorrectChannel(privatePlayer, game), privatePlayer.getRepresentation()
                            + " You had infantry2 to be revived, but the bot couldnt find planets you own in your HS to place them, so per the rules they now disappear into the ether");

                    }
                }
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            List<Button> buttons = new ArrayList<>();
            if (p2.hasTechReady("qdn") && p2.getTg() > 2 && p2.getStrategicCC() > 0) {
                buttons.add(Button.success("startQDN", "Use Quantum Datahub Node"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p2, game),
                    p2.getRepresentation(true, true) + " you have the opportunity to use QDN",
                    buttons);
            }
            buttons = new ArrayList<>();
            if (isPlayerElected(game, p2, "arbiter")) {
                buttons.add(Button.success("startArbiter", "Use Imperial Arbiter"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p2, game),
                    p2.getRepresentation(true, true) + " you have the opportunity to use Imperial Arbiter",
                    buttons);
            }
        }
    }

    public static void startStatusHomework(GenericInteractionCreateEvent event, Game game) {
        game.setCurrentPhase("statusHomework");
        game.setStoredValue("startTimeOfRound" + game.getRound() + "StatusHomework", new Date().getTime() + "");
        // first do cleanup if necessary
        int playersWithSCs = 0;
        for (Player player : game.getRealPlayers()) {
            if (player.getSCs() != null && player.getSCs().size() > 0 && !player.getSCs().contains(0)) {
                playersWithSCs++;
            }
        }

        if (playersWithSCs > 0) {
            new Cleanup().runStatusCleanup(game);
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                game.getPing() + "Status Cleanup Run!");
            if (!game.isFoWMode()) {
                DisplayType displayType = DisplayType.map;
                MapGenerator.saveImage(game, displayType, event)
                    .thenAccept(fileUpload -> MessageHelper.sendFileUploadToChannel(game.getActionsChannel(),
                        fileUpload));
            }
        }

        for (Player player : game.getRealPlayers()) {
            Leader playerLeader = player.getLeader("naaluhero").orElse(null);

            if (player.hasLeader("naaluhero") && player.getLeaderByID("naaluhero").isPresent()
                && playerLeader != null && !playerLeader.isLocked()) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("naaluHeroInitiation", "Play Naalu Hero"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " Reminder this is the window to do Naalu Hero. You can use the buttons to start the process",
                    buttons);
            }
            if (player.getRelics() != null && player.hasRelic("mawofworlds") && game.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " Reminder this is the window to do Maw of Worlds");
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " You can use these buttons to resolve Maw Of Worlds",
                    getMawButtons());
            }
            if (player.getRelics() != null && player.hasRelic("twilight_mirror") && game.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " Reminder this is the window to do Twilight Mirror");
                List<Button> playerButtons = new ArrayList<>();
                playerButtons.add(Button.success("resolveTwilightMirror", "Purge Twilight Mirror"));
                playerButtons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation() + " You can use these buttons to resolve Twilight Mirror",
                    playerButtons);
            }
            if (player.getRelics() != null && player.hasRelic("emphidia")) {
                for (String pl : player.getPlanets()) {
                    Tile tile = game.getTile(AliasHandler.resolveTile(pl));
                    if (tile == null) {
                        continue;
                    }
                    UnitHolder unitHolder = tile.getUnitHolders().get(pl);
                    if (unitHolder != null && unitHolder.getTokenList() != null
                        && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                            player.getRepresentation()
                                + "Reminder this is the window to purge Crown of Emphidia if you want to.");
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentation()
                                + " You can use these buttons to resolve Crown of Emphidia",
                            getCrownButtons());
                    }
                }
            }

            if (player.getActionCards() != null && player.getActionCards().containsKey("stability")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + "Reminder this is the window to play political stability.");
            }

            if (player.getActionCards() != null && player.getActionCards().containsKey("abs")
                && game.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + "Reminder this is the window to play ancient burial sites.");
            }

            for (String pn : player.getPromissoryNotes().keySet()) {
                if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                    String cyberMessage = "# " + player.getRepresentation(true, true)
                        + " reminder to use cybernetic enhancements!";
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        cyberMessage);
                }
            }
        }
        String message2 = "Resolve status homework using the buttons. \n ";
        game.setACDrawStatusInfo("");
        Button draw1AC = Button.success("drawStatusACs", "Draw Status Phase ACs")
            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        Button getCCs = Button.success("redistributeCCButtons", "Redistribute, Gain, & Confirm CCs")
            .withEmoji(Emoji.fromFormatted("🔺"));
        Button yssarilPolicy = null;
        for (Player player : game.getRealPlayers()) {
            if (isPlayerElected(game, player, "minister_policy") && player.hasAbility("scheming")) {
                yssarilPolicy = Button.secondary("FFCC_" + player.getFaction() + "_yssarilMinisterOfPolicy",
                    "Draw Minister of Policy AC").withEmoji(Emoji.fromFormatted(Emojis.Yssaril));
            }
        }
        boolean custodiansTaken = game.isCustodiansScored();
        Button passOnAbilities;
        if (custodiansTaken) {
            passOnAbilities = Button.danger("pass_on_abilities", "Ready For Agenda");
            message2 = message2
                + "This is the moment when you should resolve: \n- Political Stability \n- Ancient Burial Sites\n- Maw of Worlds \n- Naalu hero\n- Crown of Emphidia";
        } else {
            passOnAbilities = Button.danger("pass_on_abilities", "Ready For Strategy Phase");
            message2 = message2
                + "Ready For Strategy Phase means you are done playing/passing on: \n- Political Stability \n- Summit \n- Manipulate Investments ";
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(draw1AC);
        buttons.add(getCCs);
        buttons.add(passOnAbilities);
        if (yssarilPolicy != null) {
            buttons.add(yssarilPolicy);
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message2, buttons);
    }

    public static void startStrategyPhase(GenericInteractionCreateEvent event, Game game) {
        int round = game.getRound();
        if (game.getHasHadAStatusPhase()) {
            round++;
            game.setRound(round);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Started Round " + round);
        if (game.getShowBanners()) {
            MapGenerator.drawPhaseBanner("strategy", round, event);
        }
        if (game.getRealPlayers().size() == 6) {
            game.setStrategyCardsPerPlayer(1);
        }
        ButtonHelperFactionSpecific.checkForNaaluPN(game);
        for (Player p2 : game.getRealPlayers()) {
            if (game.getStoredValue("Summit") != null
                && game.getStoredValue("Summit").contains(p2.getFaction())
                && p2.getActionCards().containsKey("summit")) {
                PlayAC.playAC(event, game, p2, "summit", game.getMainGameChannel());
            }
            if (game.getStoredValue("Investments") != null
                && game.getStoredValue("Investments").contains(p2.getFaction())
                && p2.getActionCards().containsKey("investments")) {
                PlayAC.playAC(event, game, p2, "investments", game.getMainGameChannel());
            }
            if (p2.hasLeader("zealotshero") && p2.getLeader("zealotshero").get().isActive()) {
                if (!game.getStoredValue("zealotsHeroTechs").isEmpty()) {
                    String list = game.getStoredValue("zealotsHeroTechs");
                    List<Button> buttons = new ArrayList<>();
                    for (String techID : list.split("-")) {
                        buttons.add(Button.success("purgeTech_" + techID, "Purge " + Mapper.getTech(techID).getName()));
                    }
                    String msg = p2.getRepresentation(true, true) + " due to zealots hero, you have to purge 2 techs. Use buttons to purge ";
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p2, game), msg + "the first tech", buttons);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p2, game), msg + "the second tech", buttons);
                    p2.removeLeader("zealotshero");
                    game.setStoredValue("zealotsHeroTechs", "");
                }
            }
        }
        if (!game.getStoredValue("agendaConstitution").isEmpty()) {
            game.setStoredValue("agendaConstitution", "");
            for (Player p2 : game.getRealPlayers()) {
                for (String planet : p2.getPlanets()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    if (game.getTileFromPlanet(planet) == p2.getHomeSystemTile()) {
                        p2.exhaustPlanet(planet);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# Exhausted all home systems due to that one agenda");
        }
        if (!game.getStoredValue("agendaArmsReduction").isEmpty()) {
            game.setStoredValue("agendaArmsReduction", "");
            for (Player p2 : game.getRealPlayers()) {
                for (String planet : p2.getPlanets()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    if (isPlanetTechSkip(planet, game)) {
                        p2.exhaustPlanet(planet);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# Exhausted all tech skip planets due to that one agenda");
        }
        if (!game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
            game.setStoredValue("agendaChecksNBalancesAgainst", "");
            for (Player p2 : game.getRealPlayers()) {
                String message = p2.getRepresentation() + " Click the names of up to 3 planets you wish to ready after checks and balances resolved against";

                List<Button> buttons = Helper.getPlanetRefreshButtons(event, p2, game);
                buttons.add(Button.danger("deleteButtons_spitItOut", "Done Readying Planets")); //spitItOut
                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), message, buttons);
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# Sent buttons to refresh 3 planets due to checks and balances");
        }
        if (!game.getStoredValue("agendaRevolution").isEmpty()) {
            game.setStoredValue("agendaRevolution", "");
            for (Player p2 : game.getRealPlayers()) {
                String message = p2.getRepresentation() + " Exhaust 1 planet for each tech you own (" + p2.getTechs().size() + ")";

                List<Button> buttons = Helper.getPlanetExhaustButtons(p2, game);
                buttons.add(Button.danger("deleteButtons_spitItOut", "Done Exhausting")); //spitItOut
                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), message, buttons);
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# Sent buttons to exhaust 1 planet for each tech due to anti-intellectual revolution resolving against");
        }
        if (!game.getStoredValue("agendaRepGov").isEmpty()) {
            for (Player p2 : game.getRealPlayers()) {
                if (game.getStoredValue("agendaRepGov").contains(p2.getFaction())) {
                    for (String planet : p2.getPlanets()) {
                        Planet p = game.getPlanetsInfo().get(planet);
                        if (p != null && p.getPlanetTypes().contains("cultural")) {
                            p2.exhaustPlanet(planet);
                        }
                    }
                }
            }
            game.setStoredValue("agendaRepGov", "");
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# Exhausted all cultural planets of those who voted against on that one agenda");
        }
        if (game.isFoWMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Pinged speaker to pick SC.");
        }
        Player speaker;
        if (game.getPlayer(game.getSpeaker()) != null) {
            speaker = game.getPlayers().get(game.getSpeaker());
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Speaker not found. Can't proceed");
            return;
        }
        String message = speaker.getRepresentation(true, true)
            + " UP TO PICK SC\n";
        game.updateActivePlayer(speaker);
        game.setCurrentPhase("strategy");
        String pickSCMsg = "Use Buttons to Pick SC";
        if (game.getLaws().containsKey("checks") || game.getLaws().containsKey("absol_checks")) {
            pickSCMsg = "Use Buttons to Pick the SC you want to give someone";
        }
        ButtonHelperAbilities.giveKeleresCommsNTg(game, event);
        game.setStoredValue("startTimeOfRound" + game.getRound() + "Strategy", new Date().getTime() + "");
        if (game.isFoWMode()) {
            if (!game.isHomeBrewSCMode()) {
                MessageHelper.sendMessageToChannelWithButtons(speaker.getPrivateChannel(),
                    message + pickSCMsg, Helper.getRemainingSCButtons(event, game, speaker));
            } else {
                MessageHelper.sendPrivateMessageToPlayer(speaker, game, message);
            }
        } else {
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message + pickSCMsg,
                Helper.getRemainingSCButtons(event, game, speaker));
        }
        if (!game.isFoWMode()) {
            updateMap(game, event,
                "Start of Strategy Phase For Round #" + game.getRound());
        }
        for (Player player2 : game.getRealPlayers()) {
            if (player2.getActionCards() != null && player2.getActionCards().containsKey("summit")
                && game.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    player2.getRepresentation(true, true)
                        + "Reminder this is the window to do summit");
            }
            for (String pn : player2.getPromissoryNotes().keySet()) {
                if (!player2.ownsPromissoryNote("scepter") && "scepter".equalsIgnoreCase(pn)) {
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pn);
                    Player owner = game.getPNOwner(pn);
                    Button transact = Button.success("resolvePNPlay_" + pn, "Play " + promissoryNote.getName())
                        .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(transact);
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    String cyberMessage = player2.getRepresentation(true, true)
                        + " reminder this is the window to play Mahact PN if you want (button should work)";
                    MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(),
                        cyberMessage, buttons);
                    if (!game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                            "You should all pause for a potential mahact PN play here if you think it relevant");
                    }
                }
            }
        }
    }

    public static List<Button> getMawButtons() {
        List<Button> playerButtons = new ArrayList<>();
        playerButtons.add(Button.success("resolveMaw", "Purge Maw of Worlds"));
        playerButtons.add(Button.danger("deleteButtons", "Decline"));
        return playerButtons;
    }

    public static List<Button> getCrownButtons() {
        List<Button> playerButtons = new ArrayList<>();
        playerButtons.add(Button.success("resolveCrownOfE", "Purge Crown"));
        playerButtons.add(Button.danger("deleteButtons", "Decline"));
        return playerButtons;
    }

    public static void resolveMaw(Game game, Player player, ButtonInteractionEvent event) {
        player.removeRelic("mawofworlds");
        player.removeExhaustedRelic("mawofworlds");
        for (String planet : player.getPlanets()) {
            player.exhaustPlanet(planet);
        }
        game.setComponentAction(true);

        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
            player.getRepresentation() + " purged Maw Of Worlds.");
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(),
            player.getRepresentation() + " Use the button to get a tech", Buttons.GET_A_FREE_TECH);
        deleteMessage(event);
    }

    public static void endTurnWhenAllReacted(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String sc = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), game.getPing() + " the active player has elected to end turn once everyone has resolved SC #" + sc + ". Please resolve it as soon as possible so the game can progress");
        game.setTemporaryPingDisable(true);
        game.setStoredValue("endTurnWhenSCFinished", sc + player.getFaction());
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveTwilightMirror(Game game, Player player, ButtonInteractionEvent event) {
        player.removeRelic("twilight_mirror");
        player.removeExhaustedRelic("twilight_mirror");
        for (String planet : player.getPlanets()) {
            player.exhaustPlanet(planet);
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
            player.getRepresentation() + " purged Twilight Mirror to take one action.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation()
                + " Use the button to do an action. It is advised you avoid the end turn button at the end of it, and just delete it. ",
            TurnStart.getStartOfTurnButtons(player, game, false, event));
        game.updateActivePlayer(player);
        deleteMessage(event);
    }

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
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game),
            player.getRepresentation() + " scored Crown of Emphidia");
        deleteMessage(event);
        Helper.checkEndGame(game, player);
    }

    public static List<Button> getPlayersToTransact(Game game, Player p) {
        List<Button> playerButtons = new ArrayList<>();
        String finChecker = "FFCC_" + p.getFaction() + "_";
        for (Player player : game.getPlayers().values()) {
            if (player.isRealPlayer()) {
                if (player.getFaction().equalsIgnoreCase(p.getFaction())) {
                    continue;
                }
                String faction = player.getFaction();
                if (faction != null && Mapper.isValidFaction(faction)) {
                    Button button;
                    if (!game.isFoWMode()) {
                        String label = player.getUserName();
                        if (!canTheseTwoTransact(game, p, player)) {
                            label = player.getUserName() + "(Not Neighbors)";
                        }
                        button = Button.secondary(finChecker + "transactWith_" + faction, label);

                        String factionEmojiString = player.getFactionEmoji();
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    } else {
                        button = Button.secondary(finChecker + "transactWith_" + player.getColor(), player.getColor());
                    }
                    playerButtons.add(button);
                }

            }
        }
        return playerButtons;
    }

    public static List<Button> getStuffToTransButtonsNew(Game game, Player player, Player p1, Player p2) {
        List<Button> stuffToTransButtons = new ArrayList<>();
        if (p1.getTg() > 0) {
            Button transact = Button.success("newTransact_TGs_" + p1.getFaction() + "_" + p2.getFaction(), "TGs");
            stuffToTransButtons.add(transact);
        }
        if (p1.getDebtTokenCount(p2.getColor()) > 0) {
            Button transact = Button.primary("newTransact_ClearDebt_" + p1.getFaction() + "_" + p2.getFaction(), "Clear Debt");
            stuffToTransButtons.add(transact);
        }
        stuffToTransButtons.add(Button.danger("newTransact_SendDebt_" + p1.getFaction() + "_" + p2.getFaction(), "Send Debt"));
        if (p1.getCommodities() > 0 && !p1.hasAbility("military_industrial_complex")) {
            Button transact = Button.success("newTransact_Comms_" + p1.getFaction() + "_" + p2.getFaction(), "Commodities");
            stuffToTransButtons.add(transact);
        }

        if (p1 == player && !game.isFoWMode() && (p1.getCommodities() > 0 || p2.getCommodities() > 0) && !p1.hasAbility("military_industrial_complex")
            && !p1.getAllianceMembers().contains(p2.getFaction())) {
            Button transact = Button.secondary("send_WashComms_" + p2.getFaction() + "_0",
                "Wash Both Players Comms");
            stuffToTransButtons.add(transact);
        }
        if (getPlayersShipOrders(p1).size() > 0) {
            Button transact = Button.secondary("newTransact_shipOrders_" + p1.getFaction() + "_" + p2.getFaction(), "Axis Orders");
            stuffToTransButtons.add(transact);
        }
        if (getNumberOfStarCharts(p1) > 0) {
            Button transact = Button.secondary("newTransact_starCharts_" + p1.getFaction() + "_" + p2.getFaction(), "Star Charts");
            stuffToTransButtons.add(transact);
        }
        if ((p1.hasAbility("arbiters") || p2.hasAbility("arbiters")) && p1.getAc() > 0) {
            Button transact = Button.success("newTransact_ACs_" + p1.getFaction() + "_" + p2.getFaction(), "Action Cards");
            stuffToTransButtons.add(transact);
        }
        if (p1.getPnCount() > 0) {
            Button transact = Button.success("newTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction(), "Promissory Notes");
            stuffToTransButtons.add(transact);
        }
        if (p1.getFragments().size() > 0) {
            Button transact = Button.success("newTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction(), "Fragments");
            stuffToTransButtons.add(transact);
        }
        if (ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, game).size() > 0) {
            Button transact = Button.success("newTransact_Planets_" + p1.getFaction() + "_" + p2.getFaction(), "Planets")
                .withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("hacan")));
            stuffToTransButtons.add(transact);
        }
        if (getTradePlanetsWithAlliancePartnerButtons(p1, p2, game).size() > 0) {
            Button transact = Button
                .success("newTransact_AlliancePlanets_" + p1.getFaction() + "_" + p2.getFaction(), "Alliance Planets")
                .withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord(p2.getFaction())));
            stuffToTransButtons.add(transact);
        }
        if (game.getCurrentPhase().toLowerCase().contains("agenda")
            && !"no".equalsIgnoreCase(playerHasDMZPlanet(p1, game))) {
            Button transact = Button.secondary(
                "offerToTransact_dmz_" + p1.getFaction() + "_" + p2.getFaction() + "_" + playerHasDMZPlanet(p1, game),
                "Trade " + Mapper.getPlanet(playerHasDMZPlanet(p1, game)).getName() + " (DMZ)");
            stuffToTransButtons.add(transact);
        }

        if (player == p1) {
            stuffToTransButtons.add(Button.secondary("resetOffer_" + p2.getFaction(), "Reset Offer"));
            stuffToTransButtons.add(Button.danger("getNewTransaction_" + p2.getFaction() + "_" + p1.getFaction(), "Ask for Stuff"));
            stuffToTransButtons.add(Button.secondary("sendOffer_" + p2.getFaction(), "Send the Offer"));
        } else {
            stuffToTransButtons.add(Button.secondary("resetOffer_" + p1.getFaction(), "Reset Offer"));
            stuffToTransButtons.add(Button.danger("getNewTransaction_" + p2.getFaction() + "_" + p1.getFaction(), "Offer More"));
            stuffToTransButtons.add(Button.secondary("sendOffer_" + p1.getFaction(), "Send the Offer"));
        }

        return stuffToTransButtons;
    }

    public static List<Button> getStuffToTransButtonsOld(Game game, Player p1, Player p2) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        List<Button> stuffToTransButtons = new ArrayList<>();
        if (p1.getTg() > 0) {
            Button transact = Button.success(finChecker + "transact_TGs_" + p2.getFaction(), "TGs");
            stuffToTransButtons.add(transact);
        }
        if (p1.getDebtTokenCount(p2.getColor()) > 0) {
            Button transact = Button.primary(finChecker + "transact_ClearDebt_" + p2.getFaction(), "Clear Debt");
            stuffToTransButtons.add(transact);
        }
        stuffToTransButtons.add(Button.danger(finChecker + "transact_SendDebt_" + p2.getFaction(), "Send Debt"));
        if (p1.getCommodities() > 0 && !p1.hasAbility("military_industrial_complex")) {
            Button transact = Button.success(finChecker + "transact_Comms_" + p2.getFaction(), "Commodities");
            stuffToTransButtons.add(transact);
        }

        if (!game.isFoWMode() && (p1.getCommodities() > 0 || p2.getCommodities() > 0) && !p1.hasAbility("military_industrial_complex")
            && !p1.getAllianceMembers().contains(p2.getFaction())) {
            Button transact = Button.secondary(finChecker + "send_WashComms_" + p2.getFaction() + "_0",
                "Wash Both Players Comms");
            stuffToTransButtons.add(transact);
        }
        if (getPlayersShipOrders(p1).size() > 0) {
            Button transact = Button.secondary(finChecker + "transact_shipOrders_" + p2.getFaction(), "Axis Orders");
            stuffToTransButtons.add(transact);
        }
        if (getNumberOfStarCharts(p1) > 0) {
            Button transact = Button.secondary(finChecker + "transact_starCharts_" + p2.getFaction(), "Star Charts");
            stuffToTransButtons.add(transact);
        }
        if ((p1.hasAbility("arbiters") || p2.hasAbility("arbiters")) && p1.getAc() > 0) {
            Button transact = Button.success(finChecker + "transact_ACs_" + p2.getFaction(), "Action Cards");
            stuffToTransButtons.add(transact);
        }
        if (p1.getPnCount() > 0) {
            Button transact = Button.success(finChecker + "transact_PNs_" + p2.getFaction(), "Promissory Notes");
            stuffToTransButtons.add(transact);
        }
        if (p1.getFragments().size() > 0) {
            Button transact = Button.success(finChecker + "transact_Frags_" + p2.getFaction(), "Fragments");
            stuffToTransButtons.add(transact);
        }
        if (ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, game).size() > 0) {
            Button transact = Button.success(finChecker + "transact_Planets_" + p2.getFaction(), "Planets")
                .withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("hacan")));
            stuffToTransButtons.add(transact);
        }
        if (getTradePlanetsWithAlliancePartnerButtons(p1, p2, game).size() > 0) {
            Button transact = Button
                .success(finChecker + "transact_AlliancePlanets_" + p2.getFaction(), "Alliance Planets")
                .withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord(p2.getFaction())));
            stuffToTransButtons.add(transact);
        }
        if (game.getCurrentPhase().toLowerCase().contains("agenda")
            && !"no".equalsIgnoreCase(playerHasDMZPlanet(p1, game))) {
            Button transact = Button.secondary(
                finChecker + "resolveDMZTrade_" + playerHasDMZPlanet(p1, game) + "_" + p2.getFaction(),
                "Trade " + Mapper.getPlanet(playerHasDMZPlanet(p1, game)).getName() + " (DMZ)");
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }

    public static void resolveSetAFKTime(Game gameOG, Player player, String buttonID, ButtonInteractionEvent event) {
        String time = buttonID.split("_")[1];
        player.addHourThatIsAFK(time);
        deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            getIdent(player) + " Set hour " + time + " as a time that you are afk");
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        String afkTimes = "" + player.getHoursThatPlayerIsAFK();
        for (Game game : mapList.values()) {
            if (!game.isHasEnded()) {
                for (Player player2 : game.getRealPlayers()) {
                    if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                        player2.setHoursThatPlayerIsAFK(afkTimes);
                        GameSaveLoadManager.saveMap(game, player2.getUserName() + " Updated Player Settings");
                    }
                }
            }
        }
    }

    public static void offerAFKTimeOptions(Game game, Player player) {
        List<Button> buttons = getSetAFKButtons(game);
        player.setHoursThatPlayerIsAFK("");
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true)
            + " your afk times (if any) have been reset. Use buttons to select the hours (note they are in UTC) in which you're afk. If you select 8 for example, you will be set as AFK from 8:00 UTC to 8:59 UTC in every game you are in.",
            buttons);
    }

    public static void offerPersonalPingOptions(Game game, Player player) {
        List<Button> buttons = getPersonalAutoPingButtons(game);
        player.setHoursThatPlayerIsAFK("");
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true)
            + " select the number of hours you would like the bot to wait before it pings you that it is your turn. This will apply to all your games. 0 is off. Your current interval is "
            + player.getPersonalPingInterval(),
            buttons);
    }

    public static void offerDirectHitManagementOptions(Game game, Player player) {
        List<Button> buttons = getDirectHitManagementButtons(game, player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true)
            + " select the units you would like to either risk or not risk direct hit. Dread 2s will automatically risk direct hits.  ",
            buttons);
    }

    public static void resolveSpecificTransButtonsNew(Game game, Player player, String buttonID,
        ButtonInteractionEvent event) {

        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("newTransact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String senderFaction = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        Player p1 = game.getPlayerFromColorOrFaction(senderFaction);
        if (p2 == null) {
            return;
        }
        boolean requesting = (p1 != player);
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message = "Current Transaction Offer is: " + Helper.buildTransactionOffer(player, opposing, game) + "\n";
        String requestOrOffer = "offer";
        if (requesting) {
            requestOrOffer = "request";
        }
        switch (thingToTrans) {
            case "TGs" -> {
                message = message + "Click the amount of tgs you would like to " + requestOrOffer;
                for (int x = 1; x < p1.getTg() + 1; x++) {
                    Button transact = Button.success("offerToTransact_TGs_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
            }
            case "Comms" -> {
                message = message + "Click the amount of commodities you would like to " + requestOrOffer;
                for (int x = 1; x < p1.getCommodities() + 1; x++) {
                    Button transact = Button.success("offerToTransact_Comms_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }

            }
            case "ClearDebt" -> {
                message = message + "Click the amount of debt you would like to " + requestOrOffer + " cleared";
                for (int x = 1; x < p1.getDebtTokenCount(p2.getColor()) + 1; x++) {
                    Button transact = Button.success("offerToTransact_ClearDebt_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x,
                        "" + x);
                    stuffToTransButtons.add(transact);
                }

            }
            case "SendDebt" -> {
                message = message + "Click the amount of debt you would like to " + requestOrOffer;
                for (int x = 1; x < 6; x++) {
                    Button transact = Button.success("offerToTransact_SendDebt_" + p1.getFaction() + "_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }

            }
            case "shipOrders" -> {
                message = message + "Click the axis order you would like to " + requestOrOffer;
                for (String shipOrder : getPlayersShipOrders(p1)) {
                    Button transact = Button.success(
                        "offerToTransact_shipOrders_" + p1.getFaction() + "_" + p2.getFaction() + "_" + shipOrder,
                        "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }

            }
            case "starCharts" -> {
                message = message + "Click the star chart you would like to " + requestOrOffer;
                for (String shipOrder : getPlayersStarCharts(p1)) {
                    Button transact = Button.success(
                        "offerToTransact_starCharts_" + p1.getFaction() + "_" + p2.getFaction() + "_" + shipOrder,
                        "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
            }
            case "Planets" -> {
                message = message + "Click the planet you would like to " + requestOrOffer;
                for (String planet : p1.getPlanetsAllianceMode()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    if (ButtonHelper.getUnitHolderFromPlanetName(planet, game).getUnitCount(UnitType.Mech,
                        p1.getColor()) > 0) {
                        stuffToTransButtons.add(Button.secondary("offerToTransact_Planets_" + p1.getFaction() + "_" + p2.getFaction() + "_" + planet,
                            Helper.getPlanetRepresentation(planet, game)));
                    }
                }

            }
            case "AlliancePlanets" -> {
                message = message + "Click the planet you would like to " + requestOrOffer;
                for (String planet : p1.getPlanets()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    UnitHolder unitHolder = getUnitHolderFromPlanetName(planet, game);
                    if (unitHolder != null && unitHolder.getUnitColorsOnHolder().contains(p2.getColorID())) {
                        String refreshed = "refreshed";
                        if (p1.getExhaustedPlanets().contains(planet)) {
                            refreshed = "exhausted";
                        }
                        stuffToTransButtons.add(Button.secondary(
                            "offerToTransact_AlliancePlanets_" + p1.getFaction() + "_" + p2.getFaction() + "_" + planet + refreshed,
                            Helper.getPlanetRepresentation(planet, game)));
                    }
                }
            }
            case "ACs" -> {
                if (requesting) {
                    message = message + player.getRepresentation()
                        + " Click the number of ACs youd like to request. Since ACs are private info, you will have to use messages to explain what ACs you want, these buttons will just make sure that the player is offered buttons to send.";
                    int limit = Math.min(7, p2.getAc());
                    for (int x = 1; x < limit + 1; x++) {
                        Button transact = Button.success(
                            "offerToTransact_ACs_" + p1.getFaction() + "_" + p2.getFaction() + "_generic" + x,
                            x + " ACs");
                        stuffToTransButtons.add(transact);
                    }
                } else {
                    message = message + player.getRepresentation()
                        + " Click the GREEN button that indicates the AC you would like to " + requestOrOffer;
                    for (String acShortHand : p1.getActionCards().keySet()) {
                        Button transact = Button.success(
                            "offerToTransact_ACs_" + p1.getFaction() + "_" + p2.getFaction() + "_" + p1.getActionCards().get(acShortHand),
                            Mapper.getActionCard(acShortHand).getName());
                        stuffToTransButtons.add(transact);
                    }
                }
            }
            case "PNs" -> {
                if (requesting) {
                    message = message + player.getRepresentation()
                        + " Click the PN youd like to request. Since PNs are private info, all of the player's starting PNs which are not in play areas are available, though the player may not currently hold all of these. Click TBD Note if you want someone elses PN, and it will give the player the option to send it.";
                    for (String pnShortHand : p1.getPromissoryNotesOwned()) {
                        if (anyoneHaveInPlayArea(game, pnShortHand)) {
                            continue;
                        }
                        PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                        Player owner = game.getPNOwner(pnShortHand);
                        if (p1.getPromissoryNotes().containsKey(pnShortHand)) {
                            stuffToTransButtons.add(Button
                                .success("offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                    + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName())
                                .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji())));
                        } else {
                            stuffToTransButtons.add(Button
                                .success("offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                    + pnShortHand.replace("_", "fin9"), promissoryNote.getName())
                                .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji())));
                        }

                    }
                    Button transact = Button
                        .primary("offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                            + "generic1", "TBD PN");

                    stuffToTransButtons.add(transact);
                } else {
                    message = message + p1.getRepresentation(true, true) + " Click the PN you would like to " + requestOrOffer;
                    for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                        if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)
                            || (p2.getAbilities().contains("hubris") && pnShortHand.endsWith("an"))) {
                            continue;
                        }
                        PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                        Player owner = game.getPNOwner(pnShortHand);
                        Button transact = Button
                            .success("offerToTransact_PNs_" + p1.getFaction() + "_" + p2.getFaction() + "_"
                                + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName())
                            .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));

                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "Reminder that, unlike other things, you can only send a person 1 PN in a transaction.");
            }
            case "Frags" -> {
                message = message + "Click the amount of fragments you would like to " + requestOrOffer;
                for (int x = 1; x < p1.getCrf() + 1; x++) {
                    Button transact = Button.primary("offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction() + "_CRF" + x,
                        "Cultural Fragments (" + x + ")");
                    stuffToTransButtons.add(transact);
                }

                for (int x = 1; x < p1.getIrf() + 1; x++) {
                    Button transact = Button.success("offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction() + "_IRF" + x,
                        "Industrial Fragments (" + x + ")");
                    stuffToTransButtons.add(transact);
                }

                for (int x = 1; x < p1.getHrf() + 1; x++) {
                    Button transact = Button.danger("offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction() + "_HRF" + x,
                        "Hazardous Fragments (" + x + ")");
                    stuffToTransButtons.add(transact);
                }

                for (int x = 1; x < p1.getUrf() + 1; x++) {
                    Button transact = Button.secondary("offerToTransact_Frags_" + p1.getFaction() + "_" + p2.getFaction() + "_URF" + x,
                        "Frontier Fragments (" + x + ")");
                    stuffToTransButtons.add(transact);
                }

            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, stuffToTransButtons);

    }

    public static void resolveOfferToTransact(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String item = buttonID.split("_")[1];
        String sender = buttonID.split("_")[2];
        String receiver = buttonID.split("_")[3];
        String extraDetail = buttonID.split("_")[4];
        player.addTransactionItem("sending" + sender + "_receiving" + receiver + "_" + item + "_" + extraDetail);
        Player p1 = game.getPlayerFromColorOrFaction(sender);
        Player p2 = game.getPlayerFromColorOrFaction(receiver);
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message = "Current Transaction Offer is: " + Helper.buildTransactionOffer(player, opposing, game) + "\n Click something else for " + p1.getRepresentation(false, false) + " to offer";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message, ButtonHelper.getStuffToTransButtonsNew(game, player, p1, p2));
    }

    public static void getNewTransaction(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String sender = buttonID.split("_")[1];
        String receiver = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(sender);
        Player p2 = game.getPlayerFromColorOrFaction(receiver);
        Player opposing = p2;
        if (player == p2) {
            opposing = p1;
        }
        String message = "Current Transaction Offer is: " + Helper.buildTransactionOffer(player, opposing, game) + "\n Click something for " + p1.getRepresentation(false, false) + " to offer";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message, ButtonHelper.getStuffToTransButtonsNew(game, player, p1, p2));
    }

    public static void sendOffer(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " sent " + p2.getFactionEmoji() + " a transaction offer");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.danger("rescindOffer_" + p2.getFaction(), "Rescind Offer"));
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Successfully sent " + p2.getFactionEmoji() + " a transaction offer. You can use this button to rescind the offer", buttons);
        event.getMessage().delete().queue();
        buttons = new ArrayList<>();
        buttons.add(Button.success("acceptOffer_" + player.getFaction(), "Accept"));
        buttons.add(Button.danger("rejectOffer_" + player.getFaction(), "Reject"));
        buttons.add(Button.danger("resetOffer_" + player.getFaction(), "Reject and CounterOffer"));
        MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), p2.getRepresentation() + " you have received a transaction offer. The offer is: " + Helper.buildTransactionOffer(player, p2, game) + "\n Click to accept, reject, or counteroffer", buttons);
    }

    public static void resolveSpecificTransButtonsOld(Game game, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";

        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("transact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String factionToTrans = buttonID.substring(buttonID.indexOf("_") + 1);
        Player p2 = game.getPlayerFromColorOrFaction(factionToTrans);
        if (p2 == null) {
            return;
        }

        switch (thingToTrans) {
            case "TGs" -> {
                String message = "Click the amount of tgs you would like to send";
                for (int x = 1; x < p1.getTg() + 1; x++) {
                    Button transact = Button.success(finChecker + "send_TGs_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Comms" -> {
                String message = "Click the amount of commodities you would like to send";
                for (int x = 1; x < p1.getCommodities() + 1; x++) {
                    Button transact = Button.success(finChecker + "send_Comms_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "ClearDebt" -> {
                String message = "Click the amount of debt you would like to clear";
                for (int x = 1; x < p1.getDebtTokenCount(p2.getColor()) + 1; x++) {
                    Button transact = Button.success(finChecker + "send_ClearDebt_" + p2.getFaction() + "_" + x,
                        "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "SendDebt" -> {
                String message = "Click the amount of debt you would like to send";
                for (int x = 1; x < 6; x++) {
                    Button transact = Button.success(finChecker + "send_SendDebt_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "shipOrders" -> {
                String message = "Click the axis order you would like to send";
                for (String shipOrder : getPlayersShipOrders(p1)) {
                    Button transact = Button.success(
                        finChecker + "send_shipOrders_" + p2.getFaction() + "_" + shipOrder,
                        "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "starCharts" -> {
                String message = "Click the star chart you would like to send";
                for (String shipOrder : getPlayersStarCharts(p1)) {
                    Button transact = Button.success(
                        finChecker + "send_starCharts_" + p2.getFaction() + "_" + shipOrder,
                        "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Planets" -> {
                String message = "Click the planet you would like to send";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message,
                    ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, game));
            }
            case "AlliancePlanets" -> {
                String message = "Click the planet you would like to send";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message,
                    getTradePlanetsWithAlliancePartnerButtons(p1, p2, game));
            }
            case "ACs" -> {
                String message = p1.getRepresentation()
                    + " Click the GREEN button that indicates the AC you would like to send";
                for (String acShortHand : p1.getActionCards().keySet()) {
                    Button transact = Button.success(
                        finChecker + "send_ACs_" + p2.getFaction() + "_" + p1.getActionCards().get(acShortHand),
                        Mapper.getActionCard(acShortHand).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
            }
            case "PNs" -> {
                PNInfo.sendPromissoryNoteInfo(game, p1, false);
                String message = p1.getRepresentation(true, true) + " Click the PN you would like to send.";

                for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                    if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)
                        || (p2.getAbilities().contains("hubris") && pnShortHand.endsWith("an"))) {
                        continue;
                    }
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
                    Player owner = game.getPNOwner(pnShortHand);
                    Button transact;
                    if (game.isFoWMode()) {
                        transact = Button.success(
                            finChecker + "send_PNs_" + p2.getFaction() + "_"
                                + p1.getPromissoryNotes().get(pnShortHand),
                            owner.getColor() + " " + promissoryNote.getName());
                    } else {
                        transact = Button
                            .success(finChecker + "send_PNs_" + p2.getFaction() + "_"
                                + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName())
                            .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
                    }
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
                MessageHelper.sendMessageToChannel(p1.getCardsInfoThread(),
                    "Reminder that, unlike other things, you can only send a person 1 PN in a transaction.");
            }
            case "Frags" -> {
                String message = "Click the amount of fragments you would like to send";

                if (p1.getCrf() > 0) {
                    for (int x = 1; x < p1.getCrf() + 1; x++) {
                        Button transact = Button.primary(finChecker + "send_Frags_" + p2.getFaction() + "_CRF" + x,
                            "Cultural Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if (p1.getIrf() > 0) {
                    for (int x = 1; x < p1.getIrf() + 1; x++) {
                        Button transact = Button.success(finChecker + "send_Frags_" + p2.getFaction() + "_IRF" + x,
                            "Industrial Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if (p1.getHrf() > 0) {
                    for (int x = 1; x < p1.getHrf() + 1; x++) {
                        Button transact = Button.danger(finChecker + "send_Frags_" + p2.getFaction() + "_HRF" + x,
                            "Hazardous Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }

                if (p1.getUrf() > 0) {
                    for (int x = 1; x < p1.getUrf() + 1; x++) {
                        Button transact = Button.secondary(finChecker + "send_Frags_" + p2.getFaction() + "_URF" + x,
                            "Frontier Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);

            }
        }

    }

    public static void resolveSpecificTransButtonPress(Game game, Player p1, String buttonID,
        ButtonInteractionEvent event, boolean oldWay) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        buttonID = buttonID.replace("send_", "");
        List<Button> goAgainButtons = new ArrayList<>();

        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(thingToTrans + "_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String amountToTrans = buttonID.substring(buttonID.indexOf("_") + 1);
        Player p2 = game.getPlayerFromColorOrFaction(factionToTrans);
        String message2 = "";
        String ident = p1.getRepresentation();
        String ident2 = p2.getRepresentation();
        switch (thingToTrans) {
            case "TGs" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setTg(p1.getTg() - tgAmount);
                p2.setTg(p2.getTg() + tgAmount);
                ButtonHelper.fullCommanderUnlockCheck(p2, game, "hacan", event);
                message2 = ident + " sent " + tgAmount + " TGs to " + ident2;
                if (!p2.hasAbility("binding_debts") && p2.getDebtTokenCount(p1.getColor()) > 0) {
                    int amount = Math.min(tgAmount, p2.getDebtTokenCount(p1.getColor()));
                    ClearDebt.clearDebt(p2, p1, amount);
                    message2 = message2 + "\n" + ident2 + " cleared " + amount + " debt tokens owned by " + ident;
                }
            }
            case "Comms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setCommodities(p1.getCommodities() - tgAmount);
                if (!p1.isPlayerMemberOfAlliance(p2)) {
                    int targetTG = p2.getTg();
                    targetTG += tgAmount;
                    p2.setTg(targetTG);
                } else {
                    int targetTG = p2.getCommodities();
                    targetTG += tgAmount;
                    if (targetTG > p2.getCommoditiesTotal()) {
                        targetTG = p2.getCommoditiesTotal();
                    }
                    p2.setCommodities(targetTG);
                }

                ButtonHelper.fullCommanderUnlockCheck(p2, game, "hacan", event);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p1, p2, tgAmount);
                ButtonHelperAbilities.pillageCheck(p1, game);
                ButtonHelperAbilities.pillageCheck(p2, game);
                message2 = ident + " sent " + tgAmount + " Commodities to " + ident2;
                if (!p2.hasAbility("binding_debts") && p2.getDebtTokenCount(p1.getColor()) > 0) {
                    int amount = Math.min(tgAmount, p2.getDebtTokenCount(p1.getColor()));
                    ClearDebt.clearDebt(p2, p1, amount);
                    message2 = message2 + "\n" + ident2 + " cleared " + amount + " debt tokens owned by " + ident;
                }

            }
            case "WashComms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                int oldP1Tg = p1.getTg();
                int oldP2tg = p2.getTg();
                int oldP1Comms = p1.getCommodities();
                int newP1Comms = 0;
                int totalWashPowerP1 = p1.getCommodities() + p1.getTg();
                int totalWashPowerP2 = p2.getCommodities() + p2.getTg();
                if (oldP1Comms > totalWashPowerP2) {
                    newP1Comms = oldP1Comms - totalWashPowerP2;

                }
                int oldP2Comms = p2.getCommodities();
                int newP2Comms = 0;
                if (oldP2Comms > totalWashPowerP1) {
                    newP2Comms = oldP2Comms - totalWashPowerP1;
                }
                p1.setCommodities(newP1Comms);
                p2.setCommodities(newP2Comms);
                p1.setTg(p1.getTg() + (oldP1Comms - newP1Comms));
                p2.setTg(p2.getTg() + (oldP2Comms - newP2Comms));
                ButtonHelper.fullCommanderUnlockCheck(p2, game, "hacan", event);
                ButtonHelper.fullCommanderUnlockCheck(p1, game, "hacan", event);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p1, p2, oldP1Comms);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p2, p1, oldP2Comms);
                // ButtonHelperAbilities.pillageCheck(p1, game);
                // ButtonHelperAbilities.pillageCheck(p2, game);
                String id1 = getIdentOrColor(p1, game);
                String id2 = getIdentOrColor(p2, game);
                message2 = ident + " washed their " + (oldP1Comms - newP1Comms) + " Commodities with " + ident2 + "  ("
                    + id1 + " tg went from (" + oldP1Tg + "->" + p1.getTg() + "))\n" + id2
                    + " washed their " + (oldP2Comms - newP2Comms) + " Commodities with " + id1 + " (" + id2
                    + " tg went from (" + oldP2tg + "->" + p2.getTg() + "))";
            }
            case "shipOrders" -> {
                message2 = ident + " sent " + Mapper.getRelic(amountToTrans).getName() + " to " + ident2;
                p1.removeRelic(amountToTrans);
                p2.addRelic(amountToTrans);
            }
            case "SendDebt" -> {
                message2 = ident + " sent " + amountToTrans + " debt tokens to " + ident2;
                p2.addDebtTokens(p1.getColor(), Integer.parseInt(amountToTrans));
                fullCommanderUnlockCheck(p2, game, "vaden", event);
            }
            case "ClearDebt" -> {
                message2 = ident + " cleared " + amountToTrans + " debt tokens of " + ident2;
                p1.removeDebtTokens(p2.getColor(), Integer.parseInt(amountToTrans));
            }
            case "starCharts" -> {
                message2 = ident + " sent " + Mapper.getRelic(amountToTrans).getName() + " to " + ident2;
                p1.removeRelic(amountToTrans);
                p2.addRelic(amountToTrans);
            }
            case "ACs" -> {

                message2 = ident + " sent AC #" + amountToTrans + " to " + ident2;
                int acNum = Integer.parseInt(amountToTrans);
                String acID = null;
                if (!p1.getActionCards().containsValue(acNum)) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC sent");
                    return;
                }
                for (Map.Entry<String, Integer> ac : p1.getActionCards().entrySet()) {
                    if (ac.getValue().equals(acNum)) {
                        acID = ac.getKey();
                    }
                }
                p1.removeActionCard(acNum);
                p2.setActionCard(acID);
                ACInfo.sendActionCardInfo(game, p2);
                ACInfo.sendActionCardInfo(game, p1);
                if (!p1.hasAbility("arbiters") && !p2.hasAbility("arbiters")) {
                    if (game.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
                        MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
                    } else {
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
                    }
                    return;
                }
            }
            case "PNs" -> {
                String id = null;
                int pnIndex;
                pnIndex = Integer.parseInt(amountToTrans);
                for (Map.Entry<String, Integer> pn : p1.getPromissoryNotes().entrySet()) {
                    if (pn.getValue().equals(pnIndex)) {
                        id = pn.getKey();
                    }
                }
                if (id == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that PN, no PN sent");
                    return;
                }
                p1.removePromissoryNote(id);
                p2.setPromissoryNote(id);
                if (id.contains("dspnveld")) {
                    resolvePNPlay(id, p2, game, event);
                }
                boolean sendSftT = false;
                boolean sendAlliance = false;
                String promissoryNoteOwner = Mapper.getPromissoryNote(id).getOwner();
                if ((id.endsWith("_sftt") || id.endsWith("_an")) && !promissoryNoteOwner.equals(p2.getFaction())
                    && !promissoryNoteOwner.equals(p2.getColor())
                    && !p2.isPlayerMemberOfAlliance(game.getPlayerFromColorOrFaction(promissoryNoteOwner))) {
                    p2.setPromissoryNotesInPlayArea(id);
                    if (id.endsWith("_sftt")) {
                        sendSftT = true;
                    } else {
                        sendAlliance = true;
                        if (game.getPNOwner(id).hasLeaderUnlocked("bentorcommander")) {
                            p2.setCommoditiesTotal(p2.getCommodities() + 1);
                        }
                    }
                }
                PNInfo.sendPromissoryNoteInfo(game, p1, false);
                CardsInfo.sendVariousAdditionalButtons(game, p1);
                PNInfo.sendPromissoryNoteInfo(game, p2, false);
                CardsInfo.sendVariousAdditionalButtons(game, p2);
                String text = sendSftT ? "**Support for the Throne** " : (sendAlliance ? "**Alliance** " : "");
                message2 = p1.getRepresentation() + " sent " + Emojis.PN + text + "PN to " + ident2;
                Helper.checkEndGame(game, p2);
            }
            case "Frags" -> {

                String fragType = amountToTrans.substring(0, 3).toUpperCase();
                int fragNum = Integer.parseInt(amountToTrans.charAt(3) + "");
                String trait = switch (fragType) {
                    case "CRF" -> "cultural";
                    case "HRF" -> "hazardous";
                    case "IRF" -> "industrial";
                    case "URF" -> "frontier";
                    default -> "";
                };
                new SendFragments().sendFrags(event, p1, p2, trait, fragNum, game);
                message2 = "";
            }
        }
        Button button = Button.secondary(finChecker + "transactWith_" + p2.getColor(),
            "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");

        goAgainButtons.add(button);
        goAgainButtons.add(Button.success("demandSomething_" + p2.getColor(), "Expect something in return"));
        goAgainButtons.add(done);
        if (game.isFoWMode()) {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            if (oldWay) {
                MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
            if (oldWay) {
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
        }
    }

    public static List<Button> getSetAFKButtons(Game game) {
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 24; x++) {
            buttons.add(Button.secondary("setHourAsAFK_" + x, "" + x));
        }
        buttons.add(Button.danger("deleteButtons", "Done"));
        return buttons;
    }

    public static boolean anyoneHaveInPlayArea(Game game, String pnID) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPromissoryNotesInPlayArea().contains(pnID)) {
                return true;
            }
        }
        return false;
    }

    public static List<Button> getPersonalAutoPingButtons(Game game) {
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 13; x++) {
            buttons.add(Button.secondary("setPersonalAutoPingInterval_" + x, "" + x));
        }
        buttons.add(Button.secondary("setPersonalAutoPingInterval_" + 24, "" + 24));
        buttons.add(Button.secondary("setPersonalAutoPingInterval_" + 48, "" + 48));
        return buttons;
    }

    public static List<Button> getDirectHitManagementButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String stuffNotToSustain = game
            .getStoredValue("stuffNotToSustainFor" + player.getFaction());
        if (stuffNotToSustain.isEmpty()) {
            game.setStoredValue("stuffNotToSustainFor" + player.getFaction(), "warsun");
            stuffNotToSustain = "warsun";
        }
        String unit = "warsun";
        if (stuffNotToSustain.contains(unit)) {
            buttons.add(Button.danger("riskDirectHit_" + unit + "_yes", "Risk " + StringUtils.capitalize(unit)));
        } else {
            buttons.add(Button.success("riskDirectHit_" + unit + "_no", "Don't Risk " + StringUtils.capitalize(unit)));
        }
        unit = "flagship";
        if (stuffNotToSustain.contains(unit)) {
            buttons.add(Button.danger("riskDirectHit_" + unit + "_yes", "Risk " + StringUtils.capitalize(unit)));
        } else {
            buttons.add(Button.success("riskDirectHit_" + unit + "_no", "Don't Risk " + StringUtils.capitalize(unit)));
        }
        unit = "dreadnought";
        if (stuffNotToSustain.contains(unit)) {
            buttons.add(Button.danger("riskDirectHit_" + unit + "_yes", "Risk " + StringUtils.capitalize(unit)));
        } else {
            buttons.add(Button.success("riskDirectHit_" + unit + "_no", "Don't Risk " + StringUtils.capitalize(unit)));
        }
        unit = "cruiser";
        if (player.hasTech("se2")) {
            if (stuffNotToSustain.contains(unit)) {
                buttons.add(Button.danger("riskDirectHit_" + unit + "_yes", "Risk " + StringUtils.capitalize(unit)));
            } else {
                buttons.add(
                    Button.success("riskDirectHit_" + unit + "_no", "Don't Risk " + StringUtils.capitalize(unit)));
            }
        }
        buttons.add(Button.secondary("deleteButtons", "Done"));
        return buttons;
    }

    public static void resolveRiskDirectHit(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String yesOrNo = buttonID.split("_")[2];
        String unit = buttonID.split("_")[1];
        String stuffNotToSustain = game
            .getStoredValue("stuffNotToSustainFor" + player.getFaction());
        if ("yes".equalsIgnoreCase(yesOrNo)) {
            stuffNotToSustain = stuffNotToSustain.replace(unit, "");
        } else {
            stuffNotToSustain = stuffNotToSustain + unit;
        }
        if (stuffNotToSustain.isEmpty()) {
            stuffNotToSustain = "none";
        }
        game.setStoredValue("stuffNotToSustainFor" + player.getFaction(), stuffNotToSustain);
        List<Button> systemButtons = getDirectHitManagementButtons(game, player);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(turnButtonListIntoActionRowList(systemButtons)).queue();
    }

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

            if (techText.contains("ACTION") || (tech.equalsIgnoreCase("det") && game.isAgeOfExplorationMode())) {
                if ("lgf".equals(tech) && !p1.controlsMecatol(false)) {
                    continue;
                }
                Button tButton = Button.danger(finChecker + "exhaustTech_" + tech, "Exhaust " + techName)
                    .withEmoji(Emoji.fromFormatted(techEmoji));
                compButtons.add(tButton);
            }
        }
        if (getNumberOfStarCharts(p1) > 1) {
            Button tButton = Button.danger(finChecker + prefix + "doStarCharts_", "Purge 2 Starcharts ");
            compButtons.add(tButton);
        }

        // Legendary Planets
        List<String> implementedLegendaryPlanets = List.of("prism");
        for (String planet : implementedLegendaryPlanets) {
            String prettyPlanet = Mapper.getPlanet(planet).getName();
            if (p1.getPlanets().contains(planet) && !p1.getExhaustedPlanetsAbilities().contains(planet)) {
                compButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use " + prettyPlanet + " Ability"));
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
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + led,
                                    "Use " + leaderName + " as Umbat (Muaat Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "naaluagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + led,
                                    "Use " + leaderName + " as Z'eu (Naalu Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "arborecagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + led,
                                    "Use " + leaderName + " as Letani Ospha (Arborec Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "bentoragent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + led,
                                    "Use " + leaderName + " as C.O.O. Mgur (Bentor Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "kolumeagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + led,
                                    "Use " + leaderName + " as Disciple Fran (Kolume Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }

                        led = "axisagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + led,
                                    "Use " + leaderName + " as Shipmonger Zsknck (Axis Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "xxchaagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button
                                .secondary(finChecker + prefix + "leader_" + led,
                                    "Use " + leaderName + " as Ggrocuto Rinn (Xxcha Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "yssarilagent";
                        Button lButton = Button
                            .secondary(finChecker + prefix + "leader_" + led,
                                "Use " + leaderName + " as Unimplemented Component Agent")
                            .withEmoji(Emoji.fromFormatted(factionEmoji));
                        compButtons.add(lButton);
                        if (ButtonHelperFactionSpecific.doesAnyoneElseHaveJr(game, p1)) {
                            Button jrButton = Button
                                .secondary(finChecker + "yssarilAgentAsJr",
                                    "Use " + leaderName + " JR-XS455-O (Relic Agent)")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(jrButton);
                        }

                    } else {
                        Button lButton = Button
                            .secondary(finChecker + prefix + "leader_" + leaderID, "Use " + leaderName)
                            .withEmoji(Emoji.fromFormatted(factionEmoji));
                        compButtons.add(lButton);
                    }

                } else if ("mahactcommander".equalsIgnoreCase(leaderID) && p1.getTacticalCC() > 0
                    && getTilesWithYourCC(p1, game, event).size() > 0) {
                    Button lButton = Button.secondary(finChecker + "mahactCommander", "Use " + leaderName)
                        .withEmoji(Emoji.fromFormatted(factionEmoji));
                    compButtons.add(lButton);
                }
            }
        }

        // Relics
        boolean dontEnigTwice = true;
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
                    if (!dontEnigTwice) {
                        continue;
                    }
                    rButton = Button.danger(finChecker + prefix + "relic_" + relic, "Purge Enigmatic Device");
                    dontEnigTwice = false;
                } else {
                    if ("titanprototype".equalsIgnoreCase(relic) || "absol_jr".equalsIgnoreCase(relic)) {
                        if (!p1.getExhaustedRelics().contains(relic)) {
                            rButton = Button.primary(finChecker + prefix + "relic_" + relic,
                                "Exhaust " + relicData.getName());
                        } else {
                            continue;
                        }

                    } else {
                        rButton = Button.danger(finChecker + prefix + "relic_" + relic, "Purge " + relicData.getName());
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
                    Button pnButton = Button.danger(finChecker + prefix + "pn_" + pn, "Use " + pnName);
                    compButtons.add(pnButton);
                }
            }
            if (prom == null) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(p1, game), p1.getRepresentation(true, true)
                    + " you have a null PN. Please use /pn purge after reporting it " + pn);
                PNInfo.sendPromissoryNoteInfo(game, p1, false);
            }
        }

        // Abilities
        if (p1.hasAbility("star_forge") && (p1.getStrategicCC() > 0 || p1.hasRelicReady("emelpar"))
            && getTilesOfPlayersSpecificUnits(game, p1, UnitType.Warsun).size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_starForge", "Starforge")
                .withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("meditation") && (p1.getStrategicCC() > 0 || p1.hasRelicReady("emelpar"))
            && p1.getExhaustedTechs().size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_meditation", "Meditation")
                .withEmoji(Emoji.fromFormatted(Emojis.kolume));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("orbital_drop") && p1.getStrategicCC() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_orbitalDrop", "Orbital Drop")
                .withEmoji(Emoji.fromFormatted(Emojis.Sol));
            compButtons.add(abilityButton);
        }
        if (p1.hasUnit("lanefir_mech") && p1.getFragments().size() > 0 && getNumberOfUnitsOnTheBoard(game, p1, "mech", true) < 4) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_lanefirMech", "Purge Frag For Mech")
                .withEmoji(Emoji.fromFormatted(Emojis.lanefir));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("mantle_cracking")
            && ButtonHelperAbilities.getMantleCrackingButtons(p1, game).size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_mantlecracking", "Mantle Crack")
                .withEmoji(Emoji.fromFormatted(Emojis.gledge));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("stall_tactics") && p1.getActionCards().size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_stallTactics", "Stall Tactics")
                .withEmoji(Emoji.fromFormatted(Emojis.Yssaril));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("fabrication") && p1.getFragments().size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_fabrication", "Purge 1 Frag for a CC")
                .withEmoji(Emoji.fromFormatted(Emojis.Naaz));
            compButtons.add(abilityButton);
        }

        // Other "abilities"
        if (p1.getUnitsOwned().contains("muaat_flagship") && p1.getStrategicCC() > 0
            && getTilesOfPlayersSpecificUnits(game, p1, UnitType.Flagship).size() > 0) {
            Button abilityButton = Button
                .success(finChecker + prefix + "ability_muaatFS", "Spend a Strat CC for a Cruiser with your FS")
                .withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            compButtons.add(abilityButton);
        }

        // Get Relic
        if (p1.enoughFragsForRelic()) {
            Button getRelicButton = Button.success(finChecker + prefix + "getRelic_", "Get Relic");
            if (p1.hasAbility("a_new_edifice")) {
                getRelicButton = Button.success(finChecker + prefix + "getRelic_", "Purge Frags to Explore");
            }
            compButtons.add(getRelicButton);
        }

        // ACs
        Button acButton = Button.secondary(finChecker + prefix + "actionCards_", "Play \"ACTION:\" AC");
        compButtons.add(acButton);

        // absol
        if (isPlayerElected(game, p1, "absol_minswar")
            && !game.getStoredValue("absolMOW").contains(p1.getFaction())) {
            Button absolButton = Button.secondary(finChecker + prefix + "absolMOW_", "Absol Minister of War Action");
            compButtons.add(absolButton);
        }

        // Generic
        Button genButton = Button.secondary(finChecker + prefix + "generic_", "Generic Component Action");
        compButtons.add(genButton);

        return compButtons;
    }

    public static void resolvePreAssignment(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String messageID = buttonID.split("_")[1];
        String msg = getIdent(player) + " successfully preset " + messageID;
        String part2 = player.getFaction();
        if (game.getStoredValue(messageID) != null
            && !game.getStoredValue(messageID).isEmpty()) {
            part2 = game.getStoredValue(messageID) + "_" + player.getFaction();
        }
        if (StringUtils.countMatches(buttonID, "_") > 1) {
            part2 = part2 + "_" + buttonID.split("_")[2];
            msg = msg + " on " + buttonID.split("_")[2];
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        game.setStoredValue(messageID, part2);
        deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.danger("removePreset_" + messageID, "Remove The Preset"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation() + " you can use this button to undo the preset. Ignore it otherwise",
            buttons);
        if ("Public Disgrace".equalsIgnoreCase(messageID)) {
            String msg2 = player.getRepresentation()
                + " Additionally, you can set Public Disgrace to only trigger on a particular person. Choose to do so if you wish, or decline, or ignore this";
            List<Button> buttons2 = new ArrayList<>();
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (!game.isFoWMode()) {
                    buttons2.add(Button.secondary("resolvePreassignment_Public Disgrace Only_" + p2.getFaction(),
                        p2.getFaction()));
                } else {
                    buttons2.add(Button.secondary("resolvePreassignment_Public Disgrace Only_" + p2.getFaction(),
                        p2.getColor()));
                }
            }
            buttons2.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons2);
        }
    }

    public static void resolveRemovalOfPreAssignment(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String messageID = buttonID.split("_")[1];
        String msg = getIdent(player) + " successfully removed the preset for " + messageID;
        String part2 = player.getFaction();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (game.getStoredValue(messageID) != null) {
            game.setStoredValue(messageID, game.getStoredValue(messageID).replace(part2, ""));
        }
        deleteMessage(event);
    }

    public static String mechOrInfCheck(String planetName, Game game, Player player) {
        String message;
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;
        int numInf = 0;
        String colorID = Mapper.getColorID(player.getColor());
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        UnitKey infKey = Mapper.getUnitKey("gf", colorID);
        if (unitHolder.getUnits() != null) {

            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
            if (unitHolder.getUnits().get(infKey) != null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        if (numMechs > 0 || numInf > 0) {
            if (numMechs > 0) {
                message = "Planet had a mech. ";
            } else {
                message = "Planet did not have a mech. Removed 1 infantry (" + numInf + "->" + (numInf - 1) + "). ";
                tile.removeUnit(planetName, infKey, 1);
            }
        } else {
            message = "Planet did not have a mech or infantry. Please try again.";
        }
        return message;
    }

    public static void addReaction(ButtonInteractionEvent event, boolean skipReaction, boolean sendPublic, String message, String additionalMessage) {
        if (event == null)
            return;

        String userID = event.getUser().getId();
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        Player player = Helper.getGamePlayer(game, null, event.getMember(), userID);
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

            ButtonListener.checkForAllReactions(event, game);
            if (message == null || message.isEmpty()) {
                return;
            }
        }

        String text = player.getRepresentation();
        if ("Not Following".equalsIgnoreCase(message))
            text = player.getRepresentation(false, false);
        text = text + " " + message;
        if (game.isFoWMode() && sendPublic) {
            text = message;
        } else if (game.isFoWMode() && !sendPublic) {
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
        }

        if (additionalMessage != null && !additionalMessage.isEmpty()) {
            text += game.getPing() + " " + additionalMessage;
        }

        if (game.isFoWMode() && !sendPublic) {
            MessageHelper.sendPrivateMessageToPlayer(player, game, text);
            return;
        }

        MessageHelper.sendMessageToChannel(Helper.getThreadChannelIfExists(event), text);
    }

    public static void addReaction(Player player, boolean skipReaction, boolean sendPublic, String message,
        String additionalMessage, String messageID, Game game) {
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
                    ButtonListener.checkForAllReactions(messageId, game);
                    if (message == null || message.isEmpty()) {
                        return;
                    }
                }

                String text = player.getRepresentation() + " " + message;
                if (game.isFoWMode() && sendPublic) {
                    text = message;
                } else if (game.isFoWMode() && !sendPublic) {
                    text = "(You) " + emojiToUse.getFormatted() + " " + message;
                }

                if (additionalMessage != null && !additionalMessage.isEmpty()) {
                    text += game.getPing() + " " + additionalMessage;
                }

                if (game.isFoWMode() && !sendPublic) {
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
        String youCanSpend;
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        StringBuilder youCanSpendBuilder = new StringBuilder("You have available to you to spend: ");
        for (String planet : planets) {
            youCanSpendBuilder.append(Helper.getPlanetRepresentation(planet, game)).append(", ");
        }
        youCanSpend = youCanSpendBuilder.toString();
        if (planets.isEmpty()) {
            youCanSpend = "You have available to you 0 unexhausted planets ";
        }
        if (!game.getCurrentPhase().contains("agenda")) {
            youCanSpend = youCanSpend + "and " + player.getTg() + " tgs";
        }

        return youCanSpend;
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

    public static void resolveDiploPrimary(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        if (type.toLowerCase().contains("mahact")) {
            String color2 = type.replace("mahact", "");
            Player mahactP = game.getPlayerFromColorOrFaction(color2);
            if (mahactP == null) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), "Could not find mahact player");
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
            String message = getIdent(player) + " chose to use the mahact PN in the tile " + tile.getRepresentation();
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), message);
        } else {
            if (!DiploSystem.diploSystem(event, game, player, planet.toLowerCase())) {
                return;
            }
            String message = getIdent(player) + " chose to diplo the system containing " + Helper.getPlanetRepresentation(planet, game);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        deleteMessage(event);
    }

    public static void acquireATech(Player player, Game game, ButtonInteractionEvent event, String messageID,
        boolean sc) {
        String finsFactionCheckerPrefix = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();
        if (sc) {
            game.setComponentAction(false);
            boolean used = ButtonListener.addUsedSCPlayer(messageID, game, player, event, "");
            StrategyCardModel scModel = game.getStrategyCardModelByName("technology").orElse(null);
            if (!used && scModel != null && scModel.usesAutomationForSCID("pok7technology") && !player.getFollowedSCs().contains(scModel.getInitiative())) {
                int scNum = scModel.getInitiative();
                player.addFollowedSC(scNum, event);
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Tech");
                }
                String message = ButtonListener.deductCC(player, event);
                addReaction(event, false, false, message, "");
            }
        } else {
            game.setComponentAction(true);
        }
        Button propulsionTech = Button.primary(finsFactionCheckerPrefix + "getAllTechOfType_propulsion", "Get a Blue Tech");
        propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
        buttons.add(propulsionTech);

        Button bioticTech = Button.success(finsFactionCheckerPrefix + "getAllTechOfType_biotic", "Get a Green Tech");
        bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
        buttons.add(bioticTech);

        Button cyberneticTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic", "Get a Yellow Tech");
        cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
        buttons.add(cyberneticTech);

        Button warfareTech = Button.danger(finsFactionCheckerPrefix + "getAllTechOfType_warfare", "Get a Red Tech");
        warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
        buttons.add(warfareTech);

        Button unitupgradesTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade", "Get A Unit Upgrade Tech");
        unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Emojis.UnitUpgradeTech));
        buttons.add(unitupgradesTech);
        ButtonHelperCommanders.yinCommanderSummary(player, game);
        String message = player.getRepresentation() + " What type of tech would you want?";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

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
                    List<String> leadersThatNeedSpecialSelection = List.of("naaluagent", "muaatagent", "kolumeagent",
                        "arborecagent", "bentoragent", "xxchaagent", "axisagent");
                    if (leadersThatNeedSpecialSelection.contains(buttonID)) {
                        List<Button> buttons = getButtonsForAgentSelection(game, buttonID);
                        String message = p1.getRepresentation(true, true)
                            + " Use buttons to select the user of the agent";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    } else {
                        ExhaustLeader.exhaustLeader(event, game, p1, p1.getLeader(buttonID).orElse(null), null);
                        if ("fogallianceagent".equalsIgnoreCase(buttonID)) {
                            ButtonHelperAgents.exhaustAgent("fogallianceagent", event, game, p1, p1.getFactionEmoji());
                        }
                    }
                } else if (buttonID.contains("hero")) {
                    HeroPlay.playHero(event, game, p1, p1.getLeader(buttonID).orElse(null));
                }
            }
            case "relic" -> resolveRelicComponentAction(game, p1, event, buttonID);
            case "pn" -> resolvePNPlay(buttonID, p1, game, event);
            case "ability" -> {
                if ("starForge".equalsIgnoreCase(buttonID)) {

                    List<Tile> tiles = getTilesOfPlayersSpecificUnits(game, p1, UnitType.Warsun);
                    List<Button> buttons = new ArrayList<>();
                    MessageHelper.sendMessageToChannel(event.getChannel(), p1.getFactionEmoji() + " Chose to use the starforge ability");
                    String message = "Select the tile you would like to starforge in";
                    for (Tile tile : tiles) {
                        Button starTile = Button.success("starforgeTile_" + tile.getPosition(),
                            tile.getRepresentationForButtons(game, p1));
                        buttons.add(starTile);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("orbitalDrop".equalsIgnoreCase(buttonID)) {
                    String successMessage = p1.getFactionEmoji() + " Spent 1 strategy token using " + Emojis.Sol + "Orbital Drop (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event, Emojis.Sol + "Orbital Drop");
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    String message = "Select the planet you would like to place 2 infantry on.";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(p1, game, "2gf", "place");
                    buttons.add(Button.danger("orbitolDropFollowUp", "Done Dropping Infantry"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("muaatFS".equalsIgnoreCase(buttonID)) {
                    String successMessage = p1.getFactionEmoji() + " Spent 1 strategy token using " + Emojis.Muaat + Emojis.flagship + "The Inferno (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ") \n";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event, Emojis.Muaat + Emojis.flagship + "The Inferno");
                    List<Tile> tiles = getTilesOfPlayersSpecificUnits(game, p1, UnitType.Flagship);
                    Tile tile = tiles.get(0);
                    List<Button> buttons = TurnStart.getStartOfTurnButtons(p1, game, true, event);
                    new AddUnits().unitParsing(event, p1.getColor(), tile, "1 cruiser", game);
                    successMessage = successMessage + "Produced 1 " + Emojis.cruiser + " in tile " + tile.getRepresentationForButtons(game, p1) + ".";
                    MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
                    String message = "Use buttons to end turn or do another action";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    deleteMessage(event);
                } else if ("lanefirMech".equalsIgnoreCase(buttonID)) {
                    String message3 = "Use buttons to drop a mech on a planet";
                    List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(p1, game,
                        "mech", "placeOneNDone_skipbuild"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message3, buttons);
                    String message2 = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Button.primary(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Button.success(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Button.danger(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_1",
                            "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact3 = Button.danger(finChecker + "deleteButtons",
                        "Done Purging");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, purgeFragButtons);
                    String message = "Use buttons to end turn or do an action";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(p1, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, systemButtons);

                } else if ("fabrication".equalsIgnoreCase(buttonID)) {
                    String message = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Button.primary(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Button.success(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Button.danger(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_1",
                            "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact2 = Button.success(finChecker + "gain_CC", "Gain CC");
                    purgeFragButtons.add(transact2);
                    Button transact3 = Button.danger(finChecker + "finishComponentAction",
                        "Done Resolving Fabrication");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, purgeFragButtons);

                } else if ("stallTactics".equalsIgnoreCase(buttonID)) {
                    String secretScoreMsg = "_ _\n" + p1.getRepresentation(true, true)
                        + " Click a button below to discard an Action Card";
                    List<Button> acButtons = ACInfo.getDiscardActionCardButtons(game, p1, true);
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
                        String successMessage = getIdent(p1) + " Reduced strategy pool CCs by 1 (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
                        p1.setStrategicCC(p1.getStrategicCC() - 1);
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event, Emojis.kolume + "Meditation");
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    } else {
                        String successMessage = getIdent(p1) + " Exhausted Scepter";
                        p1.addExhaustedRelic("emelpar");
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    }
                    String message = "Select the tech you would like to ready";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message, getAllTechsToReady(game, p1));
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
                        Button transact = Button.primary(finChecker + "resolvePNPlay_bmfNotHand", "Play BMF");
                        purgeFragButtons.add(transact);
                    }

                }
                if (p1.getCrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getCrf() + 1 && x < 4); x++) {
                        Button transact = Button.primary(finChecker + "purge_Frags_CRF_" + x,
                            "Cultural Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (p1.getIrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getIrf() + 1 && x < 4); x++) {
                        Button transact = Button.success(finChecker + "purge_Frags_IRF_" + x,
                            "Industrial Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (p1.getHrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getHrf() + 1 && x < 4); x++) {
                        Button transact = Button.danger(finChecker + "purge_Frags_HRF_" + x,
                            "Hazardous Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }

                if (p1.getUrf() > 0) {
                    for (int x = 1; x < p1.getUrf() + 1; x++) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_" + x,
                            "Frontier Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Button.danger(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                if (p1.hasAbility("a_new_edifice")) {
                    transact2 = Button.danger(finChecker + "drawRelicFromFrag", "Finish Purging and Explore");
                }
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, purgeFragButtons);
            }
            case "generic" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Doing unspecified component action.");
            case "absolMOW" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    getIdent(p1) + " is exhausting the " + Emojis.Agenda + "Minister of War" + Emojis.Absol + " and spending a strategy CC to remove 1 CC from the board");
                if (p1.getStrategicCC() > 0) {
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), getIdent(p1) + " strategy CC went from " + (p1.getStrategicCC() + 1) + " to " + p1.getStrategicCC());
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, game, event);
                }
                List<Button> buttons = getButtonsToRemoveYourCC(p1, game, event, "absol");
                MessageChannel channel = getCorrectChannel(p1, game);
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
                purge2StarCharters(p1);
                DrawBlueBackTile.drawBlueBackTiles(event, game, p1, 1);
            }
        }

        if (!firstPart.contains("ability") && !firstPart.contains("getRelic") && !firstPart.contains("pn")) {
            serveNextComponentActionButtons(event, game, p1);
        }
    }

    private static void resolveRelicComponentAction(Game game, Player player, ButtonInteractionEvent event, String relicID) {
        if (!Mapper.isValidRelic(relicID) || !player.hasRelic(relicID)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid relic or player does not have specified relic: `" + relicID + "`");
            return;
        }
        String purgeOrExhaust = "Purged";
        if ("titanprototype".equalsIgnoreCase(relicID) || "absol_jr".equalsIgnoreCase(relicID)) { // EXHAUST THE RELIC
            List<Button> buttons2 = AgendaHelper.getPlayerOutcomeButtons(game, null, "jrResolution", null);
            player.addExhaustedRelic(relicID);
            purgeOrExhaust = "Exhausted";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to decide who to use JR on", buttons2);

            // OFFER TCS
            for (Player p2 : game.getRealPlayers()) {
                if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                    List<Button> buttons3 = new ArrayList<>();
                    buttons3.add(Button.success("exhaustTCS_" + relicID + "_" + player.getFaction(), "Exhaust TCS to Ready " + relicID));
                    buttons3.add(Button.danger("deleteButtons", "Decline"));
                    String msg = p2.getRepresentation(true, true)
                        + " you have the opportunity to exhaust your TCS tech to ready " + relicID
                        + " and potentially resolve a transaction.";
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p2, game), msg, buttons3);
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
            case "enigmaticdevice" -> ButtonHelperActionCards.resolveFocusedResearch(game, player, relicID, event);
            case "codex", "absol_codex" -> offerCodexButtons(player, game, event);
            case "nanoforge", "absol_nanoforge", "baldrick_nanoforge" -> offerNanoforgeButtons(player, game, event);
            case "decrypted_cartoglyph" -> DrawBlueBackTile.drawBlueBackTiles(event, game, player, 3);
            case "throne_of_the_false_emperor" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("drawRelic", "Draw a relic"));
                buttons.add(Button.primary("thronePoint", "Score a secret someone else scored"));
                buttons.add(Button.danger("deleteButtons", "Score one of your unscored secrets"));
                message = player.getRepresentation()
                    + " choose one of the options. Reminder than you cant score more secrets than normal with this relic (even if they're someone elses), and you cant score the same secret twice. If scoring one of your unscored secrets, just score it via the normal process after pressing the button";
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
                message = player.getRepresentation(true, true) + " Your tgs increased from " + oldTg + " -> " + player.getTg();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, player.getTg() - oldTg);
            }
            case "stellarconverter" -> {
                message = player.getRepresentation(true, true) + " Select the planet you want to destroy";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, getButtonsForStellar(player, game));
            }
            case "passturn" -> {
                MessageHelper.sendMessageToChannelWithButton(event.getChannel(), null, Buttons.REDISTRIBUTE_CCs);
            }
            case "titanprototype", "absol_jr" -> {
                //handled above
            }
            default -> MessageHelper.sendMessageToChannel(event.getChannel(), "This Relic is not tied to any automation. Please resolve manually.");
        }
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
            if ((threadChannel_.getName().startsWith(threadName) || threadChannel_.getName().equals(threadName + "WinnuHero")) && (!"technology".equalsIgnoreCase(stratName) || !game.getComponentAction())) {
                MessageHelper.sendMessageToChannelWithButtons(threadChannel_, message, buttons);
                return;
            }
        }
        if (player != null) {
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(game.getActionsChannel(), message, buttons);
        }
    }

    public static void offerNanoforgeButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet unitHolder = game.getPlanetsInfo().get(planet);
            Planet planetReal = unitHolder;
            if (planetReal == null)
                continue;

            boolean legendaryOrHome = isPlanetLegendaryOrHome(planet, game, false, null);
            if (!legendaryOrHome) {
                buttons.add(Button.success("nanoforgePlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Use buttons to select which planet to nanoforge";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    public static void offerCodexButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        Button codex1 = Button.success("codexCardPick_1", "Card #1");
        Button codex2 = Button.success("codexCardPick_2", "Card #2");
        Button codex3 = Button.success("codexCardPick_3", "Card #3");
        String message = "Use buttons to select cards from the discard";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
            List.of(codex1, codex2, codex3));
    }

    public static void resolveSARMechStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String tilePos = buttonID.split("_")[1];
        String warfareOrNot = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(tilePos);
        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            if (player.getPlanetsAllianceMode().contains(uH.getName())) {
                buttons.add(Button.success("sarMechStep2_" + uH.getName() + "_" + warfareOrNot,
                    "Place mech on " + Helper.getPlanetRepresentation(uH.getName(), game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation(true, true) + " choose the planet to drop a mech on", buttons);
        deleteTheOneButton(event);
    }

    public static void resolveSARMechStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String warfareOrNot = buttonID.split("_")[2];
        String msg1 = getIdent(player) + " exhausted Self-Assembly Routines to place a mech on "
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
            message = getIdent(p2) + " Drew 2 AC With Scheming. Please Discard An AC with the blue buttons";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                p2.getRepresentation(true, true) + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(game, p2, false));
        } else if (p2.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, p2, 1);
            message = getIdent(p2) + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(p2.getUserID());
            message = getIdent(p2) + " Drew 1 AC";
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

    public static void offerPlayerPreferences(Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary("playerPref_autoSaboReact", "Change Auto No-Sabo React Time")
            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
        buttons.add(Button.secondary("playerPref_afkTimes", "Change AFK Times"));
        buttons.add(Button.secondary("playerPref_tacticalAction", "Change Distance-Based Tactical Action Preference"));
        buttons.add(Button.secondary("playerPref_autoNoWhensAfters", "Change Auto No Whens/Afters React")
            .withEmoji(Emoji.fromFormatted(Emojis.Agenda)));
        buttons.add(Button.secondary("playerPref_personalPingInterval", "Change Personal Ping Interval"));
        buttons.add(Button.secondary("playerPref_directHitManagement",
            "Tell The Bot What Units Not To Risk Direct Hit On"));
        // deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
            player.getRepresentation() + " Choose the thing you wish to change", buttons);
    }

    public static void resolvePlayerPref(Player player, ButtonInteractionEvent event, String buttonID, Game game) {
        String thing = buttonID.split("_")[1];
        switch (thing) {
            case "autoSaboReact" -> {
                offerSetAutoPassOnSaboButtons(game, player);
            }
            case "afkTimes" -> {
                offerAFKTimeOptions(game, player);
            }
            case "tacticalAction" -> {
                List<Button> buttons = new ArrayList<>();
                String msg = player.getRepresentation()
                    + " Choose whether you want your tactical action buttons to be distance based (offer you 0 tiles away initially, then 1, 2, 3 tiles away upon more button presses) or ring based (choose what ring the active system is in). Default is ring based. This will apply to all your games";
                buttons.add(Button.success("playerPrefDecision_true_distance", "Make it distance based"));
                buttons.add(Button.success("playerPrefDecision_false_distance", "Make it ring based"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            }
            case "autoNoWhensAfters" -> {
                List<Button> buttons = new ArrayList<>();
                String msg = player.getRepresentation()
                    + " Choose whether you want the game to auto react no whens/afters after a random amount of time for you when you have no whens/afters. Default is off. This will only apply to this game. If you have any whens or afters or related when/after abilities, it will not do anything. ";
                buttons.add(Button.success("playerPrefDecision_true_agenda", "Turn on"));
                buttons.add(Button.success("playerPrefDecision_false_agenda", "Turn off"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            }
            case "personalPingInterval" -> {
                offerPersonalPingOptions(game, player);
            }
            case "directHitManagement" -> {
                offerDirectHitManagementOptions(game, player);
            }
        }
        deleteMessage(event);
    }

    public static void resolvePlayerPrefDecision(Player player, ButtonInteractionEvent event, String buttonID, Game game) {
        String trueOrFalse = buttonID.split("_")[1];
        String distanceOrAgenda = buttonID.split("_")[2];
        if ("true".equals(trueOrFalse)) {
            if ("distance".equals(distanceOrAgenda)) {
                player.setPreferenceForDistanceBasedTacticalActions(true);
                Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
                for (Game game2 : mapList.values()) {
                    for (Player player2 : game2.getRealPlayers()) {
                        if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                            player2.setPreferenceForDistanceBasedTacticalActions(true);
                            GameSaveLoadManager.saveMap(game2, player2.getUserName() + " Updated Player Settings");
                        }
                    }
                }
            } else {
                player.setAutoPassWhensAfters(true);
            }
        } else {
            if ("distance".equals(distanceOrAgenda)) {
                player.setPreferenceForDistanceBasedTacticalActions(false);
                Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
                for (Game game2 : mapList.values()) {
                    for (Player player2 : game2.getRealPlayers()) {
                        if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                            player2.setPreferenceForDistanceBasedTacticalActions(false);
                            GameSaveLoadManager.saveMap(game2, player2.getUserName() + " Updated Player Settings");
                        }
                    }
                }
            } else {
                player.setAutoPassWhensAfters(false);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Set setting successfully");

        deleteMessage(event);
    }

    // TODO: Combine with PlayPN.playPN()
    public static void resolvePNPlay(String id, Player player, Game game, GenericInteractionCreateEvent event) {
        boolean longPNDisplay = false;
        boolean fromHand = true;
        if ("bmfNotHand".equals(id)) {
            fromHand = false;
            id = "bmf";
        }

        if (id.contains("dspnflor")) {
            if (id.contains("Checked")) {
                id = "dspnflor";
            } else {
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, player.getRepresentation(true, true)
                    + " this PN will be applied automatically the next time you draw a relic. It will not work if you play it before then, so I am stopping you here");
                return;
            }
        }
        PromissoryNoteModel pn = Mapper.getPromissoryNote(id);
        String pnName = pn.getName();
        // String pnOwner = Mapper.getPromissoryNoteOwner(id);
        Player owner = game.getPNOwner(id);
        if (pn.getPlayArea() && !player.isPlayerMemberOfAlliance(owner)) {
            player.setPromissoryNotesInPlayArea(id);
        } else {
            player.removePromissoryNote(id);
            if (!"dspncymi".equalsIgnoreCase(id)) {
                owner.setPromissoryNote(id);
                PNInfo.sendPromissoryNoteInfo(game, owner, false);
            }
            PNInfo.sendPromissoryNoteInfo(game, player, false);
        }
        String emojiToUse = game.isFoWMode() ? "" : owner.getFactionEmoji();
        StringBuilder sb = new StringBuilder(player.getRepresentation() + " played promissory note: " + pnName + "\n");
        sb.append(emojiToUse).append(Emojis.PN);
        String pnText;

        // Handle AbsolMode Political Secret
        if (game.isAbsolMode() && id.endsWith("_ps")) {
            pnText = "Political Secret" + Emojis.Absol
                + ":  *When you cast votes:* You may exhaust up to 3 of the {color} player's planets and cast additional votes equal to the combined influence value of the exhausted planets. Then return this card to the {color} player.";
        } else {
            pnText = longPNDisplay ? Mapper.getPromissoryNote(id).getText() : Mapper.getPromissoryNote(id).getName();
        }
        sb.append(pnText).append("\n");

        // TERRAFORM TIP

        if (id.contains("dspnveld")) {
            ButtonHelperFactionSpecific.offerVeldyrButtons(player, game, id);
        }
        if ("dspnolra".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.resolveOlradinPN(player, game, event);
        }
        if ("terraform".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerTerraformButtons(player, game, event);
        }
        if ("dspnrohd".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerAutomatonsButtons(player, game, event);
        }
        if ("dspnbent".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerBentorPNButtons(player, game, event);
        }
        if ("dspngled".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerGledgeBaseButtons(player, game, event);
        }
        if ("iff".equalsIgnoreCase(id)) {
            List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
            String message = player.getRepresentation(true, true) + " select type of wormhole you wish to drop";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), message, buttons);
        }
        if ("greyfire".equalsIgnoreCase(id)) {
            List<Button> buttons = ButtonHelperFactionSpecific.getGreyfireButtons(game);
            String message = player.getRepresentation(true, true) + " select planet you wish to use greyfire on";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), message, buttons);
        }
        if ("dspnlizh".equalsIgnoreCase(id) || "dspnchei".equalsIgnoreCase(id)) {
            new AddUnits().unitParsing(event, player.getColor(),
                game.getTileByPosition(game.getActiveSystem()), "2 ff", game);
            String message = player.getRepresentation(true, true) + " added 2 ff to the active system";
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), message);
        }
        if ("dspncymi".equalsIgnoreCase(id)) {
            pickACardFromDiscardStep1(game, player);
        }
        if ("dspnkort".equalsIgnoreCase(id)) {
            List<Button> buttons = getButtonsToRemoveYourCC(player, game, event, "kortalipn");
            MessageChannel channel = getCorrectChannel(player, game);
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        }
        if ("ragh".equalsIgnoreCase(id)) {
            String message = player.getRepresentation(true, true) + " select planet to raghs call on";
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), message,
                ButtonHelperFactionSpecific.getRaghsCallButtons(player, game,
                    game.getTileByPosition(game.getActiveSystem())));
        }
        if ("cavalry".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.resolveCavStep1(game, player);
        }
        if ("dspntnel".equalsIgnoreCase(id)) {
            game.drawSecretObjective(player.getUserID());
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), player.getRepresentation() + " drew an extra SO due to Tnelis PN. Please discard an extra SO");
        }
        if ("dspnvade".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.resolveVadenTgForSpeed(player, game, event);
        }
        if ("crucible".equalsIgnoreCase(id)) {
            game.setStoredValue("crucibleBoost", "2");
        }
        if ("ms".equalsIgnoreCase(id)) {
            List<Button> buttons = new ArrayList<>(
                Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                MessageHelper.sendMessageToChannel(getCorrectChannel(owner, game),
                    owner.getRepresentation(true, true)
                        + " lost a command counter from strategy pool due to a Military Support play");
            }
            String message = player.getRepresentation(true, true) + " Use buttons to drop 2 infantry on a planet";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), message, buttons);
        }
        if (!"agendas_absol".equals(game.getAgendaDeckID()) && id.endsWith("_ps")) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(owner, game), owner.getRepresentation(true, true)
                + " due to a play of your Political Secret, you will be unable to vote in agenda (unless you have xxcha alliance). The bot doesnt enforce the other restrictions regarding no abilities, but you should abide by them.");
            game.setStoredValue("AssassinatedReps",
                game.getStoredValue("AssassinatedReps") + owner.getFaction());
        }

        // Fog of war ping
        if (game.isFoWMode()) {
            // Add extra message for visibility
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, sb.toString());
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), sb.toString());
        if ("fires".equalsIgnoreCase(id)) {
            player.addTech("ws");
            if (player.getLeaderIDs().contains("mirvedacommander") && !player.hasLeaderUnlocked("mirvedacommander")) {
                commanderUnlockCheck(player, game, "mirveda", event);
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getRepresentation(true, true) + " acquired Warsun tech");
            owner.setFleetCC(owner.getFleetCC() - 1);
            ButtonHelper.checkFleetInEveryTile(owner, game, event);
            String reducedMsg = owner.getRepresentation(true, true)
                + " reduced your fleet CC by 1 due to fires being played";
            if (game.isFoWMode()) {
                MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), reducedMsg);
            }
        }
        if (id.endsWith("_ta")) {
            int comms = owner.getCommodities();
            owner.setCommodities(0);
            String reducedMsg = owner.getRepresentation(true, true) + " your TA was played.";
            String reducedMsg2 = player.getRepresentation(true, true)
                + " you gained tgs equal to the number of comms the player had (your tgs went from "
                + player.getTg() + "tgs to -> " + (player.getTg() + comms)
                + "tgs). Please follow up with the player if this number seems off";
            player.setTg(player.getTg() + comms);
            ButtonHelperFactionSpecific.resolveDarkPactCheck(game, owner, player, owner.getCommoditiesTotal());
            ButtonHelperAbilities.pillageCheck(player, game);
            MessageHelper.sendMessageToChannel(getCorrectChannel(owner, game), reducedMsg);
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, game), reducedMsg2);
        }
        if (("favor".equalsIgnoreCase(id))) {
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                String reducedMsg = owner.getRepresentation(true, true)
                    + " reduced your strategy CC by 1 due to your PN getting played";
                if (game.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
                } else {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), reducedMsg);
                }
                new RevealAgenda().revealAgenda(event, false, game, game.getMainGameChannel());
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    "Political Facor (xxcha PN) was played");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "PN owner did not have a strategy CC, agenda not vetod");
            }
        }
        if (("scepter".equalsIgnoreCase(id))) {
            String message = player.getRepresentation(true, true) + " Use buttons choose which system to mahact diplo";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), message,
                Helper.getPlanetSystemDiploButtons(event, player, game, false, owner));
        }
        if (("dspnkoll".equalsIgnoreCase(id))) {
            ButtonHelperFactionSpecific.offerKolleccPNButtons(game, player);
        }
        if (id.contains("rider")) {
            String riderName = "Keleres Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            MessageHelper.sendMessageToChannelWithFactionReact(game.getMainGameChannel(),
                "Please select your rider target", game, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
                "Please indicate no afters again.", game, afterButtons, "after");

        }
        if ("dspnedyn".equalsIgnoreCase(id)) {
            String riderName = "Edyn Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            MessageHelper.sendMessageToChannelWithFactionReact(game.getMainGameChannel(),
                "Please select your rider target", game, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
                "Please indicate no afters again.", game, afterButtons, "after");
        }
        if ("dspnkyro".equalsIgnoreCase(id)) {
            String riderName = "Kyro Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            MessageHelper.sendMessageToChannelWithFactionReact(game.getMainGameChannel(),
                "Please select your rider target", game, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
                "Please indicate no afters again.", game, afterButtons, "after");
        }
        PNInfo.sendPromissoryNoteInfo(game, player, false);
        PNInfo.sendPromissoryNoteInfo(game, owner, false);
        if ("spynet".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerSpyNetOptions(player);
        }
        if ("gift".equalsIgnoreCase(id)) {
            startActionPhase(event, game);
        }
        if ("bmf".equalsIgnoreCase(id)) {
            if (fromHand) {
                String finChecker = "";
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - player.getUrf();

                numToBeat = numToBeat - 1;

                if (player.getCrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getCrf() + 1 && x < 4); x++) {
                        Button transact = Button.primary(finChecker + "purge_Frags_CRF_" + x,
                            "Cultural Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (player.getIrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getIrf() + 1 && x < 4); x++) {
                        Button transact = Button.success(finChecker + "purge_Frags_IRF_" + x,
                            "Industrial Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (player.getHrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getHrf() + 1 && x < 4); x++) {
                        Button transact = Button.danger(finChecker + "purge_Frags_HRF_" + x,
                            "Hazardous Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }

                if (player.getUrf() > 0) {
                    for (int x = 1; x < player.getUrf() + 1; x++) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_" + x,
                            "Frontier Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Button.danger(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                if (player.hasAbility("a_new_edifice")) {
                    transact2 = Button.danger(finChecker + "drawRelicFromFrag", "Finish Purging and Explore");
                }
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), message,
                    purgeFragButtons);
            }
        }
        if (pn.getText().toLowerCase().contains("action:") && !"acq".equalsIgnoreCase(id)) {
            serveNextComponentActionButtons(event, game, player);
        }

    }

    public static void offerSpeakerButtons(Game game, Player player) {
        String assignSpeakerMessage = "Please, before you draw your action cards or look at agendas, click a faction below to assign Speaker "
            + Emojis.SpeakerToken;
        List<Button> assignSpeakerActionRow = getAssignSpeakerButtons(game);
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), assignSpeakerMessage,
            assignSpeakerActionRow);
    }

    private static List<Button> getAssignSpeakerButtons(Game game) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            if (player.isRealPlayer() && !player.getUserID().equals(game.getSpeaker())) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isValidFaction(faction)) {
                    Button button = Button.secondary("assignSpeaker_" + faction, " ");
                    String factionEmojiString = player.getFactionEmoji();
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    assignSpeakerButtons.add(button);
                }
            }
        }
        return assignSpeakerButtons;
    }

    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event) {
        List<String> unplayedACs = new ArrayList<>(game.getActionCards());
        for (Player player : game.getRealPlayers()) {
            unplayedACs.addAll(player.getActionCards().keySet());
        }
        List<String> unplayedACNames = new ArrayList<>();
        for (String acID : unplayedACs) {
            unplayedACNames.add(Mapper.getActionCard(acID).getName());
        }
        Collections.sort(unplayedACNames);
        Map<String, Integer> namesNValues = new HashMap<String, Integer>();
        for (String acName : unplayedACNames) {
            if (namesNValues.containsKey(acName)) {
                namesNValues.put(acName, namesNValues.get(acName) + 1);
            } else {
                namesNValues.put(acName, 1);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Unplayed Action Cards: ").append("\n");
        int x = 1;
        List<String> sortedNamed = new ArrayList<>();
        sortedNamed.addAll(namesNValues.keySet());
        Collections.sort(sortedNamed);
        for (String id : sortedNamed) {
            sb.append(x).append(". " + id + " x" + namesNValues.get(id) + "\n");
            x++;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    public static void serveNextComponentActionButtons(GenericInteractionCreateEvent event, Game game,
        Player player) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, game), message, systemButtons);
    }
}
