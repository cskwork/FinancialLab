package io.github.cskwork.finlab.auth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(nullable = false, length = 20)
    private String role;

    protected User() {}
    public User(String email, String passwordHash, String role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
}
