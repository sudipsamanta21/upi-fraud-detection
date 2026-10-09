package com.sudip.transaction_service.dto;

import com.sudip.transaction_service.entity.TransactionType;
import com.sudip.transaction_service.entity.TransactionsStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {


    private String id;

    private String senderAccountNumber;

    private String receiverAccountNumber;

    private BigDecimal amount;

    private TransactionType transactionType;

    private TransactionsStatus transactionStatus;

    private String description;

    private String failureReason;

    private String referenceNumber;

    private LocalDateTime createAt;

    private LocalDateTime completedAt;
}
