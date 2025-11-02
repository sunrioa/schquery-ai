package cn.ling.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    private String userName;

    private String password;

    private String rePassword;

    private String email;

    private String code;

}
