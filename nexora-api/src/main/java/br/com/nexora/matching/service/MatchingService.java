package br.com.nexora.matching.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
import br.com.nexora.matching.dto.RecommendedStudentResponse;
import br.com.nexora.matching.dto.StudentSummary;
import br.com.nexora.matching.semantic.HybridScore;
import br.com.nexora.matching.semantic.SemanticSimilarityService;
import br.com.nexora.matching.semantic.SemanticTextBuilder;
import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.skills.entity.Skill;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.service.StudentProfileService;
import br.com.nexora.universities.service.AcademicEligibilityService;

/**
 * Monta a entrada do cálculo a partir do perfil, do currículo-base e das competências do catálogo.
 * O score é sempre calculado na hora: nada é gravado, então ele acompanha o currículo e a vaga atuais.
 * <p>
 * Com o matching semântico ligado, o score estruturado é combinado à similaridade dos embeddings
 * ({@link HybridScore}); sem a análise semântica para um par, vale o estruturado marcado como fallback. Os
 * embeddings vêm em lote e reaproveitados, nunca uma chamada externa por item da lista. Só o currículo-base
 * entra nos textos: PDF e snapshot de candidatura nunca são lidos.
 */
@Service
public class MatchingService {

	/** Teto de vagas avaliadas por consulta de recomendação. */
	static final int MAX_EVALUATED_JOBS = 500;

	/** Teto de estudantes avaliados por consulta de recomendação para uma vaga. */
	static final int MAX_EVALUATED_STUDENTS = 500;

	private static final Comparator<RecommendedJobResponse> BEST_MATCH_FIRST = Comparator
			.comparingInt((RecommendedJobResponse item) -> item.match().score()).reversed()
			.thenComparing(item -> item.job().createdAt(), Comparator.reverseOrder())
			.thenComparing(item -> item.job().id());

	private static final Comparator<RankedStudent> BEST_MATCH_FIRST_STUDENT = Comparator
			.comparingInt((RankedStudent item) -> item.match().score()).reversed()
			.thenComparing(item -> item.student().getCreatedAt(), Comparator.reverseOrder())
			.thenComparing(item -> item.student().getId());

	private final StudentProfileService studentProfileService;
	private final JobService jobService;
	private final AcademicEligibilityService eligibilityService;
	private final MatchCalculator matchCalculator;
	private final SemanticSimilarityService semanticSimilarity;

	public MatchingService(StudentProfileService studentProfileService, JobService jobService,
			AcademicEligibilityService eligibilityService, MatchCalculator matchCalculator,
			SemanticSimilarityService semanticSimilarity) {
		this.studentProfileService = studentProfileService;
		this.jobService = jobService;
		this.eligibilityService = eligibilityService;
		this.matchCalculator = matchCalculator;
		this.semanticSimilarity = semanticSimilarity;
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
		List<Job> jobs = jobService.listOpenJobs(MAX_EVALUATED_JOBS);
		List<MatchResult> matches = calculateAll(student, jobs);
		List<RecommendedJobResponse> ranked = java.util.stream.IntStream.range(0, jobs.size())
				.mapToObj(i -> new RecommendedJobResponse(JobResponse.from(jobs.get(i)), matches.get(i)))
				.sorted(BEST_MATCH_FIRST)
				.toList();

		Pageable pageable = PageResponse.pageable(page, size, Sort.unsorted());
		int from = (int) Math.min(pageable.getOffset(), ranked.size());
		int to = Math.min(from + pageable.getPageSize(), ranked.size());
		return PageResponse.from(new PageImpl<>(ranked.subList(from, to), pageable, ranked.size()),
				recommendation -> recommendation);
	}

	/** Estudantes recomendados para uma vaga da empresa autenticada (404 JOB_NOT_FOUND se de outra empresa). */
	@Transactional(readOnly = true)
	public PageResponse<RecommendedStudentResponse> recommendStudentsForOwnJob(UUID companyUserId, UUID jobId,
			int page, int size) {
		return recommendStudents(jobService.getOwnedActiveJob(companyUserId, jobId), page, size);
	}

	/** Estudantes recomendados para qualquer vaga não excluída: rota do administrador. */
	@Transactional(readOnly = true)
	public PageResponse<RecommendedStudentResponse> recommendStudentsForJob(UUID jobId, int page, int size) {
		return recommendStudents(jobService.getActiveJobById(jobId), page, size);
	}

	/**
	 * Só entram estudantes academicamente elegíveis (regra única de {@link AcademicEligibilityService}) e com
	 * conta ativa. Ordena por score decrescente e, no empate, pelo perfil mais recente e depois pelo id. Como o
	 * score não é gravado, ordenação e paginação são em memória sobre, no máximo, os
	 * {@value #MAX_EVALUATED_STUDENTS} perfis elegíveis mais recentes.
	 */
	private PageResponse<RecommendedStudentResponse> recommendStudents(Job job, int page, int size) {
		List<Student> students = studentProfileService
				.listActiveByIds(eligibilityService.eligibleStudentIds(), MAX_EVALUATED_STUDENTS);
		List<MatchResult> matches = calculateAll(job, students);
		List<RecommendedStudentResponse> ranked = java.util.stream.IntStream.range(0, students.size())
				.mapToObj(i -> new RankedStudent(students.get(i), matches.get(i)))
				.sorted(BEST_MATCH_FIRST_STUDENT)
				.map(item -> new RecommendedStudentResponse(StudentSummary.from(item.student()), item.match()))
				.toList();

		Pageable pageable = PageResponse.pageable(page, size, Sort.unsorted());
		int from = (int) Math.min(pageable.getOffset(), ranked.size());
		int to = Math.min(from + pageable.getPageSize(), ranked.size());
		return PageResponse.from(new PageImpl<>(ranked.subList(from, to), pageable, ranked.size()),
				recommendation -> recommendation);
	}

