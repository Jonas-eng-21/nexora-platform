package br.com.nexora.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import br.com.nexora.matching.calculator.MatchCalculator;
import br.com.nexora.matching.calculator.MatchCriterion;
import br.com.nexora.matching.calculator.MatchInput.Candidate;
import br.com.nexora.matching.calculator.MatchInput.Position;
import br.com.nexora.matching.calculator.MatchInput.SkillRef;
import br.com.nexora.matching.calculator.MatchResult;
import br.com.nexora.matching.calculator.MatchResult.CriterionResult;
import br.com.nexora.matching.calculator.MatchingWeights;
import br.com.nexora.shared.domain.ProfessionalArea;
import br.com.nexora.shared.domain.WorkModel;

/** Teste unitário puro: o cálculo não depende de banco nem de Spring. */
class MatchCalculatorTests {

	private static final SkillRef JAVA = skill("Java");
	private static final SkillRef SPRING = skill("Spring Boot");
	private static final SkillRef DOCKER = skill("Docker");
	private static final SkillRef AWS = skill("AWS");
	private static final SkillRef PYTHON = skill("Python");

	private final MatchCalculator calculator = new MatchCalculator(new MatchingWeights(45, 20, 20, 15));

	@Test
	void perfectCandidateScores100() {
		MatchResult result = calculator.calculate(
				candidate(Set.of(JAVA, SPRING, DOCKER, AWS), Set.of(ProfessionalArea.BACKEND),
						Set.of(WorkModel.HYBRID), "Garanhuns, PE"),
				position(Set.of(JAVA, SPRING), Set.of(DOCKER, AWS), ProfessionalArea.BACKEND, WorkModel.HYBRID,
						"Garanhuns - PE"));

		assertThat(result.score()).isEqualTo(100);
		assertThat(result.algorithmVersion()).isEqualTo("v1");
		assertThat(result.matchedRequiredSkills()).containsExactly("Java", "Spring Boot");
		assertThat(result.missingRequiredSkills()).isEmpty();
		assertThat(result.matchedDesiredSkills()).containsExactly("AWS", "Docker");
		assertThat(result.missingDesiredSkills()).isEmpty();
		assertThat(result.criteria()).extracting(CriterionResult::criterion).containsExactly(
				MatchCriterion.REQUIRED_SKILLS, MatchCriterion.DESIRED_SKILLS, MatchCriterion.INTERESTS,
				MatchCriterion.WORK_MODEL_AND_LOCATION);
		assertThat(result.criteria()).extracting(CriterionResult::points).containsExactly(45.0, 20.0, 20.0, 15.0);
		assertThat(result.summary()).contains("100%");
	}

	@Test
	void candidateWithNothingInCommonScoresZero() {
		MatchResult result = calculator.calculate(
				candidate(Set.of(PYTHON), Set.of(ProfessionalArea.DATA), Set.of(WorkModel.REMOTE), "Recife, PE"),
				position(Set.of(JAVA, SPRING), Set.of(DOCKER), ProfessionalArea.BACKEND, WorkModel.ON_SITE,
						"Garanhuns, PE"));

		assertThat(result.score()).isZero();
		assertThat(result.matchedRequiredSkills()).isEmpty();
		assertThat(result.missingRequiredSkills()).containsExactly("Java", "Spring Boot");
		assertThat(result.missingDesiredSkills()).containsExactly("Docker");
		assertThat(result.criteria()).extracting(CriterionResult::points).containsOnly(0.0);
	}

	@Test
	void candidateWithoutSkillsOrPreferencesOnlyEarnsWhatDoesNotDependOnThem() {
		MatchResult result = calculator.calculate(
				candidate(Set.of(), Set.of(), Set.of(), null),
				position(Set.of(JAVA), Set.of(DOCKER), ProfessionalArea.BACKEND, WorkModel.REMOTE, null));

		// Sem preferência de modelo (7,5) + vaga remota dispensa localização (7,5) = 15 de 100.
		assertThat(result.score()).isEqualTo(15);
		assertThat(points(result, MatchCriterion.REQUIRED_SKILLS)).isZero();
		assertThat(points(result, MatchCriterion.INTERESTS)).isZero();
		assertThat(points(result, MatchCriterion.WORK_MODEL_AND_LOCATION)).isEqualTo(15.0);
	}

