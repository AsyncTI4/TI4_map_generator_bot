package ti4.service.milty;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.helpers.StringHelper;

@UtilityClass
class SliceFeasibility {

    private static final int SCAR_RESOURCE_BONUS = 2;

    static Optional<String> findImpossibility(
            List<List<MiltyDraftTile>> tiers, int sliceCount, int requiredWormholes, MiltyDraftSpec spec) {
        int buildableSlices = tiers.stream().mapToInt(List::size).min().orElse(0);
        if (buildableSlices < sliceCount) {
            return Optional.of(
                    "the tile tiers only hold enough tiles for " + StringHelper.pluralize(buildableSlices, "slice"));
        }
        return Stream.of(
                        findLegendaryImpossibility(tiers, sliceCount, spec),
                        findWormholeImpossibility(tiers, sliceCount, requiredWormholes),
                        findValueImpossibility(tiers, sliceCount, spec))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> findLegendaryImpossibility(
            List<List<MiltyDraftTile>> tiers, int sliceCount, MiltyDraftSpec spec) {
        int mostLegendaries = mostChosen(tiers, sliceCount, MiltyDraftTile::isLegendary);
        if (spec.getMaxLegend() < spec.getNumSlices()) {
            mostLegendaries = Math.min(mostLegendaries, sliceCount);
        }
        if (mostLegendaries < spec.getMinLegend()) {
            return Optional.of("at most " + StringHelper.pluralize(mostLegendaries, "legendary planet")
                    + " can end up in the slices, but the minimum is " + spec.getMinLegend());
        }
        int fewestLegendaries = fewestChosen(tiers, sliceCount, MiltyDraftTile::isLegendary);
        if (fewestLegendaries > spec.getMaxLegend()) {
            return Optional.of("at least " + StringHelper.pluralize(fewestLegendaries, "legendary planet")
                    + " must end up in the slices, but the maximum is " + spec.getMaxLegend());
        }
        return Optional.empty();
    }

    private static Optional<String> findWormholeImpossibility(
            List<List<MiltyDraftTile>> tiers, int sliceCount, int requiredWormholes) {
        int mostWormholes = mostChosen(tiers, sliceCount, MiltyDraftTile::hasAnyWormhole);
        if (mostWormholes < requiredWormholes) {
            return Optional.of("at most " + StringHelper.pluralize(mostWormholes, "wormhole")
                    + " can end up in the slices, but extra wormholes needs " + requiredWormholes);
        }
        int fewestAlphas = fewestChosen(tiers, sliceCount, MiltyDraftTile::isHasAlphaWH);
        int fewestBetas = fewestChosen(tiers, sliceCount, MiltyDraftTile::isHasBetaWH);
        if (fewestAlphas > sliceCount || fewestBetas > sliceCount) {
            return Optional.of("some slice would have to hold two alpha or two beta wormholes");
        }
        return Optional.empty();
    }

    private static Optional<String> findValueImpossibility(
            List<List<MiltyDraftTile>> tiers, int sliceCount, MiltyDraftSpec spec) {
        int slicesWithScar = Math.min(sliceCount, mostChosen(tiers, sliceCount, MiltyDraftTile::isHasScar));
        int scarBonus = SCAR_RESOURCE_BONUS * slicesWithScar;

        int bestInfluence = bestSum(tiers, sliceCount, MiltyDraftTile::getMiltyInf);
        if (bestInfluence < sliceCount * spec.getMinInf()) {
            return Optional.of("even the highest-influence tiles average "
                    + perSlice(bestInfluence, sliceCount) + " optimal influence per slice, below the minimum of "
                    + spec.getMinInf());
        }
        int bestResources = bestSum(tiers, sliceCount, MiltyDraftTile::getMiltyRes) + scarBonus;
        if (bestResources < sliceCount * spec.getMinRes()) {
            return Optional.of("even the highest-resource tiles average "
                    + perSlice(bestResources, sliceCount) + " optimal resources per slice, below the minimum of "
                    + spec.getMinRes());
        }
        int bestTotal = bestSum(tiers, sliceCount, SliceFeasibility::optimalTotal) + scarBonus;
        if (bestTotal < sliceCount * spec.getMinTot()) {
            return Optional.of("even the best tiles average " + perSlice(bestTotal, sliceCount)
                    + " optimal total value per slice, below the minimum of " + spec.getMinTot());
        }
        int worstTotal = worstSum(tiers, sliceCount, SliceFeasibility::optimalTotal);
        if (worstTotal > sliceCount * spec.getMaxTot()) {
            return Optional.of("even the weakest tiles average " + perSlice(worstTotal, sliceCount)
                    + " optimal total value per slice, above the maximum of " + spec.getMaxTot());
        }
        return Optional.empty();
    }

    private static int optimalTotal(MiltyDraftTile tile) {
        return tile.getMiltyRes() + tile.getMiltyInf() + tile.getMiltyFlex();
    }

    private static int mostChosen(List<List<MiltyDraftTile>> tiers, int sliceCount, Predicate<MiltyDraftTile> matches) {
        return tiers.stream()
                .mapToInt(tier -> Math.min(sliceCount, countMatching(tier, matches)))
                .sum();
    }

    private static int fewestChosen(
            List<List<MiltyDraftTile>> tiers, int sliceCount, Predicate<MiltyDraftTile> matches) {
        return tiers.stream()
                .mapToInt(tier -> Math.max(0, countMatching(tier, matches) - (tier.size() - sliceCount)))
                .sum();
    }

    private static int countMatching(List<MiltyDraftTile> tier, Predicate<MiltyDraftTile> matches) {
        return (int) tier.stream().filter(matches).count();
    }

    private static int bestSum(List<List<MiltyDraftTile>> tiers, int sliceCount, ToIntFunction<MiltyDraftTile> value) {
        return tiers.stream()
                .mapToInt(tier -> sumOfFirst(tier, sliceCount, value, Comparator.reverseOrder()))
                .sum();
    }

    private static int worstSum(List<List<MiltyDraftTile>> tiers, int sliceCount, ToIntFunction<MiltyDraftTile> value) {
        return tiers.stream()
                .mapToInt(tier -> sumOfFirst(tier, sliceCount, value, Comparator.naturalOrder()))
                .sum();
    }

    private static int sumOfFirst(
            List<MiltyDraftTile> tier, int sliceCount, ToIntFunction<MiltyDraftTile> value, Comparator<Integer> order) {
        return tier.stream()
                .map(value::applyAsInt)
                .sorted(order)
                .limit(sliceCount)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private static String perSlice(int total, int sliceCount) {
        return String.format("%.1f", total / (double) sliceCount);
    }
}