	private record RankedStudent(Student student, MatchResult match) {
	}

	/**
	 * Score atual (0 a 100) do estudante para a vaga, calculado agora com o currículo-base e as competências do
	 * catálogo. Vazio quando o currículo-base não tem nada para comparar — nenhuma competência do catálogo e
	 * nenhuma área de interesse —, caso em que um número só refletiria modelo de trabalho e localização.
	 * Para quem precisa informar "nota indisponível" em vez de um número enganoso (ex.: candidaturas).
	 */
	public Optional<Integer> currentScore(Student student, Job job) {
		return currentScores(job, List.of(student)).get(0);
	}

	/**
	 * Mesmo critério de {@link #currentScore}, para vários estudantes de uma mesma vaga (uma página de
	 * candidaturas), com os embeddings obtidos em lote. Um resultado por estudante, na mesma ordem.
	 */
	public List<Optional<Integer>> currentScores(Job job, List<Student> students) {
		List<MatchResult> matches = calculateAll(job, students);
		return java.util.stream.IntStream.range(0, students.size())
				.mapToObj(i -> hasComparableData(students.get(i)) ? Optional.of(matches.get(i).score())
						: Optional.<Integer>empty())
				.toList();
	}

	/** Reutilizado pelas recomendações: mesma regra para qualquer par estudante–vaga. */
	MatchResult calculate(Student student, Job job) {
		return calculateAll(student, List.of(job)).get(0);
	}

	/** Um estudante contra várias vagas: os embeddings das vagas vêm em lote. */
	private List<MatchResult> calculateAll(Student student, List<Job> jobs) {
		List<MatchResult> structured = jobs.stream()
				.map(job -> matchCalculator.calculate(candidateOf(student), positionOf(job))).toList();
		if (!semanticSimilarity.isEnabled() || !hasComparableData(student) || jobs.isEmpty()) {
			return structured;
		}
		List<OptionalInt> semantic = semanticSimilarity.scores(studentText(student),
				jobs.stream().map(this::jobText).toList());
		return combine(structured, semantic);
	}

	/** Uma vaga contra vários estudantes: os embeddings dos estudantes vêm em lote. */
	private List<MatchResult> calculateAll(Job job, List<Student> students) {
		List<MatchResult> structured = students.stream()
				.map(student -> matchCalculator.calculate(candidateOf(student), positionOf(job))).toList();
		if (!semanticSimilarity.isEnabled() || students.isEmpty()) {
			return structured;
		}
		// Estudante sem nada para comparar fica só no estruturado, como no resto do matching.
		List<Student> comparable = students.stream().filter(MatchingService::hasComparableData).toList();
		if (comparable.isEmpty()) {
			return structured;
		}
		List<OptionalInt> scores = semanticSimilarity.scores(jobText(job),
				comparable.stream().map(this::studentText).toList());
		Map<UUID, OptionalInt> byStudent = new HashMap<>();
		for (int i = 0; i < comparable.size(); i++) {
			byStudent.put(comparable.get(i).getId(), scores.get(i));
		}
		return java.util.stream.IntStream.range(0, students.size())
				.mapToObj(i -> byStudent.containsKey(students.get(i).getId())
						? combine(structured.get(i), byStudent.get(students.get(i).getId()))
						: structured.get(i))
				.toList();
	}

	private List<MatchResult> combine(List<MatchResult> structured, List<OptionalInt> semantic) {
		return java.util.stream.IntStream.range(0, structured.size())
				.mapToObj(i -> combine(structured.get(i), semantic.get(i))).toList();
	}

	private MatchResult combine(MatchResult structured, OptionalInt semantic) {
		if (semantic.isEmpty()) {
			return structured.asFallback();
		}
		return structured.withSemantic(
				HybridScore.combine(structured.score(), semantic.getAsInt(), semanticSimilarity.semanticWeight()),
				semantic.getAsInt());
	}

	/** Currículo-base com algo a comparar: alguma competência do catálogo ou área de interesse. */
	private static boolean hasComparableData(Student student) {
		return !student.getSkills().isEmpty() || !student.getResume().interestAreas().isEmpty();
	}

	private String studentText(Student student) {
		return SemanticTextBuilder.forStudent(student.getSummary(), student.getCourse(),
				student.getSkills().stream().map(Skill::getName).toList(), student.getResume(),
				semanticSimilarity.maxTextLength());
	}

	private String jobText(Job job) {
		return SemanticTextBuilder.forJob(job.getTitle(), job.getDescription(), job.getArea(), job.getWorkModel(),
				skillNames(job, SkillRequirement.REQUIRED), skillNames(job, SkillRequirement.DESIRED),
				semanticSimilarity.maxTextLength());
	}

	private static List<String> skillNames(Job job, SkillRequirement requirement) {
		return job.getSkills().stream().filter(jobSkill -> jobSkill.getRequirement() == requirement)
				.map(jobSkill -> jobSkill.getSkill().getName()).toList();
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
