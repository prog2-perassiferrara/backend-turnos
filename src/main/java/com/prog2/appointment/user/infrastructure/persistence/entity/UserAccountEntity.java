package com.prog2.appointment.user.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import com.prog2.appointment.user.domain.model.RegistrationData;

/** Cuenta local persistida; no se enlaza directamente con JSON ni sale de infraestructura. */
@Entity
@Table(name = "app_user")
public class UserAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String login;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;
    @Column(name = "image_url", length = 256)
    private String imageUrl;
    @Column(name = "lang_key", nullable = false, length = 10)
    private String langKey;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false)
    private boolean activated;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @ElementCollection
    @CollectionTable(name = "user_identifier", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "identifier_value", nullable = false, length = 254)
    private Set<String> identifiers = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "user_authority", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "authority", nullable = false, length = 50)
    private Set<String> authorities = new HashSet<>();

    protected UserAccountEntity() {
        // Constructor requerido por JPA; el alta de aplicación usa el constructor completo.
    }

    /**
     * Materializa el contrato createActiveUser: identidad, activación, autoridad y auditoría propios.
     * @param data perfil validado y normalizado
     * @param passwordHash contraseña protegida antes de abrir la transacción
     * @param now instante UTC del reloj inyectado
     */
    public UserAccountEntity(RegistrationData data, String passwordHash, Instant now) {
        this.login = data.login();
        this.email = data.email();
        this.firstName = data.firstName();
        this.lastName = data.lastName();
        this.imageUrl = data.imageUrl();
        this.langKey = data.langKey();
        this.passwordHash = passwordHash;
        this.activated = true;
        this.createdAt = now;
        this.lastModifiedAt = now;
        this.identifiers.add(login);
        this.identifiers.add(email);
        this.authorities.add("ROLE_USER");
    }

    public Long getId() {
        return id;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActivated() {
        return activated;
    }

    public Set<String> getAuthorities() {
        return Set.copyOf(authorities);
    }
}
