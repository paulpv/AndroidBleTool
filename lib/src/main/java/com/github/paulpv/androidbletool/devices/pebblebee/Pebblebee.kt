package com.github.paulpv.androidbletool.devices.pebblebee

import com.github.paulpv.androidbletool.BluetoothUtils
import com.github.paulpv.androidbletool.utils.Utils
import java.util.*

object Pebblebee {
    /**
     * NOTE that these are the reverse of [PebblebeeMacAddressPrefix]
     */
    interface ManufacturerId {
        companion object {
            const val PEBBLEBEE_HONEY_DRAGON_HORNET: Int = 0x0A0E
            const val PETHUB_SIGNAL: Int = 0x0B0E
            const val PEBBLEBEE_STONE: Int = 0x0C0E
            const val PEBBLEBEE_FINDER1: Int = 0x0E0E
            const val PEBBLEBEE_FINDER2: Int = 0x060E
            const val PEBBLEBEE_BUZZER1: Int = 0x0F0E // Buzzer1/Nock
            const val PEBBLEBEE_BUZZER2: Int = 0x100E // Buzzer2/LocationMarker
            const val PEBBLEBEE_CARD: Int = 0x050E
            val ALL = Collections.unmodifiableSet(
                HashSet(
                    listOf(
                        PEBBLEBEE_HONEY_DRAGON_HORNET,
                        PETHUB_SIGNAL,
                        PEBBLEBEE_STONE,
                        PEBBLEBEE_FINDER1,
                        PEBBLEBEE_FINDER2,
                        PEBBLEBEE_BUZZER1,
                        PEBBLEBEE_BUZZER2,
                        PEBBLEBEE_CARD
                    )
                )
            )
        }
    }

    /**
     * NOTE that these are the reverse of [PebblebeeManufacturerIds]
     */
    interface MacAddressPrefix {
        companion object {
            const val PEBBLEBEE_HONEY_DRAGON_HORNET: Int = 0x0E0A
            const val PEBBLEBEE_HONEY_DRAGON_HORNET_STRING = "0e0a"
            const val PETHUB_SIGNAL: Int = 0x0E0B
            const val PETHUB_SIGNAL_STRING = "0e0b"
            const val PEBBLEBEE_STONE: Int = 0x0E0C
            const val PEBBLEBEE_STONE_STRING = "0e0c"
            const val PEBBLEBEE_FINDER1: Int = 0x0E0E
            const val PEBBLEBEE_FINDER1_STRING = "0e0e"
            const val PEBBLEBEE_FINDER2: Int = 0x0E06
            const val PEBBLEBEE_FINDER2_STRING = "0e06"
            const val PEBBLEBEE_BUZZER1: Int = 0x0E0F // Buzzer1/Nock
            const val PEBBLEBEE_BUZZER1_STRING = "0e0f"
            const val PEBBLEBEE_BUZZER2: Int = 0x0E10 // Buzzer2/LocationMarker
            const val PEBBLEBEE_BUZZER2_STRING = "0e10"
            const val PEBBLEBEE_CARD: Int = 0x0E05
            const val PEBBLEBEE_CARD_STRING = "0e05"
        }
    }

    internal interface DeviceCaseSensitiveName {
        companion object {
            const val PEBBLEBEE_HONEY = "PebbleBee" // Honey
            const val PEBBLEBEE_DRAGON_HORNET = "Pebblebee" // Dragon/Hornet
            const val PETHUB_SIGNAL = "SIGNAL" // Pethub Signal
            const val FINDER = "FNDR" // Finder
            const val BUZZER1_0_0 = "smartnock" // Buzzer1/Nock
            const val BUZZER1_0_1 = "snck" // Buzzer1/Nock
            const val BUZZER2 = "BCMK" // Buzzer2/LocationMarker
            const val CARD = "CARD" // Black Card
            const val FOUND = "FND" // Found
            const val LUMA = "LUMA" // Luma
            val ALL = Collections.unmodifiableSet(
                HashSet(
                    listOf(
                        PEBBLEBEE_HONEY,
                        PEBBLEBEE_DRAGON_HORNET,
                        PETHUB_SIGNAL,
                        FINDER,
                        BUZZER1_0_0,
                        BUZZER1_0_1,
                        BUZZER2,
                        CARD,
                        FOUND,
                        LUMA
                    )
                )
            )
        }
    }

