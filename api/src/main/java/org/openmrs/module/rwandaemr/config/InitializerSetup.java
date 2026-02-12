package org.openmrs.module.rwandaemr.config;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.initializer.api.ConfigDirUtil;
import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.initializer.api.loaders.BaseFileLoader;
import org.openmrs.module.initializer.api.loaders.Loader;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Custom loader for initializer configurations which enables us to do some things that iniz does not do by default
 * 1. Fail hard if there are any errors, rather than ignore them
 * 2. Install configurations at the time when we are ready, not in the Iniz activator
 * 3. Install only some configurations based on how the current server is configured
 */
@Component
public class InitializerSetup implements Setup {

    protected static Log log = LogFactory.getLog(InitializerSetup.class);

    public static List<Domain> ALWAYS_RELOAD = Collections.singletonList(Domain.LOCATION_TAG_MAPS);

    private final ServerSetup serverSetup;

    @Autowired
    public InitializerSetup(ServerSetup serverSetup) {
        this.serverSetup = serverSetup;
    }

    @Override
    public void initialize() {
        try {
            for (Domain domain : ALWAYS_RELOAD) {
                log.warn("Deleting checksums to force reloading of: " + domain);
                deleteChecksumsForDomains(domain);
            }
            List<String> sitesForServer = getSitesForServer();
            for (Loader loader : getInitializerService().getLoaders()) {
                log.warn("Loading from Initializer: " + loader.getDomainName());
                List<String> exclusionsForLoader = getExclusionsForLoader(loader, sitesForServer);
                loader.loadUnsafe(exclusionsForLoader, true);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("An error occurred while loading from initializer", e);
        }
    }

    /**
     * The purpose of this method is to determine if there are any site-specific configuration files,
     * and if so, to exclude them if they are not intended for the specific config in use.
     * Any config files that contain a "-site-", and which do not end with the site name, are excluded
     */
    public List<String> getExclusionsForLoader(Loader loader, List<String> sitesForServer) {
        List<String> exclusions = new ArrayList<>();
        if (loader instanceof BaseFileLoader) {
            BaseFileLoader ll = (BaseFileLoader) loader;
            for (File f : ll.getDirUtil().getFiles("csv")) {
                String filename = f.getName().toLowerCase();
                if (!fileIsValidForSite(filename, sitesForServer)) {
                    log.debug("Excluding site-specific configuration file: " + filename);
                    exclusions.add(filename);
                }
            }
        }
        return exclusions;
    }

    public boolean fileIsValidForSite(String filename, List<String> sitesForServer) {
        if (!filename.contains("-site-")) {
            return true;
        }
        for (String site : sitesForServer) {
            if (filename.toLowerCase().endsWith("-site-" + site.toLowerCase() + ".csv")) {
                return true;
            }
        }
        return false;
    }

    public List<String> getSitesForServer() {
        List<String> ret = new ArrayList<>();
        String site = serverSetup.getServerName();
        Properties p = getSiteConfigProperties();
        String sitesForServer = p.getProperty(site);
        if (StringUtils.isNotBlank(sitesForServer)) {
            ret.addAll(Arrays.asList(sitesForServer.split(",")));
        }
        ret.add(site);
        return ret;
    }

    public Properties getSiteConfigProperties() {
        Properties p = new Properties();
        File configDir = OpenmrsUtil.getDirectoryInApplicationDataDirectory("configuration");
        File rwandaEmrDir = new File(configDir, "rwandaemr");
        File configFile = new File(rwandaEmrDir, "site-config.properties");
        if (configFile.exists()) {
            try (InputStream is = Files.newInputStream(configFile.toPath())) {
                p.load(is);
            }
            catch (Exception e) {
                throw new IllegalStateException("An error occurred while loading site-config.properties", e);
            }
        }
        return p;
    }

    /**
     * Deletes the checksum files for the given domains
     * @param domains the domains for which to delete the checksum files
     */
    public void deleteChecksumsForDomains(Domain... domains) {
        String configDirPath = getInitializerService().getConfigDirPath();
        String checksumsDirPath = getInitializerService().getChecksumsDirPath();
        for (Domain domain : domains) {
            ConfigDirUtil util = new ConfigDirUtil(configDirPath, checksumsDirPath, domain.getName(), false);
            util.deleteChecksums();
        }
    }

    protected static InitializerService getInitializerService() {
        return Context.getService(InitializerService.class);
    }
}
