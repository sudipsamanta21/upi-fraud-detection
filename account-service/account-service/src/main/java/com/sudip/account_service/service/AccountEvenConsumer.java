package com.sudip.account_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEvenConsumer {

    private final AccountService accountService;


    @KafkaListener(topics = "transaction-completed")
    public void consumeTransactionCompleted(@Payload Map<String, Object> payload) {
        log.info("Received transaction completed event: {}", payload);
        // Process the event and update account balance accordingly
        try{
            String receiverAccountNumber = (String) payload.get("receiverAccountNumber");
            BigDecimal amount =new BigDecimal ( payload.get("amount").toString());

            log.info("Crediting account {}  amount {}", receiverAccountNumber, amount);

            accountService.creditBalance(receiverAccountNumber, amount);

        }catch(Exception e){
            log.error("Error crediting account: {}", e.getMessage());
        }
    }



    @KafkaListener(topics = "Fraud-detected")
    public void consumeFraudDetected (@Payload Map<String, Object> payload) {
        try {
            String accountNumber = (String) payload.get("accountNumber");
            log.info("Fraud detected account - blocking account: {}", accountNumber);
            accountService.blockedAccount(accountNumber);
        } catch (Exception e) {
           log.info("Error blocking account: {}", e.getMessage());
        }
    }
}
