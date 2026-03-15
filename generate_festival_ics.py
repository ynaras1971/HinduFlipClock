#!/usr/bin/env python3
"""
generate_festival_ics.py
Generates a Hindu festival ICS calendar (importable into Outlook / Google Calendar)
for the next 24 months using the same astronomical calculations as HinduCalendar.kt.
"""

import math
import uuid
from datetime import date, timedelta
from pathlib import Path

OUTPUT = Path.home() / "Desktop" / "HinduFestivals_2026_2028.ics"

# ── Data tables (mirrors HinduCalendar.kt) ────────────────────────────────────

MASA_NAMES = [
    "Chaitra", "Vaisakha", "Jyeshtha", "Ashadha",
    "Shravana", "Bhadrapada", "Ashwin", "Kartik",
    "Margashirsha", "Pausha", "Magha", "Phalguna"
]

SAMVATSARA = [
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
]

TITHI_NAMES = [
    "", "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami",
    "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
    "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima",
    "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami",
    "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
    "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Amavasya"
]

# Festival lookup: masa (1-12) -> tithi (1-30) -> name
MONTH_FESTIVALS = {
    1:  {
        1:  "Ugadi / Gudi Padwa / Hindu New Year / Chaitra Navratri Begins",
        9:  "Ram Navami",
        15: "Hanuman Jayanti",
    },
    2:  {
        3:  "Akshaya Tritiya",
        15: "Narasimha Jayanti",
    },
    3:  {
        15: "Vat Purnima",
    },
    4:  {
        2:  "Jagannath Rath Yatra",
        15: "Guru Purnima",
    },
    5:  {
        5:  "Nag Panchami",
        15: "Raksha Bandhan",
    },
    6:  {
        4:  "Ganesh Chaturthi",
        14: "Ganesh Visarjan / Anant Chaturdashi",
        15: "Onam / Bhadra Purnima",
        23: "Janmashtami",
    },
    7:  {
        1:  "Sharad Navratri Begins / Ghatasthapana",
        10: "Dussehra / Vijayadashami",
        15: "Sharad Purnima / Kojagiri",
        30: "Mahalaya Amavasya / Pitru Moksha Amavasya",
    },
    8:  {
        1:  "Govardhan Puja / Annakut",
        2:  "Bhai Dooj",
        6:  "Chhath Puja",
        11: "Dev Uthani Ekadashi / Tulsi Vivah",
        15: "Kartik Purnima / Dev Diwali",
        19: "Karva Chauth",
        28: "Dhanteras",
        29: "Narak Chaturdashi / Choti Diwali",
        30: "Diwali / Deepavali",
    },
    9:  {
        11: "Vaikunta Ekadashi / Geeta Jayanti",
        15: "Dattatreya Jayanti",
    },
    10: {
        11: "Putrada Ekadashi",
    },
    11: {
        5:  "Vasant Panchami / Saraswati Puja",
        7:  "Ratha Saptami",
        15: "Maghi Purnima",
        29: "Maha Shivaratri",
    },
    12: {
        14: "Holika Dahan",
        15: "Holi",
        29: "Maha Shivaratri (some regions)",
    },
}

# ── Astronomy (mirrors HinduCalendar.kt exactly) ──────────────────────────────

