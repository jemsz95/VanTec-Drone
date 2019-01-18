package mx.tec.vanttec.dron

data class Setting(val key: String, val type: Setting.Type) {
    enum class Type {
        INT, FLOAT, BOOL, STRING
    }

    companion object {
        const val SHARED_PREFS_NAME = "vanttec_drone"

        val BASE_ADDRESS = Setting("base_addr", Setting.Type.STRING)
        val BASE_PORT = Setting("base_port", Setting.Type.INT)
        val DEFAULT_ALTITUDE = Setting("alt", Setting.Type.FLOAT)
    }
}