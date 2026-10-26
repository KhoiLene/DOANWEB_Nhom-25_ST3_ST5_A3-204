package com.shopchain.repository;

import com.shopchain.entity.InternalMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InternalMessageRepository extends JpaRepository<InternalMessage, Long> {
    List<InternalMessage> findTop100ByOrderByCreatedAtDesc();

    @Query("select m from InternalMessage m where m.recipientRole in :roles or m.sender.username = :username order by m.createdAt desc")
    List<InternalMessage> findVisibleTo(@Param("roles") List<String> roles,
                                        @Param("username") String username, Pageable pageable);
}
