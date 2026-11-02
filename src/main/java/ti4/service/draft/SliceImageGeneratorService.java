package ti4.service.draft;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draft.SliceSheet;
import ti4.image.io.ImageUploads;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.Mapper;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftSlice;

@UtilityClass
public class SliceImageGeneratorService {

    public static FileUpload tryGenerateImage(
            DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {
        SliceDraftable sliceDraftable = (SliceDraftable) draftManager.getDraftable(SliceDraftable.TYPE);
        if (sliceDraftable == null) return null;

        Function<String, String> getPlayerFromSlice =
                (sliceName) -> draftManager.getPlayersWithChoiceKey(SliceDraftable.TYPE, sliceName).stream()
                        .findFirst()
                        .orElse(null);
        Function<String, FactionModel> getFactionFromPlayer = (playerUserID) -> {
            if (playerUserID == null) return null;
            List<DraftChoice> factionChoices = draftManager.getPlayerPicks(playerUserID, FactionDraftable.TYPE);
            if (!factionChoices.isEmpty()) {
                return FactionDraftable.getFactionByChoice(factionChoices.getFirst());
            }
            return null;
        };

        List<MiltyDraftSlice> slices = sliceDraftable.getDraftSlices();
        if (restrictChoiceKeys != null) {
            slices = slices.stream()
                    .filter(slice -> restrictChoiceKeys.contains(slice.getName()))
                    .collect(Collectors.toList());
        }
        if (slices.isEmpty()) return null;

        return generateImage(draftManager.getGame(), slices, getPlayerFromSlice, getFactionFromPlayer, uniqueKey);
    }

    private static FileUpload generateImage(
            Game game,
            List<MiltyDraftSlice> slices,
            Function<String, String> getPlayerFromSlice,
            Function<String, FactionModel> getFactionFromPlayer,
            String uniqueKey) {
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        List<SliceSheet.Card> cards = slices.stream()
                .map(slice -> card(game, slice, getPlayerFromSlice, getFactionFromPlayer))
                .toList();

        FileUpload fileUpload = ImageUploads.createFileUpload(SliceSheet.render(mapTemplate, cards), uniqueKey);
        fileUpload.setDescription(
                slices.stream().map(MiltyDraftSlice::ttsString).collect(Collectors.joining(";\n")));
        return fileUpload;
    }

    private static SliceSheet.Card card(
            Game game,
            MiltyDraftSlice slice,
            Function<String, String> getPlayerFromSlice,
            Function<String, FactionModel> getFactionFromPlayer) {
        String playerUserID = getPlayerFromSlice.apply(slice.getName());
        Player player = game.getPlayer(playerUserID);
        boolean taken = player != null;
        if (game.isFowMode()) {
            return SliceSheet.Card.hidden(slice, taken);
        }
        FactionModel faction = getFactionFromPlayer.apply(playerUserID);
        return SliceSheet.Card.revealed(slice, taken, taken ? player.getUserName() : null, faction);
    }
}
