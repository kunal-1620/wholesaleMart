package org.example.repo;

import org.example.domain.Business;
import org.example.domain.Role;
import org.example.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByBusinessAndPhone(Business business, String phone);

    List<UserAccount> findByPhone(String phone);

    List<UserAccount> findByBusinessAndRoleOrderByName(Business business, Role role);

    List<UserAccount> findByBusinessAndRoleInOrderByName(Business business, List<Role> roles);

    List<UserAccount> findByRole(Role role);
}
