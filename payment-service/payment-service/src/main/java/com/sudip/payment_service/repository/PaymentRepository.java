package com.sudip.payment_service.repository;

import com.sudip.payment_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment,String> {

    Optional<Object> findByRazorpayOrderId(String razorpayOrderId);
}
