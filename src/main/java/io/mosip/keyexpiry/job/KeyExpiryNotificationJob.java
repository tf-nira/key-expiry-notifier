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

    @Value("${mosip.keymgr.expiry-notification.email.subject:Key Manager — Keys Expiring Soon}")
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

        StringBuilder rows = new StringBuilder();
        expiringKeys.forEach(k -> rows.append("<tr>")
                .append("<td style='padding:6px 12px;border:1px solid #ddd;'>")
                .append(k.getAppId()).append("</td>")
                .append("<td style='padding:6px 12px;border:1px solid #ddd;'>")
                .append(k.getRefId() != null ? k.getRefId() : "N/A").append("</td>")
                .append("<td style='padding:6px 12px;border:1px solid #ddd;'>")
                .append(k.getKeyExpireDtimes() != null
                        ? k.getKeyExpireDtimes().format(formatter) : "N/A")
                .append("</td>")
                .append("</tr>"));

        return "<html><body style='font-family:Arial,sans-serif;font-size:14px;'>"
                + "<p>Dear Team,</p>"
                + "<p>The following <strong>" + expiringKeys.size() + " keys</strong> are expiring "
                + "within <strong>" + daysThreshold + " days</strong>.</p>"
                + "<p><strong>Report Date:</strong> "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + "</p>"
                + "<table style='border-collapse:collapse;width:100%;'>"
                + "<thead><tr style='background-color:#4472C4;color:white;'>"
                + "<th style='padding:8px 12px;border:1px solid #ddd;text-align:left;'>App ID</th>"
                + "<th style='padding:8px 12px;border:1px solid #ddd;text-align:left;'>Reference ID</th>"
                + "<th style='padding:8px 12px;border:1px solid #ddd;text-align:left;'>Expiry Date</th>"
                + "</tr></thead>"
                + "<tbody>" + rows + "</tbody>"
                + "</table>"
                + "<br/><p>Please take necessary action.</p>"
                + "<p>Regards,<br/><strong>Key Manager System</strong></p>"
                + "</body></html>";
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