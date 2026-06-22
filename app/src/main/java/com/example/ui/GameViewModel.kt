package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSynth
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

enum class GameScreen {
    START_TRIGGER,
    MODES_MENU,
    SUB_MENU,
    IN_GAME,
    PAUSED,
    STAGE_WIN,
    GAME_OVER
}

enum class GameDifficulty {
    EASY, MEDIUM, HARD, FREE_RUN
}

enum class GameTheme {
    CYBERPUNK,
    MATRIX,
    SUNSET,
    OBSIDIAN
}

enum class PowerUpType {
    SHIELD,
    FREEZE,
    DOUBLE_POINTS
}

data class Point2D(val x: Int, val y: Int)

data class PowerUp(
    val x: Int,
    val y: Int,
    val type: PowerUpType,
    val spawnTime: Long
)

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    var alpha: Float,
    val decay: Float,
    val color: String
)

data class PortalPair(
    val first: Point2D,
    val second: Point2D,
    val colorHex: String
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val db = GameDatabase.getDatabase(application)
    private val repository = GameRepository(db.stageDao())

    // Exposed States
    private val _screen = MutableStateFlow(GameScreen.START_TRIGGER)
    val screen: StateFlow<GameScreen> = _screen.asStateFlow()

    private val _difficulty = MutableStateFlow(GameDifficulty.EASY)
    val difficulty: StateFlow<GameDifficulty> = _difficulty.asStateFlow()

    private val _theme = MutableStateFlow(GameTheme.CYBERPUNK)
    val theme: StateFlow<GameTheme> = _theme.asStateFlow()

    private val _challengePlus = MutableStateFlow(false)
    val challengePlus: StateFlow<Boolean> = _challengePlus.asStateFlow()

    private val _selectedStageId = MutableStateFlow(1)
    val selectedStageId: StateFlow<Int> = _selectedStageId.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _targetScore = MutableStateFlow(10)
    val targetScore: StateFlow<Int> = _targetScore.asStateFlow()

    private val _health = MutableStateFlow(5)
    val health: StateFlow<Int> = _health.asStateFlow()

    private val _livesFractionCount = MutableStateFlow(0) // increment on eat, 15 restores a life

    private val _highScore = MutableStateFlow(0)
    val highScore: StateFlow<Int> = _highScore.asStateFlow()

    private val _snake = MutableStateFlow<List<Point2D>>(emptyList())
    val snake: StateFlow<List<Point2D>> = _snake.asStateFlow()

    private val _food = MutableStateFlow(Point2D(0, 0))
    val food: StateFlow<Point2D> = _food.asStateFlow()

    private val _activePowerUp = MutableStateFlow<PowerUp?>(null)
    val activePowerUp: StateFlow<PowerUp?> = _activePowerUp.asStateFlow()

    private val _activeShield = MutableStateFlow(false)
    val activeShield: StateFlow<Boolean> = _activeShield.asStateFlow()

    private val _activeFreeze = MutableStateFlow(false)
    val activeFreeze: StateFlow<Boolean> = _activeFreeze.asStateFlow()

    private val _activeDoublePoints = MutableStateFlow(false)
    val activeDoublePoints: StateFlow<Boolean> = _activeDoublePoints.asStateFlow()

    private val _powerUpBanner = MutableStateFlow<String?>(null)
    val powerUpBanner: StateFlow<String?> = _powerUpBanner.asStateFlow()

    // Particles for Explosion on crashes
    private val _particles = MutableStateFlow<List<Particle>>(emptyList())
    val particles: StateFlow<List<Particle>> = _particles.asStateFlow()

    // Reboot countdown value during system reset
    private val _rebootCountdown = MutableStateFlow<Int?>(null)
    val rebootCountdown: StateFlow<Int?> = _rebootCountdown.asStateFlow()

    // Local stats records
    val dbStagesFlow: StateFlow<List<StageRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Motion variables
    private var dx = 1
    private var dy = 0
    private var nextDx = 1
    private var nextDy = 0

    // Game loops
    private var gameJob: Job? = null
    private var soundtrackJob: Job? = null
    private var powerUpJob: Job? = null

    // Constants
    val GRID_COUNT = 20

    val DYNAMIC_PORTALS = listOf(
        PortalPair(Point2D(1, 9), Point2D(18, 9), "#00f3ff"),      // Pair 1: Cyan
        PortalPair(Point2D(9, 1), Point2D(9, 18), "#ff007f"),      // Pair 2: Pink
        PortalPair(Point2D(1, 18), Point2D(18, 1), "#fff200")      // Pair 3: Yellow
    )

    init {
        viewModelScope.launch {
            repository.preloadStages()
        }
    }

    fun setDifficulty(diff: GameDifficulty) {
        _difficulty.value = diff
        _screen.value = GameScreen.SUB_MENU
    }

    fun setTheme(newTheme: GameTheme) {
        _theme.value = newTheme
    }

    fun toggleChallengePlus() {
        _challengePlus.value = !_challengePlus.value
    }

    fun backToMainMenu() {
        _screen.value = GameScreen.MODES_MENU
    }

    fun navigateToModesMenu() {
        _screen.value = GameScreen.MODES_MENU
    }

    fun quitToMenu() {
        stopGame()
        _screen.value = GameScreen.START_TRIGGER
    }

    fun selectStageAndStart(stageId: Int) {
        _selectedStageId.value = stageId
        startGame(stageId)
    }

    fun retryCurrentStage() {
        startGame(_selectedStageId.value)
    }

    fun advanceToNextStage() {
        val nextId = if (_selectedStageId.value >= 30) 1 else _selectedStageId.value + 1
        _selectedStageId.value = nextId
        startGame(nextId)
    }

    fun togglePause() {
        if (_screen.value == GameScreen.IN_GAME) {
            _screen.value = GameScreen.PAUSED
            stopSoundtrack()
        } else if (_screen.value == GameScreen.PAUSED) {
            _screen.value = GameScreen.IN_GAME
            startSoundtrack()
        }
    }

    fun changeDirection(newDx: Int, newDy: Int) {
        // Prevent 180 degree instant suicide turn
        if (newDx != 0 && dx == -newDx) return
        if (newDy != 0 && dy == -newDy) return
        nextDx = newDx
        nextDy = newDy
    }

    private fun startGame(stageId: Int) {
        stopGame()
        _screen.value = GameScreen.IN_GAME

        // Set parameters based on Difficulty/Stage
        val isFreeRun = _difficulty.value == GameDifficulty.FREE_RUN
        _score.value = 0
        _livesFractionCount.value = 0
        _health.value = if (isFreeRun) 999 else 5
        _activeShield.value = false
        _activeFreeze.value = false
        _activeDoublePoints.value = false
        _activePowerUp.value = null
        _powerUpBanner.value = null
        _particles.value = emptyList()
        _rebootCountdown.value = null

        if (isFreeRun) {
            _targetScore.value = 9999
        } else {
            _targetScore.value = when {
                stageId <= 10 -> 10
                stageId <= 20 -> 20
                else -> 35
            }
        }

        // Fetch high score from local DB
        viewModelScope.launch {
            val record = repository.getRecordSync(stageId)
            _highScore.value = record?.highScore ?: 0
        }

        resetSnake()
        spawnFood()

        // Start active game ticker
        gameJob = viewModelScope.launch {
            while (true) {
                if (_screen.value == GameScreen.IN_GAME && _rebootCountdown.value == null) {
                    tickGame()
                }
                val speed = getGameSpeed()
                delay(speed)
            }
        }

        // Start synth soundtrack
        startSoundtrack()

        // Power-ups spawn job
        powerUpJob = viewModelScope.launch {
            while (true) {
                delay(12000L) // check core spawn every 12 seconds
                if (_screen.value == GameScreen.IN_GAME && _activePowerUp.value == null) {
                    spawnPowerUp()
                }
            }
        }
    }

    private fun getGameSpeed(): Long {
        val baseSpeed = when (_difficulty.value) {
            GameDifficulty.EASY -> 180L
            GameDifficulty.MEDIUM -> 140L
            GameDifficulty.HARD -> 100L
            GameDifficulty.FREE_RUN -> {
                val sc = _score.value
                when {
                    sc <= 10 -> 180L
                    sc <= 20 -> 130L
                    else -> 85L
                }
            }
        }

        val actualBase = if (_difficulty.value != GameDifficulty.FREE_RUN && _challengePlus.value) {
            // CHALLENGE+ speed drops as score goes up
            val speedDrop = (_score.value / 3) * 6L
            val baselineLimit = when {
                _selectedStageId.value <= 10 -> 120L
                _selectedStageId.value <= 20 -> 80L
                else -> 50L
            }
            maxOf(baseSpeed - speedDrop, baselineLimit)
        } else {
            baseSpeed
        }

        return if (_activeFreeze.value) {
            actualBase + 60L // slow down
        } else {
            actualBase
        }
    }

    private fun resetSnake() {
        dx = 1
        dy = 0
        nextDx = 1
        nextDy = 0
        _snake.value = listOf(
            Point2D(5, 1),
            Point2D(4, 1),
            Point2D(3, 1)
        )
    }

    fun getActiveWalls(): List<Point2D> {
        val layoutId = ((_selectedStageId.value - 1) % 10) + 1
        return when (layoutId) {
            1 -> emptyList()
            2 -> listOf(
                Point2D(2, 2), Point2D(3, 2), Point2D(2, 3), Point2D(3, 3),
                Point2D(16, 2), Point2D(17, 2), Point2D(16, 3), Point2D(17, 3),
                Point2D(2, 16), Point2D(3, 16), Point2D(2, 17), Point2D(3, 17),
                Point2D(16, 16), Point2D(17, 16), Point2D(16, 17), Point2D(17, 17)
            )
            3 -> listOf(
                Point2D(10, 7), Point2D(10, 8), Point2D(10, 9), Point2D(10, 10), Point2D(10, 11), Point2D(10, 12), Point2D(10, 13),
                Point2D(7, 10), Point2D(8, 10), Point2D(9, 10), Point2D(11, 10), Point2D(12, 10), Point2D(13, 10)
            )
            4 -> listOf(
                Point2D(6, 3), Point2D(6, 4), Point2D(6, 5), Point2D(6, 6), Point2D(6, 7),
                Point2D(6, 12), Point2D(6, 13), Point2D(6, 14), Point2D(6, 15), Point2D(6, 16),
                Point2D(13, 3), Point2D(13, 4), Point2D(13, 5), Point2D(13, 6), Point2D(13, 7),
                Point2D(13, 12), Point2D(13, 13), Point2D(13, 14), Point2D(13, 15), Point2D(13, 16)
            )
            5 -> listOf(
                Point2D(10, 3), Point2D(10, 4), Point2D(10, 5),
                Point2D(10, 14), Point2D(10, 15), Point2D(10, 16)
            )
            6 -> listOf(
                Point2D(5, 4), Point2D(5, 5), Point2D(5, 6), Point2D(5, 7), Point2D(5, 8),
                Point2D(5, 11), Point2D(5, 12), Point2D(5, 13), Point2D(5, 14), Point2D(5, 15),
                Point2D(14, 4), Point2D(14, 5), Point2D(14, 6), Point2D(14, 7), Point2D(14, 8),
                Point2D(14, 11), Point2D(14, 12), Point2D(14, 13), Point2D(14, 14), Point2D(14, 15),
                Point2D(6, 9), Point2D(7, 9), Point2D(8, 9), Point2D(9, 9), Point2D(10, 9), Point2D(11, 9), Point2D(12, 9), Point2D(13, 9)
            )
            7 -> listOf(
                Point2D(0, 5), Point2D(1, 5), Point2D(2, 5), Point2D(3, 5), Point2D(4, 5), Point2D(5, 5),
                Point2D(6, 5), Point2D(7, 5), Point2D(8, 5), Point2D(9, 5), Point2D(10, 5), Point2D(11, 5),
                Point2D(12, 5), Point2D(13, 5), Point2D(14, 5),
                Point2D(5, 12), Point2D(6, 12), Point2D(7, 12), Point2D(8, 12), Point2D(9, 12), Point2D(10, 12),
                Point2D(11, 12), Point2D(12, 12), Point2D(13, 12), Point2D(14, 12), Point2D(15, 12),
                Point2D(16, 12), Point2D(17, 12), Point2D(18, 12), Point2D(19, 12)
            )
            8 -> listOf(
                Point2D(6, 6), Point2D(7, 6), Point2D(8, 6), Point2D(9, 6), Point2D(11, 6), Point2D(12, 6), Point2D(13, 6), Point2D(14, 6),
                Point2D(6, 7), Point2D(14, 7), Point2D(6, 8), Point2D(14, 8), Point2D(6, 9), Point2D(14, 9),
                Point2D(6, 11), Point2D(14, 11), Point2D(6, 12), Point2D(14, 12), Point2D(6, 13), Point2D(14, 13),
                Point2D(6, 14), Point2D(7, 14), Point2D(8, 14), Point2D(9, 14), Point2D(10, 14), Point2D(11, 14),
                Point2D(12, 14), Point2D(13, 14), Point2D(14, 14)
            )
            9 -> listOf(
                Point2D(10, 0), Point2D(10, 1), Point2D(10, 2), Point2D(10, 3), Point2D(10, 4),
                Point2D(10, 15), Point2D(10, 16), Point2D(10, 17), Point2D(10, 18), Point2D(10, 19),
                Point2D(0, 10), Point2D(1, 10), Point2D(2, 10), Point2D(3, 10), Point2D(4, 10),
                Point2D(15, 10), Point2D(16, 10), Point2D(17, 10), Point2D(18, 10), Point2D(19, 10)
            )
            10 -> listOf(
                Point2D(2, 2), Point2D(3, 3), Point2D(4, 4), Point2D(5, 5), Point2D(6, 6),
                Point2D(13, 13), Point2D(14, 14), Point2D(15, 15), Point2D(16, 16), Point2D(17, 17),
                Point2D(17, 2), Point2D(16, 3), Point2D(15, 4), Point2D(14, 5), Point2D(13, 6),
                Point2D(6, 13), Point2D(5, 14), Point2D(4, 15), Point2D(3, 16), Point2D(2, 17)
            )
            else -> emptyList()
        }
    }

    private fun getActivePortalsCount(): Int {
        return when {
            _difficulty.value == GameDifficulty.FREE_RUN -> {
                val sc = ((_selectedStageId.value - 1) % 10) + 1
                if (sc <= 3) 1 else if (sc <= 7) 2 else 3
            }
            _selectedStageId.value <= 10 -> 1
            _selectedStageId.value <= 20 -> 2
            else -> 3
        }
    }

    private fun spawnFood() {
        val currentWalls = getActiveWalls()
        val portalCount = getActivePortalsCount()
        val currentSnake = _snake.value

        var valid = false
        var rx = 0
        var ry = 0

        while (!valid) {
            rx = (0 until GRID_COUNT).random()
            ry = (0 until GRID_COUNT).random()

            val hitWall = currentWalls.any { it.x == rx && it.y == ry }
            val hitSnake = currentSnake.any { it.x == rx && it.y == ry }
            var hitPortal = false

            for (i in 0 until portalCount) {
                val p = DYNAMIC_PORTALS[i]
                if ((rx == p.first.x && ry == p.first.y) || (rx == p.second.x && ry == p.second.y)) {
                    hitPortal = true
                    break
                }
            }

            valid = !hitWall && !hitSnake && !hitPortal
        }

        _food.value = Point2D(rx, ry)
    }

    private fun spawnPowerUp() {
        val currentWalls = getActiveWalls()
        val currentSnake = _snake.value
        val currentFood = _food.value

        var rx = 0
        var ry = 0
        var valids = false

        while (!valids) {
            rx = (0 until GRID_COUNT).random()
            ry = (0 until GRID_COUNT).random()

            val hitWall = currentWalls.any { it.x == rx && it.y == ry }
            val hitSnake = currentSnake.any { it.x == rx && it.y == ry }
            val hitFood = rx == currentFood.x && ry == currentFood.y

            valids = !hitWall && !hitSnake && !hitFood
        }

        val type = PowerUpType.values().random()
        _activePowerUp.value = PowerUp(rx, ry, type, System.currentTimeMillis())
    }

    private fun tickGame() {
        dx = nextDx
        dy = nextDy

        val oldSnake = _snake.value
        if (oldSnake.isEmpty()) return

        val head = oldSnake.first()
        var newX = head.x + dx
        var newY = head.y + dy

        // Boundaries checks
        if (newX < 0 || newX >= GRID_COUNT || newY < 0 || newY >= GRID_COUNT) {
            handleCrash()
            return
        }

        // Walls checks
        val currentWalls = getActiveWalls()
        if (currentWalls.any { it.x == newX && it.y == newY }) {
            handleCrash()
            return
        }

        // Self-collision checking
        if (oldSnake.any { it.x == newX && it.y == newY }) {
            handleCrash()
            return
        }

        // Portal Teleportation
        val pCount = getActivePortalsCount()
        for (i in 0 until pCount) {
            val portal = DYNAMIC_PORTALS[i]
            if (newX == portal.first.x && newY == portal.first.y) {
                newX = portal.second.x
                newY = portal.second.y
                AudioSynth.playTeleport()
                break
            } else if (newX == portal.second.x && newY == portal.second.y) {
                newX = portal.first.x
                newY = portal.first.y
                AudioSynth.playTeleport()
                break
            }
        }

        val newHead = Point2D(newX, newY)
        val newSnake = mutableListOf(newHead)

        // Check power-up collected
        val powerUp = _activePowerUp.value
        if (powerUp != null && newHead.x == powerUp.x && newHead.y == powerUp.y) {
            triggerPowerUp(powerUp.type)
            _activePowerUp.value = null
        }

        // Check food collected
        val foodPoint = _food.value
        if (newHead.x == foodPoint.x && newHead.y == foodPoint.y) {
            // Collected food
            val rewardPoints = if (_activeDoublePoints.value) 2 else 1
            _score.value += rewardPoints

            // Increment life fractions
            if (_difficulty.value != GameDifficulty.FREE_RUN) {
                _livesFractionCount.value += 1
                if (_livesFractionCount.value >= 15) {
                    if (_health.value < 5) {
                        _health.value += 1
                    }
                    _livesFractionCount.value = 0
                }
            }

            AudioSynth.playIntake()

            if (_score.value > _highScore.value) {
                _highScore.value = _score.value
                viewModelScope.launch {
                    repository.saveRecord(_selectedStageId.value, _score.value, false)
                }
            }

            // Did we Win Stage?
            if (_difficulty.value != GameDifficulty.FREE_RUN && _score.value >= _targetScore.value) {
                triggerWin()
                return
            }

            newSnake.addAll(oldSnake)
            spawnFood()
        } else {
            newSnake.addAll(oldSnake.dropLast(1))
        }

        _snake.value = newSnake

        // Decay power-up timer if spawned and idle
        if (powerUp != null && System.currentTimeMillis() - powerUp.spawnTime > 10000L) {
            // PowerUp despawned
            _activePowerUp.value = null
        }
    }

    private fun triggerPowerUp(type: PowerUpType) {
        AudioSynth.playTone(800f, 250, "sine", 1800f, 0.4f)
        viewModelScope.launch {
            when (type) {
                PowerUpType.SHIELD -> {
                    _activeShield.value = true
                    _powerUpBanner.value = "SHIELD ACTIVE: COLLISION PROTECTION ON"
                    delay(12000L)
                    _activeShield.value = false
                    if (_powerUpBanner.value?.contains("SHIELD") == true) _powerUpBanner.value = null
                }
                PowerUpType.FREEZE -> {
                    _activeFreeze.value = true
                    _powerUpBanner.value = "CHRONO FREEZE: SPEED DECELERATED"
                    delay(10000L)
                    _activeFreeze.value = false
                    if (_powerUpBanner.value?.contains("CHRONO") == true) _powerUpBanner.value = null
                }
                PowerUpType.DOUBLE_POINTS -> {
                    _activeDoublePoints.value = true
                    _powerUpBanner.value = "DOUBLE POINTS: CORE CORE VALUES x2"
                    delay(10000L)
                    _activeDoublePoints.value = false
                    if (_powerUpBanner.value?.contains("DOUBLE") == true) _powerUpBanner.value = null
                }
            }
        }
    }

    private fun handleCrash() {
        // Test shield first
        if (_activeShield.value) {
            _activeShield.value = false
            _powerUpBanner.value = "SHIELD BROKEN! ENERGY MATRIX PRESERVED"
            AudioSynth.playTeleport()
            viewModelScope.launch {
                delay(2000L)
                if (_powerUpBanner.value?.contains("BROKEN") == true) _powerUpBanner.value = null
            }
            // Bounce away or slide back slightly to avoid instant repeat crash
            resetSnake()
            return
        }

        // Real crash details
        AudioSynth.playCrash()
        if (_difficulty.value != GameDifficulty.FREE_RUN) {
            _health.value -= 1
        }

        // Trigger particles array
        triggerExplosionParticles()

        // Turn on system downtime reboot timer
        viewModelScope.launch {
            _rebootCountdown.value = 3
            while (_rebootCountdown.value!! > 0) {
                delay(1000L)
                _rebootCountdown.value = _rebootCountdown.value!! - 1
            }
            _rebootCountdown.value = null

            if (_difficulty.value != GameDifficulty.FREE_RUN && _health.value <= 0) {
                _screen.value = GameScreen.GAME_OVER
                stopSoundtrack()
            } else {
                resetSnake()
                spawnFood()
            }
        }
    }

    private fun triggerExplosionParticles() {
        val currentSnake = _snake.value
        val list = mutableListOf<Particle>()
        val colors = listOf("#39ff14", "#00f3ff", "#ff007f")

        currentSnake.forEach { pt ->
            // Particle center around grid center
            val px = pt.x * 25f + 12.5f
            val py = pt.y * 25f + 12.5f

            for (i in 0 until 6) {
                val angle = (Math.random() * Math.PI * 2)
                val speed = 2f + (Math.random() * 5f).toFloat()
                list.add(
                    Particle(
                        x = px,
                        y = py,
                        vx = cos(angle).toFloat() * speed,
                        vy = sin(angle).toFloat() * speed,
                        size = 3f + (Math.random() * 4f).toFloat(),
                        alpha = 1.0f,
                        decay = 0.02f + (Math.random() * 0.03f).toFloat(),
                        color = colors.random()
                    )
                )
            }
        }
        _particles.value = list

        // Run updates on physics particles
        viewModelScope.launch {
            var frames = 0
            while (frames < 45 && _particles.value.isNotEmpty()) {
                delay(25L)
                _particles.value = _particles.value.map { p ->
                    p.copy(
                        x = p.x + p.vx,
                        y = p.y + p.vy,
                        alpha = maxOf(p.alpha - p.decay, 0f)
                    )
                }.filter { it.alpha > 0f }
                frames++
            }
            _particles.value = emptyList()
        }
    }

    private fun triggerWin() {
        _screen.value = GameScreen.STAGE_WIN
        stopSoundtrack()
        AudioSynth.playWin()

        viewModelScope.launch {
            repository.saveRecord(_selectedStageId.value, _score.value, true)
        }
    }

    private fun startSoundtrack() {
        stopSoundtrack()
        soundtrackJob = viewModelScope.launch {
            val baseNotes = floatArrayOf(110f, 130f, 146f, 165f)
            var step = 0
            while (true) {
                if (_screen.value == GameScreen.IN_GAME && _rebootCountdown.value == null) {
                    val noteIndex = step % baseNotes.size
                    var frequency = baseNotes[noteIndex]
                    if (step % 4 == 0) frequency *= 1.25f // off-beat syncopation

                    // Dynamically map synth type by stage level
                    val type = when {
                        _selectedStageId.value <= 10 -> "sine"
                        _selectedStageId.value <= 20 -> "triangle"
                        else -> "square"
                    }

                    val duration = getGameSpeed().toInt() * 4
                    AudioSynth.playTone(frequency, (duration * 0.85).toInt(), type, volume = 0.025f)
                    step++
                    delay(duration.toLong())
                } else {
                    delay(200L)
                }
            }
        }
    }

    private fun stopSoundtrack() {
        soundtrackJob?.cancel()
        soundtrackJob = null
    }

    private fun stopGame() {
        gameJob?.cancel()
        gameJob = null
        stopSoundtrack()
        powerUpJob?.cancel()
        powerUpJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopGame()
    }
}
