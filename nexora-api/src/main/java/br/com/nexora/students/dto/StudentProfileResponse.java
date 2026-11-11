package br.com.nexora.students.dto;

import java.util.UUID;

import br.com.nexora.students.entity.Student;

public record StudentProfileResponse(UUID id, String fullName, String course, String institution, String location,
		String summary) {

	public static StudentProfileResponse from(Student student) {
		return new StudentProfileResponse(student.getId(), student.getFullName(), student.getCourse(),
				student.getInstitution(), student.getLocation(), student.getSummary());
	}
}
