package dev.derops.registry.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    @Query("select distinct r from Role r left join fetch r.permissions order by r.id")
    List<Role> findAllWithPermissions();
}