    object DeviceModelNumber {
        fun getDefaultModelNumber(deviceMacAddress: String): Int {
            var deviceMacAddress = deviceMacAddress
            var defaultModelNumber = HONEY_0
            if (Utils.isNullOrEmpty(deviceMacAddress)) {
                return defaultModelNumber
            }
            deviceMacAddress = BluetoothUtils.macAddressStringToStrippedLowerCaseString(deviceMacAddress)
            when (deviceMacAddress.substring(0, 3)) {
                MacAddressPrefix.PEBBLEBEE_FINDER1_STRING -> defaultModelNumber = FINDER1_0
                MacAddressPrefix.PEBBLEBEE_FINDER2_STRING -> defaultModelNumber = FINDER2_0
                MacAddressPrefix.PEBBLEBEE_BUZZER1_STRING -> defaultModelNumber = BUZZER1_0
                MacAddressPrefix.PEBBLEBEE_BUZZER2_STRING -> defaultModelNumber = BUZZER2_0
                MacAddressPrefix.PEBBLEBEE_CARD_STRING -> defaultModelNumber = CARD_0
            }
            return defaultModelNumber
        }

        const val UNKNOWN = -1
        const val HONEY_0 = 0
        const val FIRST = HONEY_0
        const val HONEY_1 = 1
        const val DRAGON_0 = 2
        const val HORNET_0 = 3
        const val STONE_0 = 4
        const val FINDER1_0 = 5
        const val FINDER2_0 = 13
        const val STONE_1 = 6
        const val BUZZER1_0 = 7 // Nock
        const val BUZZER2_0 = 8 // Location Marker
        private const val GOLF_0 = 9
        private const val UMBRELLA_0 = 10
        const val CARD_0 = 11
        const val LAST = FINDER2_0
        private const val NEXT = LAST + 1

        fun isKnown(modelNumber: Int): Boolean {
            return modelNumber in (UNKNOWN + 1) until NEXT
        }

        @JvmOverloads
        fun toString(modelNumber: Int, pretty: Boolean = false): String {
            val modelName = when (modelNumber) {
                HONEY_0 -> if (pretty) "Honey0" else "HONEY_0"
                HONEY_1 -> if (pretty) "Honey1" else "HONEY_1"
                DRAGON_0 -> if (pretty) "Dragon" else "DRAGON_0"
                HORNET_0 -> if (pretty) "Hornet" else "HORNET_0"
                STONE_0 -> if (pretty) "Stone0" else "STONE_0"
                FINDER1_0 -> if (pretty) "Finder1" else "FINDER1_0"
                FINDER2_0 -> if (pretty) "Finder2" else "FINDER2_0"
                STONE_1 -> if (pretty) "Stone1" else "STONE_1"
                BUZZER1_0 -> if (pretty) "Buzzer1" else "BUZZER1_0"
                BUZZER2_0 -> if (pretty) "Buzzer2" else "BUZZER2_0"
                GOLF_0 -> if (pretty) "Golf" else "GOLF_0"
                UMBRELLA_0 -> if (pretty) "Umbrella" else "UMBRELLA_0"
                CARD_0 -> if (pretty) "Card" else "CARD_0"
                UNKNOWN -> if (pretty) "Unknown" else "UNKNOWN"
                else -> if (pretty) "Unknown" else "UNKNOWN"
            }
            return if (pretty) modelName else "$modelName($modelNumber)"
        }

