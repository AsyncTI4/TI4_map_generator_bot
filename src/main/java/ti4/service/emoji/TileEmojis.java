package ti4.service.emoji;

import javax.annotation.Nullable;

import ti4.image.TileHelper;
import ti4.model.TileModel;

public enum TileEmojis implements TI4Emoji {

    // tile backs
    TileGreenBack, TileRedBack, TileBlueBack,

    // base
    Jord_01, MollPrimus_02, Darien_03, Muaat_04, Nestphar_05, L1_000_06, Winnu_07, MordaiII_08, //
    Maaluuk_09, ArcPime_10, LisisII_11, Nar_12, Trenlak_13, ArchonRen_14, Retillion_15, Arretze_16, //
    DeltaWH_17, MR_18, Wellon_19, VefutII_20, Thibah_21, Tarmann_22, Saudor_23, MeharXull_24, //
    Quann_25, Lodor_26, NewAlbion_27, Tequran_28, Qucenn_29, Mellon_30, Lazar_31, DalBootha_32, //
    Corneeq_33, Centauri_34, Bereg_35, Arnor_36, Arinam_37, Abyz_38, AlphaWH_39, BetaWH_40, //
    GravityRift_41, Nebula_42, Supernova_43, Asteroids_44, Asteroids_45, Void_46, Void_47, Void_48, //
    Void_49, Void_50, Creuss_51,

    // pok
    Ixth_52, Arcturus_53, Acheron_54, Elysium_55, TheDark_56, NaazirRokha_57, YlirAvarValk_58, //
    ArchonVail_59, Perimeter_60, Ang_61, SemLore_62, Vorhal_63, Atlas_64, Primor_65, HopesEnd_66, //
    Cormund_67, Everra_68, AccoenJeolIr_69, KraagSiig_70, BakalAlioPrima_71, LisisVelnor_72, //
    CealdriXanhact_73, Vegas_74, Devils_75, Rigels_76, Void_77, Void_78, AsteroidsAlphaWH_79, //
    Supernova_80, NovaSeed_81, MalliceLocked_82a, MalliceUnlocked_82b

    // codex 3

    // discordant stars

    // uncharted space
    ;

    @Override
    public String toString() {
        return emojiString();
    }

    @Nullable
    public static TI4Emoji getTileEmojiFromTileID(String tileID) {
        return switch (tileID) {
            case "01" -> Jord_01;
            case "02" -> MollPrimus_02;
            default -> getTileBackEmojiFromTileID(tileID);
        };
    }

    @Nullable
    public static TI4Emoji getTileBackEmojiFromTileID(String tileID) {
        if (!TileHelper.isValidTile(tileID)) return null;
        TileModel tileModel = TileHelper.getTileById(tileID);
        if (tileModel.getTileBackOption().isEmpty()) return null;

        return switch (tileModel.getTileBack()) {
            case "green" -> TileGreenBack;
            case "blue" -> TileBlueBack;
            case "red" -> TileRedBack;
            default -> null;
        };
    }
}
