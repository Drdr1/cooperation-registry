package dev.derops.registry.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    /** Loads the user with roles and permissions in one query (used on every authenticated request). */
    @Query("select distinct u from AppUser u left join fetch u.roles r left join fetch r.permissions where u.username = :username")
    Optional<AppUser> findWithRolesByUsername(@Param("username") String username);

    @Query("select distinct u from AppUser u left join fetch u.roles r left join fetch r.permissions where u.oneidSubject = :subject")
    Optional<AppUser> findWithRolesByOneidSubject(@Param("subject") String subject);

    @Query("select distinct u from AppUser u left join fetch u.roles r left join fetch r.permissions where u.id = :id")
    Optional<AppUser> findWithRolesById(@Param("id") Long id);

    boolean existsByUsername(String username);

    boolean existsByOneidSubject(String oneidSubject);

    @Query("""
            select u from AppUser u
            where lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.fullName) like lower(concat('%', :q, '%'))
            """)
    Page<AppUser> search(@Param("q") String q, Pageable pageable); // pass "" for no filter
}
