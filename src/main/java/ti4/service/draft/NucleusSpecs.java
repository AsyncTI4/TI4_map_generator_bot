package ti4.service.draft;

import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.MapTemplateModel;

// TODO: Dangerous Wilds balance (1-2 hazardous planets per core slice, 0-2 per player slice)
public record NucleusSpecs(
        int numSlices,
        int minNucleusWormholes,
        int maxNucleusWormholes,
        int minNucleusLegendaries,
        int maxNucleusLegendaries,
        int minMapWormholes,
        int maxMapWormholes,
        int minMapLegendaries,
        int maxMapLegendaries,
        int minSliceValue,
        int maxSliceValue,
        int minNucleusValue,
        int maxNucleusValue,
        int minSlicePlanets,
        int maxSlicePlanets,
        int minSliceRes,
        int minSliceInf,
        int maxNucleusQualityDifference,
        int expectedRedTiles) {
    NucleusSpecs(DraftSpec draftSpec) {
        this(draftSpec.getTemplate().getPlayerCount(), draftSpec.getNumSlices());
    }

    public NucleusSpecs(int players, int slices) {
        this(
                slices,
                0, // min nucleus wormholes
                Math.min(3, Math.round(players / 2.0f)), // max nucleus wormholes
                0, // min nucleus legendaries
                Math.max(1, Math.round(players / 3.0f)), // max nucleus legendaries
                Math.max(2, Math.round(players / 4.0f)), // min map wormholes
                Math.min(3, Math.round(players / 2.0f)), // max map wormholes
                1, // min map legendaries
                Math.max(1, Math.round(players / 3.0f)), // max map legendaries
                4, // min slice value
                9, // max slice value
                4, // min nucleus value
                8, // max nucleus value
                2, // min slice planets
                5, // max slice planets
                0, // min slice resources
                0, // min slice influence
                3, // max nucleus quality difference
                Math.round(11 * players / 6.0f) // expected red tiles
        );
    }

    public static String validateSpecsForGame(NucleusSpecs specs, Game game) {
        String mapTemplateId = game.getMapTemplateID();
        if (mapTemplateId == null || mapTemplateId.isBlank()) {
            return "No map template is set on the game.";
        }
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);
        if (!mapTemplate.isNucleusTemplate()) {
            return "Map template " + mapTemplate.getAlias()
                    + " is not a nucleus template, but nucleus generation was requested.";
        }
        int players = mapTemplate.getPlayerCount();
        if (specs.numSlices < players) {
            return "Number of slices (" + specs.numSlices + ") must be at least the number of players (" + players
                    + ").";
        }
        if (specs.minNucleusWormholes < 0
                || specs.maxNucleusWormholes < 0
                || specs.minNucleusWormholes > specs.maxNucleusWormholes) {
            return "Nucleus wormhole counts must be non-negative and max >= min.";
        }
        if (specs.minNucleusLegendaries < 0
                || specs.maxNucleusLegendaries < 0
                || specs.minNucleusLegendaries > specs.maxNucleusLegendaries) {
            return "Nucleus legendary counts must be non-negative and max >= min.";
        }
        if (specs.minMapWormholes < 0 || specs.maxMapWormholes < 0 || specs.minMapWormholes > specs.maxMapWormholes) {
            return "Map wormhole counts must be non-negative and max >= min.";
        }
        if (specs.minMapLegendaries < 0
                || specs.maxMapLegendaries < 0
                || specs.minMapLegendaries > specs.maxMapLegendaries) {
            return "Map legendary counts must be non-negative and max >= min.";
        }
        int nucleusTiles = mapTemplate.getTemplateTiles().stream()
                .filter(tile -> tile.getNucleusNumbers() != null
                        && !tile.getNucleusNumbers().isEmpty())
                .toList()
                .size();
        if (specs.maxNucleusWormholes + specs.maxNucleusLegendaries > nucleusTiles) {
            return "The maximum number of nucleus wormholes and legendaries ("
                    + (specs.maxNucleusWormholes + specs.maxNucleusLegendaries)
                    + ") exceeds the number of nucleus tiles (" + nucleusTiles + ").";
        }
        return null;
    }
}
