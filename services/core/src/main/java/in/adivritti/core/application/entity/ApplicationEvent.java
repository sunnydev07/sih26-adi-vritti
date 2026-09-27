package in.adivritti.core.application.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "application_event")
public class ApplicationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "application_id")
    public java.util.UUID applicationId;
    public String stage;
    public String actor;
    public ZonedDateTime timestamp = ZonedDateTime.now();
    public String notes;
}
