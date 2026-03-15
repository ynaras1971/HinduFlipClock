#!/usr/bin/env python3
"""
hindu_cal_outlookICS_generator.py
----------------------------------
Generates a daily Hindu Panchang ICS calendar importable into
Outlook, Google Calendar, or Apple Calendar.

Each day becomes an all-day calendar event:
  Subject : <Masa> <Paksha> <Tithi>
            e.g.  "Chaitra Krishna Ekadashi"
            with --tamil-subject:
            e.g.  "Chaitra Krishna Ekadashi | Chittirai 15"
            with --nakshatra-subject:
            e.g.  "Chaitra Krishna Ekadashi | Rohini"
                  "Chaitra Krishna Ekadashi | Rohini / Mrigashira"  (overlap day)
            with --festival-subject (on festival days only):
            e.g.  "Kartik Krishna Amavasya | Diwali / Deepavali"

  Details : Samvatsara, Tamil calendar month/date, Nakshatra
            (always shown, overlap noted), and any festivals/observances.

Run without arguments to see this help.
"""

import argparse
import math
import sys
import uuid
from datetime import date, timedelta
from pathlib import Path

# ─────────────────────────────────────────────────────────────────────────────
# Calendar data tables
# ─────────────────────────────────────────────────────────────────────────────

MASA_NAMES = [
    "Chaitra", "Vaisakha", "Jyeshtha", "Ashadha",
    "Shravana", "Bhadrapada", "Ashwin", "Kartik",
    "Margashirsha", "Pausha", "Magha", "Phalguna",
]

TITHI_NAMES = [
    "", "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami",
    "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
    "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima",
    "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami",
    "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
    "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Amavasya",
]

SAMVATSARA = [
    "Prabhava",    "Vibhava",      "Shukla",       "Pramoda",     "Prajapati",
    "Angirasa",    "Shrimukha",    "Bhava",         "Yuva",        "Dhata",
    "Ishvara",     "Bahudhanya",   "Pramathi",      "Vikrama",     "Vrisha",
    "Chitrabhanu", "Subhanu",      "Tarana",        "Parthiva",    "Vyaya",
    "Sarvajit",    "Sarvadhari",   "Virodhi",       "Vikrita",     "Khara",
    "Nandana",     "Vijaya",       "Jaya",          "Manmatha",    "Durmukhi",
    "Hevilambi",   "Vilambi",      "Vikari",        "Sharvari",    "Plava",
    "Shubhakrit",  "Shobhana",     "Krodhi",        "Vishvavasu",  "Parabhava",
    "Plavanga",    "Kilaka",       "Saumya",        "Sadharana",   "Virodhikrit",
    "Paridhavin",  "Pramadi",      "Ananda",        "Rakshasa",    "Nala",
    "Pingala",     "Kalayukti",    "Siddharthi",    "Raudra",      "Durmati",
    "Dundubhi",    "Rudhirodgari", "Raktakshi",     "Krodhana",    "Akshaya",
]

# 27 Nakshatras — each spans 13°20' (13.3333°) of sidereal ecliptic
NAKSHATRA_NAMES = [
    "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra",
    "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni",
    "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha",
    "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha", "Uttara Ashadha",
    "Shravana", "Dhanishtha", "Shatabhisha", "Purva Bhadrapada",
    "Uttara Bhadrapada", "Revati",
]

# Tamil solar months — correspond to sidereal zodiac signs 0–11
TAMIL_MONTHS = [
    "Chittirai",  # Mesha    / Aries
    "Vaikasi",    # Vrishabha/ Taurus
    "Aani",       # Mithuna  / Gemini
    "Aadi",       # Kataka   / Cancer
    "Aavani",     # Simha    / Leo
    "Purattasi",  # Kanya    / Virgo
    "Aippasi",    # Tula     / Libra
    "Karthigai",  # Vrischika/ Scorpio
    "Margazhi",   # Dhanus   / Sagittarius
    "Thai",       # Makara   / Capricorn
    "Maasi",      # Kumbha   / Aquarius
    "Panguni",    # Meena    / Pisces
]

