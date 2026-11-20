package com.mukeshsolanki.barcodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exact-string checks for every [Payloads] builder. */
class PayloadsTest {
    /** Text and URL pass through. */
    @Test
    fun textAndUrlAreUnchanged() {
        assertEquals("hello", Payloads.text("hello"))
        assertEquals("https://example.com", Payloads.url("https://example.com"))
    }

    /** WPA network with a plain password. */
    @Test
    fun wifiWpa() {
        assertEquals("WIFI:T:WPA;S:Home;P:secret;;", Payloads.wifi("Home", "secret"))
    }

    /** Every special character is backslash-escaped in SSID and password. */
    @Test
    fun wifiEscapesSpecials() {
        assertEquals(
            "WIFI:T:WPA;S:a\\\\b\\;c\\,d\\:e\\\"f;P:\\;\\,\\:\\\"\\\\;;",
            Payloads.wifi("a\\b;c,d:e\"f", ";,:\"\\"),
        )
    }

    /** An empty password is kept as `P:;`. */
    @Test
    fun wifiEmptyPassword() {
        assertEquals("WIFI:T:WPA;S:Home;P:;;", Payloads.wifi("Home", ""))
    }

    /** A null password is omitted. */
    @Test
    fun wifiNullPassword() {
        assertEquals("WIFI:T:WPA;S:Home;;", Payloads.wifi("Home", null))
    }

    /** WEP type code. */
    @Test
    fun wifiWep() {
        assertEquals("WIFI:T:WEP;S:Home;P:key;;", Payloads.wifi("Home", "key", WifiSecurity.Wep))
    }

    /** Open networks use nopass and drop any password. */
    @Test
    fun wifiOpen() {
        assertEquals("WIFI:T:nopass;S:Cafe;;", Payloads.wifi("Cafe", "ignored", WifiSecurity.None))
    }

    /** Hidden networks add `H:true;`. */
    @Test
    fun wifiHidden() {
        assertEquals("WIFI:T:WPA;S:Home;P:pw;H:true;;", Payloads.wifi("Home", "pw", hidden = true))
    }

    /** Subject and body are percent-encoded as UTF-8. */
    @Test
    fun emailEncodesQuery() {
        assertEquals("mailto:a@b.co", Payloads.email("a@b.co"))
        assertEquals(
            "mailto:a@b.co?subject=Hi%20there%26more&body=caf%C3%A9~-._",
            Payloads.email("a@b.co", "Hi there&more", "café~-._"),
        )
        assertEquals("mailto:a@b.co?body=x%3Dy", Payloads.email("a@b.co", body = "x=y"))
    }

    /** SMSTO with and without a message. */
    @Test
    fun sms() {
        assertEquals("SMSTO:+123", Payloads.sms("+123"))
        assertEquals("SMSTO:+123:hi there", Payloads.sms("+123", "hi there"))
    }

    /** tel: URI. */
    @Test
    fun phone() {
        assertEquals("tel:+123", Payloads.phone("+123"))
    }

    /** geo: URI with optional altitude. */
    @Test
    fun geo() {
        assertEquals("geo:37.5,-122.25", Payloads.geo(37.5, -122.25))
        assertEquals("geo:37.5,-122.25,10.0", Payloads.geo(37.5, -122.25, 10.0))
    }

    /** vCard escapes `\ ; ,` and newlines and omits null fields. */
    @Test
    fun vCard() {
        assertEquals(
            "BEGIN:VCARD\nVERSION:3.0\nN:Doe\\; John\nFN:Doe\\; John\nTEL:+1\n" +
                "ORG:A\\, B\\\\C\nADR:;;1 Main\\nTown;;;;\nEND:VCARD",
            Payloads.vCard("Doe; John", phone = "+1", organization = "A, B\\C", address = "1 Main\nTown"),
        )
    }

    /** MeCard escapes `\ ; , :` and ends with `;;`. */
    @Test
    fun meCard() {
        assertEquals("MECARD:N:Jo;;", Payloads.meCard("Jo"))
        assertEquals(
            "MECARD:N:A\\;B;TEL:1;EMAIL:a@b;URL:https\\://x.y;ADR:1\\, St;;",
            Payloads.meCard("A;B", "1", "a@b", "https://x.y", "1, St"),
        )
    }

    /** VEVENT with escaped text and validated timestamps. */
    @Test
    fun calendarEvent() {
        assertEquals(
            "BEGIN:VEVENT\nSUMMARY:Meet\\, greet\nDTSTART:20261120T090000Z\n" +
                "DTEND:20261120T100000Z\nLOCATION:Room 1\nEND:VEVENT",
            Payloads.calendarEvent("Meet, greet", "20261120T090000Z", "20261120T100000Z", location = "Room 1"),
        )
        assertFailsWith<IllegalArgumentException> { Payloads.calendarEvent("x", "2026-11-20", "20261120T100000Z") }
    }
}
