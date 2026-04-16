package com.typenull.pingdom.domain.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ChangeUsernameRequest {

    @NotBlank(message = "새 이름을 입력해주세요.")
    @Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하여야 합니다.")
    private String newUsername;
}
