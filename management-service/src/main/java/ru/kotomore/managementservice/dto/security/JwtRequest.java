package ru.kotomore.managementservice.dto.security;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class JwtRequest implements Serializable {
    @NotEmpty(message = "Not be empty")
    @Size(min = 3, max = 20, message = "Login size must be between 3 and 20 characters")
    private String login;
    @NotEmpty(message = "Not be empty")
    @Size(min = 3, max = 20, message = "Password size must be between 3 and 20 characters")
    private String password;
}
