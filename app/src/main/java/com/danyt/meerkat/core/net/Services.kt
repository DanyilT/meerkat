package com.danyt.meerkat.core.net

/**
 * The service that usually listens on a TCP port. This is a guess from the port number alone: nothing is read
 * from the port, and any program can listen anywhere.
 */
private val TcpServices = mapOf(
    20 to "FTP data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS", 79 to "Finger",
    80 to "HTTP", 81 to "HTTP", 88 to "Kerberos", 110 to "POP3", 111 to "RPC", 113 to "Ident", 119 to "NNTP",
    135 to "MS RPC", 139 to "NetBIOS", 143 to "IMAP", 179 to "BGP", 389 to "LDAP", 427 to "SLP", 443 to "HTTPS",
    445 to "SMB", 465 to "SMTPS", 514 to "Remote shell", 515 to "Printer (LPD)", 543 to "Kerberos login",
    548 to "AFP", 554 to "RTSP", 587 to "SMTP submission", 631 to "IPP printing", 636 to "LDAPS", 873 to "rsync",
    990 to "FTPS", 993 to "IMAPS", 995 to "POP3S", 1025 to "MS RPC", 1080 to "SOCKS proxy", 1194 to "OpenVPN",
    1433 to "SQL Server", 1521 to "Oracle DB", 1723 to "PPTP", 1883 to "MQTT", 2049 to "NFS", 2121 to "FTP",
    2222 to "SSH", 2375 to "Docker", 2376 to "Docker TLS", 3000 to "Dev server", 3128 to "Proxy", 3306 to "MySQL",
    3389 to "Remote Desktop", 3689 to "iTunes sharing", 4443 to "HTTPS", 4444 to "Remote shell", 5000 to "UPnP",
    5001 to "Synology", 5060 to "SIP", 5061 to "SIP TLS", 5222 to "XMPP", 5357 to "Web Services Discovery",
    5432 to "PostgreSQL", 5555 to "Android debug", 5631 to "pcAnywhere", 5666 to "Nagios", 5672 to "AMQP",
    5800 to "VNC web", 5900 to "VNC", 5901 to "VNC", 5985 to "WinRM", 5986 to "WinRM TLS", 6000 to "X11",
    6379 to "Redis", 6667 to "IRC", 7000 to "AirPlay", 7070 to "RealServer", 7100 to "AirPlay", 8000 to "HTTP",
    8008 to "HTTP", 8009 to "Chromecast", 8080 to "HTTP proxy", 8081 to "HTTP", 8086 to "InfluxDB",
    8123 to "Home Assistant",
    8200 to "Media server", 8443 to "HTTPS", 8883 to "MQTT TLS", 8888 to "HTTP", 9000 to "HTTP", 9090 to "HTTP",
    9100 to "Printer (raw)", 9200 to "Elasticsearch", 9418 to "Git", 9999 to "Remote admin", 10000 to "Webmin",
    11211 to "Memcached", 27017 to "MongoDB", 32400 to "Plex", 49152 to "UPnP", 49153 to "UPnP",
    62078 to "iPhone sync",
)

private val UdpServices = mapOf(
    53 to "DNS", 67 to "DHCP server", 68 to "DHCP client", 69 to "TFTP", 88 to "Kerberos", 111 to "RPC",
    123 to "NTP", 137 to "NetBIOS name", 138 to "NetBIOS datagram", 161 to "SNMP", 162 to "SNMP trap",
    500 to "IPsec IKE", 514 to "Syslog", 520 to "RIP", 631 to "IPP printing", 1194 to "OpenVPN", 1434 to "SQL Server",
    1900 to "SSDP (UPnP)", 3702 to "Web Services Discovery", 4500 to "IPsec NAT-T", 5060 to "SIP", 5353 to "mDNS",
    5355 to "LLMNR", 51820 to "WireGuard",
)

/** The usual service on [port], or null when the port has no common use we know of. A guess, not a detection. */
fun serviceName(port: Int, protocol: Protocol): String? = when (protocol) {
    Protocol.Tcp -> TcpServices[port]
    Protocol.Udp -> UdpServices[port]
}

/** The ports worth checking first: every port we can name for [protocol], lowest first. */
fun commonPorts(protocol: Protocol): List<Int> = when (protocol) {
    Protocol.Tcp -> TcpServices.keys.sorted()
    Protocol.Udp -> UdpServices.keys.sorted()
}

/** The classic "well-known" range, 1 to 1024. */
val WellKnownPorts: List<Int> = (1..1024).toList()

private val LocalBlocks = listOf(
    cidrRange(Ipv4.parse("10.0.0.0")!!, 8, hostsOnly = false),
    cidrRange(Ipv4.parse("172.16.0.0")!!, 12, hostsOnly = false),
    cidrRange(Ipv4.parse("192.168.0.0")!!, 16, hostsOnly = false),
    cidrRange(Ipv4.parse("169.254.0.0")!!, 16, hostsOnly = false),
    cidrRange(Ipv4.parse("100.64.0.0")!!, 10, hostsOnly = false),
)

/**
 * True when the range includes addresses that are only used on local networks (private, link-local, or
 * carrier-shared space). Android 17 asks the user before an app may reach those.
 */
fun IpRange.touchesLocalNetwork(): Boolean = LocalBlocks.any { first <= it.last && last >= it.first }