        /*
        fun getDeviceModelName(context: Context, device: PbBleDevice): String {
            PbRuntime.throwIllegalArgumentExceptionIfNull(device, "device")
            return getDeviceModelName(context, device.getModelNumber())
        }

        fun getDeviceModelName(context: Context, deviceModelNumber: Int): String {
            return getDeviceModelName(context, deviceModelNumber, R.string.pebblebee_model_unknown)
        }

        fun getDeviceModelName(context: Context, deviceModelNumber: Int, resIdIfUnknown: Int): String {
            PbRuntime.throwIllegalArgumentExceptionIfNull(context, "context")
            val s: String
            s = when (deviceModelNumber) {
                HONEY_0, HONEY_1 -> context.getString(R.string.pebblebee_model_honey)
                DRAGON_0 -> context.getString(R.string.pebblebee_model_dragon)
                HORNET_0 -> context.getString(R.string.pebblebee_model_hornet)
                STONE_0, STONE_1 -> context.getString(R.string.pebblebee_model_stone)
                FINDER1_0 -> context.getString(R.string.pebblebee_model_finder1)
                FINDER2_0 -> context.getString(R.string.pebblebee_model_finder2)
                BUZZER1_0 -> context.getString(R.string.pebblebee_model_buzzer1)
                BUZZER2_0 -> context.getString(R.string.pebblebee_model_buzzer2)
                GOLF_0 -> context.getString(R.string.pebblebee_model_golf)
                UMBRELLA_0 -> context.getString(R.string.pebblebee_model_umbrella)
                CARD_0 -> context.getString(R.string.pebblebee_model_blackcard)
                else -> context.getString(resIdIfUnknown)
            }
            return s
        }
        */

        fun isStone(modelNumber: Int): Boolean {
            return when (modelNumber) {
                STONE_0, STONE_1 -> true
                else -> false
            }
        }

        fun isHoney(modelNumber: Int): Boolean {
            return when (modelNumber) {
                HONEY_0, HONEY_1 -> true
                else -> false
            }
        }

        fun isFinder(modelNumber: Int): Boolean {
            return isFinder1(modelNumber) || isFinder2(modelNumber)
        }

        fun isFinder1(modelNumber: Int): Boolean {
            return when (modelNumber) {
                FINDER1_0 -> true
                else -> false
            }
        }

        fun isFinder2(modelNumber: Int): Boolean {
            return when (modelNumber) {
                FINDER2_0 -> true
                else -> false
            }
        }

        fun isCard(modelNumber: Int): Boolean {
            return when (modelNumber) {
                CARD_0 -> true
                else -> false
            }
        }
    }

    //
    //
    //

    interface Regions {
        companion object {
            /**
             * Normal UUID for ranging, turned off when button held for 10 secs (button only mode)
             */
            const val TRACKING_STONE = "d149cb95-f212-4a20-8a17-e3a2f508c1ff"
            const val TRACKING_FINDER = "d149cb95-f212-4a20-8a17-e3a2f508c1aa"

            /**
             * Transmits after a single button or hold button (3 secs then release), currently transmits fast broadcast 2S.
             * Following the Interrupt period the Data for 2S.
             * Then reverts to Tracking UUID (unless off).
             */
            const val INTERRUPT = "d149cb95-f212-4a20-8a17-e3a2f508c1cc"

            /**
             * Transmits following a motion for 10S, if it keeps moving it will keep TX's.
             * Then reverts to Tracking UUID (unless off)
             */
            const val MOTION = "d149cb95-f212-4a20-8a17-e3a2f508c1ee"
        }
    }

    object Actions {
        const val NONE: Byte = 0
        const val CLICK_SHORT: Byte = 1
        const val CLICK_LONG: Byte = 2
        const val CLICK_DOUBLE: Byte = 3
        fun toString(value: Byte): String {
            val s: String = when (value) {
                NONE -> "NONE"
                CLICK_SHORT -> "CLICK_SHORT"
                CLICK_LONG -> "CLICK_LONG"
                CLICK_DOUBLE -> "CLICK_DOUBLE"
                else -> "UNKNOWN"
            }
            return "$s($value)"
        }
    }

    object ActionSequence {
        const val NeverPressed: Byte = 0
        const val JustPressed: Byte = 1
        const val Resetting: Byte = 2
        const val PressedBefore: Byte = 4
        fun toString(value: Byte): String {
            val s: String = when (value) {
                NeverPressed -> "NeverPressed"
                JustPressed -> "JustPressed"
                Resetting -> "Resetting"
                PressedBefore -> "PressedBefore"
                else -> "UNKNOWN"
            }
            return "$s($value)"
        }
    }
}