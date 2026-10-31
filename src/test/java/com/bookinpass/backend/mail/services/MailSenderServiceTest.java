package com.bookinpass.backend.mail.services;

import io.awspring.cloud.ses.SimpleEmailServiceJavaMailSender;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MailSenderServiceTest {

    private final SimpleEmailServiceJavaMailSender ses = mock(SimpleEmailServiceJavaMailSender.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SimpleEmailServiceJavaMailSender> provider = mock(ObjectProvider.class);
    private final MailSenderService service = new MailSenderService(provider);

    private MimeMessage sent() {
        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(ses).send(message.capture());
        return message.getValue();
    }

    private void sesAvailable() {
        given(provider.getIfAvailable()).willReturn(ses);
        given(ses.createMimeMessage()).willAnswer(_ -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private boolean send(Set<String> bcc, Map<String, String> tags, Map<String, byte[]> attachments, Map<String, byte[]> images) {
        return service.sendEmailWithRetry("test", "no-reply@example.com", "Book In Pass", "awa@example.com", bcc, "Sujet", "<p>Corps</p>",
                tags, attachments, images);
    }

    @Test
    void withoutSesNothingIsSent() {
        assertThat(send(null, null, null, null)).isFalse();
        verify(provider).getIfAvailable();
    }

    @Test
    void addressesSubjectAndTagsAreSet() throws Exception {
        sesAvailable();

        assertThat(send(Set.of("audit@example.com", " "), Map.of("kind", "voucher"), null, null)).isTrue();

        MimeMessage message = sent();
        assertThat(message.getSubject()).isEqualTo("Sujet");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Book In Pass");
        assertThat(message.getRecipients(Message.RecipientType.TO)).extracting(Address::toString).containsExactly("awa@example.com");
        assertThat(message.getRecipients(Message.RecipientType.BCC)).extracting(Address::toString).containsExactly("audit@example.com");
        assertThat(message.getHeader("X-SES-MESSAGE-TAGS")).containsExactly("kind=voucher");
    }

    @Test
    void blankBccAddsNoHiddenRecipient() throws Exception {
        sesAvailable();

        assertThat(send(Set.of(" "), Map.of(), Map.of(), Map.of())).isTrue();

        MimeMessage message = sent();
        assertThat(message.getRecipients(Message.RecipientType.BCC)).isNull();
        assertThat(message.getHeader("X-SES-MESSAGE-TAGS")).isNull();
    }

    @Test
    void attachmentsAndInlineImagesAreKeptButAnOversizedAttachmentIsSkipped() throws Exception {
        sesAvailable();

        assertThat(send(null, null, Map.of("billet.pdf", new byte[]{1}, "note.txt", new byte[]{2}, "trop-gros.pdf", new byte[8_000_001]),
                Map.of("qr", new byte[]{3}))).isTrue();

        MimeMessage message = sent();
        message.saveChanges();
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        message.writeTo(raw);
        assertThat(raw.toString())
                .contains("billet.pdf", "application/pdf", "note.txt", "application/octet-stream", "Content-ID: <qr>")
                .doesNotContain("trop-gros.pdf");
    }

    @Test
    void aFailedSendIsRetriedThreeTimesThenGivenUp() {
        sesAvailable();
        willThrow(new IllegalStateException("throttled")).given(ses).send(any(MimeMessage.class));

        assertThat(send(null, null, null, null)).isFalse();

        verify(ses, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void anInterruptedRetryStopsAtOnce() {
        sesAvailable();
        willThrow(new IllegalStateException("throttled")).given(ses).send(any(MimeMessage.class));
        Thread.currentThread().interrupt();

        boolean sent = send(null, null, null, null);

        assertThat(Thread.interrupted()).isTrue();
        assertThat(sent).isFalse();
        verify(ses, times(1)).send(any(MimeMessage.class));
        verify(provider, never()).getObject();
    }
}
