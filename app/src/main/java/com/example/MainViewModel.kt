package com.example

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("yanye_dictation_prefs", Context.MODE_PRIVATE)

    // Speech TTS Engine
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Auth flows
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail = _userEmail.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Database items
    private val _allDictations = MutableStateFlow<List<DictationItem>>(emptyList())
    val allDictations = _allDictations.asStateFlow()

    // Available categories/group lists
    private val _availableGroups = MutableStateFlow<List<String>>(emptyList())
    val availableGroups = _availableGroups.asStateFlow()

    // Selected study groups
    private val _selectedGroups = MutableStateFlow<Set<String>>(emptySet())
    val selectedGroups = _selectedGroups.asStateFlow()

    // Filter Type ("all", "wrong", "poetry", "word", etc.)
    private val _filterType = MutableStateFlow("all")
    val filterType = _filterType.asStateFlow()

    // Dictation state machine
    private val _activeQuestionList = MutableStateFlow<List<DictationItem>>(emptyList())
    val activeQuestionList = _activeQuestionList.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex = _currentQuestionIndex.asStateFlow()

    private val _currentQuestion = MutableStateFlow<DictationItem?>(null)
    val currentQuestion = _currentQuestion.asStateFlow()

    private val _checkAnswerVisible = MutableStateFlow(false)
    val checkAnswerVisible = _checkAnswerVisible.asStateFlow()

    private val _correctCount = MutableStateFlow(0)
    val correctCount = _correctCount.asStateFlow()

    private val _wrongCount = MutableStateFlow(0)
    val wrongCount = _wrongCount.asStateFlow()

    private val _isSessionFinished = MutableStateFlow(false)
    val isSessionFinished = _isSessionFinished.asStateFlow()

    // Local incorrect list (Wrong Book 错题本)
    private val _wrongsList = MutableStateFlow<List<DictationItem>>(emptyList())
    val wrongsList = _wrongsList.asStateFlow()

    // Settings States
    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled = _isTtsEnabled.asStateFlow()

    private val _isIntenseMode = MutableStateFlow(false)
    val isIntenseMode = _isIntenseMode.asStateFlow()

    private val _intenseTimerLimit = MutableStateFlow(8) // in seconds, default 8s
    val intenseTimerLimit = _intenseTimerLimit.asStateFlow()

    private val _timeLeft = MutableStateFlow(8)
    val timeLeft = _timeLeft.asStateFlow()

    // Watch adaptive face simulation config
    private val _isWatchBezelSimulated = MutableStateFlow(true)
    val isWatchBezelSimulated = _isWatchBezelSimulated.asStateFlow()

    // Active stroke drawings tracking for handwriting canvas
    val drawStrokes = mutableStateListOf<StrokePath>()

    // Remote Shared resources list
    private val _sharedResources = MutableStateFlow<List<SharedResource>>(emptyList())
    val sharedResources = _sharedResources.asStateFlow()

    private val _isRefreshingResources = MutableStateFlow(false)
    val isRefreshingResources = _isRefreshingResources.asStateFlow()

    // Timer Job for intense ticker
    private var timerJob: Job? = null

    init {
        // Initialize Speech engine
        tts = TextToSpeech(context, this)

        // Load config limits
        _isTtsEnabled.value = prefs.getBoolean("tts_enabled", true)
        _isIntenseMode.value = prefs.getBoolean("intense_mode", false)
        _intenseTimerLimit.value = prefs.getInt("intense_timer", 8)
        _isWatchBezelSimulated.value = prefs.getBoolean("watch_bezel_simulated", true)

        // Read local persistent wrong cards
        loadWrongsFromPrefs()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
                ttsReady = true
            }
        }
    }

    fun toggleTts(enabled: Boolean) {
        _isTtsEnabled.value = enabled
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
    }

    fun toggleIntenseMode(enabled: Boolean) {
        _isIntenseMode.value = enabled
        prefs.edit().putBoolean("intense_mode", enabled).apply()
        // Stop active ticker and recreate session
        stopIntenseTimer()
        startNewSession()
    }

    fun setIntenseTimerLimit(limit: Int) {
        _intenseTimerLimit.value = limit
        prefs.edit().putInt("intense_timer", limit).apply()
        stopIntenseTimer()
        startNewSession()
    }

    fun toggleWatchBezel(enabled: Boolean) {
        _isWatchBezelSimulated.value = enabled
        prefs.edit().putBoolean("watch_bezel_simulated", enabled).apply()
    }

    /**
     * Connects with Supabase API
     */
    fun performLogin(email: String, pwd: String, onSuccess: () -> Unit) {
        if (email.isEmpty() || pwd.isEmpty()) {
            _loginError.value = "请完整输入邮箱和密码"
            return
        }
        _isLoading.value = true
        _loginError.value = null

        viewModelScope.launch {
            val result = SupabaseService.login(email, pwd)
            _isLoading.value = false
            if (result.isSuccess) {
                _isLoggedIn.value = true
                _userEmail.value = SupabaseService.userEmail ?: ""
                onSuccess()
                syncFromCloud()
            } else {
                _loginError.value = result.exceptionOrNull()?.message ?: "登录异常"
            }
        }
    }

    fun logout() {
        SupabaseService.logout()
        _isLoggedIn.value = false
        _userEmail.value = ""
        _allDictations.value = emptyList()
        _availableGroups.value = emptyList()
        _selectedGroups.value = emptySet()
        stopIntenseTimer()
    }

    suspend fun syncFromCloud() {
        _isLoading.value = true
        val result = SupabaseService.fetchDictationItems()
        _isLoading.value = false
        if (result.isSuccess) {
            val list = result.getOrNull() ?: emptyList()
            _allDictations.value = list

            // Collect unique group names
            val grps = list.map { it.groupName }.distinct().sorted()
            _availableGroups.value = grps
            // Autoselect first library if empty
            if (_selectedGroups.value.isEmpty() && grps.isNotEmpty()) {
                _selectedGroups.value = setOf(grps.first())
            }
            startNewSession()
        }
    }

    fun toggleGroupSelection(groupName: String) {
        val current = _selectedGroups.value.toMutableSet()
        if (current.contains(groupName)) {
            // Keep at least one group if lists exist
            if (current.size > 1) {
                current.remove(groupName)
            }
        } else {
            current.add(groupName)
        }
        _selectedGroups.value = current
        startNewSession()
    }

    fun setFilterType(type: String) {
        _filterType.value = type
        startNewSession()
    }

    /**
     * Set up current study questions card deck
     */
    fun startNewSession() {
        stopIntenseTimer()
        _isSessionFinished.value = false
        _correctCount.value = 0
        _wrongCount.value = 0
        _checkAnswerVisible.value = false
        drawStrokes.clear()

        val allItems = _allDictations.value
        val listSelected = allItems.filter { _selectedGroups.value.contains(it.groupName) }

        var deck = when (_filterType.value) {
            "wrong" -> _wrongsList.value
            "poetry" -> listSelected.filter { it.category.contains("诗") || it.category.contains("句") }
            "note" -> listSelected.filter { it.category.contains("注") || it.category.contains("释") || it.category.contains("文") }
            "word" -> listSelected.filter { !it.category.contains("诗") && !it.category.contains("句") && !it.category.contains("注") && !it.category.contains("释") }
            else -> listSelected
        }

        // Shuffle questions
        deck = deck.shuffled()
        _activeQuestionList.value = deck
        _currentQuestionIndex.value = 0

        if (deck.isNotEmpty()) {
            _currentQuestion.value = deck.first()
            speakQuestion(deck.first().question)
            if (_isIntenseMode.value) {
                startIntenseTimer()
            }
        } else {
            _currentQuestion.value = null
        }
    }

    /**
     * Jumps to next card
     */
    fun nextQuestion() {
        stopIntenseTimer()
        _checkAnswerVisible.value = false
        drawStrokes.clear()

        val nextIndex = _currentQuestionIndex.value + 1
        val deck = _activeQuestionList.value

        if (nextIndex < deck.size) {
            _currentQuestionIndex.value = nextIndex
            val q = deck[nextIndex]
            _currentQuestion.value = q
            speakQuestion(q.question)
            if (_isIntenseMode.value) {
                startIntenseTimer()
            }
        } else {
            _isSessionFinished.value = true
            _currentQuestion.value = null
        }
    }

    /**
     * Triggers active TTS voicing
     */
    fun speakQuestion(text: String) {
        if (!_isTtsEnabled.value) return
        viewModelScope.launch {
            try {
                // Slightly offset to avoid cutting initial sound during transitions
                delay(100)
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DictationID")
            } catch (e: Exception) {
                Log.e("MainViewModel", "TTS Fail", e)
            }
        }
    }

    /**
     * Mark result
     */
    fun markAnswer(isCorrect: Boolean) {
        val q = _currentQuestion.value ?: return

        if (isCorrect) {
            _correctCount.value += 1
            // Remove from wrong book list if correct
            removeWrong(q)
        } else {
            _wrongCount.value += 1
            // Save to local persistence & wrong screen
            saveWrong(q)
        }

        nextQuestion()
    }

    fun revealAnswer() {
        _checkAnswerVisible.value = true
        stopIntenseTimer()
    }

    // Timer coroutine
    private fun startIntenseTimer() {
        stopIntenseTimer()
        val limit = _intenseTimerLimit.value
        _timeLeft.value = limit

        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (_timeLeft.value > 0) {
                delay(1000)
                _timeLeft.value -= 1
            }
            // Timeout event: automatically mark wrong and jump
            markAnswer(false)
        }
    }

    private fun stopIntenseTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // Wrong Book utilities using SharedPreferences
    private fun saveWrong(item: DictationItem) {
        val current = _wrongsList.value.toMutableList()
        if (current.none { it.question == item.question }) {
            current.add(item)
            _wrongsList.value = current
            saveWrongsToPrefs(current)
        }
    }

    private fun removeWrong(item: DictationItem) {
        val current = _wrongsList.value.toMutableList()
        val index = current.indexOfFirst { it.question == item.question }
        if (index != -1) {
            current.removeAt(index)
            _wrongsList.value = current
            saveWrongsToPrefs(current)
        }
    }

    fun clearAllWrongs() {
        _wrongsList.value = emptyList()
        saveWrongsToPrefs(emptyList())
        if (_filterType.value == "wrong") {
            startNewSession()
        }
    }

    private fun saveWrongsToPrefs(list: List<DictationItem>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject().apply {
                put("q", item.question)
                put("a", item.answer)
                put("cat", item.category)
                put("group_name", item.groupName)
                put("user_id", item.userId)
            }
            arr.put(obj)
        }
        prefs.edit().putString("saved_wrongs_json_v1", arr.toString()).apply()
    }

    private fun loadWrongsFromPrefs() {
        try {
            val jsonStr = prefs.getString("saved_wrongs_json_v1", null)
            if (!jsonStr.isNullOrEmpty()) {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<DictationItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(DictationItem.fromJSON(obj))
                }
                _wrongsList.value = list
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Fail loading wrongs", e)
        }
    }

    // Shared Resources fetching
    fun fetchResources() {
        _isRefreshingResources.value = true
        viewModelScope.launch {
            val res = SupabaseService.fetchSharedResources()
            _isRefreshingResources.value = false
            if (res.isSuccess) {
                _sharedResources.value = res.getOrNull() ?: emptyList()
            }
        }
    }

    /**
     * Downloads shared resource deck into a local group, preserving user data links
     */
    fun downloadResourceItem(resource: SharedResource, targetGroup: String, onComplete: (String) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val dataToInsert = mutableListOf<DictationItem>()
                val arr = resource.rawJsonData
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    dataToInsert.add(
                        DictationItem(
                            id = null,
                            question = obj.optString("q", ""),
                            answer = obj.optString("a", ""),
                            category = obj.optString("cat", "综合"),
                            groupName = targetGroup,
                            userId = SupabaseService.userId
                        )
                    )
                }

                val dbResult = SupabaseService.insertDictationItems(dataToInsert)
                _isLoading.value = false
                if (dbResult.isSuccess) {
                    onComplete("下载成功！导入 ${dataToInsert.size} 个词条到 [${targetGroup}]")
                    syncFromCloud()
                } else {
                    onComplete("下载失败: ${dbResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _isLoading.value = false
                onComplete("数据拆解出错: ${e.message}")
            }
        }
    }

    /**
     * Upload customized dictation rows (merging / replacing cloud databases)
     */
    fun importCustomData(group: String, jsonText: String, clearFirst: Boolean, onComplete: (String) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val arr = JSONArray(jsonText)
                if (clearFirst) {
                    SupabaseService.deleteDictationGroup(group)
                }

                val list = mutableListOf<DictationItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        DictationItem(
                            id = null,
                            question = obj.optString("q", ""),
                            answer = obj.optString("a", ""),
                            category = obj.optString("cat", "自定义"),
                            groupName = group,
                            userId = SupabaseService.userId
                        )
                    )
                }

                val result = SupabaseService.insertDictationItems(list)
                _isLoading.value = false
                if (result.isSuccess) {
                    onComplete("同步导入 ${list.size} 个词条！")
                    syncFromCloud()
                } else {
                    onComplete("同步失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _isLoading.value = false
                onComplete("输入 JSON 格式解析失败: ${e.message}")
            }
        }
    }

    /**
     * Deletes whole local checked library files from Supabase
     */
    fun deleteGroupLibrary(groupName: String, onComplete: (String) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            val res = SupabaseService.deleteDictationGroup(groupName)
            _isLoading.value = false
            if (res.isSuccess) {
                onComplete("词库 [${groupName}] 已成功删除")
                syncFromCloud()
            } else {
                onComplete("删除失败: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Allows shared resource publication from inside the app!
     */
    fun shareDeckToCenter(title: String, desc: String, rawJsonInput: String, coverUrl: String, onComplete: (String) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val arr = JSONArray(rawJsonInput)
                val ret = SupabaseService.publishSharedResource(title, desc, arr, coverUrl)
                _isLoading.value = false
                if (ret.isSuccess) {
                    onComplete("发布成功！资源中心已更新。")
                    fetchResources()
                } else {
                    onComplete("发布失败: ${ret.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _isLoading.value = false
                onComplete("解析出错: ${e.message}")
            }
        }
    }

    fun removeSharedResource(id: String, onComplete: (String) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            val res = SupabaseService.deleteSharedResource(id)
            _isLoading.value = false
            if (res.isSuccess) {
                onComplete("删除成功")
                fetchResources()
            } else {
                onComplete("删除失败: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopIntenseTimer()
        tts?.shutdown()
    }
}

/**
 * Custom line representation inside Handwriting Board
 */
data class StrokePath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)
