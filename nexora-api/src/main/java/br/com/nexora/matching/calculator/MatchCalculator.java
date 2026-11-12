package br.com.nexora.matching.calculator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.com.nexora.matching.calculator.MatchInput.Candidate;
import br.com.nexora.matching.calculator.MatchInput.Position;
import br.com.nexora.matching.calculator.MatchInput.SkillRef;
import br.com.nexora.matching.calculator.MatchResult.CriterionResult;
import br.com.nexora.shared.domain.WorkModel;

/**
 * Cálculo determinístico e explicável da compatibilidade entre um estudante e uma vaga.
 * <p>
 * Função pura: mesma entrada, mesma saída; sem banco, sem relógio e sem IA. Critérios:
 * <ol>
 * <li>competências obrigatórias: proporcional às atendidas;</li>
 * <li>competências desejáveis: proporcional às atendidas; se a vaga não lista nenhuma, o critério
 * fica fora do cálculo e os demais são renormalizados;</li>
 * <li>interesses: pontua quando a área da vaga está entre as áreas de interesse do estudante;</li>
 * <li>modelo de trabalho e localização: metade pelo modelo (aceito, ou estudante sem preferência) e
 * metade pela localização (vaga remota, ou mesma cidade).</li>
 * </ol>
 * A nota é a soma dos pontos obtidos dividida pela soma dos pesos aplicáveis, de 0 a 100.
 */
@Component
public class MatchCalculator {

	/** Mude ao alterar critérios ou a forma de pontuar, para que notas antigas possam ser interpretadas. */
	public static final String ALGORITHM_VERSION = "v1";

	private final MatchingWeights weights;

	public MatchCalculator(MatchingWeights weights) {
		this.weights = weights;
	}

	public MatchResult calculate(Candidate candidate, Position position) {
		Set<UUID> candidateSkillIds = candidate.skills().stream().map(SkillRef::id).collect(Collectors.toSet());
		List<String> matchedRequired = names(position.requiredSkills(), candidateSkillIds, true);
		List<String> missingRequired = names(position.requiredSkills(), candidateSkillIds, false);
		List<String> matchedDesired = names(position.desiredSkills(), candidateSkillIds, true);
		List<String> missingDesired = names(position.desiredSkills(), candidateSkillIds, false);

		List<CriterionResult> criteria = List.of(
				skillsCriterion(MatchCriterion.REQUIRED_SKILLS, "obrigatórias", matchedRequired, missingRequired),
				skillsCriterion(MatchCriterion.DESIRED_SKILLS, "desejáveis", matchedDesired, missingDesired),
				interestsCriterion(candidate, position),
				workModelAndLocationCriterion(candidate, position));

		int score = score(criteria);
		return new MatchResult(score, ALGORITHM_VERSION, criteria, matchedRequired, missingRequired, matchedDesired,
				missingDesired, summary(score, criteria, matchedRequired, missingRequired, matchedDesired,
						missingDesired));
	}

	private CriterionResult skillsCriterion(MatchCriterion criterion, String kind, List<String> matched,
			List<String> missing) {
		int weight = weights.of(criterion);
		int total = matched.size() + missing.size();
		if (total == 0) {
			return new CriterionResult(criterion, weight, 0, false,
					"A vaga não lista competências " + kind + "; o critério não entra no cálculo.");
		}
		String explanation = "Atende " + matched.size() + " de " + total + " competências " + kind + "."
				+ (missing.isEmpty() ? "" : " Faltam: " + String.join(", ", missing) + ".");
		return new CriterionResult(criterion, weight, round(weight * (double) matched.size() / total), true,
				explanation);
	}

	private CriterionResult interestsCriterion(Candidate candidate, Position position) {
		int weight = weights.of(MatchCriterion.INTERESTS);
		String area = position.area().getLabel();
		if (candidate.interestAreas().contains(position.area())) {
			return new CriterionResult(MatchCriterion.INTERESTS, weight, weight, true,
					"A área da vaga (" + area + ") está entre as áreas de interesse do estudante.");
		}
		String reason = candidate.interestAreas().isEmpty()
				? "O estudante não informou áreas de interesse."
				: "A área da vaga (" + area + ") não está entre as áreas de interesse do estudante.";
		return new CriterionResult(MatchCriterion.INTERESTS, weight, 0, true, reason);
	}

