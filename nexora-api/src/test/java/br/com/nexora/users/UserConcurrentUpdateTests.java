package br.com.nexora.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import br.com.nexora.administration.service.AdminUserService;
import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.dto.UpdateAccountRequest;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import br.com.nexora.users.service.UserAccountService;

/**
 * Concorrência real: duas transações independentes, cada uma em sua própria thread e conexão (nada de
 * {@code @Transactional} no teste, que reuniria tudo numa sessão só). Uma suspensão administrativa confirmada
 * não pode ser sobrescrita por uma escrita que partiu do estado anterior da conta.
 */
@SpringBootTest
class UserConcurrentUpdateTests {

	private static final String PASSWORD = "Current-Pass-123";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AdminUserService adminUserService;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private WebApplicationContext context;

	/** Spy: o comportamento real é mantido; só o teste de serviço pausa dentro de {@code matches}. */
	@MockitoSpyBean
	private PasswordEncoder passwordEncoder;

	private final List<UUID> createdUsers = new ArrayList<>();
	private ExecutorService executor;
	private User admin;

	@BeforeEach
	void setUp() {
		executor = Executors.newSingleThreadExecutor();
		admin = create(UserRole.ADMIN, unique("admin"));
	}

	@AfterEach
	void cleanUp() {
		executor.shutdownNow();
		// Estes testes confirmam dados de verdade (sem rollback): remove só o que eles criaram.
		for (UUID id : createdUsers) {
			jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE admin_id = ? OR target_id = ?", id, id);
		}
		for (UUID id : createdUsers) {
			jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
		}
	}

	@Test
	void staleTransactionCannotOverwriteACommittedSuspension() throws Exception {
		User target = create(UserRole.COMPANY, unique("company"));
		CountDownLatch loaded = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		// Transação A: lê a conta ainda ATIVA e só grava depois da suspensão.
		Future<Throwable> staleWriter = executor.submit(() -> {
			try {
				new TransactionTemplate(transactionManager).executeWithoutResult(txStatus -> {
					User snapshot = userRepository.findById(target.getId()).orElseThrow();
					snapshot.changeEmail("changed-by-owner@nexora.test");
					loaded.countDown();
					await(release);
				});
				return null;
			} catch (Throwable ex) {
				return ex;
			}
		});

		assertThat(loaded.await(30, TimeUnit.SECONDS)).isTrue();
		// Transação B: o admin suspende e confirma (commit) enquanto A ainda está aberta.
		adminUserService.suspend(admin.getId(), target.getId(), "Violação dos termos de uso");
		release.countDown();
		Throwable outcome = staleWriter.get(30, TimeUnit.SECONDS);

		assertThat(statusOf(target)).as("a suspensão confirmada deve permanecer").isEqualTo("SUSPENDED");
		assertThat(emailOf(target)).as("a escrita conflitante não pode ser aplicada")
				.isEqualTo(target.getEmail());
		assertThat(outcome).as("o conflito deve falhar de forma explícita, não silenciosa")
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	void ownerAccountUpdateInFlightCannotUndoAdminSuspension() throws Exception {
		User target = create(UserRole.COMPANY, unique("owner"));
		CountDownLatch loaded = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// Fluxo real do dono (PATCH /users/me): a verificação BCrypt roda dentro da transação e abre a janela.
		doAnswer(invocation -> {
			if (Thread.currentThread().getName().startsWith("owner-")) {
				loaded.countDown();
				await(release);
			}
			return invocation.callRealMethod();
		}).when(passwordEncoder).matches(anyString(), anyString());

		ExecutorService ownerThread = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "owner-1"));
		try {
			Future<Throwable> owner = ownerThread.submit(() -> {
				try {
					userAccountService.updateAccount(target.getId(),
							new UpdateAccountRequest(PASSWORD, "owner-new-email@nexora.test", null));
					return null;
				} catch (Throwable ex) {
					return ex;
				}
			});

			assertThat(loaded.await(30, TimeUnit.SECONDS)).isTrue();
			adminUserService.suspend(admin.getId(), target.getId(), "Violação dos termos de uso");
			release.countDown();
			Throwable outcome = owner.get(30, TimeUnit.SECONDS);

			assertThat(statusOf(target)).isEqualTo("SUSPENDED");
			assertThat(emailOf(target)).isEqualTo(target.getEmail());
			assertThat(outcome).isInstanceOf(ObjectOptimisticLockingFailureException.class);

			// Tokens emitidos antes da suspensão continuam bloqueados.
			MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
			mockMvc.perform(get("/api/v1/users/me")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(target)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
		} finally {
			ownerThread.shutdownNow();
		}
	}

	@Test
	void sequentialLegitimateUpdatesStillWorkAndBumpTheVersion() {
		User target = create(UserRole.STUDENT, unique("sequential"));
		Long initial = versionOf(target);

		userAccountService.updateAccount(target.getId(),
				new UpdateAccountRequest(PASSWORD, "sequential-new@nexora.test", null));
		adminUserService.suspend(admin.getId(), target.getId(), "Motivo");
		adminUserService.reactivate(admin.getId(), target.getId(), "Revisado");

		assertThat(statusOf(target)).isEqualTo("ACTIVE");
		assertThat(emailOf(target)).isEqualTo("sequential-new@nexora.test");
		assertThat(versionOf(target)).isGreaterThan(initial);
	}

	@Test
	void conflictingWriteDoesNotLeakAnInternalErrorToCallers() {
		User target = create(UserRole.STUDENT, unique("leak"));
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(txStatus -> {
			User snapshot = userRepository.findById(target.getId()).orElseThrow();
			jdbcTemplate.update("UPDATE users SET version = version + 1 WHERE id = ?", target.getId());
			snapshot.changeEmail("leak-new@nexora.test");
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(emailOf(target)).isEqualTo(target.getEmail());
	}

	/** E-mails únicos por execução: uma rodada interrompida não deixa lixo que quebre a seguinte. */
	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID() + "@nexora.test";
	}

	private User create(UserRole role, String email) {
		User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(PASSWORD), role,
				AccountStatus.ACTIVE));
		createdUsers.add(user.getId());
		return user;
	}

	private String statusOf(User user) {
		return jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, user.getId());
	}

	private String emailOf(User user) {
		return jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user.getId());
	}

	private Long versionOf(User user) {
		return jdbcTemplate.queryForObject("SELECT version FROM users WHERE id = ?", Long.class, user.getId());
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("timeout aguardando a outra transação");
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}
}
