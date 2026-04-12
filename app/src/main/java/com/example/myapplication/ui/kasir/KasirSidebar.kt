package com.example.myapplication.ui.kasir

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.example.myapplication.R
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.ui.DashboardActivity
import com.example.myapplication.ui.LoginActivity
import com.example.myapplication.ui.PosActivity
import com.example.myapplication.ui.ProductManagementActivity
import com.example.myapplication.ui.ReportsActivity

enum class KasirMenu {
    DASHBOARD,
    POS,
    REPORTS,
    PRODUCTS
}

object KasirSidebar {
    fun bind(activity: Activity, current: KasirMenu) {
        val session = SessionManager(activity)
        activity.findViewById<TextView?>(R.id.sidebarSubtitle)?.text =
            listOfNotNull(session.name(), session.username()).joinToString(" • ").ifBlank { "Kasir" }

        val menuDashboard = activity.findViewById<View>(R.id.menuDashboard)
        val menuPos = activity.findViewById<View>(R.id.menuPos)
        val menuReports = activity.findViewById<View>(R.id.menuReports)
        val menuProducts = activity.findViewById<View>(R.id.menuProducts)
        val menuLogout = activity.findViewById<View>(R.id.menuLogout)

        menuDashboard.setOnClickListener {
            if (current != KasirMenu.DASHBOARD) {
                activity.startActivity(Intent(activity, DashboardActivity::class.java))
                activity.finish()
            }
        }
        menuPos.setOnClickListener {
            if (current != KasirMenu.POS) {
                activity.startActivity(Intent(activity, PosActivity::class.java))
                activity.finish()
            }
        }
        menuReports.setOnClickListener {
            if (current != KasirMenu.REPORTS) {
                activity.startActivity(Intent(activity, ReportsActivity::class.java))
                activity.finish()
            }
        }
        menuProducts.setOnClickListener {
            if (current != KasirMenu.PRODUCTS) {
                activity.startActivity(Intent(activity, ProductManagementActivity::class.java))
                activity.finish()
            }
        }
        menuLogout.setOnClickListener {
            session.clear()
            activity.startActivity(Intent(activity, LoginActivity::class.java))
            activity.finish()
        }

        highlight(menuDashboard, current == KasirMenu.DASHBOARD)
        highlight(menuPos, current == KasirMenu.POS)
        highlight(menuReports, current == KasirMenu.REPORTS)
        highlight(menuProducts, current == KasirMenu.PRODUCTS)
    }

    private fun highlight(view: View, active: Boolean) {
        view.isActivated = active
        view.alpha = if (active) 1.0f else 0.75f
    }
}

