package com.zina.sepa.service;

import com.zina.sepa.domain.entity.User;
import com.zina.sepa.dto.CreateUserRequest;
import com.zina.sepa.dto.RegisterRequest;
import com.zina.sepa.face.FaceService;
import com.zina.sepa.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FaceService faceService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, FaceService faceService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.faceService = faceService;
    }

    /** Inscription publique : compte inactif jusqu'à approbation par un administrateur. */
    public User register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Nom d'utilisateur déjà utilisé");
        }
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw new IllegalArgumentException("Adresse email déjà utilisée");
        }
        String faceEncoding = faceService.encoder(req.image());
        User user = User.builder()
                .username(req.username())
                .password(passwordEncoder.encode(req.password()))
                .nomComplet(req.nomComplet())
                .email(req.email())
                .role(req.role())
                .faceEncoding(faceEncoding)
                .actif(false)
                .approuve(false)
                .build();
        return userRepository.save(user);
    }

    public User approuver(Long id) {
        User user = findById(id);
        user.setApprouve(true);
        user.setActif(true);
        return userRepository.save(user);
    }

    /** Refus d'une demande d'inscription : le compte non approuvé est supprimé. */
    public void rejeter(Long id) {
        User user = findById(id);
        if (user.isApprouve()) {
            throw new IllegalStateException("Seule une demande en attente peut être rejetée");
        }
        userRepository.delete(user);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
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
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::isApprouve).thenComparing(User::getId))
                .toList();
    }

    public void deactivate(Long id) {
        User user = findById(id);
        user.setActif(false);
        userRepository.save(user);
    }
}
