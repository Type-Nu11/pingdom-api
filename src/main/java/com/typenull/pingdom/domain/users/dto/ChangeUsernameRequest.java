package com.typenull.pingdom.domain.users.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ChangeUsernameRequest {

    @NotBlank
    private String newUsername;
}
