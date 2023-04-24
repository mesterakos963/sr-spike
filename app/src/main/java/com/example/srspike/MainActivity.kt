package com.example.srspike

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.RecognizerIntent.EXTRA_BIASING_STRINGS
import android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE
import android.speech.SpeechRecognizer
import android.speech.SpeechRecognizer.RESULTS_RECOGNITION
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import java.io.File


class MainActivity : AppCompatActivity() {

    private lateinit var languageChooser: Spinner
    private lateinit var modeSwitch: SwitchCompat
    private lateinit var recognizedText: TextView
    private lateinit var button1: RadioButton
    private lateinit var button2: RadioButton
    private lateinit var button3: RadioButton
    private lateinit var startButton: FrameLayout
    private lateinit var audioButton: Button
    private lateinit var rawInputButton1: Button
    private lateinit var rawInputButton2: Button
    private lateinit var rawInputButton3: Button
    private lateinit var rawInputButton4: Button
    private lateinit var rawInputButton5: Button
    private lateinit var rawInputButton6: Button

    private lateinit var speechRecognizer: SpeechRecognizer

    private lateinit var wordButtonList: List<RadioButton>

    private val _selectedLanguageCode = MutableStateFlow("en")
    private val selectedLanguageCode: StateFlow<String> = _selectedLanguageCode.asStateFlow()

    val languageCodes = listOf("en", "de", "es", "fr")

    private var rawInputs: List<Pair<String, Int>> = emptyList()

