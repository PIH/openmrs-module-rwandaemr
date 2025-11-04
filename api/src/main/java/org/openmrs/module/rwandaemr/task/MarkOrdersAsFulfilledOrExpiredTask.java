package org.openmrs.module.rwandaemr.task;

import org.apache.commons.lang.time.StopWatch;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.ImmutableOrderInterceptor;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task which identifies any Lab Order which needs to be marked as fulfilled or expired
 *  - If there is an observation recorded against the Lab Order, then mark its fulfillerStatus as COMPLETED
 *  - If there are no observations recorded against it, and it is still active, and it has been open for more days
 *    than the allowable threshold, mark the order as expired.
 *  This corresponds with the liquibase changeset and file under sql/migrations/update-non-active-lab-orders.sql
 */
public class MarkOrdersAsFulfilledOrExpiredTask implements Runnable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static boolean isExecuting = false;

    @Override
    public void run() {
        if (isExecuting) {
            log.info(getClass() + " is still executing, not running again");
            return;
        }
        isExecuting = true;
        try {
            log.info("Executing " + getClass());
            RwandaEmrService rwandaEmrService = Context.getService(RwandaEmrService.class);

            log.debug("Completing unfulfilled lab orders with associated observations");
            StopWatch sw = new StopWatch();
            sw.start();
            int numCompleted = rwandaEmrService.markLabOrdersAsCompleted();
            sw.stop();
            if (numCompleted > 0) {
                log.info("{} lab orders were completed in {}", numCompleted, sw);
            }
            else {
                log.debug("No lab orders were completed in {}", sw);
            }

            log.debug("Expiring lab orders that have been active for too long");
            sw.reset();
            sw.start();

            ImmutableOrderInterceptor interceptor = Context.getRegisteredComponent("immutableOrderInterceptor", ImmutableOrderInterceptor.class);
            try {
                interceptor.addMutablePropertiesForThread("autoExpireDate");
                int numExpired = rwandaEmrService.markLabOrdersAsExpired();
                if (numExpired > 0) {
                    log.info("{} lab orders were expired in {}", numExpired, sw);
                }
                else {
                    log.debug("No lab orders were expired in {}", sw);
                }
            }
            finally {
                interceptor.removeMutablePropertiesForThread();
            }
            sw.stop();
        }
        finally {
            isExecuting = false;
        }
    }
}
