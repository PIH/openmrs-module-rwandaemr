package org.openmrs.module.rwandaemr.config;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * For any provider that is not associated with a person, this creates a person record and associates it with the provider
 */
@Component
public class ProviderCleanup implements Setup {

    protected Logger log = LoggerFactory.getLogger(getClass());

    private final ProviderService providerService;
    private final PersonService personService;

    @Autowired
    public ProviderCleanup(ProviderService providerService, PersonService personService) {
        this.providerService = providerService;
        this.personService = personService;
    }

    public void initialize() {
        List<Provider> providersWithoutPersons = new ArrayList<>();
        for (Provider provider : providerService.getAllProviders(true)) {
            if (provider.getPerson() == null) {
                providersWithoutPersons.add(provider);
            }
        }
        if (providersWithoutPersons.isEmpty()) {
            log.debug("No providers without persons found");
        }
        else {
            log.warn("Found {} providers without persons.  Creating person records for each",  providersWithoutPersons.size());
            for (Provider provider : providersWithoutPersons) {
                log.warn("Provider {} is not associated with a person", provider);
                Person person = new Person();
                PersonName personName = new PersonName();
                if (StringUtils.isNotBlank(provider.getName())) {
                    String[] split = provider.getName().split(" ", 2);
                    personName.setGivenName(split[0].trim());
                    if (split.length > 1) {
                        personName.setFamilyName(split[1].trim());
                    }
                    personName.setPreferred(true);
                }
                else {
                    personName.setGivenName("Unknown");
                    personName.setFamilyName("Unknown");
                }
                log.warn("Person Name set to {}", personName);
                person.addName(personName);
                person = personService.savePerson(person);
                log.warn("Created person {}", person);
                provider.setPerson(person);
                providerService.saveProvider(provider);
                log.warn("Associated provider {} with person {}", provider, person);
            }
            log.warn("Processing providers without persons completed");
        }
    }
}
