/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.emrapi.event;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.UserService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.emrapi.utils.GeneralUtils;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PatientViewedEventListenerTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private PatientService patientService;
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private AdministrationService adminService;
	
	private User user;
	
	@Autowired
	private PatientViewedEventListener listener;
	
	@BeforeEach
	public void setup() {
		if (user == null)
			user = userService.getUser(502);
	}
	
	private void setInitialLastViewedPatients(List<Integer> patientIds) {
		userService.setUserProperty(user, EmrApiConstants.USER_PROPERTY_NAME_LAST_VIEWED_PATIENT_IDS,
		    StringUtils.join(patientIds, ","));
	}
	
	private PatientViewedEvent createEvent(Patient patient, User user) {
		return new PatientViewedEvent(patient.getUuid(), user.getUuid());
	}
	
	/**
	 * @verifies add the patient to the last viewed user property
	 * @see PatientViewedEventListener#processEvent(PatientViewedEvent)
	 */
	@Test
	public void processEvent_shouldAddThePatientToTheLastViewedUserProperty() {
		setInitialLastViewedPatients(Arrays.asList(2, 6, 7));
		final Integer lastViewedPatientId = 8;
		PatientViewedEvent event = createEvent(patientService.getPatient(lastViewedPatientId), user);
		listener.processEvent(event);
		
		List<Patient> lastViewed = GeneralUtils.getLastViewedPatients(user);
		assertEquals(lastViewedPatientId, lastViewed.get(0).getId());
		assertEquals(7, lastViewed.get(1).getId().intValue());
		assertEquals(6, lastViewed.get(2).getId().intValue());
		assertEquals(2, lastViewed.get(3).getId().intValue());
		
	}
	
	/**
	 * @verifies remove the first patient and add the new one to the start if the list is full
	 * @see PatientViewedEventListener#processEvent(PatientViewedEvent)
	 */
	@Test
	public void processEvent_shouldRemoveTheFirstPatientAndAddTheNewOneToTheStartIfTheListIsFull() {
		final Integer newLimit = 3;
		GlobalProperty gp = new GlobalProperty(EmrApiConstants.GP_LAST_VIEWED_PATIENT_SIZE_LIMIT, newLimit.toString());
		adminService.saveGlobalProperty(gp);
		
		final Integer patientIdToRemove = 2;
		setInitialLastViewedPatients(Arrays.asList(patientIdToRemove, 6, 7));
		final Integer lastSeenPatientId = 8;
		PatientViewedEvent event = createEvent(patientService.getPatient(lastSeenPatientId), user);
		listener.processEvent(event);
		
		List<Patient> lastViewed = GeneralUtils.getLastViewedPatients(user);
		assertEquals(newLimit.intValue(), lastViewed.size());
		assertEquals(lastSeenPatientId, lastViewed.get(0).getId());
		assertEquals(7, lastViewed.get(1).getId().intValue());
		assertEquals(6, lastViewed.get(2).getId().intValue());
	}
	
	/**
	 * @verifies not add a duplicate and should move the existing patient to the start
	 * @see PatientViewedEventListener#processEvent(PatientViewedEvent)
	 */
	@Test
	public void processEvent_shouldNotAddADuplicateAndShouldMoveTheExistingPatientToTheStart() {
		final Integer duplicatePatientId = 2;
		List<Integer> initialPatientIds = Arrays.asList(duplicatePatientId, 6, 7, 8);
		final int initialSize = initialPatientIds.size();
		setInitialLastViewedPatients(initialPatientIds);
		PatientViewedEvent event = createEvent(patientService.getPatient(duplicatePatientId), user);
		listener.processEvent(event);
		
		List<Patient> lastViewed = GeneralUtils.getLastViewedPatients(user);
		assertEquals(initialSize, lastViewed.size());
		assertEquals(duplicatePatientId, lastViewed.get(0).getId());
		assertEquals(8, lastViewed.get(1).getId().intValue());
		assertEquals(7, lastViewed.get(2).getId().intValue());
		assertEquals(6, lastViewed.get(3).getId().intValue());
	}
	
	/**
	 * @verifies not remove any patient if a duplicate is added to a full list
	 * @see PatientViewedEventListener#processEvent(PatientViewedEvent)
	 */
	@Test
	public void processEvent_shouldNotRemoveAnyPatientIfADuplicateIsAddedToAFullList() {
		final Integer newLimit = 4;
		GlobalProperty gp = new GlobalProperty(EmrApiConstants.GP_LAST_VIEWED_PATIENT_SIZE_LIMIT, newLimit.toString());
		adminService.saveGlobalProperty(gp);
		
		final Integer duplicatePatientId = 2;
		setInitialLastViewedPatients(Arrays.asList(6, duplicatePatientId, 7, 8));
		PatientViewedEvent event = createEvent(patientService.getPatient(duplicatePatientId), user);
		listener.processEvent(event);
		
		List<Patient> lastViewed = GeneralUtils.getLastViewedPatients(user);
		assertEquals(newLimit.intValue(), lastViewed.size());
		//The duplicate should still have been moved to the top of the list
		assertEquals(duplicatePatientId, lastViewed.get(0).getId());
		assertEquals(8, lastViewed.get(1).getId().intValue());
		assertEquals(7, lastViewed.get(2).getId().intValue());
		assertEquals(6, lastViewed.get(3).getId().intValue());
	}
}
