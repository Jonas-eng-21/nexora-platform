package br.com.nexora.matching.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.entity.JobSkill;
import br.com.nexora.jobs.entity.SkillRequirement;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.matching.calculator.MatchCalculator;
import br.com.nexora.matching.calculator.MatchInput.Candidate;
import br.com.nexora.matching.calculator.MatchInput.Position;
import br.com.nexora.matching.calculator.MatchInput.SkillRef;
import br.com.nexora.matching.calculator.MatchResult;
import br.com.nexora.matching.dto.JobMatchResponse;
import br.com.nexora.skills.entity.Skill;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.service.StudentProfileService;

/**
 * Monta a entrada do cálculo a partir do perfil, do currículo-base e das competências do catálogo.
 * O score é sempre calculado na hora: nada é gravado, então ele acompanha o currículo e a vaga atuais.
 */
@Service
public class MatchingService {

	private final StudentProfileService studentProfileService;
	private final JobService jobService;
	private final MatchCalculator matchCalculator;

	public MatchingService(StudentProfileService studentProfileService, JobService jobService,
			MatchCalculator matchCalculator) {
		this.studentProfileService = studentProfileService;
		this.jobService = jobService;
		this.matchCalculator = matchCalculator;
	}

	/** Compatibilidade do estudante autenticado com uma vaga aberta de empresa ativa. */
	@Transactional(readOnly = true)
	public JobMatchResponse matchOwnProfileWithJob(UUID studentUserId, UUID jobId) {
		Student student = studentProfileService.getByUserId(studentUserId);
		Job job = jobService.getOpenJob(jobId);
		return new JobMatchResponse(job.getId(), job.getTitle(), calculate(student, job));
	}

	/** Reutilizado pelas recomendações: mesma regra para qualquer par estudante–vaga. */
	MatchResult calculate(Student student, Job job) {
		return matchCalculator.calculate(candidateOf(student), positionOf(job));
	}

	private static Candidate candidateOf(Student student) {
		return new Candidate(
				student.getSkills().stream().map(MatchingService::ref).collect(Collectors.toSet()),
				Set.copyOf(student.getResume().interestAreas()),
				Set.copyOf(student.getResume().preferredWorkModels()),
				student.getLocation());
	}

	private static Position positionOf(Job job) {
		return new Position(skills(job, SkillRequirement.REQUIRED), skills(job, SkillRequirement.DESIRED),
				job.getArea(), job.getWorkModel(), job.getLocation());
	}

	private static Set<SkillRef> skills(Job job, SkillRequirement requirement) {
		return job.getSkills().stream()
				.filter(jobSkill -> jobSkill.getRequirement() == requirement)
				.map(JobSkill::getSkill)
				.map(MatchingService::ref)
				.collect(Collectors.toSet());
	}

	private static SkillRef ref(Skill skill) {
		return new SkillRef(skill.getId(), skill.getName());
	}
}
