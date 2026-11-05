package com.meshalive.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Current UI language: "en" or "ta". */
val LocalLang = staticCompositionLocalOf { "en" }

/**
 * Tamil strings for the screens a person in distress uses (Home, SOS, situation report,
 * navigation). Keys are the English text, so untranslated text falls back to English.
 */
object Tamil {
    val strings = mapOf(
        "tap to send" to "அனுப்ப அழுத்தவும்",
        "getting GPS…" to "இருப்பிடம் பெறுகிறது…",
        "Starting mesh relay…" to "இணைப்பு தொடங்குகிறது…",
        "Scanning for nearby phones…" to "அருகிலுள்ள கைபேசிகளைத் தேடுகிறது…",
        "Connected to " to "இணைக்கப்பட்டது: ",
        "Edit" to "திருத்து",
        "Save" to "சேமி",
        "Cancel" to "ரத்து",
        "SOS message" to "SOS செய்தி",
        "Medical help" to "மருத்துவ உதவி",
        "Trapped" to "சிக்கியுள்ளோம்",
        "Water / food" to "தண்ணீர் / உணவு",
        "My situation (sent with every SOS)" to "என் நிலைமை (ஒவ்வொரு SOS உடனும் அனுப்பப்படும்)",
        "People" to "நபர்கள்",
        "Elderly" to "முதியவர்",
        "Children" to "குழந்தைகள்",
        "Injured" to "காயம்",
        "Disabled" to "மாற்றுத்திறனாளி",
        "Water level" to "நீர் மட்டம்",
        "none" to "இல்லை",
        "knee" to "முழங்கால்",
        "waist" to "இடுப்பு",
        "chest" to "மார்பு",
        "roof" to "கூரை",
        "Urgency" to "அவசர நிலை",
        "CRITICAL" to "மிக அவசரம்",
        "HIGH" to "அதிகம்",
        "MEDIUM" to "நடுத்தரம்",
        "LOW" to "குறைவு",
        "Connection status" to "இணைப்பு நிலை",
        "I have internet – act as gateway" to "இணையம் உள்ளது – நுழைவாயிலாகச் செயல்படு",
        "Receive SOS from the mesh and forward them online" to "அருகிலுள்ள SOS-களைப் பெற்று இணையத்தில் அனுப்பும்",
        "Rescue carrier (boat / NDRF / ambulance)" to "மீட்பு வாகனம் (படகு / NDRF / ஆம்புலன்ஸ்)",
        "Carries SOS out of the dead zone and uploads them when it reaches signal" to
            "தொடர்பில்லாத பகுதியிலிருந்து SOS-களை எடுத்துச் சென்று, சிக்னல் கிடைத்ததும் அனுப்பும்",
        "Home" to "முகப்பு",
        "Messages" to "செய்திகள்",
        "Network" to "வலை",
        "Inbox" to "உள்பெட்டி",
        "Sim" to "Sim",
        "Impact" to "தாக்கம்",
        "Back" to "பின்",
        "Next" to "அடுத்து",
        "Start MeshAlive" to "MeshAlive தொடங்கு",
        "About your household" to "உங்கள் குடும்பம் பற்றி",
        "Rescuers see this with every SOS, so they bring the right help." to "ஒவ்வொரு SOS உடனும் மீட்பவர்கள் இதைப் பார்ப்பார்கள், சரியான உதவியைக் கொண்டு வருவார்கள்.",
        "Your name" to "உங்கள் பெயர்",
        "People with you" to "உங்களுடன் உள்ளவர்கள்",
        "Anyone who needs extra help?" to "கூடுதல் உதவி தேவைப்படுபவர்கள் உள்ளனரா?",
        "You can change this any time on the Home screen." to "முகப்புத் திரையில் எப்போது வேண்டுமானாலும் மாற்றலாம்.",
        "How will this phone help?" to "இந்தக் கைபேசி எப்படி உதவும்?",
        "Every phone relays SOS for others automatically." to "ஒவ்வொரு கைபேசியும் மற்றவர்களின் SOS-ஐத் தானாக அனுப்பும்.",
        "Citizen" to "பொதுமக்கள்",
        "Send SOS for my family and relay for neighbours" to "என் குடும்பத்துக்காக SOS அனுப்பி, அண்டை வீட்டாருக்கும் உதவும்",
        "Rescue carrier" to "மீட்பு வாகனம்",
        "Boat, NDRF/SDRF team or ambulance: carry SOS out of dead zones" to "படகு, NDRF/SDRF குழு அல்லது ஆம்புலன்ஸ்: தொடர்பில்லாத பகுதியிலிருந்து SOS-ஐ எடுத்துச் செல்லும்",
        "Control-room gateway" to "கட்டுப்பாட்டு அறை நுழைவாயில்",
        "Has internet: uploads every SOS to the district dashboard" to "இணையம் உள்ளது: ஒவ்வொரு SOS-ஐயும் மாவட்டக் கட்டுப்பாட்டு அறைக்கு அனுப்பும்",
        "I'm safe" to "பாதுகாப்பாக உள்ளேன்",
        "Rescue team on the way" to "மீட்புக் குழு வருகிறது",
        "Marked rescued" to "மீட்கப்பட்டதாகக் குறிக்கப்பட்டது",
        "Stay where you are. Keep this phone on." to "இருக்கும் இடத்திலேயே இருங்கள். கைபேசியை அணைக்காதீர்கள்.",
        "From the district control room" to "மாவட்டக் கட்டுப்பாட்டு அறையிலிருந்து",
    )

    /** Preset messages, so the control room still gets readable text from Tamil speakers. */
    const val SOS_TEXT = "அவசரம்! என் இடத்தில் உடனடி உதவி தேவை."
    const val TRAPPED_TEXT = "நாங்கள் சிக்கியுள்ளோம், வெளியேற முடியவில்லை"
    const val MEDICAL_TEXT = "அவசர மருத்துவ உதவி தேவை"
    const val SUPPLIES_TEXT = "குடிநீர் மற்றும் உணவு தேவை"
}

fun translate(lang: String, en: String): String = if (lang == "ta") Tamil.strings[en] ?: en else en

@Composable
fun tr(en: String): String = translate(LocalLang.current, en)
