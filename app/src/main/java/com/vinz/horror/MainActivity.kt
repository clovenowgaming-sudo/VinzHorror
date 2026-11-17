package com.vinz.horror

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class MainActivity : Activity(), GameEvents {

    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private var gameView: GameView? = null
    private var hud: HudView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        prefs = getSharedPreferences("vinzhorror_prefs", Context.MODE_PRIVATE)
        root = FrameLayout(this)
        setContentView(root)
        showMainMenu()
    }

    private fun clearRoot() {
        root.removeAllViews()
    }

    private fun showMainMenu() {
        clearRoot()
        gameView = null
        hud = null

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setBackgroundColor(Color.BLACK)

        val title = TextView(this)
        title.text = "HOLLOW WARD"
        title.setTextColor(Color.rgb(200, 40, 40))
        title.textSize = 42f
        title.gravity = Gravity.CENTER
        layout.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "it is not empty"
        subtitle.setTextColor(Color.GRAY)
        subtitle.textSize = 16f
        subtitle.gravity = Gravity.CENTER
        val subParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        subParams.topMargin = 8
        subParams.bottomMargin = 60
        layout.addView(subtitle, subParams)

        val playBtn = Button(this)
        playBtn.text = "ENTER"
        playBtn.setOnClickListener { startGame() }
        layout.addView(playBtn, menuBtnParams())

        val settingsBtn = Button(this)
        settingsBtn.text = "SETTINGS"
        settingsBtn.setOnClickListener { showSettings() }
        layout.addView(settingsBtn, menuBtnParams())

        val quitBtn = Button(this)
        quitBtn.text = "QUIT"
        quitBtn.setOnClickListener { finish() }
        layout.addView(quitBtn, menuBtnParams())

        root.addView(layout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun menuBtnParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(600, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = 16
        return p
    }

    private fun showSettings() {
        clearRoot()
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setBackgroundColor(Color.BLACK)

        val title = TextView(this)
        title.text = "GRAPHICS QUALITY"
        title.setTextColor(Color.WHITE)
        title.textSize = 26f
        layout.addView(title)

        val group = RadioGroup(this)
        group.orientation = RadioGroup.VERTICAL
        val current = GraphicsQuality.fromOrdinalSafe(prefs.getInt("quality", GraphicsQuality.MEDIUM.ordinal))
        for (q in GraphicsQuality.values()) {
            val rb = RadioButton(this)
            rb.text = q.label
            rb.setTextColor(Color.WHITE)
            rb.id = q.ordinal + 1000
            rb.isChecked = q == current
            group.addView(rb)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val q = GraphicsQuality.fromOrdinalSafe(checkedId - 1000)
            prefs.edit().putInt("quality", q.ordinal).apply()
        }
        val groupParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        groupParams.topMargin = 24
        groupParams.bottomMargin = 40
        layout.addView(group, groupParams)

        val backBtn = Button(this)
        backBtn.text = "BACK"
        backBtn.setOnClickListener { showMainMenu() }
        layout.addView(backBtn, menuBtnParams())

        root.addView(layout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startGame() {
        clearRoot()
        val quality = GraphicsQuality.fromOrdinalSafe(prefs.getInt("quality", GraphicsQuality.MEDIUM.ordinal))
        val renderer = GameRenderer(this, this)
        renderer.quality = quality

        val gv = GameView(this, renderer)
        gameView = gv
        root.addView(gv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val h = HudView(this)
        hud = h
        root.addView(h, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val flashBtn = Button(this)
        flashBtn.text = "LIGHT"
        flashBtn.setOnClickListener { renderer.flashlightToggle = true }
        val flashParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        flashParams.gravity = Gravity.BOTTOM or Gravity.END
        flashParams.rightMargin = 40
        flashParams.bottomMargin = 40
        root.addView(flashBtn, flashParams)

        val menuBtn = Button(this)
        menuBtn.text = "MENU"
        menuBtn.setOnClickListener { showMainMenu() }
        val menuParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        menuParams.gravity = Gravity.TOP or Gravity.END
        menuParams.topMargin = 24
        menuParams.rightMargin = 24
        root.addView(menuBtn, menuParams)
    }

    override fun onJumpscare() {
        mainHandler.post {
            hud?.jumpscareFlash = true
            hud?.showGameOver = true
            hud?.invalidate()
            try {
                val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(350)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onBatteryChanged(pct: Float) {
        mainHandler.post {
            hud?.batteryPct = pct
            hud?.invalidate()
        }
    }

    override fun onRespawn() {
        mainHandler.post {
            hud?.jumpscareFlash = false
            hud?.showGameOver = false
            hud?.invalidate()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        gameView?.onResume()
    }
}