def to_jd(year, month, day, hour=6):
    A = (14 - month) // 12
    Y = year + 4800 - A
    M = month + 12 * A - 3
    jd = (day + (153 * M + 2) // 5 + 365 * Y + Y // 4
          - Y // 100 + Y // 400 - 32045)
    jd += (hour - 12) / 24.0
    return float(jd)

def norm360(v):
    return ((v % 360) + 360) % 360

def sun_longitude(jd):
    n = jd - 2451545.0
    L = norm360(280.46646 + 0.9856474 * n)
    g = math.radians(norm360(357.52911 + 0.9856003 * n))
    return norm360(L + 1.9146 * math.sin(g) + 0.0200 * math.sin(2 * g))

def moon_longitude(jd):
    n = jd - 2451545.0
    L0    = norm360(218.3165 + 13.17639648 * n)
    Mrad  = math.radians(norm360(134.9634 + 13.06499295 * n))
    Drad  = math.radians(norm360(297.8502 + 12.19074912 * n))
    Frad  = math.radians(norm360(93.2721  + 13.22935020 * n))
    Msrad = math.radians(norm360(357.5291 +  0.98560028 * n))
    return norm360(
        L0
        + 6.2886 * math.sin(Mrad)
        + 1.2740 * math.sin(2*Drad - Mrad)
        + 0.6583 * math.sin(2*Drad)
        + 0.2136 * math.sin(2*Mrad)
        - 0.1851 * math.sin(Msrad)
        - 0.1143 * math.sin(2*Frad)
        + 0.0588 * math.sin(2*Drad - 2*Mrad)
        + 0.0533 * math.sin(2*Drad + Mrad)
        + 0.0458 * math.sin(2*Drad - Msrad)
        + 0.0409 * math.sin(Mrad - Msrad)
        - 0.0347 * math.sin(Drad)
        - 0.0305 * math.sin(Mrad + Msrad)
    )

# ── Panchang calculation ───────────────────────────────────────────────────────

def calculate_panchang(d: date):
    jd       = to_jd(d.year, d.month, d.day, hour=6)
    sun_lon  = sun_longitude(jd)
    moon_lon = moon_longitude(jd)

    elongation = ((moon_lon - sun_lon) % 360 + 360) % 360
    tithi_num  = min(30, max(1, int(elongation / 12) + 1))

    paksha     = "Shukla" if tithi_num <= 15 else "Krishna"
    tithi_name = TITHI_NAMES[tithi_num]

    sidereal_sun = ((sun_lon - 23.85) % 360 + 360) % 360
    solar_month  = max(0, min(11, int(sidereal_sun / 30)))
    masa_name    = MASA_NAMES[solar_month]
    masa_num     = solar_month + 1

    g_month    = d.month - 1  # 0-based
    vs_year    = d.year + 57 if g_month >= 3 else d.year + 56
    sv_idx     = ((vs_year - 2044) % 60 + 60) % 60
    samvatsara = SAMVATSARA[sv_idx]

    festivals = get_festivals(masa_num, tithi_num)

    return {
        "samvatsara": f"{samvatsara} Samvatsara",
        "masa":       masa_name,
        "masa_num":   masa_num,
        "paksha":     paksha,
        "tithi_num":  tithi_num,
        "tithi_name": tithi_name,
        "festivals":  festivals,
    }

# ── Festival detection (mirrors HinduCalendar.kt) ─────────────────────────────

def get_festivals(masa, tithi):
    fests = []

    # Universal markers every month
    if tithi == 15:          fests.append("Purnima")
    if tithi == 30:          fests.append("Amavasya")
    if tithi in (11, 26):    fests.append("Ekadashi")
    if tithi in (8,  23):    fests.append("Ashtami")
    if tithi in (13, 28):    fests.append("Pradosha")
    if tithi == 4:           fests.append("Vinayaka Chaturthi")
    if tithi == 19:          fests.append("Sankashti Chaturthi")
    # Masik Shivaratri — suppressed when Maha Shivaratri is shown
    if tithi == 29 and masa not in (11, 12):
        fests.append("Masik Shivaratri")

    # Month-specific
    if masa in MONTH_FESTIVALS and tithi in MONTH_FESTIVALS[masa]:
        fests.append(MONTH_FESTIVALS[masa][tithi])

    return fests

# ── ICS generation ────────────────────────────────────────────────────────────

def ics_date(d: date) -> str:
    return d.strftime("%Y%m%d")

def make_event(d: date, summary: str, description: str) -> str:
    uid       = str(uuid.uuid4())
    dtstart   = ics_date(d)
    dtend     = ics_date(d + timedelta(days=1))
    dtstamp   = "20260312T000000Z"
    # Fold long lines at 75 chars (ICS spec)
    def fold(line):
        result, line = [], line
        while len(line.encode("utf-8")) > 75:
            result.append(line[:75])
            line = " " + line[75:]
        result.append(line)
        return "\r\n".join(result)

    lines = [
        "BEGIN:VEVENT",
        fold(f"SUMMARY:{summary}"),
        f"DTSTART;VALUE=DATE:{dtstart}",
        f"DTEND;VALUE=DATE:{dtend}",
        f"DTSTAMP:{dtstamp}",
        f"UID:{uid}",
        fold(f"DESCRIPTION:{description}"),
        "TRANSP:TRANSPARENT",
        "END:VEVENT",
    ]
    return "\r\n".join(lines)

def generate():
    start = date(2026, 3, 12)
    end   = date(2028, 3, 12)

    events = []
    current = start
    seen_tithi = {}   # (tithi_num, masa_num) -> last date seen, to avoid near-duplicate entries

    print(f"Calculating Panchang for {(end - start).days} days...")

    while current <= end:
        p = calculate_panchang(current)
        key = (current.year, current.month, p["tithi_num"], p["masa_num"])

        for festival in p["festivals"]:
            # Deduplicate: skip if same festival already emitted within 2 days
            dedup_key = (festival, p["masa_num"], p["tithi_num"])
            if dedup_key in seen_tithi:
                last = seen_tithi[dedup_key]
                if (current - last).days < 2:
                    continue
            seen_tithi[dedup_key] = current

            paksha = p["paksha"]
            tithi  = p["tithi_name"]
            masa   = p["masa"]
            samv   = p["samvatsara"]
            desc   = (f"{paksha} {tithi} - {masa} Masa\\n"
                      f"{samv}\\n"
                      f"Hindu Calendar: {masa} {paksha} {tithi}")

            event = make_event(current, festival, desc)
            events.append(event)
            print(f"  {current}  {festival}  ({masa} {paksha} {tithi})")

        current += timedelta(days=1)

    # Build ICS file
    header = "\r\n".join([
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//HinduFlipClock//Hindu Festival Calendar//EN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:Hindu Festivals 2026-2028",
        "X-WR-CALDESC:Hindu festivals calculated from Panchang (lunar calendar)",
        "X-WR-TIMEZONE:America/Chicago",
    ])
    footer = "END:VCALENDAR"

    ics_content = header + "\r\n" + "\r\n".join(events) + "\r\n" + footer

    OUTPUT.write_text(ics_content, encoding="utf-8")
    print(f"\nDone! {len(events)} festival events written to:")
    print(f"  {OUTPUT}")
    print("\nTo import into Outlook:")
    print("  File → Open & Export → Import/Export → Import an iCalendar (.ics)")

if __name__ == "__main__":
    generate()