	private CriterionResult workModelAndLocationCriterion(Candidate candidate, Position position) {
		int weight = weights.of(MatchCriterion.WORK_MODEL_AND_LOCATION);
		double half = weight / 2.0;
		List<String> reasons = new ArrayList<>();
		double points = 0;

		if (candidate.preferredWorkModels().isEmpty()) {
			points += half;
			reasons.add("O estudante não informou preferência de modelo de trabalho.");
		}
		else if (candidate.preferredWorkModels().contains(position.workModel())) {
			points += half;
			reasons.add("O modelo de trabalho da vaga está entre os aceitos pelo estudante.");
		}
		else {
			reasons.add("O modelo de trabalho da vaga não está entre os aceitos pelo estudante.");
		}

		if (position.workModel() == WorkModel.REMOTE) {
			points += half;
			reasons.add("A vaga é remota, então a localização não restringe.");
		}
		else {
			String candidateCity = cityOf(candidate.location());
			String positionCity = cityOf(position.location());
			if (candidateCity != null && candidateCity.equals(positionCity)) {
				points += half;
				reasons.add("O estudante está na mesma cidade da vaga.");
			}
			else if (candidateCity == null || positionCity == null) {
				reasons.add("Não foi possível comparar a localização: estudante ou vaga sem cidade informada.");
			}
			else {
				reasons.add("O estudante está em cidade diferente da vaga.");
			}
		}
		return new CriterionResult(MatchCriterion.WORK_MODEL_AND_LOCATION, weight, round(points), true,
				String.join(" ", reasons));
	}

	/** Pontos obtidos sobre os pesos dos critérios aplicáveis, arredondado para inteiro de 0 a 100. */
	private static int score(List<CriterionResult> criteria) {
		int applicableWeight = criteria.stream().filter(CriterionResult::applicable)
				.mapToInt(CriterionResult::weight).sum();
		if (applicableWeight == 0) {
			return 0;
		}
		double earned = criteria.stream().filter(CriterionResult::applicable)
				.mapToDouble(CriterionResult::points).sum();
		return (int) Math.clamp(Math.round(100.0 * earned / applicableWeight), 0L, 100L);
	}

	private static String summary(int score, List<CriterionResult> criteria, List<String> matchedRequired,
			List<String> missingRequired, List<String> matchedDesired, List<String> missingDesired) {
		List<String> parts = new ArrayList<>();
		parts.add(matchedRequired.size() + " de " + (matchedRequired.size() + missingRequired.size())
				+ " competências obrigatórias");
		int desiredTotal = matchedDesired.size() + missingDesired.size();
		parts.add(desiredTotal == 0 ? "vaga sem competências desejáveis"
				: matchedDesired.size() + " de " + desiredTotal + " desejáveis");
		CriterionResult interests = criteria.get(2);
		parts.add(interests.points() > 0 ? "área de interesse compatível" : "área fora dos interesses informados");
		CriterionResult location = criteria.get(3);
		parts.add("modelo de trabalho e localização: " + format(location.points()) + " de " + location.weight()
				+ " pontos");
		return "Compatibilidade de " + score + "%: " + String.join("; ", parts) + ".";
	}

	/** Nomes em ordem alfabética, para a saída ser estável. */
	private static List<String> names(Set<SkillRef> skills, Set<UUID> candidateSkillIds, boolean matched) {
		return skills.stream()
				.filter(skill -> candidateSkillIds.contains(skill.id()) == matched)
				.map(SkillRef::name)
				.sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
				.toList();
	}

	/**
	 * Cidade para comparação: trecho antes de vírgula, barra ou " - ", sem acentos e em minúsculas
	 * ("Garanhuns - PE" e "garanhuns, pe" viram "garanhuns").
	 */
	private static String cityOf(String location) {
		if (location == null) {
			return null;
		}
		String city = location.split(",|/|\\s[-–]\\s", 2)[0];
		String normalized = Normalizer.normalize(city, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		return normalized.isEmpty() ? null : normalized;
	}

	private static double round(double value) {
		return Math.round(value * 10) / 10.0;
	}

	private static String format(double points) {
		return points == Math.rint(points) ? String.valueOf((long) points) : String.valueOf(points).replace('.', ',');
	}
}
