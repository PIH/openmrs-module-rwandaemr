package org.openmrs.module.rwandaemr.task;

import org.openmrs.api.context.Daemon;
import org.openmrs.module.DaemonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * @see TimerTask#run()
     */
    @Override
    public final void run() {
        if (daemonToken != null && enabled) {
            try {
                log.debug("Running task: " + taskClass.getSimpleName());
                Runnable taskInstance = taskClass.newInstance();
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
