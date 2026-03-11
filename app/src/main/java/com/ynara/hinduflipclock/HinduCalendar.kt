package com.ynara.hinduflipclock

import java.util.Calendar
import kotlin.math.*

object HinduCalendar {

    data class Panchang(
        val samvatsara: String,
        val masa: String,
        val paksha: String,
        val tithi: String,
        val festivals: List<String>
    )

    private val SAMVATSARA = listOf(
        "Prabhava","Vibhava","Shukla","Pramoda","Prajapati",
        "Angirasa","Shrimukha","Bhava","Yuva","Dhata",
        "Ishvara","Bahudhanya","Pramathi","Vikrama","Vrisha",
        "Chitrabhanu","Subhanu","Tarana","Parthiva","Vyaya",
        "Sarvajit","Sarvadhari","Virodhi","Vikrita","Khara",
        "Nandana","Vijaya","Jaya","Manmatha","Durmukhi",
        "Hevilambi","Vilambi","Vikari","Sharvari","Plava",
        "Shubhakrit","Shobhana","Krodhi","Vishvavasu","Parabhava",
        "Plavanga","Kilaka","Saumya","Sadharana","Virodhikrit",
        "Paridhavin","Pramadi","Ananda","Rakshasa","Nala",
        "Pingala","Kalayukti","Siddharthi","Raudra","Durmati",
        "Dundubhi","Rudhirodgari","Raktakshi","Krodhana","Akshaya"
    )

    private val MASA = listOf(
        "Chaitra","Vaisakha","Jyeshtha","Ashadha",
        "Shravana","Bhadrapada","Ashwin","Kartik",
        "Margashirsha","Pausha","Magha","Phalguna"
    )

    // tithi names for index 1–30
    private val TITHI = listOf(
        "","Pratipada","Dwitiya","Tritiya","Chaturthi","Panchami",
        "Shashthi","Saptami","Ashtami","Navami","Dashami",
        "Ekadashi","Dwadashi","Trayodashi","Chaturdashi","Purnima",
        "Pratipada","Dwitiya","Tritiya","Chaturthi","Panchami",
        "Shashthi","Saptami","Ashtami","Navami","Dashami",
        "Ekadashi","Dwadashi","Trayodashi","Chaturdashi","Amavasya"
    )

    fun calculate(cal: Calendar): Panchang {
        val jd = toJD(cal)
        val sunLon  = sunLongitude(jd)
        val moonLon = moonLongitude(jd)

        // Tithi 1–30
        val elongation = ((moonLon - sunLon) % 360 + 360) % 360
        val tithiNum = (elongation / 12).toInt() + 1

        val paksha = if (tithiNum <= 15) "Shukla" else "Krishna"
        val tithiName = TITHI[tithiNum]

        // Sidereal solar longitude → masa
        val ayanamsa = 23.85
        val siderealSun = ((sunLon - ayanamsa) % 360 + 360) % 360
        val solarMonth = (siderealSun / 30).toInt().coerceIn(0, 11)
        val masaName = MASA[solarMonth]

        // Samvatsara: reference VS 2082 → index 38, year 2025
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH)  // 0-based
        // Rough VS year: Hindu new year is around April
        val vsYear = if (gMonth >= 3) gYear + 57 else gYear + 56
        val svIdx = ((vsYear - 2044) % 60 + 60) % 60
        val samvatsaraName = SAMVATSARA[svIdx]

        val festivals = festivals(solarMonth + 1, tithiNum)

