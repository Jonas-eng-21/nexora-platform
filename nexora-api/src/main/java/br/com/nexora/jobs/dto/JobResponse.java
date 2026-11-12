package br.com.nexora.jobs.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.entity.JobLevel;
import br.com.nexora.jobs.entity.JobSkill;
import br.com.nexora.jobs.entity.JobStatus;
import br.com.nexora.jobs.entity.SkillRequirement;
import br.com.nexora.jobs.entity.WorkModel;
import br.com.nexora.skills.dto.SkillResponse;
import br.com.nexora.skills.entity.Skill;

/** Montado dentro da transação, pois empresa e competências são carregadas sob demanda. */
public record JobResponse(UUID id, UUID companyId, String companyName, String title, String description,
		String area, String location, WorkModel workModel, JobLevel level, JobStatus status,
		List<SkillResponse> requiredSkills, List<SkillResponse> desiredSkills, Instant createdAt,
		Instant updatedAt) {

	public static JobResponse from(Job job) {
		return new JobResponse(job.getId(), job.getCompany().getId(), job.getCompany().getName(), job.getTitle(),
				job.getDescription(), job.getArea(), job.getLocation(), job.getWorkModel(), job.getLevel(),
				job.getStatus(), skills(job, SkillRequirement.REQUIRED), skills(job, SkillRequirement.DESIRED),
				job.getCreatedAt(), job.getUpdatedAt());
	}

	private static List<SkillResponse> skills(Job job, SkillRequirement requirement) {
		return job.getSkills().stream()
				.filter(skill -> skill.getRequirement() == requirement)
				.map(JobSkill::getSkill)
				.sorted(Comparator.comparing(Skill::getNormalizedName))
				.map(SkillResponse::from)
				.toList();
	}
}
