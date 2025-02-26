package org.openmrs.module.rwandaemr;

import org.openmrs.EncounterType;
import org.openmrs.LocationAttributeType;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.module.initializer.api.InitializerService;

public class MockRwandaEmrConfig extends RwandaEmrConfig {

	public MockRwandaEmrConfig() {
		this(null, null, null, null, null);
	}

	public MockRwandaEmrConfig(PatientService patientService,
							   PersonService personService,
							   LocationService locationService,
							   EncounterService encounterService,
							   InitializerService initializerService) {
		super(patientService, personService, locationService, encounterService, initializerService);
	}

	@Override
	public PatientIdentifierType getPrimaryCareIdentifierType() {
		PatientIdentifierType t = new PatientIdentifierType();
		t.setUuid("4e458867-9a68-4e55-9fe0-fb49fac4e6b0");
		t.setName("Primary Care Registration ID");
		return t;
	}

	@Override
	public PatientIdentifierType getNationalId() {
		PatientIdentifierType t = new PatientIdentifierType();
		t.setUuid("ed52ec82-4b7c-411b-804a-13bd9651bb3e");
		t.setName("National ID");
		return t;
	}

	@Override
	public PatientIdentifierType getNidApplicationNumber() {
		PatientIdentifierType t = new PatientIdentifierType();
		t.setUuid("0f7d2e40-956a-11ef-93fa-0242ac120002");
		t.setName("NID Application Number");
		return t;
	}

	@Override
	public PatientIdentifierType getUPID() {
		PatientIdentifierType t = new PatientIdentifierType();
		t.setUuid("01edaedd-956a-11ef-93fa-0242ac120002");
		t.setName("UPID");
		return t;
	}

	@Override
	public PatientIdentifierType getNIN() {
		PatientIdentifierType t = new PatientIdentifierType();
		t.setUuid("0c69d739-956a-11ef-93fa-0242ac120002");
		t.setName("NIN");
		return t;
	}

	@Override
	public PatientIdentifierType getPassportNumber() {
		PatientIdentifierType t = new PatientIdentifierType();
		t.setUuid("12a72978-956a-11ef-93fa-0242ac120002");
		t.setName("Passport Number");
		return t;
	}

	@Override
	public PersonAttributeType getTelephoneNumber() {
		PersonAttributeType t = new PersonAttributeType();
		t.setUuid("d6bcc287-4576-4264-961b-6bf1c08fbf68");
		t.setName("Phone Number");
		return t;
	}

	@Override
	public PersonAttributeType getMothersName() {
		PersonAttributeType t = new PersonAttributeType();
		t.setUuid("8d871d18-c2cc-11de-8d13-0010c6dffd0f");
		t.setName("Mothers Name");
		return t;
	}

	@Override
	public PersonAttributeType getFathersName() {
		PersonAttributeType t = new PersonAttributeType();
		t.setUuid("b7e948d4-9458-4f06-8d93-e859b6be9b76");
		t.setName("Fathers Name");
		return t;
	}

	@Override
	public PersonAttributeType getEducationLevel() {
		PersonAttributeType t = new PersonAttributeType();
		t.setUuid("9add985a-cba2-421a-8dd5-6323eb5bda4f");
		t.setName("Education Level");
		return t;
	}

	@Override
	public PersonAttributeType getProfession() {
		PersonAttributeType t = new PersonAttributeType();
		t.setUuid("ceb19b28-4327-472f-aac4-4c6c6106c7f9");
		t.setName("Profession");
		return t;
	}

	@Override
	public PersonAttributeType getReligion() {
		PersonAttributeType t = new PersonAttributeType();
		t.setUuid("287ad1fe-cd21-4577-bb32-7cd36d6a0ebb");
		t.setName("Religion");
		return t;
	}

	@Override
	public EncounterType getRegistrationEncounterType() {
		EncounterType t = new EncounterType();
		t.setUuid("cfe614d5-fa7e-4919-b76b-a66117f57e4c");
		t.setName("Registration");
		return t;
	}

	@Override
	public LocationAttributeType getFosaId() {
		LocationAttributeType t = new LocationAttributeType();
		t.setUuid("eb844bfb-b1d9-11ef-8756-0242ac120002");
		t.setName("FOSA ID");
		return t;
	}
}
