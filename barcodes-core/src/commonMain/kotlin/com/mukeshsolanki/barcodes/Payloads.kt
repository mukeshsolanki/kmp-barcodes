package com.mukeshsolanki.barcodes

/** Wi-Fi network security type, written as `T:WPA`, `T:WEP` or `T:nopass`. */
public enum class WifiSecurity(
    /** The value written after `T:`. */
    internal val code: String,
) {
    /** Open network. */
    None("nopass"),

    /** WEP. */
    Wep("WEP"),

    /** WPA/WPA2/WPA3 personal. */
    Wpa("WPA"),
}

/** Builders for the common structured payloads that barcode readers recognise. */
public object Payloads {
    /** Characters escaped with a backslash in Wi-Fi and MeCard fields. */
    private const val MECARD_SPECIALS = "\\;,:\""

    /** Accepted UTC timestamp form for [calendarEvent]. */
    private val UTC_TIMESTAMP = Regex("\\d{8}T\\d{6}Z")

    /** Plain text, unchanged. */
    public fun text(text: String): String = text

    /** A URL, unchanged. */
    public fun url(url: String): String = url

    /**
     * `WIFI:T:..;S:..;P:..;H:true;;` with `\ ; , : "` escaped;
     * [password] is omitted when null or the network is open.
     */
    public fun wifi(
        ssid: String,
        password: String?,
        security: WifiSecurity = WifiSecurity.Wpa,
        hidden: Boolean = false,
    ): String = buildString {
        append("WIFI:T:").append(security.code).append(';')
        append("S:").append(ssid.escape(MECARD_SPECIALS)).append(';')
        if (security != WifiSecurity.None && password != null) {
            append("P:").append(password.escape(MECARD_SPECIALS)).append(';')
        }
        if (hidden) append("H:true;")
        append(';')
    }

    /** `mailto:` address with a percent-encoded `subject`/`body` query. */
    public fun email(to: String, subject: String? = null, body: String? = null): String {
        val query = listOfNotNull(
            subject?.let { "subject=${it.percentEncode()}" },
            body?.let { "body=${it.percentEncode()}" },
        )
        return "mailto:$to" + if (query.isEmpty()) "" else query.joinToString("&", prefix = "?")
    }

    /** `SMSTO:number:message`, or `SMSTO:number` without a message. */
    public fun sms(number: String, message: String? = null): String =
        "SMSTO:$number" + message?.let { ":$it" }.orEmpty()

    /** `tel:number`. */
    public fun phone(number: String): String = "tel:$number"

    /** `geo:lat,lon[,alt]`. */
    public fun geo(latitude: Double, longitude: Double, altitude: Double? = null): String =
        "geo:$latitude,$longitude" + altitude?.let { ",$it" }.orEmpty()

    /** vCard 3.0 with `\ ; ,` and newlines escaped; null fields are omitted. */
    public fun vCard(
        name: String,
        phone: String? = null,
        email: String? = null,
        organization: String? = null,
        url: String? = null,
        address: String? = null,
    ): String = listOfNotNull(
        "BEGIN:VCARD",
        "VERSION:3.0",
        "N:${name.vEscape()}",
        "FN:${name.vEscape()}",
        phone?.let { "TEL:${it.vEscape()}" },
        email?.let { "EMAIL:${it.vEscape()}" },
        organization?.let { "ORG:${it.vEscape()}" },
        url?.let { "URL:${it.vEscape()}" },
        address?.let { "ADR:;;${it.vEscape()};;;;" },
        "END:VCARD",
    ).joinToString("\n")

    /** `MECARD:N:..;TEL:..;EMAIL:..;URL:..;ADR:..;;` with `\ ; , :` escaped; null fields are omitted. */
    public fun meCard(
        name: String,
        phone: String? = null,
        email: String? = null,
        url: String? = null,
        address: String? = null,
    ): String = listOfNotNull(
        "N" to name,
        phone?.let { "TEL" to it },
        email?.let { "EMAIL" to it },
        url?.let { "URL" to it },
        address?.let { "ADR" to it },
    ).joinToString("", prefix = "MECARD:", postfix = ";") { (key, value) ->
        "$key:${value.escape(MECARD_SPECIALS)};"
    }

    /** iCalendar `BEGIN:VEVENT..END:VEVENT`; [startUtc]/[endUtc] must be `yyyyMMdd'T'HHmmss'Z'`. */
    public fun calendarEvent(
        summary: String,
        startUtc: String,
        endUtc: String,
        location: String? = null,
        description: String? = null,
    ): String {
        require(UTC_TIMESTAMP.matches(startUtc)) { "startUtc must be yyyyMMdd'T'HHmmss'Z'" }
        require(UTC_TIMESTAMP.matches(endUtc)) { "endUtc must be yyyyMMdd'T'HHmmss'Z'" }
        return listOfNotNull(
            "BEGIN:VEVENT",
            "SUMMARY:${summary.vEscape()}",
            "DTSTART:$startUtc",
            "DTEND:$endUtc",
            location?.let { "LOCATION:${it.vEscape()}" },
            description?.let { "DESCRIPTION:${it.vEscape()}" },
            "END:VEVENT",
        ).joinToString("\n")
    }
}