        return Panchang(
            samvatsara = "$samvatsaraName Samvatsara",
            masa = "$masaName Masa",
            paksha = paksha,
            tithi = "$paksha $tithiName",
            festivals = festivals
        )
    }

    // --- Astronomical calculations ---

    private fun toJD(cal: Calendar): Double {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val mn = cal.get(Calendar.MINUTE)
        val A = (14 - m) / 12
        val Y = y + 4800 - A
        val M = m + 12 * A - 3
        return d + (153 * M + 2) / 5 + 365 * Y + Y / 4 - Y / 100 + Y / 400 - 32045 +
                (h - 12) / 24.0 + mn / 1440.0
    }

    private fun norm360(v: Double): Double = ((v % 360) + 360) % 360

    private fun sunLongitude(jd: Double): Double {
        val n = jd - 2451545.0
        val L = norm360(280.46646 + 0.9856474 * n)
        val g = Math.toRadians(norm360(357.52911 + 0.9856003 * n))
        return norm360(L + 1.9146 * sin(g) + 0.0200 * sin(2 * g))
    }

    private fun moonLongitude(jd: Double): Double {
        val n = jd - 2451545.0
        val L0  = norm360(218.3165 + 13.17639648 * n)
        val Mrad = Math.toRadians(norm360(134.9634 + 13.06499295 * n))
        val Drad = Math.toRadians(norm360(297.8502 + 12.19074912 * n))
        val Frad = Math.toRadians(norm360(93.2721  + 13.22935020 * n))
        val Msrad= Math.toRadians(norm360(357.5291 +  0.98560028 * n))

        return norm360(L0
            + 6.2886 * sin(Mrad)
            + 1.2740 * sin(2*Drad - Mrad)
            + 0.6583 * sin(2*Drad)
            + 0.2136 * sin(2*Mrad)
            - 0.1851 * sin(Msrad)
            - 0.1143 * sin(2*Frad)
            + 0.0588 * sin(2*Drad - 2*Mrad)
            + 0.0533 * sin(2*Drad + Mrad)
            + 0.0458 * sin(2*Drad - Msrad)
            + 0.0409 * sin(Mrad - Msrad)
            - 0.0347 * sin(Drad)
            - 0.0305 * sin(Mrad + Msrad)
        )
    }

    // masa: 1=Chaitra…12=Phalguna, tithi: 1–30
    private fun festivals(masa: Int, tithi: Int): List<String> {
        val list = mutableListOf<String>()

        // Universal markers
        when (tithi) {
            15 -> list.add("Purnima")
            30 -> list.add("Amavasya")
            11, 26 -> list.add("Ekadashi")
            8, 23  -> list.add("Ashtami")
        }

        // Month-specific
        when (masa) {
            1  -> when (tithi) { // Chaitra
                1  -> list.add("Ugadi / Gudi Padwa / Hindu New Year")
                9  -> list.add("Ram Navami")
                15 -> list.add("Hanuman Jayanti")
            }
            2  -> when (tithi) { // Vaisakha
                3  -> list.add("Akshaya Tritiya")
                15 -> list.add("Narasimha Jayanti") // Vaisakha Purnima
            }
            3  -> when (tithi) { // Jyeshtha
                15 -> list.add("Vat Purnima")
            }
            4  -> when (tithi) { // Ashadha
                2  -> list.add("Jagannath Rath Yatra")
                15 -> list.add("Guru Purnima")
            }
            5  -> when (tithi) { // Shravana
                4  -> list.add("Ganesh Chaturthi (some regions)")
                15 -> list.add("Raksha Bandhan")
            }
            6  -> when (tithi) { // Bhadrapada
                4  -> list.add("Ganesh Chaturthi")
                23 -> list.add("Janmashtami")
                15 -> list.add("Onam / Bhadra Purnima")
            }
            7  -> when (tithi) { // Ashwin
                1  -> list.add("Navratri Begins / Ghatasthapana")
                10 -> list.add("Dussehra / Vijayadashami")
                15 -> list.add("Sharad Purnima / Kojagiri")
            }
            8  -> when (tithi) { // Kartik
                28 -> list.add("Dhanteras")
                29 -> list.add("Narak Chaturdashi / Choti Diwali")
                30 -> list.add("Diwali / Deepavali")
                2  -> list.add("Bhai Dooj")
                11 -> list.add("Dev Uthani Ekadashi")
                15 -> list.add("Kartik Purnima / Dev Diwali")
            }
            11 -> when (tithi) { // Magha
                15 -> list.add("Maghi Purnima")
                29 -> list.add("Maha Shivaratri")
            }
            12 -> when (tithi) { // Phalguna
                14 -> list.add("Holika Dahan")
                15 -> list.add("Holi")
                29 -> list.add("Maha Shivaratri (some regions)")
            }
        }

        return list
    }
}
