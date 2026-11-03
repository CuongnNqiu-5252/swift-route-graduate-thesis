package com.swiftroute.authservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.swiftroute.authservice.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    @org.springframework.data.jpa.repository.Query("select u from User u where lower(u.email)=lower(:email)")
    Optional<User> findByEmail(@org.springframework.data.repository.query.Param("email") String email);

}
