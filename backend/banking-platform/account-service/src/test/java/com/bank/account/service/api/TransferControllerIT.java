package com.bank.account.service.api;

import com.bank.account.domain.AccountType;
import com.bank.sharedkernel.domain.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class TransferControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.bank.transfer.application.BalanceProjectionRepository balanceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private void fundAccount(String accountId, BigDecimal amount) {
        // Retry logic to handle race condition with AccountProjector
        for (int i = 0; i < 3; i++) {
            try {
                balanceRepository.upsert(accountId, Currency.USD, amount, 0, java.time.Instant.now());
                return;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                // If it failed with 0, try to load the current version and update (or just retry if it was a lock)
            }
        }
    }

    /**
     * Creates a new account via the API and returns its accountId.
     */
    private String createAccount(String customerId) throws Exception {
        AccountRequests.OpenAccountRequest req = new AccountRequests.OpenAccountRequest(
                customerId, AccountType.CHECKING, Currency.USD, "PROD-001"
        );
        String res = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("accountId").asText();
    }

    @Test
    void shouldCreateInternalTransfer() throws Exception {
        String srcId = createAccount("alice-" + UUID.randomUUID().toString().substring(0, 8));
        String dstId = createAccount("bob-" + UUID.randomUUID().toString().substring(0, 8));

        // Allow projections to settle
        Thread.sleep(1500);
        
        // Fund source account
        fundAccount(srcId, new BigDecimal("1000.0000"));

        TransferRequests.CreateInternalTransferRequest request = new TransferRequests.CreateInternalTransferRequest(
                srcId,
                dstId,
                "Bob Doe",
                new BigDecimal("25.0000"),
                Currency.USD,
                "Lunch"
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transferId").exists());
    }

    @Test
    void shouldGetProjectedBalance() throws Exception {
        String accountId = createAccount("balance-test-" + UUID.randomUUID().toString().substring(0, 8));

        // Wait for Kafka projection to PostgreSQL read model
        Thread.sleep(1000);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId));
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
        TransferRequests.CreateInternalTransferRequest request = new TransferRequests.CreateInternalTransferRequest(
                "SRC-1", "DST-1", "Alice", new BigDecimal("10.00"), Currency.USD, "Ref"
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnConflictWhenIdempotencyKeyIsReusedWithDifferentRequest() throws Exception {
        String srcId = createAccount("idem-src-" + UUID.randomUUID().toString().substring(0, 8));
        String dstId = createAccount("idem-dst-" + UUID.randomUUID().toString().substring(0, 8));
        
        // Wait longer for both Account and Balance projections
        Thread.sleep(3000);
        fundAccount(srcId, new BigDecimal("1000.0000"));

        String key = "idem-key-" + UUID.randomUUID();
        TransferRequests.CreateInternalTransferRequest request1 = new TransferRequests.CreateInternalTransferRequest(
                srcId, dstId, "Alice", new BigDecimal("10.0000"), Currency.USD, "Ref"
        );

        // First request with key
        mockMvc.perform(post("/api/v1/transfers")
                .header("X-Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isAccepted());

        // Second request — same key, different amount → conflict
        TransferRequests.CreateInternalTransferRequest request2 = new TransferRequests.CreateInternalTransferRequest(
                srcId, dstId, "Alice", new BigDecimal("20.0000"), Currency.USD, "Ref"
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict());
    }
}
