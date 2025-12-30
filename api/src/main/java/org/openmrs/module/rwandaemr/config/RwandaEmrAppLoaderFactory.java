package org.openmrs.module.rwandaemr.config;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appframework.domain.AppTemplate;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.factory.AppFrameworkFactory;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class RwandaEmrAppLoaderFactory implements AppFrameworkFactory {

    protected Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RwandaEmrAppLoaderFactory() {
        // Tell the parser to all // and /* style comments.
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    }

    private List<File> getConfigFilesBySuffix(String suffix) {
        File configDir = OpenmrsUtil.getDirectoryInApplicationDataDirectory("configuration");
        File appFrameworkDir = new File(configDir, "appframework");
        return getNestedFilesBySuffix(appFrameworkDir, suffix);
    }

    public List<File> getNestedFilesBySuffix(File directory, String suffix) {
        List<File> files = new ArrayList<>();
        if (directory.exists() && directory.isDirectory()) {
            File[] filesInDirectory = directory.listFiles();
            if (filesInDirectory != null) {
                for (File f : filesInDirectory) {
                    if (f.isDirectory()) {
                        files.addAll(getNestedFilesBySuffix(f, suffix));
                    } else {
                        if (f.getName().endsWith(suffix)) {
                            files.add(f);
                        }
                    }
                }
            }
        }
        return files;
    }

    @Override
    public List<AppTemplate> getAppTemplates() {
        List<AppTemplate> templates = new ArrayList<>();
        for (File f : getConfigFilesBySuffix("AppTemplates.json")) {
            try {
                List<AppTemplate> l = objectMapper.readValue(f, new TypeReference<List<AppTemplate>>() {});
                templates.addAll(l);
            }
            catch (Exception e) {
                logger.error("Error reading AppTemplates configuration file: {}", f.getName(), e);
            }
        }
        return templates;
    }

    @Override
    public List<AppDescriptor> getAppDescriptors() {
        List<AppDescriptor> descriptors = new ArrayList<>();
        for (File f : getConfigFilesBySuffix("app.json")) {
            try {
                List<AppDescriptor> l = objectMapper.readValue(f, new TypeReference<List<AppDescriptor>>() {});
                descriptors.addAll(l);
            }
            catch (Exception e) {
                logger.error("Error reading AppDescriptors configuration file: {}", f.getName(), e);
            }
        }
        return descriptors;
    }

    @Override
    public List<Extension> getExtensions() {
        List<Extension> extensions = new ArrayList<>();
        for (File f : getConfigFilesBySuffix("extension.json")) {
            try {
                List<Extension> l = objectMapper.readValue(f, new TypeReference<List<Extension>>() {});
                extensions.addAll(l);
            }
            catch (Exception e) {
                logger.error("Error reading Extension configuration file: {}", f.getName(), e);
            }
        }
        return extensions;
    }
}