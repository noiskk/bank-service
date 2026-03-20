package com.bank.common.enums;

/**
 * 계좌 상태
 */
public enum AccountStatus {
    ACTIVE("정상"),
    SUSPENDED("정지"),
    CLOSED("해지");
    
    private final String description;
    
    AccountStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
