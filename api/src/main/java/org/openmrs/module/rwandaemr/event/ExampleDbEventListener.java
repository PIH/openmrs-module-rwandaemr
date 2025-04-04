package org.openmrs.module.rwandaemr.event;

import org.apache.commons.io.FileUtils;
import org.openmrs.module.dbevent.DbEvent;
import org.openmrs.module.dbevent.DbEventListener;
import org.openmrs.module.dbevent.Operation;
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * DbEventListener which demonstrates what is available
 */
public class ExampleDbEventListener extends DbEventListener {

    File logFile = new File(OpenmrsUtil.getApplicationDataDirectoryAsFile(), "dbevent.log");

    @Override
    public void processEvent(DbEvent e) {
        StringBuilder sb = new StringBuilder();
        Date eventDate = new Date();
        eventDate.setTime(eventDate.getTime());
        sb.append(eventDate).append('\t');
        sb.append(e.getOperation()).append('\t');
        sb.append(e.getTable()).append('\t');
        sb.append(e.getKey()).append('\t');
        Map<String, String> changes = new TreeMap<>();
        for (String column : e.getValues().keySet()) {
            Object beforeValue = e.getBefore().get(column);
            Object afterValue = e.getAfter().get(column);
            if (!Objects.equals(beforeValue, afterValue)) {
                String before = beforeValue == null ? "null" : beforeValue.toString().replace(System.lineSeparator(), " ");
                if (before.length() > 100) {
                    before = before.substring(0, 100) + "...";
                }
                String after = afterValue == null ? "null" : afterValue.toString().replace(System.lineSeparator(), " ");
                if (after.length() > 100) {
                    after = after.substring(0, 100) + "...";
                }
                if (e.getOperation() == Operation.INSERT) {
                    changes.put(column, after);
                }
                else if (e.getOperation() == Operation.UPDATE) {
                    changes.put(column, before + " -> " + after);
                }
            }
        }
        sb.append(changes).append(System.lineSeparator());
        try {
            FileUtils.writeStringToFile(logFile, sb.toString(), StandardCharsets.UTF_8, true);
        }
        catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }
}