# Festival lookup: masa (1–12) → tithi (1–30) → display name
MONTH_FESTIVALS = {
    1:  {
        1:  "Ugadi / Gudi Padwa / Hindu New Year / Chaitra Navratri Begins",
        9:  "Ram Navami",
        15: "Hanuman Jayanti",
    },
    2:  {3: "Akshaya Tritiya", 15: "Narasimha Jayanti"},
    3:  {15: "Vat Purnima"},
    4:  {2: "Jagannath Rath Yatra", 15: "Guru Purnima"},
    5:  {5: "Nag Panchami", 15: "Raksha Bandhan"},
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
    9:  {11: "Vaikunta Ekadashi / Geeta Jayanti", 15: "Dattatreya Jayanti"},
    10: {11: "Putrada Ekadashi"},
    11: {
        5:  "Vasant Panchami / Saraswati Puja",
        7:  "Ratha Saptami",
        15: "Maghi Purnima",
        29: "Maha Shivaratri",
    },
    12: {14: "Holika Dahan", 15: "Holi", 29: "Maha Shivaratri (some regions)"},
}

# ─────────────────────────────────────────────────────────────────────────────
# Astronomy  (mirrors HinduCalendar.kt exactly)
# ─────────────────────────────────────────────────────────────────────────────

AYANAMSA = 23.85          # sidereal correction (degrees)
MEAN_SOLAR_MOTION = 0.9856  # degrees per day (mean solar motion)


def _norm(v):
    return ((v % 360) + 360) % 360


def _to_jd(year, month, day, hour=6):
    A = (14 - month) // 12
    Y = year + 4800 - A
    M = month + 12 * A - 3
    jd = day + (153 * M + 2) // 5 + 365 * Y + Y // 4 - Y // 100 + Y // 400 - 32045
    return float(jd) + (hour - 12) / 24.0


def _sun_lon(jd):
    n = jd - 2451545.0
    L = _norm(280.46646 + 0.9856474 * n)
    g = math.radians(_norm(357.52911 + 0.9856003 * n))
    return _norm(L + 1.9146 * math.sin(g) + 0.0200 * math.sin(2 * g))


def _moon_lon(jd):
    n  = jd - 2451545.0
    L0 = _norm(218.3165 + 13.17639648 * n)
    M  = math.radians(_norm(134.9634 + 13.06499295 * n))
    D  = math.radians(_norm(297.8502 + 12.19074912 * n))
    F  = math.radians(_norm(93.2721  + 13.22935020 * n))
    Ms = math.radians(_norm(357.5291 +  0.98560028 * n))
    return _norm(
        L0
        + 6.2886 * math.sin(M)
        + 1.2740 * math.sin(2*D - M)
        + 0.6583 * math.sin(2*D)
        + 0.2136 * math.sin(2*M)
        - 1.8510 * math.sin(Ms) / 10
        - 1.1430 * math.sin(2*F) / 10
        + 0.0588 * math.sin(2*D - 2*M)
        + 0.0533 * math.sin(2*D + M)
        + 0.0458 * math.sin(2*D - Ms)
        + 0.0409 * math.sin(M - Ms)
        - 0.0347 * math.sin(D)
        - 0.0305 * math.sin(M + Ms)
    )


def _nakshatra_index(jd):
    """Return the Nakshatra index (0–26) for a given Julian Day."""
    sid_moon = ((_moon_lon(jd) - AYANAMSA) % 360 + 360) % 360
    return int(sid_moon / (360 / 27)) % 27


def _nakshatras_for_day(d: date) -> list:
    """
    Return the list of Nakshatra names active on this calendar day.
    Checks at 6 AM (sunrise) and 6 AM next day — if the Moon crosses
    into a new Nakshatra during the day, both names are returned.
    """
    jd_start = _to_jd(d.year, d.month, d.day, hour=6)
    jd_end   = _to_jd(d.year, d.month, d.day, hour=30)  # hour=30 → next day 6 AM
    n_start  = _nakshatra_index(jd_start)
    n_end    = _nakshatra_index(jd_end)
    if n_start == n_end:
        return [NAKSHATRA_NAMES[n_start]]
    return [NAKSHATRA_NAMES[n_start], NAKSHATRA_NAMES[n_end]]


# ─────────────────────────────────────────────────────────────────────────────
# Panchang & Tamil calendar calculation
# ─────────────────────────────────────────────────────────────────────────────

