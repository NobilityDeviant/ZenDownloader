package nobility.downloader.core.entities.settings

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class SettingsMeta(
    @Id var id: Long = 0,
    var key: String = "",
    var stringValue: String? = null,
    var intValue: Int? = null,
    var longValue: Long? = null,
    var doubleValue: Double? = null,
    var floatValue: Float? = null,
    var booleanValue: Boolean? = null
) {

    fun value(): Any? {
        return if (stringValue != null) {
            stringValue
        } else if (intValue != null) {
            intValue
        } else if (longValue != null) {
            longValue
        } else if (doubleValue != null) {
            doubleValue
        } else if (floatValue != null) {
            floatValue
        } else if (booleanValue != null) {
            booleanValue
        } else {
            null
        }
    }

    fun setValue(value: Any?) {
        stringValue = null
        intValue = null
        longValue = null
        doubleValue = null
        floatValue = null
        booleanValue = null
        when (value) {
            is String -> {
                stringValue = value
            }

            is Int -> {
                intValue = value
            }

            is Long -> {
                longValue = value
            }

            is Double -> {
                doubleValue = value
            }

            is Float -> {
                floatValue = value
            }

            is Boolean -> {
                booleanValue = value
            }
        }
    }

    fun stringVal(): String {
        return stringValue ?: ""
    }

    fun intVal(): Int {
        if (intValue != null) {
            return intValue!!
        } else if (longValue != null) {
            return longValue!!.toInt()
        }
        return 0
    }

    fun longVal(): Long {
        if (longValue != null) {
            return longValue!!
        } else if (intValue != null) {
            return intValue!!.toLong()
        }
        return 0L
    }

    fun booleanVal(): Boolean {
        return booleanValue ?: false
    }

    fun doubleVal(): Double {
        if (doubleValue != null) {
            return doubleValue as Double
        } else if (intValue != null) {
            return intValue!!.toDouble()
        } else if (longValue != null) {
            return longValue!!.toDouble()
        }
        return 0.1
    }

    fun floatVal(): Float {
        return floatValue ?: 0.1f
    }

}