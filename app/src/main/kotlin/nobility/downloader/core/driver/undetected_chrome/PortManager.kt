package nobility.downloader.core.driver.undetected_chrome

import java.net.ServerSocket

object PortManager {

    private val lock = Any()
    private val allocated = mutableSetOf<Int>()

    fun allocatePort(): Int {
        synchronized(lock) {
            repeat(15) {
                val port = findFreePort()
                if (port != -1 && !allocated.contains(port)) {
                    allocated.add(port)
                    return port
                }
            }
        }
        return -1
    }

    fun releasePort(port: Int) {
        synchronized(lock) {
            allocated.remove(port)
        }
    }

    private fun findFreePort(): Int {
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (_: Exception) {
            -1
        }
    }
}