	@Test
	void requiredAndDesiredSkillsScoreProportionally() {
		MatchResult result = calculator.calculate(
				candidate(Set.of(JAVA, DOCKER), Set.of(), Set.of(WorkModel.ON_SITE), "Recife, PE"),
				position(Set.of(JAVA, SPRING), Set.of(DOCKER, AWS), ProfessionalArea.BACKEND, WorkModel.HYBRID,
						"Garanhuns, PE"));

		// Metade das obrigatórias (22,5) + metade das desejáveis (10) = 32,5 -> 33.
		assertThat(points(result, MatchCriterion.REQUIRED_SKILLS)).isEqualTo(22.5);
		assertThat(points(result, MatchCriterion.DESIRED_SKILLS)).isEqualTo(10.0);
		assertThat(result.score()).isEqualTo(33);
		assertThat(result.matchedRequiredSkills()).containsExactly("Java");
		assertThat(result.missingRequiredSkills()).containsExactly("Spring Boot");
		assertThat(result.matchedDesiredSkills()).containsExactly("Docker");
		assertThat(result.missingDesiredSkills()).containsExactly("AWS");
	}

	@Test
	void jobWithoutDesiredSkillsLeavesThatCriterionOutOfTheCalculation() {
		Position position = position(Set.of(JAVA), Set.of(), ProfessionalArea.BACKEND, WorkModel.REMOTE, null);

		MatchResult full = calculator.calculate(
				candidate(Set.of(JAVA), Set.of(ProfessionalArea.BACKEND), Set.of(), null), position);
		MatchResult onlySkills = calculator.calculate(
				candidate(Set.of(JAVA), Set.of(), Set.of(WorkModel.ON_SITE), null), position);

		assertThat(full.score()).isEqualTo(100);
		CriterionResult desired = criterion(full, MatchCriterion.DESIRED_SKILLS);
		assertThat(desired.applicable()).isFalse();
		assertThat(desired.points()).isZero();
		// 45 (obrigatórias) + 7,5 (vaga remota) de 80 pontos aplicáveis = 65,6 -> 66.
		assertThat(onlySkills.score()).isEqualTo(66);
	}

	@Test
	void interestCriterionRequiresTheJobAreaAmongTheStudentInterestAreas() {
		Position position = position(Set.of(JAVA), Set.of(DOCKER), ProfessionalArea.DATA, WorkModel.REMOTE, null);

		MatchResult interested = calculator.calculate(
				candidate(Set.of(), Set.of(ProfessionalArea.BACKEND, ProfessionalArea.DATA), Set.of(), null), position);
		MatchResult notInterested = calculator.calculate(
				candidate(Set.of(), Set.of(ProfessionalArea.BACKEND), Set.of(), null), position);

		assertThat(points(interested, MatchCriterion.INTERESTS)).isEqualTo(20.0);
		assertThat(points(notInterested, MatchCriterion.INTERESTS)).isZero();
		assertThat(criterion(interested, MatchCriterion.INTERESTS).explanation())
				.contains("Ciência e Análise de Dados");
	}

	@Test
	void workModelAndLocationAreScoredInHalves() {
		Candidate remoteOnly = candidate(Set.of(), Set.of(), Set.of(WorkModel.REMOTE), "Garanhuns, PE");

		// Modelo recusado, mas mesma cidade: só a metade da localização.
		assertThat(points(calculator.calculate(remoteOnly,
				position(Set.of(JAVA), Set.of(), ProfessionalArea.BACKEND, WorkModel.ON_SITE, "Garanhuns, PE")),
				MatchCriterion.WORK_MODEL_AND_LOCATION)).isEqualTo(7.5);
		// Modelo aceito e remoto: as duas metades, mesmo sem localização na vaga.
		assertThat(points(calculator.calculate(remoteOnly,
				position(Set.of(JAVA), Set.of(), ProfessionalArea.BACKEND, WorkModel.REMOTE, null)),
				MatchCriterion.WORK_MODEL_AND_LOCATION)).isEqualTo(15.0);
		// Modelo aceito, cidade diferente: só a metade do modelo.
		assertThat(points(calculator.calculate(
				candidate(Set.of(), Set.of(), Set.of(WorkModel.HYBRID), "Recife, PE"),
				position(Set.of(JAVA), Set.of(), ProfessionalArea.BACKEND, WorkModel.HYBRID, "Garanhuns, PE")),
				MatchCriterion.WORK_MODEL_AND_LOCATION)).isEqualTo(7.5);
		// Vaga presencial e estudante sem localização: a localização não pontua.
		assertThat(points(calculator.calculate(
				candidate(Set.of(), Set.of(), Set.of(), null),
				position(Set.of(JAVA), Set.of(), ProfessionalArea.BACKEND, WorkModel.ON_SITE, "Garanhuns, PE")),
				MatchCriterion.WORK_MODEL_AND_LOCATION)).isEqualTo(7.5);
	}

