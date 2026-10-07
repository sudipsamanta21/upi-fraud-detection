package com.sudip.account_service.service;

import com.sudip.account_service.dto.AccountResponse;
import com.sudip.account_service.dto.CreateAccountRequest;
import com.sudip.account_service.entity.Account;
import com.sudip.account_service.entity.AccountStatus;
import com.sudip.account_service.entity.AccountType;
import com.sudip.account_service.repository.AccountRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;


@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    private static SecureRandom secureRandom = new SecureRandom();



    public AccountResponse createAccount(
            @Valid CreateAccountRequest createAccountRequest) {
        log.info("Creating account request {}", createAccountRequest);

        if(accountRepository.existsByEmail(createAccountRequest.getEmail())){
            throw new RuntimeException("Account already exists for this email : "
                    + createAccountRequest.getEmail());
        }
        Account account = Account.builder()
                .accountHolderName(createAccountRequest.getAccountHolderName())
                .email(createAccountRequest.getEmail())
                .phone(createAccountRequest.getPhone())
                .accountType(createAccountRequest.getAccountType())
                .accountStatus(AccountStatus.ACTIVE)
                .balance(createAccountRequest.getInitialDeposit())
                .accountNumber(generateAccountNumber())
                .dailyTransactionLimit(
                        createAccountRequest.getAccountType()== AccountType.SAVINGS
                        ? new BigDecimal("100000")
                        : new BigDecimal("500000")
                )
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Account created successfully : {}", savedAccount.getAccountNumber());
        return mapToAccountResponse(savedAccount);
    }



    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(()-> new RuntimeException("Account not found"));
        return mapToAccountResponse(account);
    }



    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account not found"));
        return account.getBalance();
    }


//   Generate unique 12-digit account Number
    private String generateAccountNumber() {
        String accountNumber;
        do {
           long randomNum = secureRandom.nextLong(1_000_000_000_000L);
           accountNumber = String.format("%012d", randomNum);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }



    private AccountResponse mapToAccountResponse(Account savedAccount) {
        AccountResponse accountResponse = AccountResponse.builder()
                .id(savedAccount.getId())
                .accountNumber(savedAccount.getAccountNumber())
                .accountHolderName(savedAccount.getAccountHolderName())
                .email(savedAccount.getEmail())
                .phone(savedAccount.getPhone())
                .accountType(savedAccount.getAccountType())
                .accountStatus(savedAccount.getAccountStatus())
                .balance(savedAccount.getBalance())
                .dailyTransactionLimit(savedAccount.getDailyTransactionLimit())
                .createdAt(savedAccount.getCreatedAt())
                .build();
        return accountResponse;

    }


    public void blockedAccount(String accountNumber) {
        log.info("Blocking account for account number {}", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked successfully for account number {}", accountNumber);
    }


//    Deduct balance from sender account
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting balance {} from account  {}", amount,accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account status is not active"+ accountNumber);
        }

        if(account.getBalance().compareTo(amount) <= 0){
            throw new RuntimeException("Insufficient balance in account "+ accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Balance updated, New balance {} for account {}", account.getBalance(), accountNumber);
    }





    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting  {} to account  {}", amount,accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Balance credited, New balance : {}", account.getBalance());
    }
}
