package ti4.draft;

import ti4.map.Game;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.TileEmojis;
import ti4.service.emoji.UnitEmojis;

public enum DraftCategory {
    ABILITY,
    TECH,
    BREAKTHROUGH,
    AGENT,
    COMMANDER,
    HERO,
    MECH,
    FLAGSHIP,
    COMMODITIES,
    PN,
    HOMESYSTEM,
    STARTINGTECH,
    STARTINGFLEET,
    BLUETILE,
    REDTILE,
    DRAFTORDER,
    MAHACTKING,
    UNIT,
    PLOT;

    public String title(Game game) {
        TI4Emoji emoji = emoji(game);
        return "## "
                + switch (this) {
                    case ABILITY -> "Abilities";
                    case TECH -> game.isTwilightsFallMode() ? "Abilities" : "Faction Techs";
                    case AGENT -> game.isTwilightsFallMode() ? "Genomes" : "Agents";
                    case COMMANDER -> "Commanders";
                    case HERO -> "Heroes";
                    case MECH -> "Mechs";
                    case FLAGSHIP -> "Flagships";
                    case COMMODITIES -> "Commodities";
                    case PN -> "Promissory Notes";
                    case HOMESYSTEM -> "Home Systems";
                    case STARTINGTECH -> "Starting Techs";
                    case STARTINGFLEET -> "Starting Fleets";
                    case BLUETILE -> "Blue-backed Tiles";
                    case REDTILE -> "Red-backed Tiles";
                    case DRAFTORDER -> "Drafting Orders";
                    case MAHACTKING -> "Mahact Kings";
                    case UNIT -> "Units";
                    case BREAKTHROUGH -> "Breakthroughs";
                    case PLOT -> "Plot cards";
                }
                + (emoji != null ? (" " + emoji) : "");
    }

    public TI4Emoji emoji(Game game) {
        return switch (this) {
            case ABILITY -> MiscEmojis.tf_ability;
            case TECH -> game.isTwilightsFallMode() ? MiscEmojis.tf_ability : TechEmojis.CyberneticPropulsion;
            case AGENT -> game.isTwilightsFallMode() ? MiscEmojis.tf_genome : LeaderEmojis.Agent;
            case COMMANDER -> LeaderEmojis.Commander;
            case HERO -> LeaderEmojis.Hero;
            case MECH -> UnitEmojis.mech;
            case FLAGSHIP -> UnitEmojis.flagship;
            case COMMODITIES -> MiscEmojis.comm;
            case PN -> CardEmojis.PN;
            case HOMESYSTEM -> TileEmojis.TileGreenBack;
            case STARTINGTECH -> null;
            case STARTINGFLEET -> null;
            case BLUETILE -> TileEmojis.TileBlueBack;
            case REDTILE -> TileEmojis.TileRedBack;
            case DRAFTORDER -> MiltyDraftEmojis.positionUnpicked;
            case MAHACTKING -> FactionEmojis.Mahact;
            case UNIT -> TechEmojis.UnitUpgradeTech;
            case BREAKTHROUGH -> TechEmojis.SynergyAll;
            case PLOT -> FactionEmojis.Firmament;
        };
    }

    public boolean showDescrByDefault() {
        return switch (this) {
            case STARTINGTECH, STARTINGFLEET -> true;
            default -> true;
        };
    }
}
