package io.mosip.keyexpiry.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "key_alias", schema ="keymgr")
@Data
public class KeyAlias {
	
	@Id
	@Column(name = "id")
	private String id;
	

    @Column(name = "app_id")
    private String appId;

    @Column(name = "ref_id")
    private String refId;

    @Column(name = "status_code")
    private String statusCode;

    @Column(name = "lang_code")
    private String langCode;

    @Column(name = "uni_ident")
    private String uniIdent;

    @Column(name = "cert_thumbprint")
    private String certThumbprint;

    @Column(name = "key_gen_dtimes")
    private LocalDateTime keyGenDtimes;

    @Column(name = "key_expire_dtimes")
    private LocalDateTime keyExpireDtimes;

    @Column(name = "cr_by")
    private String crBy;

    @Column(name = "cr_dtimes")
    private LocalDateTime crDtimes;

    @Column(name = "upd_by")
    private String updBy;

    @Column(name = "upd_dtimes")
    private LocalDateTime updDtimes;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name = "del_dtimes")
    private LocalDateTime delDtimes;
	

}
