package ti4.service.emoji;

import ti4.map.Leader;

public enum LeaderEmojis implements TI4Emoji {
    // Official Content
    ArborecAgent, ArborecCommander, ArborecHero, //
    ArgentAgent, ArgentCommander, ArgentHero, //
    CabalAgent, CabalCommander, CabalHero, //
    CreussAgent, CreussCommander, CreussHero, //
    EmpyreanAgent, EmpyreanCommander, EmpyreanHero, //
    HacanAgent, HacanCommander, HacanHero, //
    JolNarAgent, JolNarCommander, JolNarHero, //
    L1Z1XAgent, L1Z1XCommander, L1Z1XHero, //
    LetnevAgent, LetnevCommander, LetnevHero, //
    MahactAgent, MahactCommander, MahactHero, //
    MentakAgent, MentakCommander, MentakHero, //
    MuaatAgent, MuaatCommander, MuaatHero, //
    NaaluAgent, NaaluCommander, NaaluHero, //
    NaazAgent, NaazCommander, NaazHero, //
    NekroAgent, NekroCommander, NekroHero, //
    NomadAgentArtuno, NomadAgentMercer, NomadAgentThundarian, NomadCommander, NomadHero, //
    KeleresAgent, KeleresCommander, KeleresHeroKuuasi, KeleresHeroHarka, KeleresHeroOdlynn, //
    SaarAgent, SaarCommander, SaarHero, //
    SardakkAgent, SardakkCommander, SardakkHero, //
    SolAgent, SolCommander, SolHero, //
    TitansAgent, TitansCommander, TitansHero, //
    WinnuAgent, WinnuCommander, WinnuHero, //
    XxchaAgent, XxchaCommander, XxchaHero, //
    YinAgent, YinCommander, YinHero, //
    YssarilAgent, YssarilCommander, YssarilHero, //

    // Discordant Stars
    AugersAgent, AugersCommander, AugersHero, //
    AxisAgent, AxisCommander, AxisHero, //
    BentorAgent, BentorCommander, BentorHero, //
    KyroAgent, KyroCommander, KyroHero, //
    CeldauriAgent, CeldauriCommander, CeldauriHero, //
    CheiranAgent, CheiranCommander, CheiranHero, //
    GheminaAgent, GheminaCommander, GheminaHeroLady, GheminaHeroLord, //
    CymiaeAgent, CymiaeCommander, CymiaeHero, //
    DihmohnAgent, DihmohnCommander, DihmohnHero, //
    EdynAgent, EdynCommander, EdynHero, //
    FlorzenAgent, FlorzenCommander, FlorzenHero, //
    FreesystemsAgent, FreesystemCommander, FreesystemHero, //
    GhotiAgent, GhotiCommander, GhotiHero, //
    GledgeAgent, GledgeCommander, GledgeHero, //
    KhraskAgent, KhraskCommander, KhraskHero, //
    KjalengardAgent, KjalengardCommander, KjalengardHero, //
    /* KolleccAgent, */ KolleccCommander, KolleccHero, //
    KolumeAgent, KolumeCommander, KolumeHero, //
    KortaliAgent, KortaliCommander, KortaliHero, //
    LanefirAgent, LanefirCommander, /* LanefirHero, */ //
    UydaiAgent, UydaiCommander, UydaiHero, PharadnAgent, PharadnCommander, PharadnHero,
    // Generic
    Agent, Commander, Hero, Envoy;

    public static TI4Emoji getLeaderTypeEmoji(String type) {
        if (type == null) return TI4Emoji.getRandomGoodDog();
        return switch (type.toLowerCase()) {
            case "agent" -> Agent;
            case "commander" -> Commander;
            case "hero" -> Hero;
            case "envoy" -> Envoy;
            default -> TI4Emoji.getRandomGoodDog(type);
        };
    }

    public static TI4Emoji getLeaderEmoji(Leader leader) {
        return getLeaderEmoji(leader.getId());
    }

