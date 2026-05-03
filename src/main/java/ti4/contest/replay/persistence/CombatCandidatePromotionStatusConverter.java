package ti4.contest.replay.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;

@Converter(autoApply = true)
public class CombatCandidatePromotionStatusConverter
        implements AttributeConverter<CombatCandidatePromotionStatus, String> {

    @Override
    public String convertToDatabaseColumn(CombatCandidatePromotionStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CombatCandidatePromotionStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CombatCandidatePromotionStatus.valueOf(dbData);
    }
}
