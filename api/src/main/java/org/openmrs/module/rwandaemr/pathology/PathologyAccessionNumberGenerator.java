package org.openmrs.module.rwandaemr.pathology;

import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.openmrs.GlobalProperty;
import org.openmrs.api.APIException;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Generates pathology accession numbers, such as PAT2026-0001: the year and a sequence that starts again each year.  The
 * number has to fit on a glass slide.
 */
@Component
public class PathologyAccessionNumberGenerator {

	public static final String GP_NEXT_SEQUENCE_VALUE = "rwandaemr.pathology.accessionNextSequenceValue";

	public static final String PREFIX = "PAT";

	private final DbSessionFactory dbSessionFactory;

	public PathologyAccessionNumberGenerator(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * @return the next accession number.  The sequence is kept in a global property, locked while it is read and updated,
	 * so that no two cases get the same number.  It is updated in a transaction of its own, so that the lock is not held
	 * while the case is saved; a number is therefore skipped if the case then fails to save.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public synchronized String next() {
		String year = new SimpleDateFormat("yyyy", Locale.ENGLISH).format(new Date());
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		GlobalProperty gp = session.get(GlobalProperty.class, GP_NEXT_SEQUENCE_VALUE, LockOptions.UPGRADE);
		if (gp == null) {
			gp = new GlobalProperty(GP_NEXT_SEQUENCE_VALUE, "");
		}
		int nextValue = getNextValue(gp.getPropertyValue(), year);
		gp.setPropertyValue(year + ":" + (nextValue + 1));
		session.saveOrUpdate(gp);
		return format(year, nextValue);
	}

	/**
	 * @return the sequence value to use in the given year, from the stored value year:nextValue.  The sequence starts at 1
	 * in a new year.
	 */
	static int getNextValue(String storedValue, String year) {
		if (storedValue != null && storedValue.contains(":")) {
			String[] parts = storedValue.split(":", 2);
			if (parts[0].trim().equals(year)) {
				try {
					return Integer.parseInt(parts[1].trim());
				}
				catch (NumberFormatException e) {
					throw new APIException("Invalid value '" + storedValue + "' for global property named: "
							+ GP_NEXT_SEQUENCE_VALUE, e);
				}
			}
		}
		return 1;
	}

	static String format(String year, int sequenceValue) {
		return PREFIX + year + "-" + String.format(Locale.ENGLISH, "%04d", sequenceValue);
	}
}
