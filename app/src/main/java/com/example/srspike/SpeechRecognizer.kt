package com.example.srspike

import android.content.ComponentName
import android.content.Context
import android.os.Build

const val ANDROID_13_SPEECH_RECOGNIZER_COMPONENT_NAME =
    "com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"
const val STANDARD_ANDROID_SPEECH_RECOGNIZER_COMPONENT_NAME =
    "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"

class SpeechRecognizer(
    context: Context
) {
    private val componentName = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> ANDROID_13_SPEECH_RECOGNIZER_COMPONENT_NAME
        else -> STANDARD_ANDROID_SPEECH_RECOGNIZER_COMPONENT_NAME
    }

    val speechRecognizer =
        android.speech.SpeechRecognizer.createSpeechRecognizer(
            context,
            ComponentName.unflattenFromString(componentName)
        )
}