    private val _recognitionIntent =
        MutableStateFlow(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                2000
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    private val recognitionIntent = _recognitionIntent.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    private var isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _focusedWord = MutableStateFlow("")
    private var focusedWord: StateFlow<String> = _focusedWord.asStateFlow()

    private val _selectedRawInput = MutableStateFlow<Int?>(null)
    private var selectedRawInput: StateFlow<Int?> = _selectedRawInput.asStateFlow()

    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestRecordAudioPermission()
        initSpeechRecogniser()
        initLanguageChooser()
        initSwitch()
        initStartButton()
        initRecognisedText()
        initWordButtons()
        initAudioButton()
        initRawInputButtons()
        lifecycleScope.launchWhenStarted {
            selectedLanguageCode.collectLatest {
                _focusedWord.value = ""
                recognitionIntent.value.apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, it)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, it)
                }
            }
        }
        lifecycleScope.launchWhenStarted {
            isOnline.collectLatest {
                recognitionIntent.value.apply {
                    putExtra(EXTRA_PREFER_OFFLINE, it)
                }
            }
        }
        lifecycleScope.launchWhenStarted {
            focusedWord.collectLatest { word ->
                recognitionIntent.value.apply {
                    putExtra(EXTRA_BIASING_STRINGS, word)
                }
                wordButtonList.forEach { button ->
                    button.isChecked = word == button.text.toString()
                }
            }
        }
        lifecycleScope.launchWhenStarted {
            selectedRawInput.filterNotNull().collectLatest {
                //itt kapod meg a resId-ját a raw file-nak
                mediaPlayer = MediaPlayer.create(this@MainActivity, it)
                mediaPlayer.start()
            }
        }
    }

    private fun initSpeechRecogniser() {
        speechRecognizer = if (Build.VERSION.SDK_INT > 30) {
            SpeechRecognizer.createSpeechRecognizer(
                this,
                ComponentName.unflattenFromString("com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService")
            )
        } else {
            SpeechRecognizer.createSpeechRecognizer(
                this,
                ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService")
            )
        }

    }

    private fun initLanguageChooser() {
        languageChooser = findViewById(R.id.language_chooser)
        ArrayAdapter.createFromResource(
            this,
            R.array.languages_array,
            R.layout.language_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            languageChooser.adapter = adapter
        }
        languageChooser.onItemSelectedListener = (object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                _selectedLanguageCode.value = languageCodes[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
    }

    private fun initSwitch() {
        modeSwitch = findViewById(R.id.mode_switch)
        lifecycleScope.launchWhenStarted {
            isOnline.collectLatest {
                modeSwitch.isChecked = it
            }
        }
        modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            _isOnline.value = isChecked
        }
    }

    private fun initRecognisedText() {
        recognizedText = findViewById(R.id.recognised_text)
    }

    private fun initWordButtons() {
        button1 = findViewById(R.id.word_1)
        button2 = findViewById(R.id.word_2)
        button3 = findViewById(R.id.word_3)
        wordButtonList = listOf(button1, button2, button3)
        lifecycleScope.launchWhenStarted {
            selectedLanguageCode.collectLatest {
                when (it) {
                    "en" -> {
                        button1.text = "he"
                        button2.text = "she"
                        button3.text = "own"
                    }

                    "de" -> {
                        button1.text = "und"
                        button2.text = "als"
                        button3.text = "um"
                    }

                    "es" -> {
                        button1.text = "yo"
                        button2.text = "muy"
                        button3.text = "con"
                    }

                    "fr" -> {
                        button1.text = "oui"
                        button2.text = "car"
                        button3.text = "tout"
                    }
                }
            }
        }
        wordButtonList.forEach { button ->
            button.setOnClickListener {
                _focusedWord.value = button.text.toString()
            }
        }
    }

    private fun initStartButton() {
        startButton = findViewById(R.id.start_button)
        startButton.setOnClickListener {
            startButton.isSelected = true
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    startButton.isSelected = false
                    startButton.isClickable = true
                    recognizedText.text = results?.getStringArrayList(RESULTS_RECOGNITION)?.get(0)
                }

                override fun onError(error: Int) {
                    startButton.isSelected = false
                    startButton.isClickable = true
                    recognizedText.text = "Error code: $error"
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    startButton.isClickable = false
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

            })
            lifecycleScope.launchWhenStarted {
                recognitionIntent.collectLatest {
                    speechRecognizer.startListening(it)
                }
            }

        }
    }

    private fun requestRecordAudioPermission() {
        val requiredPermission: String = Manifest.permission.RECORD_AUDIO
        if (checkCallingOrSelfPermission(requiredPermission) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(requiredPermission), 101)
        }
    }

    private fun initAudioButton() {
        audioButton = findViewById(R.id.audio_button)
        lifecycleScope.launchWhenStarted {
            selectedLanguageCode.collectLatest {
                when (it) {
                    "en" -> {
                        rawInputs = listOf(
                            "He" to R.raw.he,
                            "She" to R.raw.she,
                            "Own" to R.raw.own,
                            "She is a woman" to R.raw.she_is_a_woman,
                            "My name is Professor Max" to R.raw.my_name_is_professor_max,
                            "The weather is nice outside" to R.raw.the_weather_is_nice
                        )
                    }

                    "de" -> {
                    }

                    "es" -> {
                    }

                    "fr" -> {
                    }
                }
            }
        }
    }

    private fun initRawInputButtons() {
        rawInputButton1 = findViewById(R.id.button_1)
        rawInputButton2 = findViewById(R.id.button_2)
        rawInputButton3 = findViewById(R.id.button_3)
        rawInputButton4 = findViewById(R.id.button_4)
        rawInputButton5 = findViewById(R.id.button_5)
        rawInputButton6 = findViewById(R.id.button_6)
        val buttons = listOf(
            rawInputButton1,
            rawInputButton2,
            rawInputButton3,
            rawInputButton4,
            rawInputButton5,
            rawInputButton6
        )
        lifecycleScope.launchWhenStarted {
            selectedLanguageCode.collectLatest {
                rawInputs.forEachIndexed { index, pair ->
                    buttons[index].text = pair.first
                }
            }
        }
        buttons.forEach { button ->
            button.setOnClickListener {
                rawInputs.map {
                    if (button.text.toString() == it.first) {
                        _selectedRawInput.value = it.second
                    }
                }
            }
        }
    }
    /*
    * resource
    * 3 rövid, 3 hosszú minden nyelvhez
    * modal pl
    * */

}