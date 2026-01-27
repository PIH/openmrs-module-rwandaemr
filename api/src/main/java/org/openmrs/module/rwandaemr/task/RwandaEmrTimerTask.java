package org.openmrs.module.rwandaemr.task;

import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.TimerTask;

/**
 * Abstract class for a timer task that utilises a daemon thread
 */
public class RwandaEmrTimerTask extends TimerTask {

    private static final Logger log = LoggerFactory.getLogger(RwandaEmrTimerTask.class);

    private static DaemonToken daemonToken;
    public static void setDaemonToken(DaemonToken daemonToken) {
        RwandaEmrTimerTask.daemonToken = daemonToken;
    }

    private static boolean enabled = false;
    public static void setEnabled(boolean enabled) {
        RwandaEmrTimerTask.enabled = enabled;
        log.warn("RwandaEmrTimerTask enabled");
    }
    public static boolean isEnabled() {
        return enabled;
    }

    private Class<? extends Runnable> taskClass;

    public RwandaEmrTimerTask(Class<? extends Runnable> taskClass) {
        this.taskClass = taskClass;
    }

    /**
     * Check if this is an HIE-related task that requires HIE to be enabled
     */
    private boolean isHieRelatedTask() {
        String className = taskClass.getSimpleName();
        return className.equals("UpdateShrEncounterTask") ||
               className.equals("UpdateShrObsTask") ||
               className.equals("UpdateClientRegistryTask");
    }

    /**
     * @see TimerTask#run()
     */
    @Override
    public final void run() {
        if (daemonToken != null && enabled) {
            try {
                // For HIE-related tasks, check if HIE is enabled before creating daemon thread
                // This prevents unnecessary thread creation when HIE is disabled
                if (isHieRelatedTask()) {
                    try {
                        List<IntegrationConfig> configs = Context.getRegisteredComponents(IntegrationConfig.class);
                        if (configs == null || configs.isEmpty() || !configs.get(0).isHieEnabled()) {
                            log.debug("Skipping HIE task " + taskClass.getSimpleName() + " - HIE is not enabled");
                            return;
                        }
                    } catch (Exception e) {
                        // If we can't check HIE status, log and skip to prevent issues
                        log.debug("Unable to check HIE status for task " + taskClass.getSimpleName() + ", skipping: " + e.getMessage());
                        return;
                    }
                }
                
                log.debug("Running task: " + taskClass.getSimpleName());
                Runnable taskInstance = taskClass.getDeclaredConstructor().newInstance();
                Daemon.runInDaemonThread(taskInstance, daemonToken);
            }
            catch (Exception e) {
                log.error("An error occurred while running scheduled task " + taskClass.getSimpleName(), e);
            }
        }
        else {
            log.debug("Not running scheduled task. DaemonToken = " + daemonToken + "; enabled = " + enabled);
        }
    }
}