def calculate(d: date) -> dict:
    jd      = _to_jd(d.year, d.month, d.day, hour=6)
    sun     = _sun_lon(jd)
    moon    = _moon_lon(jd)

    # Tithi
    elong      = ((moon - sun) % 360 + 360) % 360
    tithi_num  = max(1, min(30, int(elong / 12) + 1))
    paksha     = "Shukla" if tithi_num <= 15 else "Krishna"
    tithi_name = TITHI_NAMES[tithi_num]

    # Masa (lunar month via sidereal solar longitude — Amavasyanta system)
    # In Amavasyanta the month runs Shukla 1→Krishna 15 (Amavasya).
    # During Krishna paksha the masa belongs to the same month as the preceding
    # Shukla paksha, which is one solar month earlier than the current Sun sign.
    sid_sun    = ((sun - AYANAMSA) % 360 + 360) % 360
    solar_idx  = max(0, min(11, int(sid_sun / 30)))
    if tithi_num > 15:               # Krishna paksha → shift back one month
        solar_idx = (solar_idx - 1) % 12
    masa_name  = MASA_NAMES[solar_idx]
    masa_num   = solar_idx + 1

    # Samvatsara (60-year Hindu year cycle)
    g_month    = d.month - 1          # 0-based
    vs_year    = d.year + 57 if g_month >= 3 else d.year + 56
    sv_idx     = ((vs_year - 2044) % 60 + 60) % 60
    samvatsara = SAMVATSARA[sv_idx]

    # Tamil solar calendar — always follows the Sun (not shifted by paksha)
    tamil_solar_idx = max(0, min(11, int(((sun - AYANAMSA) % 360 + 360) % 360 / 30)))
    tamil_month_idx = tamil_solar_idx
    tamil_month     = TAMIL_MONTHS[tamil_month_idx]
    deg_in_sign     = sid_sun % 30
    tamil_date      = max(1, min(32, int(deg_in_sign / MEAN_SOLAR_MOTION) + 1))

    # Festivals & Nakshatra
    festivals  = _get_festivals(masa_num, tithi_num)
    nakshatras = _nakshatras_for_day(d)

    return {
        "masa":        masa_name,
        "masa_num":    masa_num,
        "paksha":      paksha,
        "tithi_num":   tithi_num,
        "tithi_name":  tithi_name,
        "samvatsara":  f"{samvatsara} Samvatsara",
        "tamil_month": tamil_month,
        "tamil_date":  tamil_date,
        "festivals":   festivals,
        "nakshatras":  nakshatras,
    }


def _get_festivals(masa, tithi):
    f = []
    if tithi == 15:        f.append("Purnima")
    if tithi == 30:        f.append("Amavasya")
    if tithi in (11, 26):  f.append("Ekadashi")
    if tithi in (8,  23):  f.append("Ashtami")
    if tithi in (13, 28):  f.append("Pradosha")
    if tithi == 4:         f.append("Vinayaka Chaturthi")
    if tithi == 19:        f.append("Sankashti Chaturthi")
    if tithi == 29 and masa not in (11, 12):
        f.append("Masik Shivaratri")
    if masa in MONTH_FESTIVALS and tithi in MONTH_FESTIVALS[masa]:
        f.append(MONTH_FESTIVALS[masa][tithi])
    return f


# ─────────────────────────────────────────────────────────────────────────────
# ICS helpers
# ─────────────────────────────────────────────────────────────────────────────

def _fold(line: str) -> str:
    """Fold ICS line at 75 octets as required by RFC 5545."""
    encoded = line.encode("utf-8")
    if len(encoded) <= 75:
        return line
    result = []
    buf = b""
    for char in line:
        cb = char.encode("utf-8")
        if len(buf) + len(cb) > 75:
            result.append(buf.decode("utf-8"))
            buf = b" " + cb
        else:
            buf += cb
    if buf:
        result.append(buf.decode("utf-8"))
    return "\r\n".join(result)


def _ics_date(d: date) -> str:
    return d.strftime("%Y%m%d")


def _make_event(d: date, summary: str, description: str) -> str:
    lines = [
        "BEGIN:VEVENT",
        _fold(f"SUMMARY:{summary}"),
        f"DTSTART;VALUE=DATE:{_ics_date(d)}",
        f"DTEND;VALUE=DATE:{_ics_date(d + timedelta(days=1))}",
        f"DTSTAMP:{date.today().strftime('%Y%m%d')}T000000Z",
        f"UID:{uuid.uuid4()}@hindu-panchang",
        _fold(f"DESCRIPTION:{description}"),
        "TRANSP:TRANSPARENT",
        "END:VEVENT",
    ]
    return "\r\n".join(lines)


