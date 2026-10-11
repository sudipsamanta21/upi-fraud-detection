package com.sudip.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentOrderResponse {

    private String paymentId;

    private String razorpayOrderId;

    private BigDecimal amount;

    private String currency;

    private String paymentStatus;

    private String razorpayKeyId;
}
