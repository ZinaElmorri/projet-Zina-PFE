package com.zina.sepa.service;

import com.zina.sepa.domain.entity.User;
import com.zina.sepa.dto.CreateUserRequest;
import com.zina.sepa.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User create(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Nom d'utilisateur déjà utilisé");
        }
        User user = User.builder()
                .username(req.username())
                .password(passwordEncoder.encode(req.password()))
                .nomComplet(req.nomComplet())
                .email(req.email())
                .role(req.role())
                .actif(true)
                .build();
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public void deactivate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        user.setActif(false);
        userRepository.save(user);
    }
}
