package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Exception
import org.apache.commons.lang3.StringUtils

object Preconditions {
    fun checkArgument(expression: Boolean) {
        require(expression)
    }

    fun checkArgument(expression: Boolean, errorMessage: String) {
        require(expression) { errorMessage }
    }

    fun checkArgument(expression: Boolean, format: String?, vararg args: Any?) {
        require(expression) { String.format(format!!, *args) }
    }

    fun checkNotBlank(reference: String): String {
        require(!StringUtils.isBlank(reference))
        return reference
    }

    fun checkNotBlank(
        reference: String,
        errorMessageTemplate: String?,
        vararg errorMessageArgs: Any?
    ): String {
        require(!StringUtils.isBlank(reference)) { String.format(errorMessageTemplate!!, *errorMessageArgs) }
        return reference
    }

    fun <T> checkNotNull(reference: T?): T {
        requireNotNull(reference)
        return reference
    }

    fun checkPositive(value: Int, name: String): Int {
        require(value > 0) { "$name must be positive but was: $value" }
        return value
    }

    fun checkPositive(value: Long, name: String): Long {
        require(value > 0) { "$name must be positive but was: $value" }
        return value
    }

    fun checkNonNegative(value: Int, name: String): Int {
        require(value >= 0) { "$name cannot be negative but was: $value" }
        return value
    }

    fun m3u8Check(expression: Boolean, format: String?, vararg args: Any?) {
        if (!expression) {
            throw M3u8Exception(String.format(format!!, *args))
        }
    }

    fun m3u8Exception(errorMessage: String?) {
        throw M3u8Exception(errorMessage)
    }

    fun m3u8Exception(format: String?, vararg args: Any?) {
        throw M3u8Exception(String.format(format!!, *args))
    }

    fun m3u8Exception(errorMessage: String?, cause: Throwable?) {
        throw M3u8Exception(errorMessage, cause)
    }

}
