package com.shopchain.repository;

import com.shopchain.entity.UserAddress;
import com.shopchain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserId(User user);
}
