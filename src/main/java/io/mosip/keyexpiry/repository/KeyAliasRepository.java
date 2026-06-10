package io.mosip.keyexpiry.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.mosip.keyexpiry.entity.KeyAlias;

public interface KeyAliasRepository extends JpaRepository<KeyAlias, String>{
	@Query(value =
		    "WITH ranked_keys AS ( " +
		    "    SELECT *, " +
		    "           ROW_NUMBER() OVER ( " +
		    "               PARTITION BY app_id, ref_id " +
		    "               ORDER BY key_expire_dtimes DESC " +
		    "           ) AS rn, " +
		    "           MAX(CASE WHEN key_expire_dtimes > :now THEN 1 ELSE 0 END) " +
		    "               OVER (PARTITION BY app_id, ref_id) AS has_valid_key " +
		    "    FROM keymgr.key_alias " +
		    "    WHERE (is_deleted IS NULL OR is_deleted = false) " +
		    ") " +
		    "SELECT * FROM ranked_keys " +
		    "WHERE rn = 1 " +
		    "  AND has_valid_key = 0 " +
		    "  AND key_expire_dtimes <= :threshold " +
		    "ORDER BY key_expire_dtimes ASC " +
		    "LIMIT :pageSize OFFSET :offset",
		    nativeQuery = true)
	List<KeyAlias> findExpiringKeys(@Param("threshold") LocalDateTime threshold, @Param("now") LocalDateTime now,
									@Param("pageSize") int pageSize, @Param("offset") int offset);

}
