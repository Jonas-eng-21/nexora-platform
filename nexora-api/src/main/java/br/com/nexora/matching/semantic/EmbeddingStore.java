package br.com.nexora.matching.semantic;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persistência simples dos embeddings já calculados (tabela {@code text_embeddings}, sem entidade JPA). */
@Repository
public class EmbeddingStore {

	private final JdbcTemplate jdbcTemplate;

	public EmbeddingStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Vetores já guardados para os hashes, neste modelo e nesta dimensão. */
	public Map<String, float[]> find(Set<String> hashes, String model, int dimensions) {
		Map<String, float[]> found = new HashMap<>();
		if (hashes.isEmpty()) {
			return found;
		}
		String placeholders = String.join(",", hashes.stream().map(h -> "?").toList());
		Object[] args = new Object[hashes.size() + 2];
		int i = 0;
		for (String hash : hashes) {
			args[i++] = hash;
		}
		args[i++] = model;
		args[i] = dimensions;
		jdbcTemplate.query("select content_hash, embedding from text_embeddings where content_hash in ("
				+ placeholders + ") and model = ? and dimensions = ?", rs -> {
					found.put(rs.getString("content_hash").trim(), decode(rs.getBytes("embedding")));
				}, args);
		return found;
	}

	/**
	 * Grava em transação própria: as listagens de matching são somente leitura e não podem gravar. Duas gravações
	 * simultâneas do mesmo vetor não conflitam (a segunda é ignorada).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void save(String hash, String model, int dimensions, float[] vector) {
		jdbcTemplate.update("""
				insert into text_embeddings (content_hash, model, dimensions, embedding)
				values (?, ?, ?, ?) on conflict do nothing""", hash, model, dimensions, encode(vector));
	}

	static byte[] encode(float[] vector) {
		ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
		for (float value : vector) {
			buffer.putFloat(value);
		}
		return buffer.array();
	}

	static float[] decode(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		float[] vector = new float[bytes.length / Float.BYTES];
		for (int i = 0; i < vector.length; i++) {
			vector[i] = buffer.getFloat();
		}
		return vector;
	}
}
