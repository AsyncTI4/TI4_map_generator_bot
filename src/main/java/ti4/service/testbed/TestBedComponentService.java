package ti4.service.testbed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.map.AddBorderAnomaly;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.model.DeckModel;
import ti4.model.DeckModel.DeckType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.TestBedPreset;
import ti4.model.TestBedPreset.Seat;
import ti4.service.fow.FOWPlusService;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class TestBedComponentService {

    static final Map<String, BiConsumer<Game, Integer>> GAME_OPTIONS =
            Map.of(Constants.WORMHOLE_TOKEN_MERGE, Game::setWormholeTokenMergeFrom);

    static String gameOptionProblem(String key, String value) {
        if (!GAME_OPTIONS.containsKey(key)) {
            return "unknown game option `" + key + "`; use " + GAME_OPTIONS.keySet() + ".";
        }
        if (!value.matches("[0-9]")) {
            return "game option `" + key + "` takes a number from 0 to 9.";
        }
        return null;
    }

    static void setGameOption(Game game, String key, String value) {
        GAME_OPTIONS.get(key).accept(game, Integer.parseInt(value));
    }

    static void applyGameState(Game game, TestBedPreset preset, List<String> warnings) {
        for (String option : preset.getFowOptions()) {
            FOWOption fowOption = FOWOption.fromString(option);
            if (fowOption == FOWOption.FOW_PLUS) {
                FOWPlusService.setActive(game, true);
            } else {
                game.setFowOption(fowOption, true);
            }
        }
        preset.getStored().forEach(game::setStoredValue);
        preset.getGameOptions().forEach((key, value) -> setGameOption(game, key, value));
        for (String objective : preset.getRevealedObjectives()) {
            if (!game.revealSpecificStage1(objective) && !game.revealSpecificStage2(objective)) {
                warnings.add("Objective `" + objective + "` is not in this game's objective decks.");
            }
        }
        for (String law : preset.getLaws()) {
            String agenda = StringUtils.substringBefore(law, ":");
            String elected = law.contains(":") ? StringUtils.substringAfter(law, ":") : null;
            game.removeAgendaFromGame(agenda);
            game.addLaw(agenda, elected);
        }
        for (Map.Entry<String, List<String>> entry : preset.getTokens().entrySet()) {
            for (String token : entry.getValue()) {
                String problem = placeToken(game, entry.getKey(), token);
                if (problem != null) warnings.add(problem);
            }
        }
        for (Map.Entry<String, List<String>> entry : preset.getBorderAnomalies().entrySet()) {
            for (String border : entry.getValue()) {
                game.addBorderAnomaly(entry.getKey(), borderDirection(border), borderType(border));
            }
        }
    }

    static final Map<String, DeckType> DECK_SLOTS = Map.of(
            "ac", DeckType.ACTION_CARD,
            "so", DeckType.SECRET_OBJECTIVE,
            "relic", DeckType.RELIC,
            "explore", DeckType.EXPLORE,
            "agenda", DeckType.AGENDA);

    @Nullable
    static String deckProblem(String slot, String deckId) {
        DeckType expected = DECK_SLOTS.get(slot);
        if (expected == null) {
            return "unknown `decks` slot `" + slot + "`; use one of " + new TreeSet<>(DECK_SLOTS.keySet()) + ".";
        }
        DeckModel deck = Mapper.getDeck(deckId);
        if (deck == null) return "unknown deck `" + deckId + "` for `decks." + slot + "`.";
        if (deck.getType() != expected) {
            return "deck `" + deckId + "` is not a `" + slot + "` deck.";
        }
        return null;
    }

    static void applyDecks(
            Game game, TestBedPreset preset, GenericInteractionCreateEvent event, List<String> warnings) {
        preset.getDecks().forEach((slot, deckId) -> {
            DeckModel deck = Mapper.getDeck(deckId);
            if (deckProblem(slot, deckId) != null) return;
            boolean applied =
                    switch (DECK_SLOTS.get(slot)) {
                        case ACTION_CARD -> game.validateAndSetActionCardDeck(event, deck);
                        case SECRET_OBJECTIVE -> game.validateAndSetSecretObjectiveDeck(event, deck);
                        case RELIC -> game.validateAndSetRelicDeck(deck);
                        case EXPLORE -> game.validateAndSetExploreDeck(event, deck);
                        case AGENDA -> game.validateAndSetAgendaDeck(event, deck);
                        default -> false;
                    };
            if (!applied) warnings.add("Could not switch the `" + slot + "` deck to `" + deckId + "`.");
        });
    }

    static boolean stackExplore(Game game, String exploreId) {
        boolean inDeck = game.getAllExplores().contains(exploreId);
        boolean inDiscard = game.getAllExploreDiscard().contains(exploreId);
        if (!inDeck && !inDiscard) return false;
        List<String> deck = new ArrayList<>(game.getAllExplores());
        deck.remove(exploreId);
        deck.addFirst(exploreId);
        game.setExploreDeck(deck);
        if (inDiscard) {
            List<String> discard = new ArrayList<>(game.getAllExploreDiscard());
            discard.remove(exploreId);
            game.setExploreDiscard(discard);
        }
        return true;
    }

    static Integer borderDirection(String border) {
        return AddBorderAnomaly.parseDirection(
                StringUtils.substringBefore(border, ":").trim().toLowerCase());
    }

    static String borderType(String border) {
        return StringUtils.substringAfter(border, ":").trim();
    }

    static void applySeatComponents(Game game, Player player, Seat seat, List<String> warnings) {
        if (seat.getPns() != null) {
            for (String entry : seat.getPns()) {
                String problem = givePromissoryNote(game, player, entry);
                if (problem != null) warnings.add(problem);
            }
        }
        if (seat.getScoredObjectives() != null) {
            for (String objective : seat.getScoredObjectives()) {
                Integer index = game.getRevealedPublicObjectives().get(objective);
                if (index == null) {
                    warnings.add(player.getFaction() + ": objective `" + objective + "` is not revealed.");
                } else {
                    game.scorePublicObjective(player.getUserID(), index);
                }
            }
        }
        if (seat.getFragments() != null) {
            for (String fragment : seat.getFragments()) {
                game.pickExplore(fragment);
                player.addFragment(fragment);
            }
        }
        if (seat.getBreakthrough() != null) {
            boolean exhausted = "exhausted".equals(seat.getBreakthrough());
            for (String breakthrough : player.getBreakthroughIDs()) {
                player.setBreakthroughUnlocked(breakthrough, true);
                player.setBreakthroughExhausted(breakthrough, exhausted);
            }
        }
    }

    @Nullable
    static String givePromissoryNote(Game game, Player receiver, String entry) {
        String noteId = entry;
        if (entry.contains(":")) {
            Player owner = game.getPlayerFromColorOrFaction(StringUtils.substringAfter(entry, ":"));
            if (owner == null) return "No seat `" + StringUtils.substringAfter(entry, ":") + "` owns `" + entry + "`.";
            noteId = owner.getColor() + "_" + StringUtils.substringBefore(entry, ":");
        }
        for (Player holder : game.getRealPlayers()) {
            if (holder.getPromissoryNotes().containsKey(noteId)) {
                if (holder == receiver) return null;
                holder.removePromissoryNote(noteId);
                receiver.setPromissoryNote(noteId);
                return null;
            }
        }
        return receiver.getFaction() + ": nobody holds promissory note `" + noteId + "`.";
    }

    @Nullable
    static String removeToken(Game game, String where, String token) {
        boolean space = MapPositionMapper.isTilePositionValid(where);
        String holder = space ? Constants.SPACE : AliasHandler.resolvePlanet(where.toLowerCase());
        Tile tile = space ? game.getTileByPosition(where) : game.getTileFromPlanet(holder);
        if (tile == null) return "No tile at `" + where + "` for token `" + token + "`.";
        String file = Mapper.getAttachmentImagePath(token);
        if (file == null) file = Mapper.getTokenID(AliasHandler.resolveToken(token));
        if (file == null) return "Unknown token `" + token + "`.";
        if (!tile.removeToken(file, holder)) return "No token `" + token + "` at `" + where + "`.";
        return null;
    }

    @Nullable
    static String placeToken(Game game, String where, String token) {
        Tile tile;
        String holder;
        if (MapPositionMapper.isTilePositionValid(where)) {
            tile = game.getTileByPosition(where);
            holder = Constants.SPACE;
        } else {
            holder = AliasHandler.resolvePlanet(where.toLowerCase());
            tile = game.getTileFromPlanet(holder);
        }
        if (tile == null) return "No tile at `" + where + "` for token `" + token + "`.";
        String file = Mapper.getAttachmentImagePath(token);
        if (file == null) file = Mapper.getTokenID(AliasHandler.resolveToken(token));
        if (file == null) return "Unknown token `" + token + "`.";
        tile.addToken(file, holder);
        return null;
    }
}
