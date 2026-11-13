package br.com.nexora.applications.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Armazenamento privado dos PDFs de candidatura, em uma pasta do servidor (no Docker, um volume nomeado montado
 * só no backend). O nome de cada arquivo é gerado aqui; nada que o cliente envia vira caminho. Se um dia o deploy
 * não garantir um volume durável, esta é a única classe a trocar por um serviço privado de objetos.
 */
@Component
public class ResumeFileStorage {

	private static final Logger log = LoggerFactory.getLogger(ResumeFileStorage.class);
	private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");

	private final Path directory;

	public ResumeFileStorage(@Value("${nexora.storage.resumes-dir}") Path directory) {
		this.directory = directory.toAbsolutePath().normalize();
	}

	/** Grava o conteúdo com um nome novo e devolve a chave interna. */
	public String store(byte[] content) {
		String key = UUID.randomUUID() + ".pdf";
		try {
			Files.createDirectories(directory);
			Files.write(directory.resolve(key), content, StandardOpenOption.CREATE_NEW);
			return key;
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao gravar o PDF da candidatura", e);
		}
	}

	public byte[] read(String key) {
		try {
			return Files.readAllBytes(resolve(key));
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao ler o PDF da candidatura", e);
		}
	}

	/** Remove sem falhar: usado para não deixar arquivo órfão quando a gravação no banco não se completa. */
	public void deleteQuietly(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		} catch (IOException | RuntimeException e) {
			log.warn("Não foi possível remover o PDF órfão {}", key);
		}
	}

	/** Só chaves no formato gerado por {@link #store}: nenhum caminho de fora da pasta é alcançável. */
	private Path resolve(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Chave de armazenamento inválida");
		}
		return directory.resolve(key);
	}
}
