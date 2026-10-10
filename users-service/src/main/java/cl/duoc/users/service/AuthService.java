package cl.duoc.users.service;

import cl.duoc.users.dto.LoginRequest;
import cl.duoc.users.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
