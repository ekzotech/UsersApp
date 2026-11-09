package com.example.usersapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "teachers")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    private String fullName;

    private String email;

    private boolean active = true;

    private boolean curator;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(name = "teacher_courses",
            joinColumns = @JoinColumn(name = "teacher_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    @ToString.Exclude
    private Set<Course> courses = new HashSet<>();

    @OneToMany(mappedBy = "teacher")
    @ToString.Exclude
    private List<Enrollment> enrollments = new ArrayList<>();

    public String getDisplayStatus() {
        return active ? "Активен" : "Уволен";
    }

    public boolean teaches(Course course) {
        return courses.stream().anyMatch(c -> c.getId() != null && c.getId().equals(course.getId()));
    }
}
