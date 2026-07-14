package com.bank.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 전역 활성화 설정.
 * 엔티티의 @CreatedDate / @LastModifiedDate 가 실제로 자동 기록되게 하는 "메인 스위치".
 * 이 설정이 없으면 @CreatedDate 필드가 null로 남아 NOT NULL 제약에 걸린다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
