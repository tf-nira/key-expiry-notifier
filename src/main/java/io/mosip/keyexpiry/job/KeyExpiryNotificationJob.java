package io.mosip.keyexpiry.job;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.keyexpiry.entity.KeyAlias;
import io.mosip.keyexpiry.repository.KeyAliasRepository;
@Component
public class KeyExpiryNotificationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyExpiryNotificationJob.class);
    private static final String JOB_NAME = "KeyExpiryNotificationJob";

    @Value("${mosip.keymgr.expiry-notification.days-threshold:30}")
    private int daysThreshold;


    @Value("${mosip.keymgr.expiry-notification.recipients}")
    private String recipients;

    @Value("${mosip.keymgr.expiry-notification.email.notification.url}")
    private String emailNotificationUrl;

    @Value("${mosip.keymgr.expiry-notification.email.subject:CRITICAL: Key Manager Keys Expiring Soon - Immediate Action Required}")
    private String emailSubject;

//    @Value("${mosip.keymgr.expiry-notification.email.template}")
//    private String emailTemplate;

    @Autowired
    @Qualifier("selfTokenRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private KeyAliasRepository keyAliasRepository;

    @Scheduled(cron = "${mosip.keymgr.expiry-notification.cron:0 0 3 * * ?}")
    public void processKeyExpiryNotifications() {
        String sessionId = UUID.randomUUID().toString();

        try {
            LOGGER.info("[{}] [{}] Job started.  daysThreshold={}",
                    sessionId, JOB_NAME, daysThreshold);

            processNotification(sessionId);

            LOGGER.info("[{}] [{}] Job completed successfully", sessionId, JOB_NAME);

        } catch (Exception e) {
            LOGGER.error("[{}] [{}] Job failed: {}", sessionId, JOB_NAME, e.getMessage(), e);
            throw new RuntimeException("Key expiry notification job failed", e);
        }
    }

    private void processNotification(String sessionId) {
    	LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiryThreshold = LocalDateTime.now().plusDays(daysThreshold);
        
        int pageSize = 500;
        int offset = 0;
        
        List<KeyAlias> allExpiringKeys = new ArrayList<>();
        List<KeyAlias> batch;
        do {
        	batch = keyAliasRepository.findExpiringKeys(expiryThreshold, now, pageSize, offset);
        	allExpiringKeys.addAll(batch);
        	offset += pageSize;
        } while (batch.size() == pageSize);
        
        //List<KeyAlias> expiringKeys = keyAliasRepository.findExpiringKeys(expiryThreshold, now);

        LOGGER.info("[{}] [{}] Found {} keys expiring within {} days",
                sessionId, JOB_NAME, allExpiringKeys.size(), daysThreshold);

        if (allExpiringKeys.isEmpty()) {
            LOGGER.info("[{}] [{}] No expiring keys found. Job will exit.", sessionId, JOB_NAME);
            return;
        }

        String emailBody = buildEmailBody(allExpiringKeys);

        for (String recipient : recipients.split(",")) {
            String trimmed = recipient.trim();
            if (!trimmed.isEmpty()) {
                try {
                    sendEmail(sessionId, trimmed, emailSubject, emailBody);
                } catch (Exception e) {
                    LOGGER.error("[{}] [{}] Failed to send email to {}: {}",
                            sessionId, JOB_NAME, trimmed, e.getMessage());
                }
            }
        }
    }
    
    private String buildEmailBody(List<KeyAlias> expiringKeys) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        String reportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        // already-expired vs expiring soon
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long expiredCount = expiringKeys.stream()
                .filter(k -> k.getKeyExpireDtimes() != null && k.getKeyExpireDtimes().isBefore(now))
                .count();
        long expiringCount = expiringKeys.size() - expiredCount;

        StringBuilder rows = new StringBuilder();
        expiringKeys.forEach(k -> {
            boolean isExpired = k.getKeyExpireDtimes() != null && k.getKeyExpireDtimes().isBefore(now);
            String rowBg = isExpired ? "#fff3f3" : "#fffdf0";
            String badge = isExpired
                    ? "<span style='background:#dc3545;color:white;padding:2px 8px;border-radius:12px;font-size:11px;font-weight:bold;'>EXPIRED</span>"
                    : "<span style='background:#fd7e14;color:white;padding:2px 8px;border-radius:12px;font-size:11px;font-weight:bold;'>EXPIRING SOON</span>";

            rows.append("<tr style='background-color:").append(rowBg).append(";'>")
                    .append("<td style='padding:10px 14px;border-bottom:1px solid #e0e0e0;'>")
                    .append(k.getAppId()).append("</td>")
                    .append("<td style='padding:10px 14px;border-bottom:1px solid #e0e0e0;font-family:monospace;'>")
                    .append(k.getRefId() != null ? k.getRefId() : "<em style='color:#999;'>N/A</em>").append("</td>")
                    .append("<td style='padding:10px 14px;border-bottom:1px solid #e0e0e0;'>")
                    .append(k.getKeyExpireDtimes() != null ? k.getKeyExpireDtimes().format(formatter) : "N/A")
                    .append("</td>")
                    .append("<td style='padding:10px 14px;border-bottom:1px solid #e0e0e0;text-align:center;'>")
                    .append(badge).append("</td>")
                    .append("</tr>");
        });

        return "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;'>"

                // Wrapper
                + "<div style='max-width:700px;margin:30px auto;background:#ffffff;border-radius:8px;"
                + "box-shadow:0 2px 8px rgba(0,0,0,0.12);overflow:hidden;'>"

                // Red alert header bar
                + "<div style='background:#c0392b;padding:20px 28px;'>"
                + "<table style='width:100%;border-collapse:collapse;'><tr>"
                + "<td><span style='font-size:28px;'>🔑</span></td>"
                + "<td style='padding-left:12px;'>"
                + "<div style='color:white;font-size:20px;font-weight:bold;letter-spacing:0.5px;'>"
                + "⚠️ CRITICAL: Key Expiry Alert</div>"
                + "<div style='color:#f5b7b1;font-size:13px;margin-top:4px;'>MOSIP Key Manager — Immediate Action Required</div>"
                + "</td></tr></table>"
                + "</div>"

                // Orange urgency banner
                + "<div style='background:#e67e22;color:white;padding:10px 28px;font-size:13px;font-weight:bold;letter-spacing:0.3px;'>"
                + "⏰ ACTION REQUIRED: " + expiringKeys.size() + " key(s) require attention before they expire"
                + "</div>"

                // Body content
                + "<div style='padding:28px;'>"

                // Greeting
                + "<p style='margin:0 0 16px;color:#333;font-size:15px;'>Dear Team,</p>"
                + "<p style='margin:0 0 20px;color:#333;font-size:15px;'>The Key Manager system has detected cryptographic keys that are <strong>expiring within " + daysThreshold + " days or have already expired</strong>. "
                + "Failure to renew these keys may result in <strong>authentication failures and service disruptions</strong>.</p>"

                // Summary cards
                + "<table style='width:100%;border-collapse:collapse;margin-bottom:24px;'><tr>"
                + "<td style='width:33%;padding:16px;background:#fdf2f8;border-radius:6px;text-align:center;border:1px solid #f1c0e8;'>"
                + "<div style='font-size:28px;font-weight:bold;color:#8e44ad;'>" + expiringKeys.size() + "</div>"
                + "<div style='font-size:12px;color:#666;margin-top:4px;'>Total Keys</div></td>"
                + "<td style='width:4px;'></td>"
                + "<td style='width:33%;padding:16px;background:#fef9e7;border-radius:6px;text-align:center;border:1px solid #f9e79f;'>"
                + "<div style='font-size:28px;font-weight:bold;color:#e67e22;'>" + expiringCount + "</div>"
                + "<div style='font-size:12px;color:#666;margin-top:4px;'>Expiring Soon</div></td>"
                + "<td style='width:4px;'></td>"
                + "<td style='width:33%;padding:16px;background:#fdedec;border-radius:6px;text-align:center;border:1px solid #fadbd8;'>"
                + "<div style='font-size:28px;font-weight:bold;color:#c0392b;'>" + expiredCount + "</div>"
                + "<div style='font-size:12px;color:#666;margin-top:4px;'>Already Expired</div></td>"
                + "</tr></table>"

                // Report meta
                + "<p style='margin:0 0 16px;color:#555;font-size:13px;'>"
                + "📅 <strong>Report Date:</strong> " + reportDate
                + "</p>"

                // Table
                + "<table style='width:100%;border-collapse:collapse;font-size:13px;'>"
                + "<thead><tr style='background:#2c3e50;color:white;'>"
                + "<th style='padding:10px 14px;text-align:left;font-weight:600;'>App ID</th>"
                + "<th style='padding:10px 14px;text-align:left;font-weight:600;'>Reference ID</th>"
                + "<th style='padding:10px 14px;text-align:left;font-weight:600;'>Expiry Date/Time</th>"
                + "<th style='padding:10px 14px;text-align:center;font-weight:600;'>Status</th>"
                + "</tr></thead>"
                + "<tbody>" + rows + "</tbody>"
                + "</table>"

                + "<p style='margin-top:24px;color:#333;font-size:14px;'>Regards,<br/>"
                + "<strong>Key Manager Monitoring System</strong><br/>"
                + "<span style='color:#888;font-size:12px;'>NIRA — National Identification and Registration Authority</span></p>"

                + "</div>" // end body

                // Footer
                + "<div style='background:#2c3e50;color:#aab7c4;padding:14px 28px;font-size:11px;text-align:center;'>"
                + "This is an automated alert from the MOSIP Key Manager. Do not reply to this email."
                + "</div>"

                + "</div></body></html>";
    }
    
    
    private void sendEmail(String sessionId, String mailTo, String subject, String emailBody) {
        LOGGER.info("[{}] [{}] Sending email to: {}", sessionId, JOB_NAME, mailTo);
        try {
        	LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
            params.add("mailTo", mailTo);
            params.add("mailSubject", subject);
            params.add("mailContent", emailBody);
            params.add("mailContentType", "text/html");
        	
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(emailNotificationUrl)
                    .queryParam("mailTo", mailTo)
                    .queryParam("mailSubject", subject)
                    .queryParam("mailContent", emailBody);


            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(params, headers);

            ResponseEntity<ResponseWrapper> responseEntity = restTemplate.exchange(
            		emailNotificationUrl,
                    HttpMethod.POST,
                    requestEntity,
                    ResponseWrapper.class
            );

            if (responseEntity.getBody() == null) {
                LOGGER.error("[{}] [{}] Failed to send email. Status code: {}",
                        sessionId, JOB_NAME, responseEntity.getStatusCodeValue());
                throw new RuntimeException("Email failed with status: "
                        + responseEntity.getStatusCodeValue());
            }

            ResponseWrapper<?> responseWrapper = responseEntity.getBody();

            if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
                LOGGER.error("[{}] [{}] Email error response: {}",
                        sessionId, JOB_NAME, responseWrapper.getErrors().get(0));
                throw new RuntimeException("Email failed with error: "
                        + responseWrapper.getErrors().get(0));
            }

            LOGGER.info("[{}] [{}] Email sent successfully to: {}", sessionId, JOB_NAME, mailTo);

        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("[{}] [{}] Exception while sending email: {}",
                    sessionId, JOB_NAME, ex.getMessage(), ex);
            throw new RuntimeException("Email notification failed: " + ex.getMessage(), ex);
        }
    }
}