    public static TI4Emoji getLeaderEmoji(String leader) {
        if (leader == null) return TI4Emoji.getRandomGoodDog();
        return switch (leader.toLowerCase()) {
            // LEADERS - AGENTS
            case "arborecagent" -> ArborecAgent;
            case "argentagent" -> ArgentAgent;
            case "cabalagent" -> CabalAgent;
            case "ghostagent", "creussagent" -> CreussAgent;
            case "empyreanagent" -> EmpyreanAgent;
            case "hacanagent" -> HacanAgent;
            case "jolnaragent" -> JolNarAgent;
            case "keleresagent" -> KeleresAgent;
            case "l1z1xagent" -> L1Z1XAgent;
            case "letnevagent" -> LetnevAgent;
            case "mahactagent" -> MahactAgent;
            case "mentakagent", "kelerescommander" -> MentakAgent;
            case "muaatagent" -> MuaatAgent;
            case "naaluagent" -> NaaluAgent;
            case "naazagent" -> NaazAgent;
            case "nekroagent" -> NekroAgent;
            case "nomadagentartuno" -> NomadAgentArtuno;
            case "nomadagentmercer" -> NomadAgentMercer;
            case "nomadagentthundarian" -> NomadAgentThundarian;
            case "sardakkagent" -> SardakkAgent;
            case "saaragent" -> SaarAgent;
            case "solagent" -> SolAgent;
            case "titansagent" -> TitansAgent;
            case "winnuagent" -> WinnuAgent;
            case "xxchaagent" -> XxchaAgent;
            case "yinagent" -> YinAgent;
            case "yssarilagent" -> YssarilAgent;

            // LEADERS - COMMANDERS
            case "arboreccommander" -> ArborecCommander;
            case "argentcommander" -> ArgentCommander;
            case "cabalcommander" -> CabalCommander;
            case "ghostcommander", "creusscommander" -> CreussCommander;
            case "empyreancommander" -> EmpyreanCommander;
            case "hacancommander" -> HacanCommander;
            case "jolnarcommander" -> JolNarCommander;
            case "l1z1xcommander" -> L1Z1XCommander;
            case "letnevcommander" -> LetnevCommander;
            case "mahactcommander" -> MahactCommander;
            case "mentakcommander" -> MentakCommander;
            case "muaatcommander" -> MuaatCommander;
            case "naalucommander" -> NaaluCommander;
            case "naazcommander" -> NaazCommander;
            case "nekrocommander" -> NekroCommander;
            case "nomadcommander" -> NomadCommander;
            case "sardakkcommander" -> SardakkCommander;
            case "saarcommander" -> SaarCommander;
            case "solcommander" -> SolCommander;
            case "titanscommander" -> TitansCommander;
            case "winnucommander" -> WinnuCommander;
            case "xxchacommander" -> XxchaCommander;
            case "yincommander" -> YinCommander;
            case "yssarilcommander" -> YssarilCommander;

            // LEADERS - HEROES
            case "arborechero" -> ArborecHero;
            case "argenthero" -> ArgentHero;
            case "cabalhero" -> CabalHero;
            case "ghosthero", "creusshero" -> CreussHero;
            case "empyreanhero" -> EmpyreanHero;
            case "hacanhero" -> HacanHero;
            case "jolnarhero" -> JolNarHero;
            case "keleresherokuuasi", "keleresahero" -> KeleresHeroKuuasi;
            case "keleresheroodlynn", "keleresxhero" -> KeleresHeroOdlynn;
            case "keleresheroharka", "keleresmhero" -> KeleresHeroHarka;
            case "l1z1xhero" -> L1Z1XHero;
            case "letnevhero" -> LetnevHero;
            case "mahacthero" -> MahactHero;
            case "mentakhero" -> MentakHero;
            case "muaathero" -> MuaatHero;
            case "naaluhero" -> NaaluHero;
            case "naazhero" -> NaazHero;
            case "nekrohero" -> NekroHero;
            case "nomadhero" -> NomadHero;
            case "sardakkhero" -> SardakkHero;
            case "saarhero" -> SaarHero;
            case "solhero" -> SolHero;
            case "titanshero" -> TitansHero;
            case "winnuhero" -> WinnuHero;
            case "xxchahero" -> XxchaHero;
            case "yinhero" -> YinHero;
            case "yssarilhero" -> YssarilHero;

            // DS LEADERS
            case "augersagent" -> AugersAgent;
            case "augerscommander" -> AugersCommander;
            case "augershero" -> AugersHero;
            case "axisagent" -> AxisAgent;
            case "axiscommander" -> AxisCommander;
            case "axishero" -> AxisHero;
            case "bentoragent" -> BentorAgent;
            case "bentorcommander" -> BentorCommander;
            case "bentorhero" -> BentorHero;
            case "kyroagent" -> KyroAgent;
            case "kyrocommander" -> KyroCommander;
            case "kyrohero" -> KyroHero;
            case "celdauriagent" -> CeldauriAgent;
            case "celdauricommander" -> CeldauriCommander;
            case "celdaurihero" -> CeldauriHero;
            case "cheiranagent" -> CheiranAgent;
            case "cheirancommander" -> CheiranCommander;
            case "cheiranhero" -> CheiranHero;
            case "gheminaagent" -> GheminaAgent;
            case "gheminacommander" -> GheminaCommander;
            case "gheminaherolady" -> GheminaHeroLady;
            case "gheminaherolord" -> GheminaHeroLord;
            case "cymiaeagent" -> CymiaeAgent;
            case "cymiaecommander" -> CymiaeCommander;
            case "cymiaehero" -> CymiaeHero;
            case "dihmohnagent" -> DihmohnAgent;
            case "dihmohncommander" -> DihmohnCommander;
            case "dihmohnhero" -> DihmohnHero;
            case "edynagent" -> EdynAgent;
            case "edyncommander" -> EdynCommander;
            case "edynhero" -> EdynHero;
            case "florzenagent" -> FlorzenAgent;
            case "florzencommander" -> FlorzenCommander;
            case "florzenhero" -> FlorzenHero;
            case "freesystemcommander" -> FreesystemCommander;
            case "freesystemhero" -> FreesystemHero;
            case "freesystemsagent" -> FreesystemsAgent;
            case "ghotiagent" -> GhotiAgent;
            case "ghoticommander" -> GhotiCommander;
            case "ghotihero" -> GhotiHero;
            case "gledgeagent" -> GledgeAgent;
            case "gledgecommander" -> GledgeCommander;
            case "gledgehero" -> GledgeHero;
            case "khraskagent" -> KhraskAgent;
            case "khraskcommander" -> KhraskCommander;
            case "khraskhero" -> KhraskHero;
            case "kjalengardagent" -> KjalengardAgent;
            case "kjalengardcommander" -> KjalengardCommander;
            case "kjalengardhero" -> KjalengardHero;
            // case "kollecagent" -> "";
            case "kollecccommander" -> KolleccCommander;
            case "kollecchero" -> KolleccHero;
            case "kolumeagent" -> KolumeAgent;
            case "kolumecommander" -> KolumeCommander;
            case "kolumehero" -> KolumeHero;
            case "kortaliagent" -> KortaliAgent;
            case "kortalicommander" -> KortaliCommander;
            case "kortalihero" -> KortaliHero;
            case "lanefiragent" -> LanefirAgent;
            case "lanefircommander" -> LanefirCommander;
            case "uydaiagent" -> UydaiAgent;
            case "uydaicommander" -> UydaiCommander;
            case "uydaihero" -> UydaiHero;
            case "pharadnagent" -> PharadnAgent;
            case "pharadncommander" -> PharadnCommander;
            case "pharadnhero" -> PharadnHero;
            // case "lanefirhero" -> "";
            default -> TI4Emoji.getRandomGoodDog(leader.toLowerCase());
        };
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
