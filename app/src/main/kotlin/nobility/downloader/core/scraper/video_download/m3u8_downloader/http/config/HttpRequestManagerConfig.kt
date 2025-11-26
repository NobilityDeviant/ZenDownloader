package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import nobility.downloader.utils.user_agents.UserAgents
import java.util.concurrent.TimeUnit

class HttpRequestManagerConfig private constructor(
    val userAgent: String,
    val ioThreads: Int,
    val maxConnTotal: Int,
    val maxConnPerRoute: Int,
    val executorThreads: Int,
    val defaultMaxRetries: Int,
    val selectIntervalMills: Long,
    val socketTimeoutMills: Long,
    val connectTimeoutMills: Long,
    val connectionMaxIdleMills: Long,
    val defaultRetryIntervalMills: Long,
    val connectionRequestTimeoutMills: Long
) {

    class Builder internal constructor() {
        private var userAgent: String

        private var ioThreads: Int

        private var maxConnTotal = 1000

        private var maxConnPerRoute = 50

        private var executorThreads = 50

        private var defaultMaxRetries = 5

        private var selectIntervalMills: Long = 50

        private var socketTimeoutMills: Long

        private var connectTimeoutMills: Long

        private var connectionMaxIdleMills: Long

        private var defaultRetryIntervalMills: Long

        private var connectionRequestTimeoutMills: Long

        private fun availableProcessors(): Int {
            return Runtime.getRuntime().availableProcessors()
        }

        init {
            this.ioThreads = availableProcessors() * 2
            this.userAgent = UserAgents.random
            this.socketTimeoutMills = TimeUnit.SECONDS.toMillis(5)
            this.connectTimeoutMills = TimeUnit.SECONDS.toMillis(5)
            this.connectionMaxIdleMills = TimeUnit.MINUTES.toMillis(5)
            this.defaultRetryIntervalMills = TimeUnit.SECONDS.toMillis(1)
            this.connectionRequestTimeoutMills = TimeUnit.HOURS.toMillis(2)
        }

        fun userAgent(userAgent: String): Builder {
            Preconditions.checkNotBlank(userAgent, "userAgent is blank")
            this.userAgent = userAgent
            return this
        }

        fun ioThreads(ioThreads: Int): Builder {
            Preconditions.checkPositive(ioThreads, "ioThreads")
            this.ioThreads = ioThreads
            return this
        }

        fun maxConnTotal(maxConnTotal: Int): Builder {
            Preconditions.checkPositive(maxConnTotal, "maxConnTotal")
            this.maxConnTotal = maxConnTotal
            return this
        }

        fun maxConnPerRoute(maxConnPerRoute: Int): Builder {
            Preconditions.checkPositive(maxConnPerRoute, "maxConnPerRoute")
            this.maxConnPerRoute = maxConnPerRoute
            return this
        }

        fun executorThreads(executorThreads: Int): Builder {
            Preconditions.checkPositive(executorThreads, "executorThreads")
            this.executorThreads = executorThreads
            return this
        }

        fun defaultMaxRetries(defaultMaxRetries: Int): Builder {
            Preconditions.checkPositive(defaultMaxRetries, "defaultMaxRetries")
            this.defaultMaxRetries = defaultMaxRetries
            return this
        }

        private fun setTimeoutMillis(timeout: Long): Builder {
            Preconditions.checkPositive(timeout, "timeoutMillis")
            this.socketTimeoutMills = timeout
            this.connectTimeoutMills = timeout
            this.connectionRequestTimeoutMills = timeout
            return this
        }

        fun setTimeoutMillis(timeout: Int): Builder {
            setTimeoutMillis(timeout.toLong())
            return this
        }

        fun socketTimeoutMills(socketTimeoutMills: Long): Builder {
            Preconditions.checkPositive(socketTimeoutMills, "socketTimeoutMills")
            this.socketTimeoutMills = socketTimeoutMills
            return this
        }

        fun connectTimeoutMills(connectTimeoutMills: Long): Builder {
            Preconditions.checkPositive(connectTimeoutMills, "connectTimeoutMills")
            this.connectTimeoutMills = connectTimeoutMills
            return this
        }

        fun connectionMaxIdleMills(connectionMaxIdleMills: Long): Builder {
            Preconditions.checkPositive(connectionMaxIdleMills, "connectionMaxIdleMills")
            this.connectionMaxIdleMills = connectionMaxIdleMills
            return this
        }

        fun defaultRetryIntervalMills(defaultRetryIntervalMills: Long): Builder {
            Preconditions.checkPositive(defaultRetryIntervalMills, "defaultRetryIntervalMills")
            this.defaultRetryIntervalMills = defaultRetryIntervalMills
            return this
        }

        fun connectionRequestTimeoutMills(connectionRequestTimeoutMills: Long): Builder {
            Preconditions.checkPositive(connectionRequestTimeoutMills, "connectionRequestTimeoutMills")
            this.connectionRequestTimeoutMills = connectionRequestTimeoutMills
            return this
        }

        fun build(): HttpRequestManagerConfig {
            val globalPoolCount: Int
            val ioThreads = this.ioThreads
            globalPoolCount = if (ioThreads >= 8) {
                if (ioThreads >= 32) 4 else 2
            } else {
                1
            }

            return HttpRequestManagerConfig(
                this.userAgent,
                this.ioThreads,
                this.maxConnTotal,
                this.maxConnPerRoute,
                this.executorThreads,
                this.defaultMaxRetries,
                this.selectIntervalMills,
                this.socketTimeoutMills,
                this.connectTimeoutMills,
                this.connectionMaxIdleMills,
                this.defaultRetryIntervalMills,
                this.connectionRequestTimeoutMills
            )
        }
    }

    companion object {

        val DEFAULT: HttpRequestManagerConfig = custom().build()

        fun custom(): Builder {
            return Builder()
        }
    }
}
