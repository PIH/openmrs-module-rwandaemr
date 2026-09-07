package org.openmrs.module.rwandaemr.labs;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.module.pihapps.labs.LabIdGenerator;
import org.openmrs.module.rwandaemr.LabIdSequenceValue;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Generates Lab IDs of the form FOSA_ID-YYYYMMDD-N, where N is a sequence that resets daily
 */
@Component
public class RwandaLabIdGenerator implements LabIdGenerator {

    private final IntegrationConfig integrationConfig;

    private final RwandaEmrService rwandaEmrService;

    public RwandaLabIdGenerator(@Autowired IntegrationConfig integrationConfig,
                                 @Autowired RwandaEmrService rwandaEmrService) {
        this.integrationConfig = integrationConfig;
        this.rwandaEmrService = rwandaEmrService;
    }

    @Override
    public boolean isEnabled(Location sessionLocation) {
        return StringUtils.isNotBlank(integrationConfig.getFosaId(sessionLocation));
    }

    @Override
    public String generateLabId(Location sessionLocation) {
        String fosaId = integrationConfig.getFosaId(sessionLocation);
        if (StringUtils.isBlank(fosaId)) {
            String locationName = sessionLocation != null ? sessionLocation.getName() : "?";
            throw new IllegalStateException("Unable to generate Lab ID: no FOSA ID is configured for location " + locationName);
        }
        LabIdSequenceValue sequence = rwandaEmrService.getNextLabIdSequenceValueForToday();
        return fosaId.trim() + "-" + sequence.getDateString() + "-" + pad(sequence.getSequenceValue());
    }

    static String pad(int sequenceValue) {
        String s = String.valueOf(sequenceValue);
        return s.length() >= 4 ? s : StringUtils.leftPad(s, 4, '0');
    }
}
