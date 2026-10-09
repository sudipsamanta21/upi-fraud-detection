package com.sudip.transaction_service.service;

import com.sudip.transaction_service.Repository.TransactionRepository;
import com.sudip.transaction_service.dto.TransactionResponse;
import com.sudip.transaction_service.dto.TransferRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionResponse transfer(@Valid TransferRequest transferRequest) {
      return null;
    }
}
