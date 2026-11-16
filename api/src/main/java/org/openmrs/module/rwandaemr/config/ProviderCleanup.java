package org.openmrs.module.rwandaemr.config;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * For any provider that is not associated with a person, this creates a person record and associates it with the provider
 */
@Component
public class ProviderCleanup implements Setup {

    protected Logger log = LoggerFactory.getLogger(getClass());

    private final ProviderService providerService;
    private final PersonService personService;
    private final AdministrationService administrationService;

    @Autowired
    public ProviderCleanup(ProviderService providerService, PersonService personService,
                           @Qualifier("adminService")  AdministrationService administrationService) {
        this.providerService = providerService;
        this.personService = personService;
        this.administrationService = administrationService;
    }

    public void initialize() {
        String sql = "select provider_id, name from provider where person_id is null";
        List<List<Object>> providersWithoutPersons = administrationService.executeSQL(sql, true);
        if (providersWithoutPersons.isEmpty()) {
            log.debug("No providers without persons found");
        }
        else {
            log.warn("Found {} providers without persons.  Creating person records for each",  providersWithoutPersons.size());
            for (List<Object> providerRow : providersWithoutPersons) {
                Integer providerId = (Integer) providerRow.get(0);
                String providerName = (String) providerRow.get(1);
                log.warn("Provider {} - {} is not associated with a person", providerId, providerName);
                Provider provider = providerService.getProvider(providerId);
                if (provider != null) {
                    Person person = new Person();
                    PersonName personName = new PersonName();
                    if (StringUtils.isNotBlank(providerName)) {
                        String name = providerName.trim().replace(".", "");
                        String[] split = name.split(" ", 2);
                        if (StringUtils.isNotBlank(split[0])) {
                            personName.setGivenName(split[0].trim());
                        }
                        if (split.length > 1 && StringUtils.isNotBlank(split[1])) {
                            personName.setFamilyName(split[1].trim());
                        }
                        personName.setPreferred(true);
                    } else {
                        personName.setGivenName("Unknown");
                        personName.setFamilyName("Unknown");
                    }
                    log.warn("Person Name set to {}", personName);
                    person.addName(personName);
                    person = personService.savePerson(person);

                    log.warn("Created person {}", person);
                    provider.setPerson(person);
                    if (provider.isRetired() && StringUtils.isBlank(provider.getRetireReason())) {
                        provider.setRetireReason("Auto-populated during provider cleanup");
                    }
                    providerService.saveProvider(provider);
                    log.warn("Associated provider {} with person {}", provider, person);
                }
                else {
                    log.warn("No provider found with id {}", providerId);
                }
            }
            log.warn("Processing providers without persons completed");
        }
    }
}
