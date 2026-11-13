package br.com.nexora.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.nexora.applications.entity.ApplicationStatus;

/** Tabela de transições: regra pura, sem Spring nem banco. */
class ApplicationStatusTests {

	private static final Set<String> ALLOWED = Set.of(
			"SUBMITTED>IN_REVIEW", "SUBMITTED>APPROVED", "SUBMITTED>REJECTED",
			"IN_REVIEW>APPROVED", "IN_REVIEW>REJECTED");

	@Test
	void onlyTheFiveDecidedTransitionsAreAllowed() {
		for (ApplicationStatus from : ApplicationStatus.values()) {
			for (ApplicationStatus to : ApplicationStatus.values()) {
				boolean expected = ALLOWED.contains(from + ">" + to);
				assertEquals(expected, from.canMoveTo(to), from + " -> " + to);
			}
		}
	}

	@Test
	void finalAndSuspendedStatesLeadNowhere() {
		for (ApplicationStatus state : EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED,
				ApplicationStatus.SUSPENDED)) {
			for (ApplicationStatus to : ApplicationStatus.values()) {
				assertFalse(from(state, to), state + " -> " + to);
			}
		}
		assertTrue(ApplicationStatus.SUBMITTED.canMoveTo(ApplicationStatus.IN_REVIEW));
	}

	@Test
	void nobodyCanMoveIntoSuspendedOrBackToSubmitted() {
		for (ApplicationStatus from : ApplicationStatus.values()) {
			assertFalse(from.canMoveTo(ApplicationStatus.SUSPENDED), from + " -> SUSPENDED");
			assertFalse(from.canMoveTo(ApplicationStatus.SUBMITTED), from + " -> SUBMITTED");
		}
	}

	private static boolean from(ApplicationStatus from, ApplicationStatus to) {
		return from.canMoveTo(to);
	}
}
