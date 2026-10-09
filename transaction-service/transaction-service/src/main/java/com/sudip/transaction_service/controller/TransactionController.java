package com.sudip.transaction_service.controller;


import com.sudip.transaction_service.dto.TransactionResponse;
import com.sudip.transaction_service.dto.TransferRequest;
import com.sudip.transaction_service.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@Slf4j
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;


    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest transferRequest){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.transfer(transferRequest));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable String transactionId){
        return ResponseEntity.status(HttpStatus.OK)
                .body(transactionService.getTransaction(transactionId));
    }


    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(
            @PathVariable String accountNumber){
        return ResponseEntity.status(HttpStatus.OK)
                .body(transactionService.getTransactionHistory(accountNumber));
    }

    @PostMapping("/{transactionId}/verify")
    public ResponseEntity<TransactionResponse> verifyOTP(
            @PathVariable String transactionId,
            @RequestParam String otp){
        log.info("OTP verification request - transaction: {} ", transactionId);
        return  ResponseEntity.status(HttpStatus.OK)
                .body(transactionService.verifyOTP(transactionId,otp));
    }

}
