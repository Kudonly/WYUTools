package com.wyutools.app.campus

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Makes the campus WLAN the process's network, and remembers which one it is.
 *
 * This is the part that decides whether the portal is reachable at all. A campus
 * WLAN that has not been authenticated carries no `NET_CAPABILITY_VALIDATED`, so
 * Android keeps sending traffic out of the mobile connection instead. Every call
 * then goes to `192.168.*` / the portal's own subnet from the wrong interface and
 * fails, while the probe URL quietly answers 204 because mobile data *does* reach
 * the internet — the session looks "already online" although the WLAN was never
 * touched. Binding the process to the WLAN fixes both symptoms at once.
 *
 * Two layers, both needed:
 *  - [bind] → `bindProcessToNetwork`, which covers everything that resolves a
 *    route per request;
 *  - [liveSocketFactory] → `Network.bindSocket` on every socket, which still
 *    works on ROMs where the process bind silently reports failure.
 */
object CampusNetGate {

    @Volatile
    private var bound: Network? = null

    fun boundNetwork(): Network? = bound

    /** Cheap pre-check: is there any WLAN or Ethernet interface at all? */
    fun hasWifiOrEthernet(context: Context): Boolean = pick(context) != null

    /**
     * Binds the process to the WLAN/Ethernet network. Returns false only when
     * there is no such network to bind to; a failed bind still records the
     * network so [liveSocketFactory] can route around it.
     */
    fun bind(context: Context): Boolean {
        val cm = connectivity(context) ?: return false
        val network = pick(context) ?: return false
        try {
            cm.bindProcessToNetwork(network)
        } catch (_: Exception) {
            // Some ROMs throw here yet honour the socket-level bind below.
        }
        bound = network
        return true
    }

    fun release(context: Context) {
        try {
            connectivity(context)?.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
        bound = null
    }

    /**
     * Transport of the network currently in use. Deliberately says nothing about
     * whether it works: the system's VALIDATED flag disagrees with reality on
     * campus WLANs (it flips as soon as *any* traffic gets through), so the probe
     * result is the only thing the page is allowed to call "online".
     */
    fun describe(context: Context): String {
        val cm = connectivity(context) ?: return "无法读取网络状态"
        val network = bound ?: pick(context) ?: return "未连接 WLAN/以太网"
        val caps = try {
            cm.getNetworkCapabilities(network)
        } catch (_: Exception) {
            null
        } ?: return "已连接（类型未知）"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "已连接 WLAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "已连接以太网"
            else -> "已连接（类型未知）"
        }
    }

    /**
     * Default gateway of the bound network — the ticket request needs it, and
     * reading it from the *active* network would hand over the mobile gateway
     * whenever the WLAN is unauthenticated.
     */
    fun gatewayIp(context: Context): String? {
        val cm = connectivity(context) ?: return null
        val network = bound ?: pick(context) ?: return null
        return try {
            cm.getLinkProperties(network)
                ?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    /**
     * IPv4 address of this device on the bound network. The portal normally
     * reports the same value as `wlanuserip`; when the redirect drops the query
     * string this is the fallback that keeps the ticket request valid.
     */
    fun localIpv4(context: Context): String? {
        val cm = connectivity(context) ?: return null
        val network = bound ?: pick(context) ?: return null
        return try {
            cm.getLinkProperties(network)
                ?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
                ?.address
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Socket factory that binds every socket to the remembered network, so a
     * connection cannot escape through mobile data even after the process bind
     * is reset by the system.
     */
    fun liveSocketFactory(): SocketFactory = object : SocketFactory() {
        private fun bindSocket(socket: Socket): Socket {
            val network = bound ?: return socket
            try {
                network.bindSocket(socket)
            } catch (_: IOException) {
            }
            return socket
        }

        override fun createSocket(): Socket = bindSocket(Socket())

        override fun createSocket(host: String?, port: Int): Socket =
            bindSocket(Socket()).apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }

        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int,
        ): Socket = bindSocket(Socket()).apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        }

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            bindSocket(Socket()).apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int,
        ): Socket = bindSocket(Socket()).apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
        }
    }

    private fun connectivity(context: Context): ConnectivityManager? = try {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } catch (_: Exception) {
        null
    }

    /**
     * WLAN first, then Ethernet: this is a phone, so a validated WLAN is the
     * network the portal lives on. Unvalidated candidates are still accepted —
     * that is exactly the state a campus WLAN is in before login.
     */
    @Suppress("DEPRECATION")
    private fun pick(context: Context): Network? {
        val cm = connectivity(context) ?: return null
        val wifis = ArrayList<Network>()
        val ethernets = ArrayList<Network>()
        var wifiValidated: Network? = null
        var ethernetValidated: Network? = null
        val all = try {
            cm.allNetworks
        } catch (_: Exception) {
            return null
        }
        for (network in all) {
            val caps = try {
                cm.getNetworkCapabilities(network)
            } catch (_: Exception) {
                null
            } ?: continue
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!isWifi && !isEthernet) continue
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (isWifi) {
                wifis.add(network)
                if (validated) wifiValidated = network
            }
            if (isEthernet) {
                ethernets.add(network)
                if (validated) ethernetValidated = network
            }
        }
        return wifiValidated ?: ethernetValidated ?: wifis.firstOrNull() ?: ethernets.firstOrNull()
    }

    private const val CONNECT_TIMEOUT_MS = 10_000
}
