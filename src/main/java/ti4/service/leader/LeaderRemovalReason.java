package ti4.service.leader;

/**
 * Describes why a hero is removed from a player's leader area after use.
 *
 * <p>The bot removes heroes from the player's leader list once they are spent so they cannot be used again, but
 * that removal is not always the same as a rules purge. Some heroes are attached to another game element instead
 * of being purged, while others remain active for a while and are only purged later during cleanup.
 *
 * <p>This distinction matters because other effects, such as the Lanefir breakthrough, care about actual purge
 * events rather than the bot's internal act of removing a leader from the player's available leaders.
 */
public enum LeaderRemovalReason {
    PURGED,
    ATTACHED,
    STATUS_CLEANUP;

    public static LeaderRemovalReason fromHeroId(String leaderId) {
        return switch (leaderId) {
            case "titanshero", "kyrohero", "toldarhero", "freesystemshero" -> ATTACHED;

            case "letnevhero",
                    "nomadhero",
                    "zealotshero",
                    "nokarhero",
                    "kolumehero",
                    "qhethero",
                    "nivynhero",
                    "lunariumhero" -> STATUS_CLEANUP;

            case "arborechero",
                    "argenthero",
                    "atokerahero",
                    "augershero",
                    "axishero",
                    "bastionhero",
                    "bentorhero",
                    "brilliancehero",
                    "cabalhero",
                    "celdaurihero",
                    "cheiranhero",
                    "cymiaehero",
                    "deepwroughthero",
                    "devourhero",
                    "dihmohnhero",
                    "edynhero",
                    "empyreanhero",
                    "eternityhero",
                    "eventhero",
                    "firmamenthero",
                    "florzenhero",
                    "forgehero",
                    "ghosthero",
                    "ghotihero",
                    "gledgehero",
                    "hacanhero",
                    "jolnarhero",
                    "kaltrimhero",
                    "keleresherokuuasi",
                    "khraskhero",
                    "kjalengardhero",
                    "kollecchero",
                    "kortalihero",
                    "l1z1xhero",
                    "lanefirhero",
                    "lawshero",
                    "lizhohero",
                    "mahacthero",
                    "mentakhero",
                    "mirvedahero",
                    "mortheushero",
                    "muaathero",
                    "naaluhero",
                    "naazhero",
                    "nekrohero",
                    "obsidianhero",
                    "olradinhero",
                    "onyxxahero",
                    "orlandohero",
                    "pharadnhero",
                    "poisonhero",
                    "ralnelhero",
                    "redcreusshero",
                    "rohdhnahero",
                    "saarhero",
                    "sanctionhero",
                    "sardakkhero",
                    "solhero",
                    "uydaihero",
                    "vadenhero",
                    "vaylerianhero",
                    "veldyrhero",
                    "voicehero",
                    "winnuhero",
                    "witchinghero",
                    "xanhero",
                    "yinhero",
                    "yssarilhero",
                    "zelianhero" -> PURGED;

            default -> PURGED;
        };
    }

    public String getRemovalMessage(String leaderName) {
        return switch (this) {
            case PURGED, STATUS_CLEANUP -> leaderName + " has been purged.";
            case ATTACHED -> leaderName + " is being attached to a planet.";
        };
    }
}
