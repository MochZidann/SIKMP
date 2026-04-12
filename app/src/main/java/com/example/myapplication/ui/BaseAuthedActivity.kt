package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myapplication.R
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.model.Role

abstract class BaseAuthedActivity : AppCompatActivity() {
    protected val session by lazy { SessionManager(this) }

    protected open fun allowedRoles(): Set<Role> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Fix: Header is white, so status bar icons MUST be dark (AppearanceLightStatusBars = true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        super.onCreate(savedInstanceState)
        if (!session.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        val allowed = allowedRoles()
        if (allowed.isNotEmpty()) {
            val role = session.role()
            if (role == null || !allowed.contains(role)) {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
                return
            }
        }
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        if (view != null) applyEdgeToEdgeInsets(view)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        val content = findViewById<ViewGroup>(android.R.id.content)
        val root = if (content.childCount > 0) content.getChildAt(0) else content
        applyEdgeToEdgeInsets(root)
    }

    private fun applyEdgeToEdgeInsets(root: View) {
        val already = root.getTag(R.id.edge_to_edge_applied) as? Boolean ?: false
        if (already) return
        root.setTag(R.id.edge_to_edge_applied, true)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Try to find toolbar in the whole view hierarchy
            val toolbar = v.findViewById<View?>(R.id.toolbar)
            
            if (toolbar != null && toolbar.visibility != View.GONE) {
                val lp = toolbar.layoutParams
                val originalHeight = (toolbar.getTag(R.id.edge_to_edge_original_height) as? Int) ?: lp.height.also {
                    toolbar.setTag(R.id.edge_to_edge_original_height, it)
                }
                val originalPaddingTop = (toolbar.getTag(R.id.edge_to_edge_original_padding_top) as? Int) ?: toolbar.paddingTop.also {
                    toolbar.setTag(R.id.edge_to_edge_original_padding_top, it)
                }
                
                if (originalHeight > 0) {
                    lp.height = originalHeight + bars.top
                    toolbar.layoutParams = lp
                }
                
                toolbar.setPadding(
                    toolbar.paddingLeft,
                    originalPaddingTop + bars.top,
                    toolbar.paddingRight,
                    toolbar.paddingBottom
                )
                
                // Content below toolbar shouldn't have top padding from bars
                v.setPadding(bars.left, 0, bars.right, bars.bottom)
            } else {
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
    }
}
