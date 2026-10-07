package ti4.service.testbed;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.model.TestBedPreset;
import ti4.model.TestBedPreset.Seat;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class TestBedComponentService {

    static void applyGameState(Game game, TestBedPreset preset, List<String> warnings) {
        for (String option : preset.getFowOptions()) {
            game.setFowOption(FOWOption.fromString(option), true);
        }
        preset.getStored().forEach(game::setStoredValue);
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
    static String placeToken(Game game, String where, String token) {
        Tile tile;
        String holder;
        if (PositionMapper.isTilePositionValid(where)) {
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
