package ti4.service.emoji;

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
    /*KolleccAgent,*/ KolleccCommander, KolleccHero, //
    KolumeAgent, KolumeCommander, KolumeHero, //
    KortaliAgent, KortaliCommander, KortaliHero, //
    LanefirAgent, LanefirCommander, /*LanefirHero,*/ //

    // Generic
    Agent, Commander, Hero, Envoy;

    @Override
    public String toString() {
        return emojiString();
    }
}
