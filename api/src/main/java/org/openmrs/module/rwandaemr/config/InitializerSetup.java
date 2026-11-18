package org.openmrs.module.rwandaemr.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.initializer.api.ConfigDirUtil;
import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.initializer.api.loaders.Loader;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Custom loader for initializer configurations which enables us to do some things that iniz does not do by default
 * 1. Fail hard if there are any errors, rather than ignore them
 * 2. Install configurations at the time when we are ready, not in the Iniz activator
 */
@Component
public class InitializerSetup implements Setup {

    protected static Log log = LogFactory.getLog(InitializerSetup.class);

    public static List<Domain> ALWAYS_RELOAD = Collections.singletonList(Domain.LOCATION_TAG_MAPS);

    @Override
    public void initialize() {
        try {
            for (Domain domain : ALWAYS_RELOAD) {
                log.warn("Deleting checksums to force reloading of: " + domain);
                deleteChecksumsForDomains(domain);
            }
            for (Loader loader : getInitializerService().getLoaders()) {
                log.warn("Loading from Initializer: " + loader.getDomainName());
                loader.loadUnsafe(Collections.emptyList(), true);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("An error occurred while loading from initializer", e);
        }
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
