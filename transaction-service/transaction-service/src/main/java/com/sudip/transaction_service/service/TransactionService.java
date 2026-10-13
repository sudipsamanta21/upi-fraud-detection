package com.sudip.transaction_service.service;

import com.sudip.transaction_service.Repository.TransactionRepository;
import com.sudip.transaction_service.client.AccountServiceClient;
import com.sudip.transaction_service.dto.TransactionResponse;
import com.sudip.transaction_service.dto.TransferRequest;
import com.sudip.transaction_service.entity.Transaction;
import com.sudip.transaction_service.entity.TransactionType;
import com.sudip.transaction_service.entity.TransactionsStatus;
import com.sudip.transaction_service.event.TransactionCompletedEvent;
import com.sudip.transaction_service.event.TransactionInitiatedEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;



    private static final String  TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String  TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String  TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String  FRAUD_DETECTED_TOPIC ="fraud.detected";





    public TransactionResponse transfer(
            @Valid TransferRequest transferRequest) {
        log.info("SAGA START - Transfer: {} --> {} amount: {}",
                transferRequest.getSenderAccountNumber(),
                transferRequest.getReceiverAccountNumber(),
                transferRequest.getAmount());

        accountServiceClient.deductBalance(
                transferRequest.getSenderAccountNumber(),
                transferRequest.getAmount()
        );
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(transferRequest.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(transferRequest.getReceiverAccountNumber());
        transaction.setAmount(transferRequest.getAmount());
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionsStatus.PROCESSING);
        transaction.setDescription(transferRequest.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", savedTransaction.getId());

        //Publish for fraud check
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savedTransaction.getId(), event);
        log.info("SAGA STEP 2 - TransactionInitiated published: {}", savedTransaction.getId());

        return mapToResponse(savedTransaction);
    }


    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse transactionResponse = new TransactionResponse();
        transactionResponse.setId(transaction.getId());
        transactionResponse.setSenderAccountNumber(transaction.getSenderAccountNumber());
        transactionResponse.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        transactionResponse.setAmount(transaction.getAmount());
        transactionResponse.setDescription(transaction.getDescription());
        transactionResponse.setReferenceNumber(transaction.getReferenceNumber());
        transactionResponse.setTransactionType(transaction.getTransactionType());
        transactionResponse.setTransactionStatus(transaction.getTransactionStatus());
        transactionResponse.setFailureReason(transaction.getFailureReason());
        transactionResponse.setCreateAt(transaction.getCreateAt());
        transactionResponse.setCompletedAt(transaction.getCompletedAt());
        return transactionResponse;

    }





    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository
                .findById(transactionId)
                .orElseThrow(() ->new RuntimeException("Transaction not found: " + transactionId))
        );
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.findBySenderAccountNumberOrderByCreateAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse verifyOTP(String transactionId, String otp) {
        log.info("OTP verification for the transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() ->new RuntimeException("Transaction not found: " + transactionId));

        String otpKey = "verification:otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null){
            log.warn("OTP  expired for the transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP expired - transaction cancelled and amount refunded  ");
            return mapToResponse(transaction);
        }


        if(!storedOtp.equals(otp)){
            log.warn("Wrong OTP - blocking account and refunding: {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,
                    "Wrong OTP entered - transaction cancelled, "+
                            "account blocked for security "
                    );
            return mapToResponse(transaction);
        }
        // otp correct - complete transaction
        log.info("OTP verified - completing transaction: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }

    private void completeTransaction(Transaction transaction) {
        transaction.setTransactionStatus(TransactionsStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent =
                new TransactionCompletedEvent(
                        transaction.getId(),
                        transaction.getSenderAccountNumber(),
                        transaction.getReceiverAccountNumber(),
                        transaction.getAmount(),
                        transaction.getDescription()
                );


        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);

        log.info("SAGA COMPLETE - Transaction {} completed", transaction.getId());
    }

    private void blockAccountAndCompensate(Transaction transaction , String reason) {
        Map<String,Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", "Wrong OTP entered - transaction cancelled, account blocked for security");

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("fraud.detected published - account: {} will be blocked, Kindly contact to the bank"
                , transaction.getSenderAccountNumber());

        //SAGA compensation
        compensateTransaction(transaction, "Wrong OTP entered - transaction cancelled, account blocked for security");
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount:{}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount());


        //credited money back to sender synchronously
        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
        );
        transaction.setTransactionStatus(TransactionsStatus.FLAGGED);
        transaction.setFailureReason(reason+
                "- SAGA Compensation executed, amount refunded at." + LocalDateTime.now());
        transactionRepository.save(transaction);

        // PUBLISH refund event - Notification service will alert user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

        log.info("SAGA COMPENSATION COMPLETED - {} refunded to {}",
                transaction.getAmount(), transaction.getSenderAccountNumber());
    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() ->new RuntimeException("Transaction not found: " + transactionId));

        if(transaction.getTransactionStatus() != TransactionsStatus.PROCESSING){
            log.info("Transaction {} not PROCESSING- skipping", transactionId);
            return;
        }
        completeTransaction(transaction);
    }


}
