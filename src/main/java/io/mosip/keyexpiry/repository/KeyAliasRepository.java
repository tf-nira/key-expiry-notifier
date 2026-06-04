package io.mosip.keyexpiry.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.mosip.keyexpiry.entity.KeyAlias;

public interface KeyAliasRepository extends JpaRepository<KeyAlias, String>{
	@Query("SELECT k FROM KeyAlias k " +
	         "WHERE k.keyExpireDtimes <= :threshold " +
	         "AND (k.isDeleted IS NULL OR k.isDeleted = false) " +
	         "ORDER BY k.keyExpireDtimes ASC")
	List<KeyAlias> findExpiringKeys(@Param("threshold") LocalDateTime thresold);

}
