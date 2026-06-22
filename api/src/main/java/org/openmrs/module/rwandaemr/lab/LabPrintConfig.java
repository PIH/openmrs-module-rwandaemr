package org.openmrs.module.rwandaemr.lab;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.util.ConfigUtil;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Base64;

/**
 * Configuration for printed lab result reports.
 * Properties use a rwandaemr.lab.print.* prefix and fall back to legacy module properties.
 */
@Component
public class LabPrintConfig {

    protected final Log log = LogFactory.getLog(getClass());

    public static final String PROP_FACILITY_NAME    = "rwandaemr.lab.print.facilityName";
    public static final String PROP_FACILITY_ADDRESS = "rwandaemr.lab.print.facilityAddress";
    public static final String PROP_FACILITY_EMAIL   = "rwandaemr.lab.print.facilityEmail";
    public static final String PROP_FACILITY_PHONE   = "rwandaemr.lab.print.facilityPhone";
    public static final String PROP_FACILITY_LOGO    = "rwandaemr.lab.print.facilityLogo";
    public static final String PROP_LAB_TECH_NAME    = "rwandaemr.lab.print.labTechName";
    public static final String PROP_LAB_TECH_STAMP   = "rwandaemr.lab.print.labTechStamp";

    public String getFacilityName() {
        return firstNonBlank(
                ConfigUtil.getProperty(PROP_FACILITY_NAME),
                ConfigUtil.getProperty("laboratorymodule.healthfacility.name"),
                ConfigUtil.getProperty("billing.healthFacilityName"));
    }

    public String getFacilityAddress() {
        return firstNonBlank(
                ConfigUtil.getProperty(PROP_FACILITY_ADDRESS),
                ConfigUtil.getProperty("billing.healthFacilityPhysicalAddress"),
                ConfigUtil.getProperty("laboratorymodule.healthfacility.POBOX"));
    }

    public String getFacilityEmail() {
        return firstNonBlank(
                ConfigUtil.getProperty(PROP_FACILITY_EMAIL),
                ConfigUtil.getProperty("laboratorymodule.healthfacility.email"),
                ConfigUtil.getProperty("billing.healthFacilityEmail"));
    }

    public String getFacilityPhone() {
        return firstNonBlank(
                ConfigUtil.getProperty(PROP_FACILITY_PHONE),
                ConfigUtil.getProperty("laboratorymodule.healthfacility.telephone"));
    }

    public ImageData getFacilityLogo() {
        return loadImage(firstNonBlank(
                ConfigUtil.getProperty(PROP_FACILITY_LOGO),
                ConfigUtil.getProperty("billing.healthFacilityLogo")));
    }

    public String getLabTechName() {
        return firstNonBlank(ConfigUtil.getProperty(PROP_LAB_TECH_NAME));
    }

    public ImageData getLabTechStamp() {
        return loadImage(ConfigUtil.getProperty(PROP_LAB_TECH_STAMP));
    }

    private ImageData loadImage(String relativePath) {
        if (StringUtils.isBlank(relativePath)) return null;
        try {
            File file = new File(OpenmrsUtil.getApplicationDataDirectory(), relativePath);
            if (!file.exists() || !file.isFile()) return null;
            String mimeType = mimeTypeForFile(file);
            if (mimeType == null) return null;
            byte[] bytes = FileUtils.readFileToByteArray(file);
            return new ImageData(Base64.getEncoder().encodeToString(bytes), mimeType);
        } catch (Exception e) {
            log.warn("Failed to load lab print image from path: " + relativePath, e);
            return null;
        }
    }

    private String mimeTypeForFile(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) return v;
        }
        return null;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ImageData {
        private final String data;
        private final String mimeType;
    }
}