# ─────────────────────────────────────────────────────────────────────────────
# ICS generation
# ─────────────────────────────────────────────────────────────────────────────

def generate_ics(start: date, days: int, tamil_in_subject: bool,
                 festival_in_subject: bool, nakshatra_in_subject: bool,
                 output: Path, verbose: bool):

    print(f"\n  Generating {days} days from {start} …")

    events = []
    for offset in range(days):
        d = start + timedelta(days=offset)
        p = calculate(d)

        tamil_str    = f"{p['tamil_month']} {p['tamil_date']}"
        nakshatra_str = " / ".join(p["nakshatras"])

        # ── Subject line ──────────────────────────────────────────────────
        subject = f"{p['masa']} {p['paksha']} {p['tithi_name']}"
        if tamil_in_subject:
            subject += f" | {tamil_str}"
        if nakshatra_in_subject:
            subject += f" | {nakshatra_str}"
        if festival_in_subject and p["festivals"]:
            subject += " | " + ", ".join(p["festivals"])

        # ── Description ───────────────────────────────────────────────────
        overlap_note = " (two Nakshatras today)" if len(p["nakshatras"]) > 1 else ""
        desc_lines = [
            p["samvatsara"],
            f"Tamil: {tamil_str}",
            f"Nakshatra: {nakshatra_str}{overlap_note}",
        ]
        if p["festivals"]:
            desc_lines.append("")
            desc_lines.append("Festivals / Observances:")
            for fest in p["festivals"]:
                desc_lines.append(f"  * {fest}")

        description = "\\n".join(desc_lines)

        events.append(_make_event(d, subject, description))

        if verbose:
            overlap = "*" if len(p["nakshatras"]) > 1 else " "
            fest_str = ", ".join(p["festivals"]) if p["festivals"] else ""
            print(f"  {d}  {subject:<60}  {nakshatra_str:<30}{overlap}  {fest_str}")

    # ── Assemble ICS ──────────────────────────────────────────────────────
    header_lines = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//HinduFlipClock//Hindu Panchang Calendar//EN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:Hindu Panchang",
        "X-WR-CALDESC:Daily Hindu Panchang — Masa\\, Paksha\\, Tithi\\, Festivals",
    ]
    header  = "\r\n".join(header_lines)
    body    = "\r\n".join(events)
    content = header + "\r\n" + body + "\r\n" + "END:VCALENDAR"

    output.write_text(content, encoding="utf-8")

    fest_count = sum(1 for offset in range(days)
                     if calculate(start + timedelta(days=offset))["festivals"])

    subject_parts = ["Masa Paksha Tithi"]
    if tamil_in_subject:       subject_parts.append("Tamil month/date")
    if nakshatra_in_subject:   subject_parts.append("Nakshatra")
    if festival_in_subject:    subject_parts.append("Festival name (when applicable)")

    print(f"\n  ✓ {days} daily events written")
    print(f"  ✓ {fest_count} days have festivals / observances")
    print(f"  ✓ Subject line contains: {' | '.join(subject_parts)}")
    print(f"\n  Saved to: {output}")
    print()
    print("  To import into Outlook:")
    print("    File → Open & Export → Import/Export")
    print("    → Import an iCalendar (.ics) → select the file above")
    print()


# ─────────────────────────────────────────────────────────────────────────────
# CLI
# ─────────────────────────────────────────────────────────────────────────────

