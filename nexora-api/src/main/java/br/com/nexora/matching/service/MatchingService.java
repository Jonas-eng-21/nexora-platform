package br.com.nexora.matching.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.jobs.dto.JobResponse;
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
import br.com.nexora.matching.dto.RecommendedJobResponse;
import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.skills.entity.Skill;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.service.StudentProfileService;

/**
 * Monta a entrada do cálculo a partir do perfil, do currículo-base e das competências do catálogo.
 * O score é sempre calculado na hora: nada é gravado, então ele acompanha o currículo e a vaga atuais.
 */
@Service
public class MatchingService {

	/** Teto de vagas avaliadas por consulta de recomendação. */
	static final int MAX_EVALUATED_JOBS = 500;

	private static final Comparator<RecommendedJobResponse> BEST_MATCH_FIRST = Comparator
			.comparingInt((RecommendedJobResponse item) -> item.match().score()).reversed()
			.thenComparing(item -> item.job().createdAt(), Comparator.reverseOrder())
			.thenComparing(item -> item.job().id());

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

	/**
	 * Vagas abertas de empresas ativas, ordenadas pela compatibilidade com o estudante autenticado
	 * (maior score primeiro; no empate, a mais recente e depois o id, para a ordem ser estável).
	 * <p>
	 * O score não é gravado, então a ordenação e a paginação são feitas em memória sobre, no máximo,
	 * as {@value #MAX_EVALUATED_JOBS} vagas abertas mais recentes — suficiente para o porte do MVP.
	 */
	@Transactional(readOnly = true)
	public PageResponse<RecommendedJobResponse> recommendJobs(UUID studentUserId, int page, int size) {
		Student student = studentProfileService.getByUserId(studentUserId);
		List<RecommendedJobResponse> ranked = jobService.listOpenJobs(MAX_EVALUATED_JOBS).stream()
				.map(job -> new RecommendedJobResponse(JobResponse.from(job), calculate(student, job)))
				.sorted(BEST_MATCH_FIRST)
				.toList();

		Pageable pageable = PageResponse.pageable(page, size, Sort.unsorted());
		int from = (int) Math.min(pageable.getOffset(), ranked.size());
		int to = Math.min(from + pageable.getPageSize(), ranked.size());
		return PageResponse.from(new PageImpl<>(ranked.subList(from, to), pageable, ranked.size()),
				recommendation -> recommendation);
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
