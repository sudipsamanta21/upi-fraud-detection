package com.sudip.transaction_service.service;

import com.sudip.transaction_service.Repository.TransactionRepository;
import com.sudip.transaction_service.entity.Transaction;
import com.sudip.transaction_service.entity.TransactionsStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String,String> redisTemplate;
    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String TRANSACTION_OTP_GENERATED_TOPIC= "transaction.otp.generated";

    private static final long OTP_EXPIRY_MINUTES = 5;


    @KafkaListener(topics = "verification.required")
    public void consumeTransactionRequired(
            @Payload Map<String,Object> payload) {
        try{
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            log.info("Verification required - transaction: {}, reason: {}", transactionId, reason);

            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new RuntimeException(
                            "Transaction not found " + transactionId));


            if(transaction.getTransactionStatus() != TransactionsStatus.PROCESSING){
                log.info("Transaction {} not PROCESSING- skipping", transactionId);
                return;
            }

            //Generate 6 digit otp
            String otp = String.format("%6d",(int) (Math.random()*900000) +100000);


            // Store otp in Redis
            String otpKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(otpKey,otp,OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);


            // update status
            transaction.setTransactionStatus(TransactionsStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP generated for transaction: {} expires in {} min", transactionId, OTP_EXPIRY_MINUTES);

            // notify user
            Map<String,Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId",transactionId);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("reason",reason);
            otpEvent.put("otp",otp);
            otpEvent.put("amount", payload.get("amount"));

            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId, otpEvent);

        }catch (Exception e){
            log.info("Error handing verification required: {}",  e.getMessage());
        }
    }
}
