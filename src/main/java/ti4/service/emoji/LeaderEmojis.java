package ti4.service.emoji;

import ti4.map.Leader;

public enum LeaderEmojis implements TI4Emoji {
    // Official Content
    ArborecAgent,
    ArborecCommander,
    ArborecHero,
    ArgentAgent,
    ArgentCommander,
    ArgentHero,
    CabalAgent,
    CabalCommander,
    CabalHero,
    GhostAgent,
    GhostCommander,
    GhostHero,
    EmpyreanAgent,
    EmpyreanCommander,
    EmpyreanHero,
    HacanAgent,
    HacanCommander,
    HacanHero,
    JolNarAgent,
    JolNarCommander,
    JolNarHero,
    L1Z1XAgent,
    L1Z1XCommander,
    L1Z1XHero,
    LetnevAgent,
    LetnevCommander,
    LetnevHero,
    MahactAgent,
    MahactCommander,
    MahactHero,
    MentakAgent,
    MentakCommander,
    MentakHero,
    MuaatAgent,
    MuaatCommander,
    MuaatHero,
    NaaluAgent,
    NaaluCommander,
    NaaluHero,
    NaazAgent,
    NaazCommander,
    NaazHero,
    NekroAgent,
    NekroCommander,
    NekroHero,
    NomadAgentArtuno,
    NomadAgentMercer,
    NomadAgentThundarian,
    NomadCommander,
    NomadHero,
    KeleresAgent,
    KeleresCommander,
    KeleresHeroKuuasi,
    KeleresHeroHarka,
    KeleresHeroOdlynn,
    SaarAgent,
    SaarCommander,
    SaarHero,
    SardakkAgent,
    SardakkCommander,
    SardakkHero,
    SolAgent,
    SolCommander,
    SolHero,
    TitansAgent,
    TitansCommander,
    TitansHero,
    WinnuAgent,
    WinnuCommander,
    WinnuHero,
    XxchaAgent,
    XxchaCommander,
    XxchaHero,
    YinAgent,
    YinCommander,
    YinHero,
    YssarilAgent,
    YssarilCommander,
    YssarilHero,
    // Codex 4 Liberation Scenarion
    RedCreussAgent,
    RedCreussCommander,
    RedCreussHero,
    OrlandoHero,
    // Thunders Edge
    BastionAgent,
    BastionCommander,
    BastionHero,
    CrimsonAgent,
    CrimsonCommander,
    CrimsonHero,
    DeepwroughtAgent,
    DeepwroughtCommander,
    DeepwroughtHero,
    FirmamentAgent,
    FirmamentCommander,
    FirmamentHero,
    ObsidianAgent,
    ObsidianCommander,
    ObsidianHero,
    RalnelAgent,
    RalnelCommander,
    RalnelHero,

    // Discordant Stars
    AugersAgent,
    AugersCommander,
    AugersHero,
    AxisAgent,
    AxisCommander,
    AxisHero,
    BentorAgent,
    BentorCommander,
    BentorHero,
    KyroAgent,
    KyroCommander,
    KyroHero,
    CeldauriAgent,
    CeldauriCommander,
    CeldauriHero,
    CheiranAgent,
    CheiranCommander,
    CheiranHero,
    GheminaAgent,
    GheminaCommander,
    GheminaHeroLady,
    GheminaHeroLord,
    CymiaeAgent,
    CymiaeCommander,
    CymiaeHero,
    DihmohnAgent,
    DihmohnCommander,
    DihmohnHero,
    EdynAgent,
    EdynCommander,
    EdynHero,
    FlorzenAgent,
    FlorzenCommander,
    FlorzenHero,
    FreesystemsAgent,
    FreesystemsCommander,
    FreesystemsHero,
    GhotiAgent,
    GhotiCommander,
    GhotiHero,
    GledgeAgent,
    GledgeCommander,
    GledgeHero,
    KhraskAgent,
    KhraskCommander,
    KhraskHero,
    KjalengardAgent,
    KjalengardCommander,
    KjalengardHero,
    KolleccAgent,
    KolleccCommander,
    KolleccHero,
    KolumeAgent,
    KolumeCommander,
    KolumeHero,
    KortaliAgent,
    KortaliCommander,
    KortaliHero,
    LanefirAgent,
    LanefirCommander,
    LanefirHero,
    LiZhoAgent,
    LiZhoCommander,
    LiZhoHero,
    MirvedaAgent,
    MirvedaCommander,
    MirvedaHero,
    MortheusAgent,
    MortheusCommander,
    MortheusHero,
    MykoMentoriAgent,
    MykoMentoriCommander,
    MykoMentoriHero,
    NivynAgent,
    NivynCommander,
    NivynHero,
    NokarAgent,
    NokarCommander,
    NokarHero,
    OlradinAgent,
    OlradinCommander,
    OlradinHero,
    RohDhnaAgent,
    RohDhnaCommander,
    RohDhnaHero,
    TnelisAgent,
    TnelisCommander,
    TnelisHero,
    VadenAgent,
    VadenCommander,
    VadenHero,
    VaylerianAgent,
    VaylerianCommander,
    VaylerianHero,
    VeldyrAgent,
    VeldyrCommander,
    VeldyrHero,
    ZealotsAgent,
    ZealotsCommander,
    ZealotsHero,
    ZelianAgent,
    ZelianCommander,
    ZelianHero,

    // Blue Reverie
    AtokeraAgent,
    AtokeraCommander,
    AtokeraHero,
    BelkoseaAgent,
    BelkoseaCommander,
    BelkoseaHero,
    KaltrimAgent,
    KaltrimCommander,
    KaltrimHero,
    PharadnAgent,
    PharadnCommander,
    PharadnHero,
    QhetAgent,
    QhetCommander,
    QhetHero,
    SarcosaAgent,
    SarcosaCommander,
    SarcosaHero,
    ToldarAgent,
    ToldarCommander,
    ToldarHero,
    UydaiAgent,
    UydaiCommander,
    UydaiHero,
    XinAgent,
    XinCommander,
    XinHero,

    // Generic
    Agent,
    Commander,
    Hero,
    Envoy;

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
        for (LeaderEmojis e : values()) {
            if (leader.equalsIgnoreCase(e.name())) return e;
        }

        return switch (leader.toLowerCase()) {
            // Codex Updates
            case "xxchahero-te" -> XxchaHero;
            case "naaluagent-te" -> NaaluAgent;

            // TF Genomes
            case "experimentalagent" -> JolNarAgent;
            case "hyperagent" -> MentakAgent;
            case "researchagent" -> DeepwroughtAgent;
            case "valiantagent" -> BastionAgent;

            // TF Paradigms
            case "brilliancehero" -> JolNarHero;
            case "devourhero" -> NekroHero;
            case "sanctionhero" -> HacanHero;
            case "eternityhero" -> ObsidianHero;
            case "eventhero" -> CabalHero;
            case "forgehero" -> NaazHero;
            case "lawshero" -> DeepwroughtHero;
            case "poisonhero" -> NaaluHero;
            case "voicehero" -> XxchaHero;
            case "witchinghero" -> KeleresHeroOdlynn;

            default -> TI4Emoji.getRandomGoodDog(leader.toLowerCase());
        };
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