EPILOG = """
Examples:
  Generate 365 days starting today (Tamil info in details only):
    python hindu_cal_outlookICS_generator.py 365

  Festival name in subject line (e.g. "Kartik Krishna Amavasya | Diwali"):
    python hindu_cal_outlookICS_generator.py 365 --festival-subject

  Tamil month/date in subject line:
    python hindu_cal_outlookICS_generator.py 365 --tamil-subject

  Both Tamil and festival in subject line:
    python hindu_cal_outlookICS_generator.py 365 --tamil-subject --festival-subject

  Start from a specific date, save to custom file:
    python hindu_cal_outlookICS_generator.py 180 --start 2027-01-01 --output my_cal.ics

  List every event as it is generated:
    python hindu_cal_outlookICS_generator.py 90 --verbose

Subject line combinations:
  (default)                           →  Chaitra Krishna Ekadashi
  --tamil-subject                     →  Chaitra Krishna Ekadashi | Chittirai 15
  --nakshatra-subject                 →  Chaitra Krishna Ekadashi | Rohini
  --nakshatra-subject (overlap day)   →  Chaitra Krishna Ekadashi | Rohini / Mrigashira
  --festival-subject                  →  Kartik Krishna Amavasya | Diwali / Deepavali
  --tamil-subject --nakshatra-subject →  Chaitra Krishna Ekadashi | Chittirai 15 | Rohini
  --tamil-subject --festival-subject  →  Kartik Krishna Amavasya | Aippasi 14 | Diwali / Deepavali

Note: Nakshatra always appears in event Details regardless of --nakshatra-subject.

Default output file: ~/Desktop/HinduPanchang_YYYY-MM-DD_Ndays.ics
"""


def build_parser():
    parser = argparse.ArgumentParser(
        prog="hindu_cal_outlookICS_generator.py",
        description=(
            "Generate a daily Hindu Panchang ICS calendar for Outlook / Google Calendar.\n"
            "Each day shows the Masa, Paksha, and Tithi in the event subject,\n"
            "with Samvatsara, Tamil calendar, and festivals in the details."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=EPILOG,
    )

    parser.add_argument(
        "days",
        nargs="?",
        type=int,
        default=365,
        metavar="DAYS",
        help=(
            "Number of days to generate. "
            "Default: 365"
        ),
    )
    parser.add_argument(
        "--start",
        type=str,
        default=None,
        metavar="YYYY-MM-DD",
        help=(
            "Start date in YYYY-MM-DD format. "
            "Default: today"
        ),
    )
    parser.add_argument(
        "--tamil-subject",
        action="store_true",
        default=False,
        help=(
            "Add Tamil calendar month and date to the event subject line. "
            "Example: 'Chaitra Krishna Ekadashi | Chittirai 15'. "
            "Default: Tamil info appears in event details only."
        ),
    )
    parser.add_argument(
        "--nakshatra-subject",
        action="store_true",
        default=False,
        help=(
            "Add the Nakshatra (lunar mansion) to the event subject line. "
            "When two Nakshatras are active on the same day they are shown as 'Rohini / Mrigashira'. "
            "Nakshatra always appears in event details regardless of this flag."
        ),
    )
    parser.add_argument(
        "--festival-subject",
        action="store_true",
        default=False,
        help=(
            "Add the festival name to the event subject line on days when a "
            "festival or observance falls. "
            "Example: 'Kartik Krishna Amavasya | Diwali / Deepavali'. "
            "On non-festival days the subject is unchanged. "
            "Can be combined with --tamil-subject."
        ),
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        metavar="FILE",
        help=(
            "Output ICS file path. "
            "Default: ~/Desktop/HinduPanchang_<start>_<days>days.ics"
        ),
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        default=False,
        help="Print each event as it is generated.",
    )

    return parser


def main():
    # Show help when run with no arguments
    if len(sys.argv) == 1:
        build_parser().print_help()
        sys.exit(0)

    parser = build_parser()
    args   = parser.parse_args()

    # Validate / resolve start date
    if args.start:
        try:
            start = date.fromisoformat(args.start)
        except ValueError:
            parser.error(f"Invalid date format '{args.start}' — use YYYY-MM-DD.")
    else:
        start = date.today()

    # Validate days
    if args.days < 1:
        parser.error("DAYS must be at least 1.")
    if args.days > 3650:
        parser.error("DAYS cannot exceed 3650 (10 years).")

    # Resolve output path
    if args.output:
        output = Path(args.output)
        if not output.suffix:
            output = output.with_suffix(".ics")
    else:
        filename = f"HinduPanchang_{start}_{args.days}days.ics"
        output   = Path.home() / "Desktop" / filename

    # Ensure output directory exists
    output.parent.mkdir(parents=True, exist_ok=True)

    generate_ics(
        start                = start,
        days                 = args.days,
        tamil_in_subject     = args.tamil_subject,
        nakshatra_in_subject = args.nakshatra_subject,
        festival_in_subject  = args.festival_subject,
        output               = output,
        verbose              = args.verbose,
    )


if __name__ == "__main__":
    main()
