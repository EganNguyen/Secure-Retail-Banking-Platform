package com.bank.transfer.infrastructure.notification;

import com.bank.transfer.application.BalanceView;
import com.bank.transfer.application.TransferNotificationService;
import com.bank.transfer.application.TransferReadModel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketTransferNotificationService implements TransferNotificationService {
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketTransferNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void transferUpdated(TransferReadModel transfer) {
        messagingTemplate.convertAndSend("/topic/transfers/" + transfer.transferId(), transfer);
    }

    @Override
    public void balanceUpdated(BalanceView balanceView) {
        messagingTemplate.convertAndSend("/topic/accounts/" + balanceView.accountId() + "/balance", balanceView);
    }
}
