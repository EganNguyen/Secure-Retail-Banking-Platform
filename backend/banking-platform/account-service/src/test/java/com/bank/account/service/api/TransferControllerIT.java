package com.bank.account.service.api;

import com.bank.sharedkernel.domain.Currency;
import com.bank.transfer.application.BalanceView;
import com.bank.transfer.application.TransferQueryService;
import com.bank.transfer.application.TransferReadModel;
import com.bank.transfer.application.TransferSagaOrchestrator;
import com.bank.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
class TransferControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferSagaOrchestrator orchestrator;

    @MockBean
    private TransferQueryService queryService;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    void shouldCreateInternalTransfer() throws Exception {
        String transferId = UUID.randomUUID().toString();
        TransferReadModel readModel = new TransferReadModel(
                transferId,
                "SRC-1",
                "DST-1",
                "Alice Doe",
                new BigDecimal("25.0000"),
                Currency.USD,
                "Lunch",
                TransferStatus.COMPLETED,
                "LEDGER-123",
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        Mockito.when(orchestrator.initiateInternalTransfer(Mockito.any())).thenReturn(readModel);
        Mockito.when(idempotencyService.find(Mockito.anyString())).thenReturn(java.util.Optional.empty());
        Mockito.when(idempotencyService.begin(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(null);

        TransferRequests.CreateInternalTransferRequest request = new TransferRequests.CreateInternalTransferRequest(
                "SRC-1",
                "DST-1",
                "Alice Doe",
                new BigDecimal("25.0000"),
                Currency.USD,
                "Lunch"
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .header("X-Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transferId").value(transferId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldGetProjectedBalance() throws Exception {
        Mockito.when(queryService.getBalance("SRC-1")).thenReturn(
                new BalanceView("SRC-1", new BigDecimal("75.0000"), Currency.USD, Instant.now())
        );

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", "SRC-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("SRC-1"))
                .andExpect(jsonPath("$.availableBalance").value(75.0000));
    }
}