	@Test
	void cityComparisonIgnoresCaseAccentsAndStateSuffix() {
		for (String studentLocation : new String[] { "garanhuns", "GARANHUNS - PE", " Garanhuns / Pernambuco ",
				"Garanhuns, PE, Brasil" }) {
			MatchResult result = calculator.calculate(
					candidate(Set.of(), Set.of(), Set.of(), studentLocation),
					position(Set.of(JAVA), Set.of(), ProfessionalArea.BACKEND, WorkModel.ON_SITE, "Garanhuns, PE"));

			assertThat(points(result, MatchCriterion.WORK_MODEL_AND_LOCATION)).as(studentLocation).isEqualTo(15.0);
		}
		MatchResult accents = calculator.calculate(
				candidate(Set.of(), Set.of(), Set.of(), "Sao Joao"),
				position(Set.of(JAVA), Set.of(), ProfessionalArea.BACKEND, WorkModel.ON_SITE, "São João - PE"));
		assertThat(points(accents, MatchCriterion.WORK_MODEL_AND_LOCATION)).isEqualTo(15.0);
	}

	@Test
	void scoreIsDeterministicAndAlwaysBetween0And100() {
		SkillRef[] all = { JAVA, SPRING, DOCKER, AWS, PYTHON };
		for (int mask = 0; mask < 32; mask++) {
			int current = mask;
			Set<SkillRef> owned = Arrays.stream(new Integer[] { 0, 1, 2, 3, 4 })
					.filter(index -> (current & (1 << index)) != 0).map(index -> all[index])
					.collect(Collectors.toSet());
			Candidate candidate = candidate(owned, Set.of(ProfessionalArea.BACKEND), Set.of(WorkModel.REMOTE),
					"Garanhuns, PE");
			Position position = position(Set.of(JAVA, SPRING, PYTHON), Set.of(DOCKER, AWS),
					ProfessionalArea.BACKEND, WorkModel.HYBRID, "Garanhuns, PE");

			MatchResult first = calculator.calculate(candidate, position);
			MatchResult second = calculator.calculate(candidate, position);

			assertThat(first).isEqualTo(second);
			assertThat(first.score()).isBetween(0, 100);
		}
	}

	@Test
	void weightsAreConfigurableAndMustAddUpTo100() {
		MatchCalculator skillsOnly = new MatchCalculator(new MatchingWeights(100, 0, 0, 0));
		MatchResult result = skillsOnly.calculate(
				candidate(Set.of(JAVA), Set.of(), Set.of(), null),
				position(Set.of(JAVA, SPRING), Set.of(DOCKER), ProfessionalArea.BACKEND, WorkModel.ON_SITE, null));

		assertThat(result.score()).isEqualTo(50);
		assertThatThrownBy(() -> new MatchingWeights(45, 20, 20, 20)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MatchingWeights(120, -20, 0, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void summaryExplainsEachCriterionInPlainText() {
		MatchResult result = calculator.calculate(
				candidate(Set.of(JAVA), Set.of(ProfessionalArea.BACKEND), Set.of(), "Garanhuns, PE"),
				position(Set.of(JAVA, SPRING), Set.of(DOCKER), ProfessionalArea.BACKEND, WorkModel.HYBRID,
						"Garanhuns, PE"));

		assertThat(result.summary()).contains("1 de 2 competências obrigatórias").contains("0 de 1 desejáveis");
		assertThat(result.criteria()).allSatisfy(item -> assertThat(item.explanation()).isNotBlank());
		assertThat(criterion(result, MatchCriterion.REQUIRED_SKILLS).explanation()).contains("Spring Boot");
	}

	private static SkillRef skill(String name) {
		return new SkillRef(UUID.nameUUIDFromBytes(name.getBytes()), name);
	}

	private static Candidate candidate(Set<SkillRef> skills, Set<ProfessionalArea> interestAreas,
			Set<WorkModel> preferredWorkModels, String location) {
		return new Candidate(skills, interestAreas, preferredWorkModels, location);
	}

	private static Position position(Set<SkillRef> required, Set<SkillRef> desired, ProfessionalArea area,
			WorkModel workModel, String location) {
		return new Position(required, desired, area, workModel, location);
	}

	private static CriterionResult criterion(MatchResult result, MatchCriterion criterion) {
		return result.criteria().stream().filter(item -> item.criterion() == criterion).findFirst().orElseThrow();
	}

	private static double points(MatchResult result, MatchCriterion criterion) {
		return criterion(result, criterion).points();
	}
}
