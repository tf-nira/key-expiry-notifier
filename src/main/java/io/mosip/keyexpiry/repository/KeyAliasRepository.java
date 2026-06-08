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
	           // Only keep the latest record per (appId, referenceId) group
	           "AND k.keyExpireDtimes = (" +
	           "    SELECT MAX(k2.keyExpireDtimes) FROM KeyAlias k2 " +
	           "    WHERE k2.appId = k.appId " +
	           "    AND k2.refId = k.refId " +
	           "    AND (k2.isDeleted IS NULL OR k2.isDeleted = false) " +
	           ") " +
	           // Exclude groups where a valid (non-expired) key exists
	           "AND NOT EXISTS (" +
	           "    SELECT 1 FROM KeyAlias k3 " +
	           "    WHERE k3.appId = k.appId " +
	           "    AND k3.refId = k.refId " +
	           "    AND (k3.isDeleted IS NULL OR k3.isDeleted = false) " +
	           "    AND k3.keyExpireDtimes > :now " +
	           ") " +
	           "ORDER BY k.keyExpireDtimes ASC")
	List<KeyAlias> findExpiringKeys(@Param("threshold") LocalDateTime thresold, @Param("now") LocalDateTime now);

}
