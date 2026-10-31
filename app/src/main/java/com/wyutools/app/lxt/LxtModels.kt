package com.wyutools.app.lxt

import org.json.JSONObject

data class AreaNode(
    val id: String,
    val name: String,
    val siteFlag: Int = 0,
)

data class MachineInfo(
    val machineId: String,
    val deviceMac: String = "",
    val deviceVer: String = "",
    val roomName: String = "",
    val siteId: String = "",
    val siteFlag: Int = 0,
)

data class MachineStatus(
    val machineId: String,
    val curStatus: Int,
)

data class ValveDevice(
    val machineId: String,
    val deviceMac: String = "",
    val deviceVer: String = "",
    val siteId: String = "",
    val siteFlag: Int = 0,
    val roomName: String = "",
    val buildingName: String = "",
    val floorName: String = "",
    val campusName: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("machineId", machineId)
        put("deviceMac", deviceMac)
        put("deviceVer", deviceVer)
        put("siteId", siteId)
        put("siteFlag", siteFlag)
        put("roomName", roomName)
        put("buildingName", buildingName)
        put("floorName", floorName)
        put("campusName", campusName)
    }.toString()

    companion object {
        fun fromJson(s: String?): ValveDevice? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                val machineId = o.optString("machineId")
                if (machineId.isBlank()) null else ValveDevice(
                    machineId = machineId,
                    deviceMac = o.optString("deviceMac"),
                    deviceVer = o.optString("deviceVer"),
                    siteId = o.optString("siteId"),
                    siteFlag = o.optInt("siteFlag"),
                    roomName = o.optString("roomName"),
                    buildingName = o.optString("buildingName"),
                    floorName = o.optString("floorName"),
                    campusName = o.optString("campusName"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class BindInfo(
    val campusName: String,
    val buildingName: String,
    val floorName: String,
    val roomName: String,
    val siteId: String,
    val siteFlag: Int,
    val machineId: String,
    val deviceMac: String,
) {
    fun toJson(): String = JSONObject().apply {
        put("campusName", campusName)
        put("buildingName", buildingName)
        put("floorName", floorName)
        put("roomName", roomName)
        put("siteId", siteId)
        put("siteFlag", siteFlag)
        put("machineId", machineId)
        put("deviceMac", deviceMac)
    }.toString()

    companion object {
        fun fromJson(s: String?): BindInfo? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                BindInfo(
                    campusName = o.optString("campusName"),
                    buildingName = o.optString("buildingName"),
                    floorName = o.optString("floorName"),
                    roomName = o.optString("roomName"),
                    siteId = o.optString("siteId"),
                    siteFlag = o.optInt("siteFlag"),
                    machineId = o.optString("machineId"),
                    deviceMac = o.optString("deviceMac"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class ValveUsing(
    val machineId: String,
    val deviceVer: String,
    val communicationVal: String,
    val siteId: String,
    val siteFlag: Int,
    val roomName: String,
    val openTime: Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("machineId", machineId)
        put("deviceVer", deviceVer)
        put("communicationVal", communicationVal)
        put("siteId", siteId)
        put("siteFlag", siteFlag)
        put("roomName", roomName)
        put("openTime", openTime)
    }.toString()

    companion object {
        fun fromJson(s: String?): ValveUsing? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                if (o.optString("machineId").isBlank()) null else ValveUsing(
                    machineId = o.optString("machineId"),
                    deviceVer = o.optString("deviceVer"),
                    communicationVal = o.optString("communicationVal"),
                    siteId = o.optString("siteId"),
                    siteFlag = o.optInt("siteFlag"),
                    roomName = o.optString("roomName"),
                    openTime = o.optLong("openTime"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
