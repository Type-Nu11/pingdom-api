package com.typenull.pingdom.domain.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String name;

    // 이메일 인증 연계용 메일 주소
    @Column(length = 255)
    private String email;

    // 이메일 인증 완료 상태
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private String password;

    // 이메일 인증 완료 처리 메서드
    public void verifyEmail() {
        this.emailVerified = true;
    }
}
