package cl.duoc.users.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, unique = true, length = 12)
    private String rut;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false)
    private String password;

    /** "ADMIN" u "OPERADOR". */
    @Column(nullable = false, length = 20)
    private String rol;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @PrePersist
    void prePersist() {
        if (active == null) {
            active = true;
        }
    }
}
