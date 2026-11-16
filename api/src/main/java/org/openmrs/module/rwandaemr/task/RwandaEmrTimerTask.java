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
        log.warn("RwandaEmrTimerTask enabled set to {}", enabled);
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
                final String taskName = taskClass.getSimpleName();
                Daemon.runInDaemonThread(() -> {
                    try {
                        final long startedAt = System.currentTimeMillis();
                        Context.openSession();
                        try {
                            // Keep HIE pre-check, but execute it where user context/session are available.
                            if (isHieRelatedTask()) {
                                List<IntegrationConfig> configs = Context.getRegisteredComponents(IntegrationConfig.class);
                                if (configs == null || configs.isEmpty() || !configs.get(0).isHieEnabled()) {
                                    log.warn("Skipping HIE task {} - HIE is not enabled or IntegrationConfig is unavailable (configs null/empty={})",
                                            taskName, (configs == null || configs.isEmpty()));
                                    return;
                                }
                            }

                            log.info("Starting scheduled task: {}", taskName);
                            Runnable taskInstance = taskClass.getDeclaredConstructor().newInstance();
                            taskInstance.run();
                            long durationMs = System.currentTimeMillis() - startedAt;
                            log.info("Completed scheduled task: {} in {} ms", taskName, durationMs);
                        } finally {
                            Context.closeSession();
                        }
                    } catch (Exception e) {
                        log.error("An error occurred while running scheduled task {}", taskName, e);
                    }
                }, daemonToken);
            }
            catch (Exception e) {
                log.error("An error occurred while running scheduled task " + taskClass.getSimpleName(), e);
            }
        }
        else {
            log.warn("Not running scheduled task {}. daemonTokenPresent={}, enabled={}",
                    taskClass.getSimpleName(), daemonToken != null, enabled);
        }
    }
}
