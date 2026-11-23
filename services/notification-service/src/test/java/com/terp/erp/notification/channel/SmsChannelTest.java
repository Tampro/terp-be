package com.terp.erp.notification.channel;

import com.terp.erp.net.OutboundHttp;
import com.terp.erp.net.UrlGuard;
import com.terp.erp.notification.client.IamInternalClient.Twilio;
import com.terp.erp.notification.delivery.Targets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsChannelTest {
    private static final String SID = "AC" + "1".repeat(32);
    private final FormHttp http = mock(FormHttp.class);
    /** Fake DNS: every name resolves to a public documentation address; nothing is called for real. */
    private final UrlGuard publicDns = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("203.0.113.10")});
    private final Twilio cfg = new Twilio(SID, "test-auth-token", "+15005550006");

    @Test
    @SuppressWarnings("unchecked")
    void postsTheTwilioFormWithBasicAuth() {
        when(http.postForm(any(), anyString(), anyMap())).thenReturn(new OutboundHttp.Result(true, 201, null));

        SendResult r = new SmsChannel(publicDns, http).send(cfg, "+84901234567", "[ERP] New Lead Test & Co");

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).postForm(uri.capture(), body.capture(), headers.capture());
        assertThat(r.sent()).isTrue();
        assertThat(uri.getValue().toString())
                .isEqualTo("https://api.twilio.com/2010-04-01/Accounts/" + SID + "/Messages.json");
        assertThat(body.getValue())
                .isEqualTo("To=%2B84901234567&From=%2B15005550006&Body=%5BERP%5D+New+Lead+Test+%26+Co");
        assertThat(headers.getValue()).containsEntry("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((SID + ":test-auth-token").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aTwilioHostResolvingToAPrivateAddressIsRefused() {
        UrlGuard privateDns = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("10.0.0.5")});

        SendResult r = new SmsChannel(privateDns, http).send(cfg, "+84901234567", "x");

        assertThat(r.code()).isEqualTo("URL_REFUSED");
        verify(http, never()).postForm(any(), anyString(), anyMap());
    }

    @Test
    void aSidThatCouldChangeTheUrlIsInvalidConfig() {
        // The SID is part of the path: anything but AC + 32 hex never reaches the guard.
        SendResult r = new SmsChannel(publicDns, http)
                .send(new Twilio("AC/../../evil", "t", "+15005550006"), "+84901234567", "x");

        assertThat(r.code()).isEqualTo("INVALID_CONFIG");
        verify(http, never()).postForm(any(), anyString(), anyMap());
    }

    @Test
    void twilioAnswersAreMappedLikeOtherChannels() {
        when(http.postForm(any(), anyString(), anyMap())).thenReturn(new OutboundHttp.Result(false, 401, null));
        assertThat(new SmsChannel(publicDns, http).send(cfg, "+84901234567", "x"))
                .isEqualTo(SendResult.fail("HTTP_401"));
        when(http.postForm(any(), anyString(), anyMap())).thenReturn(new OutboundHttp.Result(false, 503, null));
        assertThat(new SmsChannel(publicDns, http).send(cfg, "+84901234567", "x").retryable()).isTrue();
    }

    @Test
    void theDeliveryTargetShowsOnlyTheLastThreeDigits() {
        assertThat(Targets.maskPhone("+84901234567")).isEqualTo("*567");
        assertThat(Targets.maskPhone("12")).isNull();
    }
}
